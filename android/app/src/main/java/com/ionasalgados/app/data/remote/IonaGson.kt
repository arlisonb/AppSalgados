package com.ionasalgados.app.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

object IonaGson {
    private val booleanAdapter = JsonDeserializer { json: JsonElement, _: Type, _: JsonDeserializationContext ->
        if (json.isJsonNull) return@JsonDeserializer false
        val primitive = json.asJsonPrimitive
        when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asInt != 0
            primitive.isString -> {
                val s = primitive.asString
                s.equals("true", ignoreCase = true) || s == "1"
            }
            else -> false
        }
    }

    val instance: Gson = GsonBuilder()
        .registerTypeAdapter(Boolean::class.javaObjectType, booleanAdapter)
        .registerTypeAdapter(Boolean::class.javaPrimitiveType, booleanAdapter)
        .create()
}
