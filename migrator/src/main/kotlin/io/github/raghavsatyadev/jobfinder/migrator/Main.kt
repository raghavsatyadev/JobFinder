package io.github.raghavsatyadev.jobfinder.migrator

import androidx.room3.useReaderConnection
import androidx.room3.withWriteTransaction
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabaseFactory
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDefaults
import io.github.raghavsatyadev.jobfinder.server.repository.LocalJobsRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalMatchingRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.BackupService
import io.github.raghavsatyadev.jobfinder.server.service.NoteFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking

private const val DEFAULT_PG = "postgresql://postgres:postgres@127.0.0.1:54322/postgres"

private val USAGE =
  """
  JobFinder migrator: one-time import from the old first2apply Supabase Postgres into the SQLite database.

  Usage: migrator [--db <path>] [--pg <url>] [--settings <settings.json>] [--force | --merge]
         migrator [--db <path>] --backup <file.zip>

    --db <path>        SQLite file to fill (default ${ServerDatabaseFactory.defaultPath()})
    --pg <url>         Postgres URL, libpq or JDBC form (default $DEFAULT_PG)
    --settings <path>  Electron settings.json (default: searched in %APPDATA%)
    --force            Replace the data in a database that is not empty
    --merge            Keep the SQLite data and add what only Postgres has: jobs (by external
                       id), links (by URL) and the notes of the jobs it adds
    --backup <file>    Write a full backup of the SQLite database (as Settings → Export
                       everything does) and exit; run it while the app is closed
  """
    .trimIndent()

private data class Options(
  val db: Path,
  val pg: String,
  val settings: Path?,
  val force: Boolean,
  val merge: Boolean,
  val backup: Path? = null,
)

private fun parseArgs(args: Array<String>): Options {
  var db = ServerDatabaseFactory.defaultPath()
  var pg = DEFAULT_PG
  var settings: Path? = null
  var force = false
  var merge = false
  var backup: Path? = null
  var i = 0
  fun value(name: String): String = args.getOrNull(++i) ?: fail("$name needs a value")
  while (i < args.size) {
    when (args[i]) {
      "--db" -> db = Path.of(value("--db"))
      "--pg" -> pg = value("--pg")
      "--settings" -> settings = Path.of(value("--settings"))
      "--force" -> force = true
      "--merge" -> merge = true
      "--backup" -> backup = Path.of(value("--backup"))
      "-h",
      "--help" -> {
        println(USAGE)
        exitProcess(0)
      }
      else -> fail("Unknown option: ${args[i]}")
    }
    i++
  }
  if (force && merge) fail("--force and --merge don't go together")
  return Options(db.toAbsolutePath(), pg, settings, force, merge, backup?.toAbsolutePath())
}

private fun fail(message: String, code: Int = 2): Nothing {
  System.err.println("ERROR: $message")
  System.err.println()
  System.err.println(USAGE)
  exitProcess(code)
}

fun main(args: Array<String>) {
  val options = parseArgs(args)
  options.backup?.let { exitProcess(backup(options.db, it)) }
  println("SQLite:   ${options.db}")
  println("Postgres: ${options.pg.replace(Regex("://([^:@/]+):[^@/]+@"), "://$1:***@")}")

  val db = ServerDatabaseFactory.create(options.db)
  val code =
    try {
      if (runBlocking { if (options.merge) merge(db, options) else migrate(db, options) }) 0 else 1
    } catch (e: RefusedException) {
      System.err.println("ERROR: ${e.message}")
      3
    } finally {
      db.close()
    }
  exitProcess(code)
}

/**
 * Writes a full backup of [dbPath] to [out]; note attachments come from the `notes` folder beside
 * it.
 */
private fun backup(dbPath: Path, out: Path): Int {
  val db = ServerDatabaseFactory.create(dbPath)
  try {
    val dataDir = dbPath.parent
    val service =
      BackupService(
        db = db,
        settings = { LocalSettingsRepository(db.settingsDao()) },
        matching = LocalMatchingRepository(db.advancedMatchingDao()),
        noteFiles = NoteFiles(dataDir.resolve("notes")),
        events = ServerEvents(),
        backupDir = dataDir.resolve("backups"),
        appVersion = "migrator",
      )
    val bytes = runBlocking { service.exportFull() }
    out.parent?.createDirectories()
    out.writeBytes(bytes)
    val manifest = BackupFiles.readManifest(bytes)
    println(
      "Backup written: $out (${bytes.size / 1024} KB): ${manifest.jobs} jobs, " +
        "${manifest.searches} searches, ${manifest.notes} notes, ${manifest.atsReviews} ATS reviews"
    )
    return 0
  } finally {
    db.close()
  }
}

private class RefusedException(message: String) : Exception(message)

/** Imports, verifies and prints the report. Returns `true` when every check matches. */
private suspend fun migrate(db: ServerDatabase, options: Options): Boolean {
  val userRows = userRowCounts(db)
  if (userRows.values.any { it > 0 } && !options.force) {
    throw RefusedException(
      "the SQLite database is not empty (" +
        userRows.filterValues { it > 0 }.entries.joinToString { "${it.key}=${it.value}" } +
        "). Re-run with --force to replace its data."
    )
  }

  val settings = ElectronSettings.load(options.settings)
  PostgresSource(options.pg).use { pg ->
    val snapshot = pg.read()
    if (snapshot.userCount != 1) {
      println("WARNING: Postgres has ${snapshot.userCount} users; importing every row.")
    }
    require(snapshot.advancedMatching.size <= 1) {
      "advanced_matching has ${snapshot.advancedMatching.size} rows; expected one"
    }

    db.withWriteTransaction {
      val oldToken = db.settingsDao().get()?.pairingToken
      // Children first, so foreign keys hold.
      db.notesDao().deleteAll()
      db.jobsDao().deleteAll()
      db.linksDao().deleteAll()
      db.sitesDao().deleteAll()
      db.advancedMatchingDao().deleteAll()
      db.aiUsageDao().deleteAll()
      db.htmlDumpsDao().deleteAll()
      db.settingsDao().deleteAll()

      db.sitesDao().insertAll(snapshot.sites)
      db.linksDao().insertAll(snapshot.links)
      db.jobsDao().insertAll(snapshot.jobs)
      db.notesDao().insertAll(snapshot.notes)
      snapshot.advancedMatching.firstOrNull()?.let { db.advancedMatchingDao().upsert(it) }
      db.aiUsageDao().insertAll(snapshot.aiUsage)
      // Keep the pairing token on --force, so paired phones stay paired.
      val row = SettingsDefaults.newRow(settings.settings)
      db.settingsDao().upsert(if (oldToken != null) row.copy(pairingToken = oldToken) else row)
    }

    println()
    println(
      "Imported. Jobs with the legacy 'downloaded' status mapped to 'archived': ${snapshot.downloadedJobs}"
    )
    println("Electron settings: ${settings.source}")
    println(
      "  -> ${db.settingsDao().get()?.let { "cronRule=${it.cronRule ?: "Never"}, preventSleep=${it.preventSleep}, useSound=${it.useSound}, inAppBrowser=${it.inAppBrowserEnabled}, port=${it.serverPort}, scraperConcurrency=${it.scraperConcurrency}" }}"
    )
    return verify(db, pg)
  }
}

/**
 * Adds to a database the new app already uses what only Postgres has, while the old app kept
 * scanning. Rows in both stay as SQLite has them; added rows get new ids.
 */
private suspend fun merge(db: ServerDatabase, options: Options): Boolean {
  val linkIds = sqliteRows(db, "SELECT url, id FROM links").associate { it[0] to it[1].toLong() }
  val siteIds = sqliteRows(db, "SELECT id FROM sites").map { it[0].toLong() }.toSet()
  PostgresSource(options.pg).use { pg ->
    val snapshot = pg.read()
    var addedLinks = 0
    val addedJobs = mutableMapOf<String, Int>()
    var addedNotes = 0
    var noSite = 0
    db.withWriteTransaction {
      val links =
        snapshot.links
          .filter { it.siteId in siteIds }
          .associate { link ->
            link.id to
              (linkIds[link.url] ?: db.linksDao().insert(link.copy(id = 0)).also { addedLinks++ })
          }
      val jobs = mutableMapOf<Long, Long>()
      for (job in snapshot.jobs) {
        if (job.siteId !in siteIds) {
          noSite++
          continue
        }
        // Ignored when the external id is already there.
        val id = db.jobsDao().insert(job.copy(id = 0, linkId = job.linkId?.let(links::get)))
        if (id > 0) {
          jobs[job.id] = id
          addedJobs.merge(job.status, 1, Int::plus)
        }
      }
      for (note in snapshot.notes) {
        val jobId = jobs[note.jobId] ?: continue
        db.notesDao().insert(note.copy(id = 0, jobId = jobId))
        addedNotes++
      }
    }
    println()
    println("Merged from Postgres:")
    println("  links added: $addedLinks of ${snapshot.links.size}")
    println(
      "  jobs added:  ${addedJobs.values.sum()} of ${snapshot.jobs.size}" +
        addedJobs.entries.joinToString(prefix = " (", postfix = ")") { "${it.key}=${it.value}" }
    )
    println("  notes added: $addedNotes of ${snapshot.notes.size}")
    if (noSite > 0) println("  jobs skipped, site unknown here: $noSite")
    return noSite == 0
  }
}

private suspend fun sqliteRows(db: ServerDatabase, sql: String): List<List<String>> =
  db.useReaderConnection { conn ->
    conn.usePrepared(sql) { st ->
      buildList {
        while (st.step()) add((0 until st.getColumnCount()).map { st.getText(it) })
      }
    }
  }

/** Rows in the tables the user fills (sites are seeded, so they don't count). */
private suspend fun userRowCounts(db: ServerDatabase): Map<String, Long> =
  linkedMapOf(
    "links" to db.linksDao().count(),
    "jobs" to db.jobsDao().count(),
    "notes" to db.notesDao().count(),
    "advanced_matching" to db.advancedMatchingDao().count(),
    "ai_usage_daily" to db.aiUsageDao().count(),
    "settings" to if (db.settingsDao().get() != null) 1L else 0L,
  )

private suspend fun verify(db: ServerDatabase, pg: PostgresSource): Boolean {
  val rows = mutableListOf<Triple<String, String, String>>()
  var ok = true
  fun check(name: String, expected: Any, actual: Any, mustMatch: Boolean = true) {
    rows += Triple(name, expected.toString(), actual.toString())
    if (mustMatch && expected != actual) ok = false
  }

  for (table in listOf("sites", "links", "jobs", "notes", "advanced_matching", "ai_usage_daily")) {
    check(table, pg.scalar("SELECT COUNT(*) FROM $table"), sqliteCount(db, table))
  }
  for (table in listOf("sites", "links", "jobs", "notes")) {
    val same = pg.ids(table) == sqliteIds(db, table)
    check("$table ids identical", "yes", if (same) "yes" else "NO")
  }

  val pgStatus = pg.statusCounts()
  val sqliteStatus = LocalJobsRepository(db.jobsDao()).countJobs().byStatus
  for (status in JobStatus.entries) {
    val expected =
      (pgStatus[status.wire] ?: 0L) +
        if (status == JobStatus.ARCHIVED) pgStatus[JobStatus.LEGACY_DOWNLOADED] ?: 0L else 0L
    check("jobs status=${status.wire}", expected, (sqliteStatus[status] ?: 0).toLong())
  }
  pgStatus[JobStatus.LEGACY_DOWNLOADED]?.let {
    check("  (pg 'downloaded' -> archived)", it, "-", mustMatch = false)
  }

  val pgHash = pg.promptSha256().firstOrNull() ?: "(no row)"
  val sqliteHash = db.advancedMatchingDao().get()?.prompt?.let(::sha256) ?: "(no row)"
  check("chatgpt_prompt sha256", pgHash, sqliteHash)

  printTable(rows)
  println()
  println(
    if (ok) "VERIFIED: every count and the prompt hash match." else "MISMATCH: see the table above."
  )
  return ok
}

private suspend fun sqliteCount(db: ServerDatabase, table: String): Long =
  db.useReaderConnection { conn ->
    conn.usePrepared("SELECT COUNT(*) FROM $table") { st ->
      st.step()
      st.getLong(0)
    }
  }

private suspend fun sqliteIds(db: ServerDatabase, table: String): List<Long> =
  db.useReaderConnection { conn ->
    conn.usePrepared("SELECT id FROM $table ORDER BY id") { st ->
      buildList { while (st.step()) add(st.getLong(0)) }
    }
  }

private fun sha256(text: String): String =
  MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") {
    "%02x".format(it)
  }

private fun printTable(rows: List<Triple<String, String, String>>) {
  val header = Triple("check", "postgres", "sqlite")
  val all = listOf(header) + rows
  val w1 = all.maxOf { it.first.length }
  val w2 = all.maxOf { it.second.length }
  val w3 = all.maxOf { it.third.length }
  fun line(r: Triple<String, String, String>, mark: String) =
    "| ${r.first.padEnd(w1)} | ${r.second.padStart(w2)} | ${r.third.padStart(w3)} | $mark"
  val rule = "+-${"-".repeat(w1)}-+-${"-".repeat(w2)}-+-${"-".repeat(w3)}-+"
  println()
  println(rule)
  println(line(header, ""))
  println(rule)
  for (r in rows) println(
    line(r, if (r.third == "-") "" else if (r.second == r.third) "OK" else "DIFF")
  )
  println(rule)
}
