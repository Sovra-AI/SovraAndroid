package ai.sovra.remote.protocol

/** Why the transport could not deliver a message. Mirrors the Rust `TransportError`. */
enum class TransportFailureKind { InvalidAddress, Unreachable, Protocol, Timeout, Closed }

class TransportFailure(val kind: TransportFailureKind, message: String) : Exception(message)

/** One authenticated QUIC connection to the desktop (one JSON message per stream). */
interface DesktopConnection {
    /** Desktop public key from the completed TLS handshake. */
    fun peer(): String
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
