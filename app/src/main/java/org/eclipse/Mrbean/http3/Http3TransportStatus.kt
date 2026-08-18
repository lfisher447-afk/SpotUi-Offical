package org.eclipse.Mrbean.http3

/**
 * HTTP/3 is intentionally reported as unavailable until a vetted native QUIC
 * provider is bundled. Existing media connections still negotiate secure HTTP/2
 * where the Android platform supports it.
 */
object Http3TransportStatus {
    const val availableInThisBuild: Boolean = false
}
