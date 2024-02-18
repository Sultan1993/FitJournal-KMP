package kz.maestrosultan.fitjournal.kmp.exercises

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kz.maestrosultan.fitjournal.kmp.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.kmp.ExercisesQueries

class ExercisesDBDataSource(private val dao: ExercisesQueries) {

}