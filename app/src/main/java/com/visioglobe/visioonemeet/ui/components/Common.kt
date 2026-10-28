package com.visioglobe.visioonemeet.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** A localized message held outside composition: a string resource and its format arguments. */
data class UiText(@StringRes val res: Int, val args: List<Any> = emptyList())

@Composable
fun UiText.resolve(): String =
    if (args.isEmpty()) stringResource(res) else stringResource(res, *args.toTypedArray())

/** An error line under a control, or nothing when [text] is null. */
@Composable
fun ErrorText(text: String?) {
    if (text == null) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = text, color = MaterialTheme.colorScheme.error)
}

/** Screen-level text field value, for screens whose only state is one place id. */
@Stable
class PlaceIdSession(initialPlaceId: String) {
    var placeId by mutableStateOf(initialPlaceId)
}
