package ai.feltner.nativeapp

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.gmtime
import platform.posix.strftime
import platform.posix.time
import platform.posix.time_tVar

actual fun platformName(): String = "Windows"

@OptIn(ExperimentalForeignApi::class)
actual fun nowIso(): String = memScoped {
    val epochSeconds = alloc<time_tVar>()
    time(epochSeconds.ptr)

    val utc = gmtime(epochSeconds.ptr) ?: return "1970-01-01T00:00:00Z"
    val buffer = allocArray<ByteVar>(21)
    strftime(buffer, 21.convert(), "%Y-%m-%dT%H:%M:%SZ", utc)
    buffer.toKString()
}
