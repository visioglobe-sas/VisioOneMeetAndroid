package com.visioglobe.visioonemeet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.visioglobe.visioonemeet.R

/**
 * `auth-token` screen: a text page only. This demo has no protected map, so it never loads one —
 * the actual deliverable is the code sample in `sample/ProtectedMapSample.kt`, which no screen
 * calls. See docs/features/auth-token.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthTokenSampleScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_auth_token_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back_to_menu_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Text(
            text = stringResource(R.string.auth_token_sample_text),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp),
        )
    }
}
