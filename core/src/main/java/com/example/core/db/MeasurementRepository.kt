package com.example.core.db

import android.content.Context

class MeasurementRepository private constructor(
    private val dao: MeasurementDao
) {

    fun addAndTrim(entity: MeasurementEntity, keepLast: Int) {
        dao.insert(entity)
        dao.trimToLatest(keepLast)
    }

    fun latest(keepLast: Int): List<MeasurementEntity> = dao.latest(keepLast)

    companion object {
        @Volatile
        private var INSTANCE: MeasurementRepository? = null

        fun getInstance(context: Context): MeasurementRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                MeasurementRepository(db.measurementDao()).also { INSTANCE = it }
            }
        }
    }
}
