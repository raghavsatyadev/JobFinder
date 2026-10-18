package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Stores the old Postgres `text[]` columns as JSON arrays in TEXT, so `json_each` can read them.
 */
class JsonColumnConverters {
  @ColumnTypeConverter fun fromStringList(value: List<String>): String = encodeStringList(value)

  @ColumnTypeConverter fun toStringList(value: String): List<String> = decodeStringList(value)

  companion object {
    private val serializer = ListSerializer(String.serializer())

    fun encodeStringList(value: List<String>): String = Json.encodeToString(serializer, value)

    fun decodeStringList(value: String): List<String> = Json.decodeFromString(serializer, value)
  }
}
