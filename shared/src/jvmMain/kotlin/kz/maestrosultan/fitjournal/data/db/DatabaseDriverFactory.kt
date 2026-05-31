package kz.maestrosultan.fitjournal.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

// JVM actual — exists only so commonMain compiles for the test-only jvm target.
// Tests build their own in-memory driver (see jvmTest TestDb); this returns a
// fresh in-memory database with the schema applied and FK constraints on.
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FitJournalDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        return driver
    }
}
