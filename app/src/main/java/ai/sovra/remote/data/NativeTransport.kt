/*
 * Adapter from the Rust transport (`rust/`, Iroh over QUIC, uniffi bindings) to the
 * app's `PhoneTransport`. Creates the phone's persistent identity on first use.
 */
package ai.sovra.remote.data

import ai.sovra.remote.protocol.DesktopConnection
import ai.sovra.remote.protocol.PeerAddress
import ai.sovra.remote.protocol.PhoneTransport
import ai.sovra.remote.protocol.Route
import ai.sovra.remote.protocol.TransportFailure
import ai.sovra.remote.protocol.TransportFailureKind
import uniffi.sovra_remote_ffi.PhoneEndpoint
import uniffi.sovra_remote_ffi.TransportException
import uniffi.sovra_remote_ffi.generatePhoneSecret
import uniffi.sovra_remote_ffi.phoneDeviceId
import uniffi.sovra_remote_ffi.DesktopConnection as NativeConnection

private fun translate(error: Throwable): TransportFailure = when (error) {
    is TransportException.InvalidAddress -> TransportFailure(TransportFailureKind.InvalidAddress, error.reason)
    is TransportException.Unreachable -> TransportFailure(TransportFailureKind.Unreachable, error.reason)
    is TransportException.Protocol -> TransportFailure(TransportFailureKind.Protocol, error.reason)
    is TransportException.Timeout -> TransportFailure(TransportFailureKind.Timeout, "Your computer took too long to answer.")
    is TransportException.Closed -> TransportFailure(TransportFailureKind.Closed, "The connection closed.")
    else -> TransportFailure(TransportFailureKind.Unreachable, "Your computer is not reachable right now.")
}

/** The phone's long-term key, created once from OS randomness and kept encrypted on this device. */
fun phoneSecret(storage: Storage): String {
    storage.loadPhoneSecret()?.let { return it }
    val created = generatePhoneSecret()
    storage.savePhoneSecret(created)
    // Re-read so a failed save never leaves us using a key we cannot recover.
    check(storage.loadPhoneSecret() == created) { "Could not save this phone’s identity." }
    return created
}

fun phoneId(storage: Storage): String? = storage.loadPhoneSecret()?.let { runCatching { phoneDeviceId(it) }.getOrNull() }

private class Connection(private val inner: NativeConnection) : DesktopConnection {
    override fun peer() = inner.peer()
    override fun route() = when (inner.route()) {
        "direct" -> Route.Direct
        "relay" -> Route.Relay
        else -> Route.Unknown
    }
    override fun isClosed() = inner.isClosed()
    override suspend fun request(json: String): String = try {
        inner.request(json)
    } catch (error: TransportException) {
        throw translate(error)
    }
    override fun close() = inner.disconnect()
}

suspend fun createNativeTransport(storage: Storage, localOnly: Boolean): PhoneTransport {
    val secret = phoneSecret(storage)
    val endpoint = try {
        PhoneEndpoint.bind(secret, localOnly)
    } catch (error: TransportException) {
        throw translate(error)
    }
    return object : PhoneTransport {
        override fun deviceId() = endpoint.deviceId()
        override suspend fun connect(address: PeerAddress): DesktopConnection = try {
            Connection(endpoint.connect(address.toJson().toString()))
        } catch (error: TransportException) {
            throw translate(error)
        }
        override suspend fun close() {
            endpoint.shutdown()
            // Free the Rust object as well.
            endpoint.close()
        }
    }
}
