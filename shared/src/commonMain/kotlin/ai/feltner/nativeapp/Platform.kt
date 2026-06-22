package ai.feltner.nativeapp

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Short human label for the current platform, shown in the UI. */
expect fun platformName(): String

/** ISO-8601 timestamp for "now", used as `lastUsedAt` on profiles. */
expect fun nowIso(): String

@OptIn(ExperimentalUuidApi::class)
fun randomUuid(): String = Uuid.random().toString()
