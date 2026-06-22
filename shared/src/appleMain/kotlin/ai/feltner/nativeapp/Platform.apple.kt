package ai.feltner.nativeapp

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSProcessInfo

actual fun platformName(): String =
    "Apple ${NSProcessInfo.processInfo.operatingSystemVersionString}"

actual fun nowIso(): String =
    NSISO8601DateFormatter().stringFromDate(NSDate())
