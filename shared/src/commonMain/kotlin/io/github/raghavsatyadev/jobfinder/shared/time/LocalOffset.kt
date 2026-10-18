package io.github.raghavsatyadev.jobfinder.shared.time

/** How far this device's time zone is ahead of UTC at [epochMs], in milliseconds. */
expect fun localOffsetMs(epochMs: Long): Long
