package ai.sovra.remote.protocol

/** Why the transport could not deliver a message. Mirrors the Rust `TransportError`. */
enum class TransportFailureKind { InvalidAddress, Unreachable, Protocol, Timeout, Closed }

class TransportFailure(val kind: TransportFailureKind, message: String) : Exception(message)

/** How traffic reaches the desktop: on the same network (or a punched hole), or through an encrypted Iroh relay. */
enum class Route { Direct, Relay, Unknown }

/** One authenticated QUIC connection to the desktop (one JSON message per stream). */
interface DesktopConnection {
    /** Desktop public key from the completed TLS handshake. */
    fun peer(): String
    /** The path in use right now; it can change while connected (relay first, then direct). */
    fun route(): Route = Route.Unknown
    fun isClosed(): Boolean
    suspend fun request(json: String): String
    fun close()
}

/** The phone's endpoint, bound with its persistent identity key. */
interface PhoneTransport {
    fun deviceId(): String
    suspend fun connect(address: PeerAddress): DesktopConnection
    suspend fun close()
}
