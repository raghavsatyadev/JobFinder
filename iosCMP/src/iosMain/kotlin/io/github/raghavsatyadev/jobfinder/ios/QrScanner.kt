@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * Scans the computer's pairing QR code with the camera, in a full-screen view with a Cancel button.
 * Without a camera (the Simulator) it returns `null` at once; pairing then goes through discovery.
 */
class QrScanner {
  suspend fun scan(): String? =
    withContext(Dispatchers.Main) {
      val camera =
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return@withContext null
      val input =
        AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return@withContext null
      val presenter = topViewController() ?: return@withContext null
      suspendCancellableCoroutine { continuation ->
        val session = AVCaptureSession()
        lateinit var navigation: UINavigationController
        var done = false
        fun finish(code: String?) {
          if (done) return
          done = true
          session.stopRunning()
          navigation.dismissViewControllerAnimated(true, null)
          continuation.resume(code)
        }
        val delegate = MetadataDelegate { finish(it) }
        val output = AVCaptureMetadataOutput()
        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        val scanner = ScannerController(session, delegate) { finish(null) }
        navigation = UINavigationController(rootViewController = scanner)
        presenter.presentViewController(navigation, animated = true, completion = null)
        continuation.invokeOnCancellation { finish(null) }
      }
    }

  private class MetadataDelegate(val onCode: (String) -> Unit) :
    NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
      output: AVCaptureOutput,
      didOutputMetadataObjects: List<*>,
      fromConnection: AVCaptureConnection,
    ) {
      didOutputMetadataObjects
        .filterIsInstance<AVMetadataMachineReadableCodeObject>()
        .firstNotNullOfOrNull { it.stringValue }
        ?.let(onCode)
    }
  }

  /** The camera preview; [delegate] is held here so it lives as long as the view. */
  private class ScannerController(
    private val session: AVCaptureSession,
    @Suppress("unused") private val delegate: MetadataDelegate,
    private val onCancel: () -> Unit,
  ) : UIViewController(nibName = null, bundle = null) {
    private val preview = AVCaptureVideoPreviewLayer(session = session)

    override fun viewDidLoad() {
      super.viewDidLoad()
      preview.videoGravity = AVLayerVideoGravityResizeAspectFill
      view.layer.addSublayer(preview)
      navigationItem.leftBarButtonItem =
        UIBarButtonItem(
          barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
          target = this,
          action = platform.objc.sel_registerName("cancel"),
        )
      session.startRunning()
    }

    override fun viewDidLayoutSubviews() {
      super.viewDidLayoutSubviews()
      preview.frame = view.bounds
    }

    @kotlinx.cinterop.ObjCAction fun cancel() = onCancel()
  }
}
