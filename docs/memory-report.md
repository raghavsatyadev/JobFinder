# Memory report: JobFinder vs Electron + Docker

Measured on 2026-10-15 on the installed app (MSI 0.0.2), Windows 11, with the real sign-ins and
data (32 searches). Numbers are the sum over the app's whole process tree (`JobFinder.exe` and
its `jcef_helper` processes), sampled every 0.7 s.

| State | Processes | Working set | Private |
|---|---|---|---|
| Idle, just started | 1 | 0.4 GB | 0.6 GB |
| Full scan of 32 searches (316 s, 17 new jobs, AI matching on) — peak | 23 | 3.6 GB | 3.2 GB |
| Full scan — typical | 14–17 | 2.4–3.1 GB | 2.4–3.0 GB |
| Idle after the scan (scan browsers closed after 60 s) | 6 | 0.9 GB | 1.1 GB |
| Idle after scans, before this fix (0.0.1, browsers kept open) | 18 | 3.0 GB | 2.8 GB |

The old stack, always on:

| Part | Memory |
|---|---|
| Electron app (17 processes) | ~3.3 GB |
| Supabase in Docker (10 containers, `docker stats` now) | 2.0 GB (~2.7 GB with the WSL VM) |
| Total | ~5.3–6.0 GB |

## Reading it

- Between scans (most of the time) JobFinder uses about 0.9 GB, against ~6 GB for the old stack.
- During a scan it peaks at 3.6 GB for a few minutes per hour. Most of it is Chromium: the
  LinkedIn renderers (400–500 MB each), the GPU process (~450 MB) and one renderer per site
  isolated frame. The JVM stays under its 1 GB heap cap.
- Before 0.0.2 the scan browsers stayed open with the last pages loaded, so the app held ~3 GB
  all the time. `CefBrowserPool` now closes a browser that is idle for 60 s.

## Possible next steps

- Fewer renderer processes during a scan (Chromium site isolation makes one per cross-site
  frame); check that bot protection still passes before changing it.
- Software rendering for the hidden browsers would drop the GPU process; check the same.
