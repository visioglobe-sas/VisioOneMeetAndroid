package com.visioglobe.visioonemeet.bridge

import android.webkit.WebView
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native -> JS: calls `window.MapBridge.<method>(args...)` in the page (see `web/src/main.js`,
 * which assembles `window.MapBridge` from every `web/src/features/<slug>.js`).
 *
 * Every argument is JSON-encoded with [org.json] here, in one place, before being put in the
 * script — never raw string concatenation, which would allow JS injection:
 * - a [String] becomes a quoted, escaped JS string ([JSONObject.quote]);
 * - a number or a [Boolean] is written as is;
 * - a [JSONObject]/[JSONArray] is written as its JSON text (a JS object/array literal);
 * - a [Collection] becomes a [JSONArray], a [Map] a [JSONObject];
 * - `null` becomes `null`.
 *
 * So the page receives real JS values (strings, numbers, arrays, objects), never a string to
 * re-parse. [onResult] receives the command's return value, JSON-encoded by the WebView (e.g.
 * `"true"`); omit it for a fire-and-forget command. A command whose result comes back
 * asynchronously instead takes a `requestId` from [nextBridgeRequestId], echoed by its
 * `AndroidBridge` callback (see [BridgeEvent]).
 */
fun WebView.callBridge(method: String, vararg args: Any?, onResult: ((String) -> Unit)? = null) {
    val script = "window.MapBridge.$method(${args.joinToString(", ") { encodeBridgeArgument(it) }})"
    evaluateJavascript(script, onResult)
}

private fun encodeBridgeArgument(value: Any?): String = when (value) {
    null -> "null"
    is String -> JSONObject.quote(value)
    is Number, is Boolean -> value.toString()
    is JSONObject, is JSONArray -> value.toString()
    is Collection<*> -> JSONArray(value).toString()
    is Map<*, *> -> JSONObject(value).toString()
    else -> throw IllegalArgumentException("Unsupported bridge argument type: ${value::class}")
}

private val bridgeRequestIds = AtomicInteger()

/**
 * Next id to send with a request/response bridge call (`resolvePositions`, `loadCustomData`, ...).
 * One counter for the whole app: a response can never be mistaken for — or be equal to — the
 * response of an earlier request, even one sent from a previous opening of the panel.
 */
fun nextBridgeRequestId(): Int = bridgeRequestIds.getAndIncrement()
