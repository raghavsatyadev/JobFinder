package io.github.raghavsatyadev.jobfinder.server.scan

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Clock
import java.time.Duration as JavaDuration
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Starts [Scanner.startScanAll] on the cron rule from the settings (the old app's `node-cron`
 * schedule), in the PC's time zone. A `null` rule is "Never". A new rule replaces the old schedule
 * at once; a scan still running from the old one finishes. Scheduled scans start at least [minGap]
 * apart whatever the rule says, and each start is delayed by a random time up to [maxJitter], so
 * job sites do not see a fast, clock-exact pattern from one address and block it.
 */
class ScanScheduler(
  private val scanner: Scanner,
  private val cronRules: Flow<String?>,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val minGap: Duration = MIN_GAP,
  private val maxJitter: Duration = MAX_JITTER,
  private val random: Random = Random.Default,
) {
  private var lastStart: ZonedDateTime? = null

  private val log = LoggerFactory.getLogger(ScanScheduler::class.java)

  /** Follows [cronRules] in [scope] until the scope ends. */
  fun start(scope: CoroutineScope): Job = scope.launch {
    cronRules.distinctUntilChanged().collectLatest { rule ->
      if (rule == null) {
        log.info("Scheduled scans are off")
        return@collectLatest
      }
      val execution =
        executionTime(rule)
          ?: run {
            log.error("Ignoring invalid cron rule '{}'", rule)
            return@collectLatest
          }
      log.info("Scheduled scans: {}", rule)
      while (true) {
        val now = ZonedDateTime.now(clock)
        val wait = untilNext(execution, now, lastStart, minGap) ?: break
        delay(wait + jitter())
        try {
          lastStart = ZonedDateTime.now(clock)
          scanner.startScanAll()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          log.error("Scheduled scan did not start", e)
        }
      }
    }
  }

  private fun jitter(): Duration =
    if (maxJitter <= Duration.ZERO) Duration.ZERO
    else random.nextLong(maxJitter.inWholeMilliseconds).milliseconds

  companion object {
    /**
     * Job sites block an address that scans them every hour. The hard floor; the app advises 12
     * hours and warns when the user picks less.
     */
    val MIN_GAP: Duration = 2.hours

    /** Scans start at a random time within this long after the rule's time. */
    val MAX_JITTER: Duration = 45.minutes

    private val parser = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))

    /** The parsed rule, or `null` when it is not a valid five-field Unix cron rule. */
    fun executionTime(rule: String): ExecutionTime? =
      try {
        ExecutionTime.forCron(parser.parse(rule))
      } catch (e: IllegalArgumentException) {
        null
      }

    /**
     * Time from [now] to the next run that is at least [minGap] after [lastStart], or `null` when
     * the rule never fires again.
     */
    fun untilNext(
      execution: ExecutionTime,
      now: ZonedDateTime,
      lastStart: ZonedDateTime? = null,
      minGap: Duration = Duration.ZERO,
    ): Duration? {
      val earliest = lastStart?.plus(minGap.toJavaDuration())?.takeIf { it.isAfter(now) } ?: now
      // nextExecution is strictly after its argument, so step back to allow a run at `earliest`.
      return execution
        .nextExecution(if (earliest == now) now else earliest.minusNanos(1))
        .map { JavaDuration.between(now, it).toKotlinDuration() }
        .orElse(null)
    }
  }
}
