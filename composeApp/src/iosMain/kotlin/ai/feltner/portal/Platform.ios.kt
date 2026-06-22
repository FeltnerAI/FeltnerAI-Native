package ai.feltner.portal

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter
import platform.UIKit.UIDevice

actual fun platformName(): String =
    UIDevice.currentDevice.systemName + " " + UIDevice.currentDevice.systemVersion

actual fun nowIso(): String =
    NSISO8601DateFormatter().stringFromDate(NSDate())
