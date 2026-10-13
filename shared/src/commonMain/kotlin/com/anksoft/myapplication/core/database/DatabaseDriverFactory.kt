package com.anksoft.myapplication.core.database

import app.cash.sqldelight.db.SqlDriver

/** File name of [AppDatabase] on Android and iOS. Each app (dev/stage/prod) has its own sandbox (AC-20). */
internal const val DATABASE_NAME = "app.db"

/** Platform access to the SQLite file. Bound on Android and iOS only; the web has no database (AC-23). */
interface DatabaseDriverFactory {
    /** Creates the driver. Creating or migrating the file may happen here or on the first query. */
    fun create(): SqlDriver

    /** Deletes the database file and its journal files. Never throws. */
    fun deleteDatabaseFile()
}
