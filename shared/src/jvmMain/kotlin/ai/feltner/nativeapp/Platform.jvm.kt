package ai.feltner.nativeapp

import java.time.Instant

actual fun platformName(): String =
    "Desktop (${System.getProperty("os.name")})"

actual fun nowIso(): String = Instant.now().toString()
