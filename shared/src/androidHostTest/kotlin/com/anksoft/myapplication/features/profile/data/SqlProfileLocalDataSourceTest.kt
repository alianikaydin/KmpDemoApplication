package com.anksoft.myapplication.features.profile.data

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.database.AppDatabaseProvider
import com.anksoft.myapplication.core.database.JdbcDatabaseDriverFactory
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.features.profile.data.datasource.SqlProfileLocalDataSource
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/** Runs the real SQL queries on a real SQLite file (JDBC), the same command CI runs. */
class SqlProfileLocalDataSourceTest {

    private lateinit var directory: File
    private lateinit var databaseFile: File
    private lateinit var factory: JdbcDatabaseDriverFactory
    private lateinit var writer: RecordingLogWriter
    private lateinit var dataSource: SqlProfileLocalDataSource

    private val profileA = AccountProfile(userId = "user-a", email = "a@example.com", name = "Alice")
    private val profileB = AccountProfile(userId = "user-b", email = "b@example.com", name = null)

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("profile-local-test").toFile()
        databaseFile = File(directory, "app.db")
        factory = JdbcDatabaseDriverFactory(databaseFile)
        val (logger, recorded) = recordingLogger()
        writer = recorded
        dataSource = SqlProfileLocalDataSource(
            AppDatabaseProvider(factory, logger),
            UnconfinedTestDispatcher(),
            logger,
        )
    }

    @AfterTest
    fun tearDown() {
        factory.closeDrivers()
        directory.deleteRecursively()
    }

    private fun rowCount(): Int =
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM account_profile").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    // AC-27
    @Test
    fun aStoredProfileIsStillThereAfterTheDatabaseWasClosedAndOpenedAgain() = runTest {
        dataSource.replaceWith(profileA)
        factory.closeDrivers()

        val (logger, _) = recordingLogger()
        val reopened = SqlProfileLocalDataSource(
            AppDatabaseProvider(JdbcDatabaseDriverFactory(databaseFile), logger),
            UnconfinedTestDispatcher(),
            logger,
        )

        assertThat(reopened.read(profileA.userId)).isEqualTo(Result.Success(profileA))
    }

    // AC-1
    @Test
    fun aProfileWithoutANameComesBackWithoutAName() = runTest {
        dataSource.replaceWith(profileB)

        assertThat(dataSource.read(profileB.userId)).isEqualTo(Result.Success(profileB))
    }

    // AC-12
    @Test
    fun replacingWithAnotherAccountDeletesTheFirstAccountsRow() = runTest {
        dataSource.replaceWith(profileA)
        dataSource.replaceWith(profileB)

        assertThat(dataSource.read(profileA.userId)).isEqualTo(Result.Success(null))
        assertThat(dataSource.read(profileB.userId)).isEqualTo(Result.Success(profileB))
        assertThat(rowCount()).isEqualTo(1)
    }

    // AC-12
    @Test
    fun readNeverReturnsAnotherAccountsRow() = runTest {
        dataSource.replaceWith(profileA)

        assertThat(dataSource.read(profileB.userId)).isEqualTo(Result.Success(null))
    }

    // AC-12
    @Test
    fun storingTheSameAccountAgainUpdatesItsRow() = runTest {
        dataSource.replaceWith(profileA)
        val renamed = profileA.copy(name = "Alice Smith")
        dataSource.replaceWith(renamed)

        assertThat(dataSource.read(profileA.userId)).isEqualTo(Result.Success(renamed))
        assertThat(rowCount()).isEqualTo(1)
    }

    // AC-10, AC-11
    @Test
    fun retainingNobodyEmptiesTheTable() = runTest {
        dataSource.replaceWith(profileA)

        assertThat(dataSource.retainOnly(null)).isEqualTo(Result.Success(Unit))

        assertThat(rowCount()).isEqualTo(0)
    }

    // AC-12
    @Test
    fun retainingOneAccountKeepsOnlyItsRow() = runTest {
        // Both rows are inserted directly: replaceWith would already remove the other account.
        dataSource.replaceWith(profileA)
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    "INSERT INTO account_profile(user_id, email, name) VALUES ('user-b', 'b@example.com', NULL)",
                )
            }
        }
        assertThat(rowCount()).isEqualTo(2)

        dataSource.retainOnly(profileB.userId)

        assertThat(dataSource.read(profileA.userId)).isEqualTo(Result.Success(null))
        assertThat(dataSource.read(profileB.userId)).isEqualTo(Result.Success(profileB))
        assertThat(rowCount()).isEqualTo(1)
    }

    // AC-23
    @Test
    fun withoutADatabaseNothingIsFoundWritesSucceedAndNothingIsLogged() = runTest {
        val (logger, noDatabaseWriter) = recordingLogger()
        val withoutDatabase = SqlProfileLocalDataSource(
            AppDatabaseProvider(driverFactory = null, logger = logger),
            UnconfinedTestDispatcher(),
            logger,
        )

        assertThat(withoutDatabase.read("user-a")).isEqualTo(Result.Success(null))
        assertThat(withoutDatabase.replaceWith(profileA)).isEqualTo(Result.Success(Unit))
        assertThat(withoutDatabase.retainOnly(null)).isEqualTo(Result.Success(Unit))
        assertThat(noDatabaseWriter.entries).isEmpty()
    }

    // AC-15, AC-17
    @Test
    fun aFailingQueryIsReportedAsAnErrorAndLoggedWithoutPersonalData() = runTest {
        dataSource.replaceWith(profileA)
        // Breaks the next query for real: the table is gone.
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { it.execute("DROP TABLE account_profile") }
        }
        val before = writer.entries.size

        val result = dataSource.read(profileA.userId)

        assertThat(result).isEqualTo(Result.Failure(DataError.Local.UNKNOWN))
        val logged = writer.entries.drop(before)
        assertThat(logged).hasSize(1)
        assertThat(logged.single().severity).isEqualTo(LogSeverity.WARN)
        assertThat(logged.single().tag).isEqualTo(LogTags.PROFILE)
        assertThat(logged.single().message).doesNotContain(profileA.userId)
        assertThat(logged.single().message).doesNotContain(profileA.email)
        assertThat(logged.single().message).doesNotContain(directory.absolutePath)
    }
}
