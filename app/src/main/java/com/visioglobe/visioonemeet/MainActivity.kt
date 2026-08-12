package com.visioglobe.visioonemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.visioglobe.visioonemeet.ui.VisioOneMapScreen
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

/** Hash of the VisioOne map to display, as found on the my.visioglobe.com portal. */
private const val DEFAULT_MAP_HASH = "kbae8e6c066cca4b02c2afac2bc963a643d87437a"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisioOneMeetTheme {
                VisioOneMapScreen(
                    mapHash = DEFAULT_MAP_HASH,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
