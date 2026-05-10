package kz.maestrosultan.fitjournal.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kz.maestrosultan.fitjournal.data.db.FitJournalDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        // foreign_keys must be enabled per-connection. Required for the
        // ON DELETE CASCADE rules on workoutExercises/workoutSets to fire.
        return AndroidSqliteDriver(
            schema = FitJournalDatabase.Schema,
            context = context,
            name = "FitJournalDatabase.db",
            callback = object : AndroidSqliteDriver.Callback(FitJournalDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
    }
}
