package com.visioglobe.visioonemeet.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.ui.FeatureMapState
import com.visioglobe.visioonemeet.ui.OnBridgeEvent

/**
 * The From/To place-id fields shared by every itinerary screen ([ItineraryControls]) and every
 * tracked-position screen (`TrackedPositionSimulationControls` in `features/SimulatedPosition.kt`).
 */
@Composable
fun FromToFields(
    from: String,
    onFromChange: (String) -> Unit,
    to: String,
    onToChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = from,
            onValueChange = onFromChange,
            label = { Text(stringResource(R.string.field_from_place_id)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = to,
            onValueChange = onToChange,
            label = { Text(stringResource(R.string.field_to_place_id)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Screen-level state of an itinerary screen (`compute-navigation`, `custom-navigation-trace`,
 * `navigation-exclude-modalities`, `accessible-mode`): the From/To fields, the screen's switch
 * ("Avoid elevator" / "Accessible route") and the last computation error, so closing and reopening
 * the panel keeps them — and the next "Itinerary" press uses what the panel shows.
 */
@Stable
class ItinerarySession(initialOrigin: String, initialDestination: String) {
    var origin by mutableStateOf(initialOrigin)
    var destination by mutableStateOf(initialDestination)

    /** `navigation-exclude-modalities`' "Avoid elevator" / `accessible-mode`'s "Accessible route". */
    var optionEnabled by mutableStateOf(false)

    /** The last `computeNavigation` failure (bad/unreachable Place ID), cleared by the next success. */
    var navigationError by mutableStateOf<String?>(null)
        private set

    internal fun onNavigationResult(error: String?) {
        navigationError = error
    }
}

/**
 * An [ItinerarySession] for the calling screen, fed by the page's `onNavigationComputed` /
 * `onNavigationError` callbacks for as long as the screen is shown.
 */
@Composable
fun rememberItinerarySession(map: FeatureMapState, origin: String, destination: String): ItinerarySession {
    val session = remember { ItinerarySession(origin, destination) }
    OnBridgeEvent<BridgeEvent.NavigationComputed>(map) { session.onNavigationResult(null) }
    OnBridgeEvent<BridgeEvent.NavigationError>(map) { session.onNavigationResult(it.message) }
    return session
}

/**
 * The From/To fields, "Itinerary" and "Clear" buttons and the error line shared by the four
 * itinerary screens; [onItinerary] makes the screen's own bridge call with the trimmed ids,
 * [onClear] removes the route, [extraContent] renders the screen's own extra control below.
 */
@Composable
fun ItineraryControls(
    session: ItinerarySession,
    onItinerary: (origin: String, destination: String) -> Unit,
    onClear: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        FromToFields(
            from = session.origin,
            onFromChange = { session.origin = it },
            to = session.destination,
            onToChange = { session.destination = it },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onItinerary(session.origin.trim(), session.destination.trim()) },
                enabled = session.origin.isNotBlank() && session.destination.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_itinerary))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onClear) {
                Text(stringResource(R.string.action_clear))
            }
        }
        ErrorText(session.navigationError)
        extraContent()
    }
}

/** A labeled switch row bound to [ItinerarySession.optionEnabled]. */
@Composable
fun ItineraryOptionSwitch(session: ItinerarySession, @StringRes labelRes: Int) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = session.optionEnabled, onCheckedChange = { session.optionEnabled = it })
    }
}
