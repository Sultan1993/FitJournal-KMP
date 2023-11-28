package kz.maestrosultan.fitjournal.kmp.sqldelight

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
