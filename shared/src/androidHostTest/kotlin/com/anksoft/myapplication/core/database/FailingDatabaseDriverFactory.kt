package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.SqlDriver

/** Host-test factory whose driver can never be created. Counts the calls. */
class FailingDatabaseDriverFactory : DatabaseDriverFactory {

    var createCalls: Int = 0
        private set

    var deleteCalls: Int = 0
        private set

    override fun create(): SqlDriver {
        createCalls++
        error("cannot open the database")
    }

    override fun deleteDatabaseFile() {
        deleteCalls++
    }
}
