package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import kotlin.test.Test

/**
 * Tripwire for the stored schema. A new table or column must be handled at session end and written
 * down in docs/local-database.md before this list is updated.
 */
class AppDatabaseSchemaTest {

    private fun newDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        return driver
    }

    private fun SqlDriver.strings(sql: String, column: Int): List<String> =
        executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val values = mutableListOf<String>()
                while (cursor.next().value) {
                    values += cursor.getString(column).orEmpty()
                }
                QueryResult.Value(values)
            },
            parameters = 0,
        ).value

    // AC-14, AC-22
    @Test
    fun schema_has_only_the_account_profile_table() {
        val driver = newDriver()
        val tables = driver.strings(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            column = 0,
        )
        assertThat(
            tables,
            "New table: clear it at session end and add it to docs/local-database.md, then update this list",
        ).containsExactlyInAnyOrder("account_profile")
        driver.close()
    }

    // AC-14, AC-22
    @Test
    fun account_profile_has_only_user_id_email_and_name_columns() {
        val driver = newDriver()
        val columns = driver.strings("PRAGMA table_info(account_profile)", column = 1)
        assertThat(
            columns,
            "New column: decide whether it is personal data, clear it at session end and document it in docs/local-database.md, then update this list",
        ).containsExactlyInAnyOrder("user_id", "email", "name")
        driver.close()
    }

    // AC-19
    @Test
    fun schema_version_is_one_until_a_migration_is_added() {
        assertThat(AppDatabase.Schema.version).isEqualTo(1L)
    }
}
