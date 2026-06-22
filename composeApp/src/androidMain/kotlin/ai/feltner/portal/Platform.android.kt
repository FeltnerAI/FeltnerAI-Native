package ai.feltner.portal

import android.os.Build
import java.time.Instant

actual fun platformName(): String = "Android ${Build.VERSION.RELEASE}"

actual fun nowIso(): String = Instant.now().toString()
