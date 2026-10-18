package com.gracefultools.astrid.ui.common

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The core answers with label KEYS (`repeating.weekly`, `reminder.15_minutes_before`), never English;
 * each shell has the words. Key `a.b_c` is the resource `core_a_b_c` (res/values/core_keys.xml,
 * generated from the Windows table so every client says the same thing).
 */
object CoreLabels {
    @SuppressLint("DiscouragedApi") // keys arrive at runtime; this is the one lookup by name
    fun resolve(context: Context, key: String, vararg args: Any): String {
        val name = "core_" + key.trimEnd('.').replace('.', '_')
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) return humanize(key)
        return if (args.isEmpty()) context.getString(id) else runCatching { context.getString(id, *args) }.getOrElse { context.getString(id) }
    }

    /** A key this build has no words for, said as plainly as its last segment allows. */
    fun humanize(key: String): String =
        key.trimEnd('.').substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** A repeat summary: the core's parts (`{key, count?, values?, date?}`) joined into one line. */
    fun summary(context: Context, parts: JsonElement?): String? {
        val list = (parts as? JsonArray)?.filterIsInstance<JsonObject>() ?: return null
        if (list.isEmpty()) return null
        return list.joinToString(" ") { part ->
            val key = (part["key"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val count = (part["count"] as? JsonPrimitive)?.longOrNull
            val values = (part["values"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val date = (part["date"] as? JsonPrimitive)?.contentOrNull?.take(10)
            val args = buildList<Any> {
                count?.let { add(it.toString()) }
                if (values.isNotEmpty()) add(values.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
                date?.let { add(it) }
            }
            resolve(context, key, *args.toTypedArray())
        }.trim().ifEmpty { null }
    }
}

@Composable
fun coreLabel(key: String, vararg args: Any): String = CoreLabels.resolve(LocalContext.current, key, *args)
