package com.anksoft.myapplication.core.database

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.recordingLogger
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class AppDatabaseProviderTest {

    private lateinit var directory: File
    private lateinit var databaseFile: File

    @BeforeTest
    fun createDirectory() {
        directory = Files.createTempDirectory("app-database-provider-test").toFile()
        databaseFile = File(directory, "app.db")
    }

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    // AC-17
    @Test
    fun opensTheDatabaseOnceAndReturnsTheSameInstance() {
        val (logger, writer) = recordingLogger()
        val factory = JdbcDatabaseDriverFactory(databaseFile)
        val provider = AppDatabaseProvider(factory, logger)

        val first = provider.databaseOrNull()
        val second = provider.databaseOrNull()

        assertThat(first).isNotNull()
        assertThat(second).isSameInstanceAs(first)
        assertThat(factory.createCalls).isEqualTo(1)
        assertThat(writer.entries).isEmpty()
    }

    @Test
    fun doesNotOpenTheFileBeforeTheFirstUse() {
        val (logger, _) = recordingLogger()
        val factory = JdbcDatabaseDriverFactory(databaseFile)

        AppDatabaseProvider(factory, logger)

        assertThat(factory.createCalls).isEqualTo(0)
    }

    // AC-17
    @Test
    fun replacesACorruptFileOnceAndOpensAnEmptyDatabase() {
        databaseFile.writeBytes(ByteArray(4096) { 'x'.code.toByte() })
        val (logger, writer) = recordingLogger()
        val factory = JdbcDatabaseDriverFactory(databaseFile)
        val provider = AppDatabaseProvider(factory, logger)

        val database = provider.databaseOrNull()

        val opened = requireNotNull(database) { "the database should open after the file was replaced" }
        assertThat(opened.accountProfileQueries.selectByUserId("any").executeAsOneOrNull()).isNull()
        assertThat(factory.deleteCalls).isEqualTo(1)
        assertThat(factory.createCalls).isEqualTo(2)
        assertThat(writer.entries).hasSize(1)
        assertThat(writer.entries.single().severity).isEqualTo(LogSeverity.ERROR)
        assertThat(writer.entries.single().tag).isEqualTo(LogTags.DATABASE)
    }

    // AC-15
    @Test
    fun logLinesNeverContainTheFilePath() {
        databaseFile.writeBytes(ByteArray(4096) { 'x'.code.toByte() })
        val (logger, writer) = recordingLogger()
        val provider = AppDatabaseProvider(JdbcDatabaseDriverFactory(databaseFile), logger)

        provider.databaseOrNull()

        assertThat(writer.entries).hasSize(1)
        writer.entries.forEach { entry ->
            assertThat(entry.message).doesNotContain(directory.absolutePath)
            assertThat(entry.message).doesNotContain("app.db")
        }
    }

    // AC-17
    @Test
    fun staysUnavailableForTheProcessWhenTheDatabaseCannotBeOpenedTwice() {
        val (logger, writer) = recordingLogger()
        val factory = FailingDatabaseDriverFactory()
        val provider = AppDatabaseProvider(factory, logger)

        val first = provider.databaseOrNull()
        val second = provider.databaseOrNull()

        assertThat(first).isNull()
        assertThat(second).isNull()
        assertThat(writer.entries).hasSize(2)
        assertThat(writer.entries.map { it.severity }).isEqualTo(listOf(LogSeverity.ERROR, LogSeverity.ERROR))
        assertThat(factory.deleteCalls).isEqualTo(1)
        assertThat(factory.createCalls).isEqualTo(2)
    }

    // AC-23
    @Test
    fun hasNoDatabaseAndLogsNothingWithoutADriverFactory() {
        val (logger, writer) = recordingLogger()
        val provider = AppDatabaseProvider(driverFactory = null, logger = logger)

        assertThat(provider.databaseOrNull()).isNull()
        assertThat(writer.entries).isEmpty()
    }
}
