@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.posix.memcpy

/** The view controller in front, to present a system sheet over. */
fun topViewController(): UIViewController? {
  val window =
    UIApplication.sharedApplication.connectedScenes
      .filterIsInstance<UIWindowScene>()
      .flatMap { it.windows.filterIsInstance<platform.UIKit.UIWindow>() }
      .firstOrNull { it.isKeyWindow() }
  var top = window?.rootViewController
  while (top?.presentedViewController != null) top = top.presentedViewController
  return top
}

fun ByteArray.toNSData(): NSData =
  if (isEmpty()) NSData()
  else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  if (size == 0) return ByteArray(0)
  return ByteArray(size).apply { usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
