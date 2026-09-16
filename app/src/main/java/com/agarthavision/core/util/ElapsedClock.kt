package com.agarthavision.core.util

/**
 * Reads a monotonic elapsed-time counter, in milliseconds.
 *
 * A one-method seam over `android.os.SystemClock.elapsedRealtime()` so the classes that
 * need "how long ago was this?" — [com.agarthavision.core.camera.FrameSampler] stamping a
 * frame, [com.agarthavision.ui.capture.CaptureViewModel] judging whether that stamp is
 * still fresh — take it as a dependency instead of calling a static. Both are then
 * testable on the plain JVM: `app/build.gradle.kts` does not set
 * `unitTests.returnDefaultValues`, so an un-shadowed `SystemClock` call throws inside a
 * unit test, and the alternative is dragging those tests onto Robolectric for one integer.
 *
 * Monotonic on purpose. Wall-clock time can jump backwards (NTP, a user changing the
 * device clock) and would make a fresh frame read as stale, or worse, a stale one as
 * fresh. `elapsedRealtime()` only moves forward.
 *
 * Bound in `core/di/ClockModule.kt`.
 */
fun interface ElapsedClock {
    fun elapsedRealtimeMs(): Long
}
