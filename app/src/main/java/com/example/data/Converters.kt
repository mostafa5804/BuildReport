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
    fun toTaskList(json: String): List<TaskEntry>? {
        val type = Types.newParameterizedType(List::class.java, TaskEntry::class.java)
        return moshi.adapter<List<TaskEntry>>(type).fromJson(json)
    }

    @TypeConverter
    fun fromMachineryList(list: List<MachineryEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, MachineryEntry::class.java)
        return moshi.adapter<List<MachineryEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toMachineryList(json: String): List<MachineryEntry>? {
        val type = Types.newParameterizedType(List::class.java, MachineryEntry::class.java)
        return moshi.adapter<List<MachineryEntry>>(type).fromJson(json)
    }

    @TypeConverter
    fun fromManpowerList(list: List<ManpowerEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, ManpowerEntry::class.java)
        return moshi.adapter<List<ManpowerEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toManpowerList(json: String): List<ManpowerEntry>? {
        val type = Types.newParameterizedType(List::class.java, ManpowerEntry::class.java)
        return moshi.adapter<List<ManpowerEntry>>(type).fromJson(json)
    }

    @TypeConverter
    fun fromMaterialList(list: List<MaterialEntry>?): String {
        val type = Types.newParameterizedType(List::class.java, MaterialEntry::class.java)
        return moshi.adapter<List<MaterialEntry>>(type).toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toMaterialList(json: String): List<MaterialEntry>? {
        val type = Types.newParameterizedType(List::class.java, MaterialEntry::class.java)
        return moshi.adapter<List<MaterialEntry>>(type).fromJson(json)
    }
}
