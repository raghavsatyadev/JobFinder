package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlinx.coroutines.Dispatchers

/** The desktop app's SQLite database (Room 3, bundled SQLite with FTS5 and JSON1). */
@Database(
  entities =
    [
      SiteEntity::class,
      LinkEntity::class,
      JobEntity::class,
      JobFtsEntity::class,
      NoteEntity::class,
      AdvancedMatchingEntity::class,
      SettingsEntity::class,
      HtmlDumpEntity::class,
      AiUsageDailyEntity::class,
      ResumeEntity::class,
      AtsReviewEntity::class,
      CompanyRatingsEntity::class,
      CompanySalaryEntity::class,
      AiPromptEntity::class,
      GhostCheckEntity::class,
      CareersBoardEntity::class,
      NegotiationTipsEntity::class,
      AiKeyEntity::class,
      AiKeyUsageEntity::class,
      FormAnswerEntity::class,
    ],
  version = 13,
  autoMigrations =
    [
      AutoMigration(from = 1, to = 2),
      AutoMigration(from = 2, to = 3),
      AutoMigration(from = 3, to = 4),
      AutoMigration(from = 4, to = 5),
      AutoMigration(from = 5, to = 6),
      AutoMigration(from = 6, to = 7),
      AutoMigration(from = 7, to = 8),
      AutoMigration(from = 8, to = 9),
      AutoMigration(from = 9, to = 10),
      AutoMigration(from = 10, to = 11),
      AutoMigration(from = 11, to = 12),
      AutoMigration(from = 12, to = 13, spec = AppModelRename::class),
    ],
)
@ColumnTypeConverters(JsonColumnConverters::class)
abstract class ServerDatabase : RoomDatabase() {
  abstract fun sitesDao(): SitesDao

  abstract fun linksDao(): LinksDao

  abstract fun jobsDao(): JobsDao

  abstract fun notesDao(): NotesDao

  abstract fun advancedMatchingDao(): AdvancedMatchingDao

  abstract fun settingsDao(): SettingsDao

  abstract fun aiKeysDao(): AiKeysDao

  abstract fun htmlDumpsDao(): HtmlDumpsDao

  abstract fun aiUsageDao(): AiUsageDao

  abstract fun resumeDao(): ResumeDao

  abstract fun atsReviewsDao(): AtsReviewsDao

  abstract fun insightsDao(): InsightsDao

  abstract fun aiPromptsDao(): AiPromptsDao

  abstract fun ghostDao(): GhostDao

  abstract fun tipsDao(): TipsDao

  abstract fun formAnswersDao(): FormAnswersDao
}

/** v13: the app's local model is `jobfinder-qwen`, renamed from `first2apply-qwen`. */
class AppModelRename : AutoMigrationSpec {
  override suspend fun onPostMigrate(connection: SQLiteConnection) {
    connection.execSQL(
      "UPDATE settings SET ai_model = replace(ai_model, 'first2apply-qwen', 'jobfinder-qwen')"
    )
  }
}

/** Opens [ServerDatabase]. A new database gets the job boards from [SiteSeed]. */
object ServerDatabaseFactory {
  /** `%LOCALAPPDATA%\jobfinder-data` (`~/.local/share/jobfinder` off Windows). */
  fun defaultDataDir(): Path {
    val base =
      System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), ".local", "share")
    return base.resolve("jobfinder")
  }

  /** `%LOCALAPPDATA%\jobfinder-data\jobfinder.db`. */
  fun defaultPath(): Path = defaultDataDir().resolve("jobfinder.db")

  /** Opens (and creates, with parent folders) the database file at [path]. */
  fun create(path: Path = defaultPath()): ServerDatabase {
    val absolute = path.toAbsolutePath()
    absolute.parent?.createDirectories()
    return Room.databaseBuilder<ServerDatabase>(absolute.toString()) { ServerDatabase_Impl() }
      .configure()
  }

  /** A private in-memory database, for tests. */
  fun inMemory(): ServerDatabase =
    Room.inMemoryDatabaseBuilder<ServerDatabase> { ServerDatabase_Impl() }.configure()

  private fun RoomDatabase.Builder<ServerDatabase>.configure(): ServerDatabase =
    setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .addCallback(SeedCallback)
      .build()

  private object SeedCallback : RoomDatabase.Callback() {
    override suspend fun onCreate(connection: SQLiteConnection) {
      SiteSeed.insert(connection, SiteSeed.load())
    }
  }
}
