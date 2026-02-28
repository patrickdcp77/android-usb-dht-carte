package com.example.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MeasurementDao {

    @Insert
    fun insert(entity: MeasurementEntity): Long

    @Query("SELECT * FROM measurements ORDER BY timestampMs DESC LIMIT :limit")
    fun latest(limit: Int): List<MeasurementEntity>

    @Query("SELECT COUNT(*) FROM measurements")
    fun count(): Int

    /**
     * Garde uniquement les N plus récentes mesures.
     * Supprime tout ce qui est plus ancien que la N-ième (trié par timestamp).
     */
    @Query(
        """
        DELETE FROM measurements
        WHERE id NOT IN (
          SELECT id FROM measurements
          ORDER BY timestampMs DESC
          LIMIT :keep
        )
        """
    )
    fun trimToLatest(keep: Int)
}
