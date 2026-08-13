package com.visioglobe.visioonemeet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.model.Feature

@Composable
fun FeatureMenuScreen(onFeatureSelected: (Feature) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
    ) {
        items(Feature.entries) { feature ->
            Card(
                onClick = { onFeatureSelected(feature) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(feature.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(feature.descriptionRes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
