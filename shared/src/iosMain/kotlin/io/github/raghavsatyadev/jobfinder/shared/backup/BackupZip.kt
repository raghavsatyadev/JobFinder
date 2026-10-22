@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.shared.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.crc32
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream
import platform.zlib.zlibVersion

/**
 * Zip archives in memory on iOS, over the system's zlib: the same format `java.util.zip` writes and
 * reads on the other apps (deflated or stored entries, no Zip64). Entries are found through the
 * central directory, so archives whose local headers carry no sizes (Java's) read too.
 */
actual object BackupZip {
  actual fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = Bytes()
    val central = Bytes()
    for ((name, bytes) in entries) {
      val nameBytes = name.encodeToByteArray()
      val crc = crc(bytes)
      val deflated = deflateRaw(bytes)
      val offset = out.size
      out.int(LOCAL_HEADER)
      out.short(VERSION)
      out.short(UTF8_NAMES)
      out.short(METHOD_DEFLATED)
      out.short(0) // time
      out.short(DOS_DATE_1980)
      out.int(crc)
      out.int(deflated.size)
      out.int(bytes.size)
      out.short(nameBytes.size)
      out.short(0) // extra
      out.bytes(nameBytes)
      out.bytes(deflated)

      central.int(CENTRAL_HEADER)
      central.short(VERSION)
      central.short(VERSION)
      central.short(UTF8_NAMES)
      central.short(METHOD_DEFLATED)
      central.short(0)
      central.short(DOS_DATE_1980)
      central.int(crc)
      central.int(deflated.size)
      central.int(bytes.size)
      central.short(nameBytes.size)
      central.short(0) // extra
      central.short(0) // comment
      central.short(0) // disk
      central.short(0) // internal attributes
      central.int(0) // external attributes
      central.int(offset)
      central.bytes(nameBytes)
    }
    val centralOffset = out.size
    out.bytes(central.toByteArray())
    out.int(END_OF_CENTRAL)
    out.short(0)
    out.short(0)
    out.short(entries.size)
    out.short(entries.size)
    out.int(central.size)
    out.int(centralOffset)
    out.short(0)
    return out.toByteArray()
  }

  actual fun read(bytes: ByteArray, maxBytes: Long): Map<String, ByteArray> {
    val end = findEnd(bytes) ?: notBackup()
    val count = bytes.u16(end + 10)
    var at = bytes.u32(end + 16).toInt()
    val entries = linkedMapOf<String, ByteArray>()
    var total = 0L
    repeat(count) {
      if (at + 46 > bytes.size || bytes.u32(at) != CENTRAL_HEADER.toLong()) notBackup()
      val method = bytes.u16(at + 10)
      val compressed = bytes.u32(at + 20)
      val size = bytes.u32(at + 24)
      val nameLength = bytes.u16(at + 28)
      val skip = bytes.u16(at + 30) + bytes.u16(at + 32)
      val local = bytes.u32(at + 42).toInt()
      if (at + 46 + nameLength > bytes.size) notBackup()
      val name = bytes.decodeToString(at + 46, at + 46 + nameLength)
      at += 46 + nameLength + skip
      if (name.endsWith("/")) return@repeat
      total += size
      if (total > maxBytes) throw BackupFormatException("This backup is too large to read.")
      if (local + 30 > bytes.size || bytes.u32(local) != LOCAL_HEADER.toLong()) notBackup()
      val start = local + 30 + bytes.u16(local + 26) + bytes.u16(local + 28)
      if (start + compressed > bytes.size) notBackup()
      val data = bytes.copyOfRange(start, start + compressed.toInt())
      entries[name] =
        when (method) {
          METHOD_STORED -> data
          METHOD_DEFLATED -> inflateRaw(data, size.toInt())
          else -> notBackup()
        }
    }
    if (entries.isEmpty()) notBackup()
    return entries
  }

  private fun notBackup(): Nothing =
    throw BackupFormatException("This file is not a JobFinder backup.")

  /** The end-of-central-directory record's offset, searched from the end past any comment. */
  private fun findEnd(bytes: ByteArray): Int? {
    val last = bytes.size - 22
    val first = maxOf(0, last - 0xFFFF)
    for (i in last downTo first) if (bytes.u32(i) == END_OF_CENTRAL.toLong()) return i
    return null
  }

  private fun crc(bytes: ByteArray): Int {
    if (bytes.isEmpty()) return 0
    return bytes
      .usePinned { crc32(0u, it.addressOf(0).reinterpret(), bytes.size.convert()) }
      .toInt()
  }

  private fun deflateRaw(input: ByteArray): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    check(
      deflateInit2_(
        stream.ptr,
        Z_DEFAULT_COMPRESSION,
        Z_DEFLATED,
        -RAW_WINDOW_BITS,
        MEM_LEVEL,
        Z_DEFAULT_STRATEGY,
        zlibVersion(),
        sizeOf<z_stream>().toInt(),
      ) == Z_OK
    ) {
      "deflateInit failed"
    }
    try {
      val output = ByteArray(deflateBound(stream.ptr, input.size.convert()).toInt() + 16)
      input.usePinned { pinnedIn ->
        output.usePinned { pinnedOut ->
          stream.next_in = if (input.isEmpty()) null else pinnedIn.addressOf(0).reinterpret()
          stream.avail_in = input.size.convert()
          stream.next_out = pinnedOut.addressOf(0).reinterpret()
          stream.avail_out = output.size.convert()
          check(deflate(stream.ptr, Z_FINISH) == Z_STREAM_END) { "deflate failed" }
        }
      }
      output.copyOf(stream.total_out.toInt())
    } finally {
      deflateEnd(stream.ptr)
    }
  }

  private fun inflateRaw(input: ByteArray, size: Int): ByteArray = memScoped {
    if (size == 0) return@memScoped ByteArray(0)
    val stream = alloc<z_stream>()
    if (
      inflateInit2_(stream.ptr, -RAW_WINDOW_BITS, zlibVersion(), sizeOf<z_stream>().toInt()) != Z_OK
    ) {
      notBackup()
    }
    try {
      val output = ByteArray(size)
      val result = input.usePinned { pinnedIn ->
        output.usePinned { pinnedOut ->
          stream.next_in = if (input.isEmpty()) null else pinnedIn.addressOf(0).reinterpret()
          stream.avail_in = input.size.convert()
          stream.next_out = pinnedOut.addressOf(0).reinterpret()
          stream.avail_out = output.size.convert()
          inflate(stream.ptr, Z_FINISH)
        }
      }
      if (result != Z_STREAM_END || stream.total_out.toInt() != size) notBackup()
      output
    } finally {
      inflateEnd(stream.ptr)
    }
  }

  private fun ByteArray.u16(at: Int): Int =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

  private fun ByteArray.u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

  /** A little-endian byte builder. */
  private class Bytes {
    private var buffer = ByteArray(64 * 1024)
    var size = 0
      private set

    fun bytes(data: ByteArray) {
      ensure(data.size)
      data.copyInto(buffer, size)
      size += data.size
    }

    fun short(value: Int) {
      ensure(2)
      buffer[size++] = value.toByte()
      buffer[size++] = (value shr 8).toByte()
    }

    fun int(value: Int) {
      short(value and 0xFFFF)
      short(value ushr 16)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensure(extra: Int) {
      if (size + extra <= buffer.size) return
      buffer = buffer.copyOf(maxOf(buffer.size * 2, size + extra))
    }
  }

  private const val LOCAL_HEADER = 0x04034b50
  private const val CENTRAL_HEADER = 0x02014b50
  private const val END_OF_CENTRAL = 0x06054b50
  private const val VERSION = 20
  /** General purpose flag bit 11: names are UTF-8, as Java writes them. */
  private const val UTF8_NAMES = 0x0800
  private const val METHOD_STORED = 0
  private const val METHOD_DEFLATED = 8
  /** 1980-01-01 in DOS date format, the earliest a zip can hold. */
  private const val DOS_DATE_1980 = (1 shl 5) or 1
  private const val RAW_WINDOW_BITS = 15
  private const val MEM_LEVEL = 8
}
