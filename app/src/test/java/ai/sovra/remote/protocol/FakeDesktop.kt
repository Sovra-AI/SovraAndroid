/*
 * In-memory desktop that enforces the same rules as the Rust runtime: pairing allowlist,
 * Hello sessions, exact sequences consumed even on error, revisions required for
 * mutations. Mirrors the Expo app's `fake-desktop.ts`.
 */
package ai.sovra.remote.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

val DESKTOP = "01".repeat(32)
val SECRET = "02".repeat(32)
val desktopAddress = PeerAddress(DESKTOP, null, listOf("127.0.0.1:1"))

private var nonceCounter = 0
private fun nonce() = (++nonceCounter).toString(16).padStart(64, '0')

/** One input the desktop received: click/scroll target, typed text or key. */
data class Input(val action: String, val target: String? = null, val text: String? = null, val key: String? = null, val lines: Int? = null)

class FakeDesktop {
    var enabled = true
    var paired = false
    var invitation: String? = SECRET
    val generation = nonce()
    val elements = mutableListOf<UiElement>()
    var revision = 1
    val delivered = mutableListOf<Input>()
    /** Execute the next request with this action, then lose its reply. */
    var dropReplyFor: String? = null
    /** Change the window between inspect and the next mutation this many times. */
    var churn = 0
    var unreachable = false
    /** Every request received, as `op` or `control:action`. */
    val requests = mutableListOf<String>()
    /** Sequence numbers of control requests, in order. */
    val sequences = mutableListOf<Int>()
    private val connections = mutableListOf<DesktopConnection>()

    /** The desktop closes idle connections; the phone must Hello again. */
    fun closeConnections() = connections.forEach { it.close() }
    var onDeliver: ((Input) -> Unit)? = null

    fun element(target: String, label: String?, role: String?, y: Double = 0.0, visible: Boolean = true): UiElement {
        val element = UiElement(target, label, role, visible, false, null, null, null, Bounds(0.0, y, 100.0, 20.0))
        elements += element
        return element
    }

    fun replace(old: UiElement, new: UiElement) {
        elements[elements.indexOf(old)] = new
    }

    fun snapshot() = InspectResult(elements.toList(), elements.size, false, "dark", 100, "r$revision")

    fun transport(): PhoneTransport = object : PhoneTransport {
        override fun deviceId() = "ab".repeat(32)
        override suspend fun connect(address: PeerAddress): DesktopConnection {
            if (unreachable || address.device != DESKTOP) throw TransportFailure(TransportFailureKind.Unreachable, "offline")
            return Connection().also { connections += it }
        }
        override suspend fun close() = Unit
    }

    fun client(): RemoteClient {
        paired = true
        return RemoteClient(transport(), desktopAddress)
    }

    private fun elementJson(element: UiElement) = buildJsonObject {
        put("target", element.target)
        element.label?.let { put("label", it) }
        element.role?.let { put("role", it) }
        put("visible", element.visible)
        put("disabled", element.disabled)
        put("bounds", buildJsonObject {
            put("x", element.bounds.x); put("y", element.bounds.y); put("width", element.bounds.width); put("height", element.bounds.height)
        })
    }

    private inner class Connection : DesktopConnection {
        var closed = false
        var session: Triple<String, String, Int>? = null

        override fun peer() = DESKTOP
        override fun isClosed() = closed
        override fun close() {
            closed = true
        }

        override suspend fun request(json: String): String {
            if (closed) throw TransportFailure(TransportFailureKind.Closed, "closed")
            val request = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
            val operation = request["operation"]!!.jsonObject
            val action = (operation["request"] as? JsonObject)?.get("action")?.jsonPrimitive?.content
            requests += if (action != null) "control:$action" else operation["op"]!!.jsonPrimitive.content
            if (action != null) sequences += operation["sequence"]!!.jsonPrimitive.int
            val reply = handle(request)
            if (dropReplyFor != null && action == dropReplyFor) {
                dropReplyFor = null
                closed = true
                throw TransportFailure(TransportFailureKind.Timeout, "lost")
            }
            return reply.toString()
        }

        private fun error(code: String, message: String = code) = buildJsonObject {
            put("status", "error"); put("code", code); put("message", message)
        }

        private fun handle(request: JsonObject): JsonObject {
            if (request["version"]!!.jsonPrimitive.int != 1) return error("unsupported_version")
            if (!enabled) return error("disabled")
            val op = request["operation"]!!.jsonObject
            when (op["op"]!!.jsonPrimitive.content) {
                "pair" -> {
                    if (op["secret"]!!.jsonPrimitive.content != invitation) return error("invalid_invitation")
                    invitation = null
                    paired = true
                    return buildJsonObject { put("status", "paired") }
                }
            }
            if (!paired) return error("not_paired")
            if (op["op"]!!.jsonPrimitive.content == "hello") {
                val next = Triple(generation, nonce(), 1)
                session = next
                return buildJsonObject {
                    put("status", "ready"); put("version", 1); put("generation", next.first); put("session", next.second)
                    put("max_request_bytes", 32768); put("max_response_bytes", 8388608)
                }
            }
            val current = session
            val sequence = op["sequence"]!!.jsonPrimitive.int
            if (current == null || op["generation"]!!.jsonPrimitive.content != current.first ||
                op["session"]!!.jsonPrimitive.content != current.second || sequence != current.third
            ) return error("stale_session")
            session = current.copy(third = current.third + 1)
            val action = op["request"]!!.jsonObject
            return when (action["action"]!!.jsonPrimitive.content) {
                "inspect" -> buildJsonObject {
                    put("status", "result")
                    put("value", buildJsonObject {
                        put("elements", JsonArray(elements.map(::elementJson)))
                        put("total", elements.size); put("truncated", false); put("appearance", "dark")
                        put("interface_scale_percent", 100); put("revision", "r$revision")
                    })
                }
                "screenshot" -> buildJsonObject { put("status", "result"); put("value", buildJsonObject { put("png", "iVBORw0KGgo=") }) }
                else -> {
                    if (churn > 0) {
                        churn -= 1
                        revision += 1
                    }
                    if ((op["revision"] as? JsonPrimitive)?.content != "r$revision") return error("stale_observation")
                    val kind = action["action"]!!.jsonPrimitive.content
                    val target = action["target"]?.jsonPrimitive?.content
                    if (target != null && elements.none { it.target == target }) return error("outcome_unknown", "Target not found")
                    val input = Input(
                        kind, target, action["text"]?.jsonPrimitive?.content, action["key"]?.jsonPrimitive?.content,
                        action["lines"]?.jsonPrimitive?.int,
                    )
                    delivered += input
                    onDeliver?.invoke(input)
                    revision += 1
                    buildJsonObject {
                        put("status", "result")
                        put("value", buildJsonObject { put("input_delivered", true); put("next", "Inspect to verify the result.") })
                    }
                }
            }
        }
    }
}
