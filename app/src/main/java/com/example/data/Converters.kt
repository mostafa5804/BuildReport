package com.example.data

import androidx.room.TypeConverter
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @TypeConverter
    fun fromTaskList(list: List<TaskEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, TaskEntry::class.java)
        return moshi.adapter<List<TaskEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toTaskList(json: String?): List<TaskEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, TaskEntry::class.java)
            moshi.adapter<List<TaskEntry>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromMachineryList(list: List<MachineryEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, MachineryEntry::class.java)
        return moshi.adapter<List<MachineryEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toMachineryList(json: String?): List<MachineryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, MachineryEntry::class.java)
            moshi.adapter<List<MachineryEntry>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromManpowerList(list: List<ManpowerEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, ManpowerEntry::class.java)
        return moshi.adapter<List<ManpowerEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toManpowerList(json: String?): List<ManpowerEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, ManpowerEntry::class.java)
            moshi.adapter<List<ManpowerEntry>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromMaterialList(list: List<MaterialEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, MaterialEntry::class.java)
        return moshi.adapter<List<MaterialEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toMaterialList(json: String?): List<MaterialEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, MaterialEntry::class.java)
            moshi.adapter<List<MaterialEntry>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromLegalPermitList(list: List<LegalPermitEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, LegalPermitEntry::class.java)
        return moshi.adapter<List<LegalPermitEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toLegalPermitList(json: String?): List<LegalPermitEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, LegalPermitEntry::class.java)
            moshi.adapter<List<LegalPermitEntry>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            moshi.adapter<List<String>>(type).fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
