package com.visioglobe.visioonemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.visioglobe.visioonemeet.ui.VisioOneMapScreen
import com.visioglobe.visioonemeet.ui.theme.VisioOneMeetTheme

/** Hash of the VisioOne map to display, as found on the my.visioglobe.com portal. */
private const val DEFAULT_MAP_HASH = "k5f59b8615f0379390e03e4cbe893ff813b9ac94a"

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
