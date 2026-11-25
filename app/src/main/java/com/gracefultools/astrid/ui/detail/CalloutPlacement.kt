package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.core.CoreRules
import com.gracefultools.astrid.core.command
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A rectangle in logical pixels (dp) in the window's space, y increasing downward: the core's `Rect`. */
data class CalloutRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Which side of its row a callout opens on. */
enum class CalloutSide { LEFT, RIGHT }

/** Where the core put a callout: the side, its box (already clamped into the viewport), and how far down its anchored edge the arrow goes. */
data class CalloutPlacement(val side: CalloutSide, val box: CalloutRect, val arrowOffset: Float)

/**
 * Where a task callout opens from its row (AATD-114): the core's `calloutPlacement` rule
 * (`astrid_rules::rows::callout::place`, task `011080d4`), the same answer the Mac, the iPad and
 * Windows get. The caller measures the callout, arrow included, and divides by density first;
 * the rule decides the side, clamps the box, and keeps the arrow on the row's middle and clear of
 * the corners.
 */
fun placeCallout(anchor: CalloutRect, viewport: CalloutRect, width: Float, height: Float): CalloutPlacement {
    val answer = CoreRules.ask(
        command("calloutPlacement", "anchor" to anchor.json(), "viewport" to viewport.json(), "width" to width, "height" to height),
    ).jsonObject
    return CalloutPlacement(
        side = if (answer.getValue("side").jsonPrimitive.content == "left") CalloutSide.LEFT else CalloutSide.RIGHT,
        box = CalloutRect(answer.f("left"), answer.f("top"), answer.f("width"), answer.f("height")),
        arrowOffset = answer.f("arrowOffset"),
    )
}

private fun CalloutRect.json() = mapOf("left" to left, "top" to top, "width" to width, "height" to height)

private fun JsonObject.f(key: String) = getValue(key).jsonPrimitive.float
