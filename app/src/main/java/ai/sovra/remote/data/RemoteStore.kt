/*
 * App-wide remote-control state. Owns one RemoteClient for the paired computer,
 * refreshes only while the remote screen is in the foreground (the desktop closes idle
 * connections after 5 s, and the contract forbids polling while suspended), and maps
 * each desktop error to its own recovery path.
 */
package ai.sovra.remote.data

import ai.sovra.remote.protocol.ErrorCode
import ai.sovra.remote.protocol.InspectResult
import ai.sovra.remote.protocol.Intent
import ai.sovra.remote.protocol.Outcome
import ai.sovra.remote.protocol.PhoneTransport
import ai.sovra.remote.protocol.RemoteClient
import ai.sovra.remote.protocol.Skill
import ai.sovra.remote.protocol.decodeTicket
import ai.sovra.remote.protocol.fail
import ai.sovra.remote.protocol.loadSkills
import ai.sovra.remote.protocol.sendChatMessage
import ai.sovra.remote.protocol.stopWork
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

enum class Phase { Loading, Unpaired, Connecting, Connected, Unreachable, Disabled, Busy, Revoked, Incompatible }

enum class Tone { Info, Success, Warning, Danger }

data class Notice(val tone: Tone, val text: String)

data class RemoteState(
    val phase: Phase = Phase.Loading,
    val desktop: SavedDesktop? = null,
    val snapshot: InspectResult? = null,
    val screenshot: String? = null,
    val updatedAt: Long = 0,
    /** Last desktop message for the current phase, shown verbatim (never parsed). */
    val detail: String? = null,
    val notice: Notice? = null,
    val working: Boolean = false,
    /** Sovra's `/` skills, read once from the computer (null until loaded). */
    val skills: List<Skill>? = null,
    val preferences: Preferences = Preferences(),
)

/** Bounded exponential backoff with full jitter, 1 s to 30 s, as the desktop contract asks. */
class Backoff(private val minMs: Long = 1000, private val maxMs: Long = 30_000) {
    private var attempt = 0
    fun next(): Long {
        val ceiling = minOf(maxMs, minMs shl attempt)
        attempt = minOf(attempt + 1, 16)
        return maxOf(minMs, (ceiling * Random.nextDouble()).toLong())
    }
    fun reset() {
        attempt = 0
    }
}

typealias TransportFactory = suspend (localOnly: Boolean) -> PhoneTransport

class RemoteStore(private val storage: Storage, private val factory: TransportFactory) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state

    private var client: RemoteClient? = null
    private var timer: Job? = null
    private var live = false
    private var pictureWanted = false
    private var fetchingSkills: Deferred<Boolean>? = null
    private val backoff = Backoff()

    init {
        val desktop = storage.loadDesktop()
        _state.value = RemoteState(
            phase = if (desktop != null) Phase.Connecting else Phase.Unpaired,
            desktop = desktop,
            preferences = storage.loadPreferences(),
        )
    }

    private fun phaseFor(code: ErrorCode): Phase = when (code) {
        ErrorCode.Unreachable -> Phase.Unreachable
        ErrorCode.Disabled -> Phase.Disabled
        ErrorCode.Busy -> Phase.Busy
        ErrorCode.NotPaired -> Phase.Revoked
        ErrorCode.UnsupportedVersion -> Phase.Incompatible
        else -> Phase.Connected
    }

    private suspend fun ensureClient(): RemoteClient? {
        client?.let { return it }
        val transport = factory(_state.value.preferences.localOnly)
        return RemoteClient(transport, _state.value.desktop?.address).also { client = it }
    }

    private suspend fun resetClient() {
        val old = client
        client = null
        runCatching { old?.close() }
    }

    private fun schedule(delayMs: Long) {
        timer?.cancel()
        timer = if (live) scope.launch { delay(delayMs); refresh() } else null
    }

    suspend fun pair(link: String, phoneName: String, desktopName: String): Outcome<Unit> {
        // The link is a bearer credential: it is decoded here and never stored or logged.
        val ticket = decodeTicket(link)
            ?: return fail(ErrorCode.InvalidInvitation, "That is not a Sovra pairing code. Scan the code shown on your computer.")
        // A dedicated client: the shared one may be mid-refresh for another computer.
        val remote = try {
            RemoteClient(factory(_state.value.preferences.localOnly), null)
        } catch (error: Exception) {
            return fail(ErrorCode.Unreachable, "Could not start a secure connection: ${error.message}")
        }
        val result = remote.pair(ticket, phoneName)
        runCatching { remote.close() }
        if (result is Outcome.Failed) return result
        resetClient()
        val desktop = SavedDesktop((result as Outcome.Ok).value, desktopName.trim().ifEmpty { "My computer" }, System.currentTimeMillis())
        if (!storage.saveDesktop(desktop)) {
            return fail(ErrorCode.StorageUnavailable, "Your computer paired this phone, but it could not be remembered. Pair again before closing the app.")
        }
        backoff.reset()
        _state.update {
            it.copy(desktop = desktop, phase = Phase.Connecting, detail = null, skills = null, notice = Notice(Tone.Success, "Connected to ${desktop.name}."))
        }
        return Outcome.Ok(Unit)
    }

    suspend fun refresh() {
        if (_state.value.desktop == null) return
        val remote = try {
            ensureClient()
        } catch (error: Exception) {
            _state.update { it.copy(phase = Phase.Unreachable, detail = error.message) }
            schedule(backoff.next())
            return
        } ?: return
        when (val view = remote.inspect()) {
            is Outcome.Ok -> {
                backoff.reset()
                // Never keep an old picture around: it would show a screen that is no longer there.
                val screenshot = if (pictureWanted || _state.value.preferences.screenshots) {
                    (remote.screenshot() as? Outcome.Ok)?.value
                } else null
                _state.update {
                    it.copy(phase = Phase.Connected, snapshot = view.value, screenshot = screenshot, updatedAt = System.currentTimeMillis(), detail = null)
                }
                schedule(_state.value.preferences.refreshMs)
            }
            is Outcome.Failed -> {
                val phase = phaseFor(view.code)
                _state.update { it.copy(phase = if (phase == Phase.Connected) it.phase else phase, detail = view.message) }
                if (phase == Phase.Revoked || phase == Phase.Incompatible) return // needs the person, not a retry
                schedule(backoff.next())
            }
        }
    }

    fun startLive() {
        if (live) return
        live = true
        backoff.reset()
        scope.launch { refresh() }
    }

    fun stopLive() {
        live = false
        timer?.cancel()
        timer = null
        // Leaving the foreground closes the endpoint: no background traffic.
        scope.launch { resetClient() }
    }

    /** The Computer tab (and the Chat tab without labelled messages) needs screenshots. */
    fun setPictureWanted(wanted: Boolean) {
        pictureWanted = wanted
    }

    /**
     * Runs in the store's own scope, so leaving a screen never cuts a flow off between
     * an inspection and its input; "working" always clears.
     */
    private suspend fun <T> run(task: suspend (RemoteClient) -> Outcome<T>): Outcome<T>? = scope.async { runHere(task) }.await()

    private suspend fun <T> runHere(task: suspend (RemoteClient) -> Outcome<T>): Outcome<T>? {
        val remote = runCatching { ensureClient() }.getOrNull() ?: return null
        _state.update { it.copy(working = true) }
        val result = try {
            task(remote)
        } finally {
            _state.update { it.copy(working = false) }
        }
        if (result is Outcome.Failed) {
            _state.update { it.copy(notice = Notice(if (result.code == ErrorCode.OutcomeUnknown) Tone.Warning else Tone.Danger, result.message)) }
        }
        scope.launch { refresh() }
        return result
    }

    suspend fun act(intent: Intent): Boolean = run { it.act(intent) } is Outcome.Ok

    /** Run a navigation flow (open a chat, search, new chat…) and refresh afterwards. */
    suspend fun perform(task: suspend (RemoteClient) -> Outcome<*>): Boolean = run { task(it) } is Outcome.Ok

    suspend fun send(text: String): Boolean = scope.async { sendHere(text) }.await()

    private suspend fun sendHere(text: String): Boolean {
        val remote = runCatching { ensureClient() }.getOrNull() ?: return false
        _state.update { it.copy(working = true) }
        val (result, partial) = try {
            sendChatMessage(remote, text)
        } finally {
            _state.update { it.copy(working = false) }
        }
        if (result is Outcome.Failed) _state.update { it.copy(notice = Notice(if (partial) Tone.Warning else Tone.Danger, result.message)) }
        scope.launch { refresh() }
        return result is Outcome.Ok
    }

    suspend fun stop(): Boolean = run { stopWork(it) } is Outcome.Ok

    /** Read Sovra's skills from the computer unless already known (`force` re-reads). */
    suspend fun fetchSkills(force: Boolean = false): Boolean {
        if (_state.value.skills != null && !force) return true
        fetchingSkills?.let { return it.await() }
        val job = scope.async {
            val remote = runCatching { ensureClient() }.getOrNull() ?: return@async false
            _state.update { it.copy(working = true) }
            val result = try {
                loadSkills(remote)
            } finally {
                _state.update { it.copy(working = false) }
            }
            if (result is Outcome.Ok) _state.update { it.copy(skills = result.value) }
            launch { refresh() }
            result is Outcome.Ok
        }
        fetchingSkills = job
        return try {
            job.await()
        } finally {
            fetchingSkills = null
        }
    }

    suspend fun forget() {
        stopLive()
        storage.deleteDesktop()
        _state.update {
            it.copy(
                desktop = null, phase = Phase.Unpaired, snapshot = null, screenshot = null, detail = null, skills = null,
                notice = Notice(Tone.Info, "Forgotten on this phone. Also choose Forget on your computer to revoke access there."),
            )
        }
    }

    fun setPreferences(preferences: Preferences) {
        storage.savePreferences(preferences)
        _state.update { it.copy(preferences = preferences) }
    }

    fun setNotice(notice: Notice?) = _state.update { it.copy(notice = notice) }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)
}
