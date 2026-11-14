package com.gracefultools.astrid.wire

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Holds what the app says to the core, and what it reads back, against the pinned core's wire
 * schema (AATD-94).
 *
 * `serde` ignores an unknown request field, so a field the core renames used to fail silently:
 * the app kept sending the old name, the core read it as absent, and nothing was written. The
 * schema is derived from the core's own types (astrid-core `generated/schema/`), and
 * `core/astrid-android/tests/wire_schema.rs` keeps the copy in `src/test/resources/core-schema`
 * equal to the pinned crate's, so this gate judges the app against the core it actually links.
 *
 * Two questions, each answered per type:
 * - **Requests.** Every `command("kind", "field" to …)` in the app names a kind the core has, and
 *   only fields that kind's own variant declares.
 * - **Responses.** A DTO paired with the core type it mirrors decodes only names that type still
 *   has. Checked against **that type's** properties, never a union of every name in the schema:
 *   `authorName` lives on `CommentRow` and `MessageRow` both, so a union check misses a rename of
 *   one (astrid-windows found this by trying it).
 */
object WireSchemaGate {
    private val json = Json { ignoreUnknownKeys = true }

    /** `src/test/resources/core-schema`, read from the file system: JVM unit tests run in `app/`. */
    fun load(name: String): JsonObject =
        json.parseToJsonElement(File("src/test/resources/core-schema/$name").readText()).jsonObject

    /** One `command(…)` call site: its kind (null when not a literal) and the literal field names. */
    data class Call(val where: String, val kind: String?, val fields: List<String>, val spread: Boolean)

    /**
     * The request variants of `command.json`/`rule.json` documents, kind → the fields it declares.
     * A kind both doors know takes the fields of both: `command(…)` builds requests for either.
     */
    fun variants(vararg documents: JsonObject): Map<String, Set<String>> {
        val out = mutableMapOf<String, Set<String>>()
        for (doc in documents) {
            for (variant in doc.getValue("oneOf") as JsonArray) {
                val props = variant.jsonObject.getValue("properties").jsonObject
                val kind = props.getValue("kind").jsonObject.getValue("const").jsonPrimitive.content
                out[kind] = out[kind].orEmpty() + (props.keys - "kind")
            }
        }
        return out
    }

    /**
     * Every problem with [calls] against [variants]; empty when the app only says what the core knows.
     * A kind or a spread that is not a literal cannot be checked statically, and is a problem too:
     * a call site this gate cannot read is one a rename could break unseen.
     */
    fun requestProblems(calls: List<Call>, variants: Map<String, Set<String>>): List<String> = calls.flatMap { call ->
        val kind = call.kind ?: return@flatMap listOf("${call.where}: the command kind is not a string literal")
        val fields = variants[kind] ?: return@flatMap listOf("${call.where}: unknown command kind \"$kind\"")
        call.fields.filter { it !in fields }.map { "${call.where}: \"$kind\" has no field \"$it\"" } +
            if (call.spread) listOf("${call.where}: \"$kind\" spreads fields this gate cannot read") else emptyList()
    }

    /** Every `command(` call in [source] (a Kotlin file's text), [file] naming it in problems. */
    fun calls(file: String, source: String): List<Call> {
        val out = mutableListOf<Call>()
        var from = 0
        while (true) {
            val at = CALL.find(source, from) ?: break
            from = at.range.last + 1
            val args = arguments(source, from) ?: continue
            val line = source.substring(0, at.range.first).count { it == '\n' } + 1
            val kind = STRING.matchEntire(args.firstOrNull().orEmpty())?.groupValues?.get(1)
            val rest = args.drop(1)
            val (spreads, named) = rest.partition { it.startsWith("*") }
            // A spread is read when its names are literals in it (`*listOfNotNull("a" to …)`) or in
            // the body of a helper in the same file it calls (`*fields(draft).toTypedArray()`).
            val spreadFields = spreads.map { spread ->
                val helper = HELPER.find(spread)?.groupValues?.get(1)?.let { helperBody(source, it) }.orEmpty()
                ANY_FIELD.findAll(spread + "\n" + helper).map { it.groupValues[1] }.toList()
            }
            out += Call(
                where = "$file:$line",
                kind = kind,
                fields = named.mapNotNull { FIELD.find(it)?.groupValues?.get(1) } + spreadFields.flatten(),
                spread = spreadFields.any { it.isEmpty() },
            )
        }
        return out
    }

    /** Every name a paired DTO decodes that its core type no longer has. */
    fun responseProblems(pairs: Map<SerialDescriptor, String>, wire: JsonObject): List<String> {
        val defs = wire.getValue("\$defs").jsonObject
        return pairs.flatMap { (descriptor, def) ->
            val type = defs[def]?.jsonObject ?: return@flatMap listOf("${descriptor.serialName} mirrors $def, which the core no longer has")
            val props = type["properties"]?.jsonObject?.keys.orEmpty()
            val missing = (0 until descriptor.elementsCount).map(descriptor::getElementName).filter { it !in props }
            if (missing.isEmpty()) emptyList() else listOf("${descriptor.serialName.substringAfterLast('.')} decodes $missing, which $def no longer has")
        }
    }

    /** `command(` as a call: not its declaration, part of a longer name, or `.command(` on something else. */
    private val CALL = Regex("""(?<!fun )(?<![\w.])command\(""")
    private val STRING = Regex("""\s*"([A-Za-z0-9_-]+)"\s*""")
    private val FIELD = Regex("""^\s*"(\w+)"\s+to\b""")
    private val ANY_FIELD = Regex(""""(\w+)"\s+to\b""")
    private val HELPER = Regex("""^\*(\w+)\(""")

    /** The text of `fun [name](` in [source], up to the next declaration: where its pairs are written. */
    private fun helperBody(source: String, name: String): String {
        val start = Regex("""\bfun $name\(""").find(source)?.range?.first ?: return ""
        val end = Regex("""\n\s*(?:private |internal |override )*(?:fun|val|var|class) """).find(source, start + 1)?.range?.first ?: source.length
        return source.substring(start, end)
    }

    /**
     * The top-level arguments of the call whose `(` ends just before [start], split at depth-0 commas,
     * trimmed; string literals (with escapes and `${…}` templates) are skipped as opaque.
     */
    private fun arguments(source: String, start: Int): List<String>? {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = start
        while (i < source.length) {
            val c = source[i]
            when {
                c == '"' -> {
                    val end = stringEnd(source, i)
                    current.append(source, i, end)
                    i = end
                    continue
                }
                c == '(' || c == '[' || c == '{' -> depth++
                (c == ')' || c == ']' || c == '}') && depth > 0 -> depth--
                c == ')' -> {
                    current.toString().trim().takeIf { it.isNotEmpty() }?.let(args::add)
                    return args
                }
                c == ',' && depth == 0 -> {
                    args += current.toString().trim()
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        return null
    }

    /** The index just past the string literal starting at [start]: `"…"` or `"""…"""`. */
    private fun stringEnd(source: String, start: Int): Int {
        if (source.startsWith("\"\"\"", start)) {
            val end = source.indexOf("\"\"\"", start + 3)
            return if (end < 0) source.length else end + 3
        }
        var i = start + 1
        var braces = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' -> i++
                c == '$' && source.getOrNull(i + 1) == '{' -> { braces++; i++ }
                c == '}' && braces > 0 -> braces--
                c == '"' && braces > 0 -> { i = stringEnd(source, i); continue }
                c == '"' -> return i + 1
            }
            i++
        }
        return source.length
    }
}
