package io.github.raghavsatyadev.jobfinder.shared.time

import java.util.TimeZone

actual fun localOffsetMs(epochMs: Long): Long = TimeZone.getDefault().getOffset(epochMs).toLong()
