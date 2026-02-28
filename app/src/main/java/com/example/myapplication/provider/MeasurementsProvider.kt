package com.example.myapplication.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import com.example.core.db.AppDatabase

/**
 * ContentProvider (lecture seule) exposé par l'app capteur (:app).
 *
 * Objectif: permettre à l'app carte (:appmap) de lire les dernières mesures
 * enregistrées dans la DB Room de l'app capteur.
 */
class MeasurementsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        if (uriMatcher.match(uri) != MATCH_LATEST) return null

        val limit = uri.getQueryParameter("limit")?.toIntOrNull() ?: 3000

        val db = AppDatabase.getInstance(ctx)
        val sqlDb = db.openHelper.readableDatabase

        // On passe par SQL direct pour retourner un Cursor.
        // Note: on ignore projection/selection/sortOrder ici (usage interne simple).
        val cursor = sqlDb.query(
            "SELECT * FROM measurements ORDER BY timestampMs DESC LIMIT ?",
            arrayOf(limit.toString())
        )

        cursor.setNotificationUri(ctx.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            MATCH_LATEST -> "vnd.android.cursor.dir/vnd.com.example.myapplication.measurement"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        throw UnsupportedOperationException("read-only")

    companion object {
        const val AUTHORITY = "com.example.myapplication.measurements"
        private const val PATH_LATEST = "latest"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_LATEST")

        private const val MATCH_LATEST = 1

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_LATEST, MATCH_LATEST)
        }

        object Columns : BaseColumns {
            const val TIMESTAMP_MS = "timestampMs"
            const val LATITUDE = "latitude"
            const val LONGITUDE = "longitude"
            const val TEMPERATURE_C = "temperatureC"
            const val HUMIDITY_PCT = "humidityPct"
            const val RAW = "raw"
        }
    }
}
