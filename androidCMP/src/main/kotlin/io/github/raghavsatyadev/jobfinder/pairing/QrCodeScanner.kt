package io.github.raghavsatyadev.jobfinder.pairing

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** The activity in front, for APIs that need one. */
class CurrentActivity : Application.ActivityLifecycleCallbacks {
  private var resumed = WeakReference<Activity>(null)

  val current: Activity?
    get() = resumed.get()

  override fun onActivityResumed(activity: Activity) {
    resumed = WeakReference(activity)
  }

  override fun onActivityPaused(activity: Activity) {
    if (resumed.get() === activity) resumed = WeakReference(null)
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Google's code scanner: Play services shows the camera, so the app needs no CAMERA permission and
 * never sees the camera feed.
 */
class QrCodeScanner(private val activities: CurrentActivity) {
  /** The QR code's text, or `null` when the user backs out. Throws when the scanner can't run. */
  suspend fun scan(): String? {
    val activity = checkNotNull(activities.current) { "No activity in front" }
    val options =
      GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    val scanner = GmsBarcodeScanning.getClient(activity, options)
    return suspendCancellableCoroutine { continuation ->
      scanner
        .startScan()
        .addOnSuccessListener { continuation.resume(it.rawValue) }
        .addOnCanceledListener { continuation.resume(null) }
        .addOnFailureListener { continuation.resumeWithException(it) }
    }
  }
}
