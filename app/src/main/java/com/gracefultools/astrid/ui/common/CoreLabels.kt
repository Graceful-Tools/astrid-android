package com.gracefultools.astrid.ui.common

import android.annotation.SuppressLint
import android.content.Context
import com.gracefultools.astrid.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

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

    /**
     * A repeat summary: the core's parts (`{key, count?, values?, date?}`) joined into one line.
     *
     * The core sends wire values (`tuesday`, month `3`, week `3`) so each shell can say them in its
     * reader's words; the arguments are astrid-windows' `Converters.Say`, so the English fragments
     * stay the shared ones. A fragment always gets its numbers in two forms — the ordinal ("3rd",
     * "3.", "3e") and the plain digits — and each language picks the one its grammar wants; the month
     * also comes as its number, for the languages that write "3月" (AATD-70).
     */
    fun summary(context: Context, parts: JsonElement?): String? {
        val list = (parts as? JsonArray)?.filterIsInstance<JsonObject>() ?: return null
        if (list.isEmpty()) return null
        val locale = context.resources.configuration.locales[0]
        val separator = context.getString(R.string.list_separator)
        val text = StringBuilder()
        for (part in list) {
            val key = (part["key"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val count = (part["count"] as? JsonPrimitive)?.longOrNull
            val values = (part["values"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val date = (part["date"] as? JsonPrimitive)?.contentOrNull?.take(10)
            val n = count ?: 1
            val piece = when (key) {
                // The singular is its own key: "every 1 weeks" is wrong in every language.
                "repeat.every_n_days", "repeat.every_n_weeks", "repeat.every_n_months", "repeat.every_n_years" ->
                    if (n == 1L) resolve(context, key.replace("every_n_", "every_").removeSuffix("s"))
                    else resolve(context, key, n.toString())
                "repeat.on_weekdays" -> resolve(context, key, values.joinToString(separator) { weekday(it, locale) })
                "repeat.on_day_of_month" -> resolve(context, key, ordinal(context, n), n.toString())
                "repeat.on_nth_weekday" ->
                    resolve(context, key, ordinal(context, n), weekday(values.firstOrNull().orEmpty(), locale), n.toString())
                "repeat.on_month_and_day" ->
                    resolve(context, key, month(values.firstOrNull(), locale), ordinal(context, n), n.toString(), values.firstOrNull().orEmpty())
                "repeat.ends_on" -> date?.let { resolve(context, key, localDate(it, locale)) }.orEmpty()
                else -> {
                    val args = buildList<Any> {
                        count?.let { add(it.toString()) }
                        if (values.isNotEmpty()) add(values.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
                        date?.let { add(it) }
                    }
                    resolve(context, key, *args.toTypedArray())
                }
            }
            if (piece.isEmpty()) continue
            // Only the trailing qualifier takes a comma, the way iOS and Windows word it.
            if (text.isNotEmpty()) text.append(if (key == "repeat.from_due_date") ", " else " ")
            text.append(piece)
        }
        return text.toString().trim().ifEmpty { null }
    }

    /** A wire weekday (`tuesday`) as this reader's language names it; an unknown one as written. */
    private fun weekday(wire: String, locale: Locale): String =
        runCatching { DayOfWeek.valueOf(wire.uppercase(Locale.ROOT)).getDisplayName(TextStyle.FULL, locale) }
            .getOrElse { wire.replaceFirstChar(Char::uppercase) }

    /** A month number in the form that goes beside a day ("15 марта", not "15 март"). */
    private fun month(number: String?, locale: Locale): String =
        number?.toIntOrNull()?.takeIf { it in 1..12 }?.let { Month.of(it).getDisplayName(TextStyle.FULL, locale) }.orEmpty()

    /**
     * "1st", "22nd", "13th": the suffix rule is English, the forms are resources, so a language whose
     * ordinals are "1." or "1e" sets all four alike (astrid-windows `Converters.Ordinal`).
     */
    private fun ordinal(context: Context, number: Long): String {
        val id = when {
            number % 100 in 11..13 -> R.string.ordinal_th
            number % 10 == 1L -> R.string.ordinal_st
            number % 10 == 2L -> R.string.ordinal_nd
            number % 10 == 3L -> R.string.ordinal_rd
            else -> R.string.ordinal_th
        }
        return context.getString(id, number.toString())
    }

    private fun localDate(iso: String, locale: Locale): String =
        runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)) }
            .getOrElse { iso }
}

@Composable
fun coreLabel(key: String, vararg args: Any): String = CoreLabels.resolve(LocalContext.current, key, *args)
