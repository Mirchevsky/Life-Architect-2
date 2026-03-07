package com.mirchevsky.lifearchitect2.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mirchevsky.lifearchitect2.data.db.entity.Prerequisite

/**
 * Type converters to allow Room to reference complex data types.
 */
class Converters {
    private val gson = Gson()

    // Converter for List<String>
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value == null) {
            return emptyList()
        }
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    // Converter for List<Prerequisite>
    @TypeConverter
    fun fromPrerequisiteList(value: String?): List<Prerequisite> {
        if (value == null) {
            return emptyList()
        }
        val listType = object : TypeToken<List<Prerequisite>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toPrerequisiteList(list: List<Prerequisite>): String {
        return gson.toJson(list)
    }
}