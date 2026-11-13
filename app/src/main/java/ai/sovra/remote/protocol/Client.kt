/*
 * Phone-side protocol client for Sovra Harness remote control.
 *
 * Follows the desktop contract in `SovraHarness/docs/remote-control.md` and the
 * reference client `crates/sovra-runtime/src/remote_reference.rs`:
 *
 * - Hello on every new connection; sequence restarts at 1 and is consumed even on error.
 * - Reads (inspect, screenshot) reconnect once automatically.
 * - Mutations never reconnect and are never replayed. A lost reply is `outcome_unknown`.
 * - Every mutation carries the revision of an inspection taken immediately before it,
 *   in the same session. `stale_observation` is refused before delivery, so retrying
 *   the inspect + mutation pair is safe; nothing else is retried.
 * - All calls are serialized: one request in flight per connection.
 */
package ai.sovra.remote.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failed(val code: ErrorCode, val message: String) : Outcome<Nothing>
}

fun <T> ok(value: T): Outcome<T> = Outcome.Ok(value)
fun fail(code: ErrorCode, message: String): Outcome.Failed = Outcome.Failed(code, message)

/** A mutation is retried only on `stale_observation`, which the desktop rejects before delivery. */
private const val MAX_STALE_RETRIES = 4

/** Identifies a control across inspections. The target is opaque and session-scoped. */
data class ElementRef(val target: String, val label: String?, val role: String?)

fun UiElement.ref() = ElementRef(target, label, role)

sealed interface Intent {
    data class Click(val element: ElementRef) : Intent
    data class Scroll(val element: ElementRef, val lines: Int) : Intent
    data class Type(val text: String) : Intent
    data class Key(val key: String) : Intent
}

data class Delivered(val before: InspectResult, val next: String?)

/** Find the same control in a fresh inspection, or null if it moved or vanished. */
fun resolveElement(element: ElementRef, snapshot: InspectResult): UiElement? {
    snapshot.elements.firstOrNull { it.target == element.target }?.let { return it }
    // Targets are discarded on reconnect; fall back to a unique labelled match.
    if (element.label == null) return null
    val matches = snapshot.elements.filter { it.label == element.label && it.role == element.role && it.visible }
    return matches.singleOrNull()
}

fun sanitizePhoneName(name: String): String {
    // Untrusted display text on the desktop: no control characters, at most 80 UTF-8 bytes.
    var clean = name.replace(Regex("[\\u0000-\\u001f\\u007f-\\u009f]"), "").trim()
    while (utf8Length(clean) > MAX_PHONE_NAME_BYTES) clean = clean.dropLast(1)
    return clean.ifEmpty { "My phone" }
}

private class Session(val connection: DesktopConnection, val generation: String, val nonce: String, var next: Int)

class RemoteClient(private val transport: PhoneTransport, private var desktop: PeerAddress?) {
    private var session: Session? = null
    private val lock = Mutex()
    private var closed = false

    val connected: Boolean get() = session?.connection?.isClosed() == false

    /**
     * Pair with a scanned invitation. Tries Hello first so a lost Paired reply can be
     * recovered without a new QR. Returns the address to persist on success.
     */
    suspend fun pair(ticket: PairingTicket, name: String): Outcome<PeerAddress> = lock.withLock {
        disconnect()
        val connection = when (val connected = connect(ticket.address)) {
            is Outcome.Failed -> return@withLock connected
            is Outcome.Ok -> connected.value
        }
        try {
            val hello = exchange(connection, RemoteOperation.Hello)
            val pair = RemoteOperation.Pair(ticket.secret, sanitizePhoneName(name))
            val response: RemoteResponse = when {
                hello is RemoteResponse.Ready && hello.version == PROTOCOL_VERSION -> {
                    // Already paired (this phone forgot the computer, or a `paired` reply was lost).
                    // Still hand over the code so the desktop closes it instead of pausing control
                    // until it expires. Hello already proved access, so any refusal is ignored.
                    exchange(connection, pair)
                    RemoteResponse.Paired
                }
                hello is RemoteResponse.Error && hello.code == ErrorCode.NotPaired ->
                    exchange(connection, pair) ?: return@withLock fail(
                        ErrorCode.OutcomeUnknown,
                        "The pairing reply was lost. Scan again: Sovra checks first whether this phone is already paired.",
                    )
                hello is RemoteResponse.Error -> return@withLock fail(hello.code, hello.message)
                hello == null -> return@withLock fail(ErrorCode.OutcomeUnknown, "Your computer did not answer. Try again when it is reachable.")
                else -> return@withLock fail(ErrorCode.UnsupportedVersion, "Your computer runs a different Sovra version. Update both apps.")
            }
            when (response) {
                RemoteResponse.Paired -> {
                    desktop = ticket.address
                    ok(ticket.address)
                }
                is RemoteResponse.Error -> fail(response.code, response.message)
                else -> fail(ErrorCode.UnsupportedVersion, "Your computer answered pairing unexpectedly.")
            }
        } finally {
            connection.close()
        }
    }

    suspend fun inspect(): Outcome<InspectResult> = lock.withLock { inspectLocked() }

    suspend fun screenshot(): Outcome<String> = lock.withLock {
        when (val response = controlLocked(UiRequest.Screenshot, null)) {
            is Outcome.Failed -> response
            is Outcome.Ok -> parseScreenshot(response.value)?.let(::ok)
                ?: fail(ErrorCode.InvalidRequest, "Your computer sent an unreadable screenshot.")
        }
    }

    /**
     * Inspect, re-find the control, then send the input against that fresh revision.
     * Never replays input whose outcome is unknown.
     */
    suspend fun act(intent: Intent): Outcome<Delivered> = lock.withLock {
        repeat(MAX_STALE_RETRIES) {
            val snapshot = when (val inspected = inspectLocked()) {
                is Outcome.Failed -> return@withLock inspected
                is Outcome.Ok -> inspected.value
            }
            val request = when (val built = requestFor(intent, snapshot)) {
                is Outcome.Failed -> return@withLock built
                is Outcome.Ok -> built.value
            }
            when (val response = controlLocked(request, snapshot.revision)) {
                is Outcome.Failed -> if (response.code != ErrorCode.StaleObservation) return@withLock response
                is Outcome.Ok -> {
                    val next = ((response.value as? JsonObject)?.get("next") as? JsonPrimitive)?.takeIf { it.isString }?.content
                    return@withLock ok(Delivered(snapshot, next))
                }
            }
        }
        fail(ErrorCode.StaleObservation, "Sovra kept changing on your computer. Wait a moment and try again.")
    }

    private fun requestFor(intent: Intent, snapshot: InspectResult): Outcome<UiRequest> = when (intent) {
        is Intent.Type -> ok(UiRequest.Type(intent.text))
        is Intent.Key -> ok(UiRequest.Key(intent.key))
        is Intent.Click, is Intent.Scroll -> {
            val ref = if (intent is Intent.Click) intent.element else (intent as Intent.Scroll).element
            val element = resolveElement(ref, snapshot)
            when {
                element == null -> fail(ErrorCode.StaleObservation, "That control is no longer on screen. Pick it again.")
                !element.visible || element.disabled == true -> fail(ErrorCode.InvalidRequest, "That control is hidden or unavailable right now.")
                intent is Intent.Click -> ok(UiRequest.Click(element.target))
                else -> ok(UiRequest.Scroll(element.target, (intent as Intent.Scroll).lines))
            }
        }
    }

    private suspend fun inspectLocked(): Outcome<InspectResult> = when (val response = controlLocked(UiRequest.Inspect, null)) {
        is Outcome.Failed -> response
        is Outcome.Ok -> parseInspect(response.value)?.let(::ok) ?: fail(ErrorCode.InvalidRequest, "Your computer sent an unreadable view.")
    }

    private suspend fun controlLocked(request: UiRequest, revision: String?): Outcome<JsonElement> {
        validateUiRequest(request)?.let { return fail(ErrorCode.InvalidRequest, it) }
        val read = !request.isMutation
        for (attempt in 0 until 2) {
            session?.let { if (it.next > MAX_SESSION_REQUESTS || it.connection.isClosed()) disconnect() }
            if (session == null) {
                if (!read) return fail(ErrorCode.StaleSession, "Reconnected. Look at the screen again before sending input.")
                val hello = hello()
                if (hello is Outcome.Failed) return hello
            }
            val current = session!!
            val operation = RemoteOperation.Control(current.generation, current.nonce, current.next, revision, request)
            // Consumed before sending, exactly like the desktop's SessionGuard.
            current.next += 1
            val response = exchange(current.connection, operation)
            if (response == null) {
                disconnect()
                if (read && attempt == 0) continue
                return if (read) {
                    fail(ErrorCode.Unreachable, "Lost the connection to your computer.")
                } else {
                    fail(ErrorCode.OutcomeUnknown, "The connection dropped before your computer replied. Check the screen before trying again.")
                }
            }
            when (response) {
                is RemoteResponse.Result -> return ok(response.value)
                is RemoteResponse.Error -> {
                    if (response.code in setOf(ErrorCode.StaleSession, ErrorCode.Disabled, ErrorCode.NotPaired)) {
                        disconnect()
                        if (read && attempt == 0 && response.code == ErrorCode.StaleSession) continue
                    }
                    return fail(response.code, response.message)
                }
                else -> return fail(ErrorCode.UnsupportedVersion, "Your computer answered unexpectedly. Update both apps.")
            }
        }
        return fail(ErrorCode.Unreachable, "Your computer is not reachable right now.")
    }

    private suspend fun hello(): Outcome<Unit> {
        val address = desktop ?: return fail(ErrorCode.NotPaired, "Pair this phone with your computer first.")
        val connection = when (val connected = connect(address)) {
            is Outcome.Failed -> return connected
            is Outcome.Ok -> connected.value
        }
        val response = exchange(connection, RemoteOperation.Hello)
        if (response is RemoteResponse.Ready && response.version == PROTOCOL_VERSION) {
            session = Session(connection, response.generation, response.session, 1)
            return ok(Unit)
        }
        connection.close()
        return when (response) {
            null -> fail(ErrorCode.Unreachable, "Your computer did not answer.")
            is RemoteResponse.Error -> fail(response.code, response.message)
            else -> fail(ErrorCode.UnsupportedVersion, "Your computer runs a different Sovra version. Update both apps.")
        }
    }

    private suspend fun connect(address: PeerAddress): Outcome<DesktopConnection> {
        if (closed) return fail(ErrorCode.Unreachable, "Remote control is paused.")
        return try {
            val connection = transport.connect(address)
            if (connection.peer() != address.device) {
                connection.close()
                fail(ErrorCode.InvalidRequest, "That computer's identity does not match the one you paired with.")
            } else ok(connection)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            fail(ErrorCode.Unreachable, error.message?.takeIf { it.isNotBlank() } ?: "Your computer is not reachable right now.")
        }
    }

    /** null means "no usable reply": transport failure, timeout or malformed message. */
    private suspend fun exchange(connection: DesktopConnection, operation: RemoteOperation): RemoteResponse? {
        val request = encodeRequest(operation)
        if (utf8Length(request) > MAX_REQUEST_BYTES) return null
        return try {
            decodeResponse(connection.request(request))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun disconnect() {
        session?.connection?.close()
        session = null
    }

    /** Stop all traffic (app suspended or leaving). A new client is needed afterwards. */
    suspend fun close() {
        closed = true
        disconnect()
        transport.close()
    }
}
