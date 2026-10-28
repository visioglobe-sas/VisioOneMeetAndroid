package com.visioglobe.visioonemeet.ui

import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.visioglobe.visioonemeet.R
import com.visioglobe.visioonemeet.bridge.BridgeEvent
import com.visioglobe.visioonemeet.bridge.DEFAULT_CAMERA_PROJECTION
import com.visioglobe.visioonemeet.bridge.DEFAULT_SDK_BASE_URL
import com.visioglobe.visioonemeet.bridge.MapPageOptions
import com.visioglobe.visioonemeet.bridge.NativeBridge
import com.visioglobe.visioonemeet.bridge.createMapWebView
import com.visioglobe.visioonemeet.model.DemoMap
import com.visioglobe.visioonemeet.model.Feature
import com.visioglobe.visioonemeet.sample.ProtectedMapOptions
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance

sealed interface MapLoadState {
    data object Loading : MapLoadState
    data object Ready : MapLoadState
    data class Error(val message: String) : MapLoadState
}

/**
 * What a feature screen needs from its map: the live [webView] to call the bridge on
 * (`webView?.callBridge(...)`), the bridge [events] to collect, the load state, and the controls
 * panel's visibility. One per feature screen ([rememberFeatureMapState]); the map — and so this
 * state's WebView — is recreated on every screen.
 *
 * [baseUrl] and [cameraProjection] are load-time options: changing them ([reloadWithBaseUrl],
 * [reloadWithCameraProjection]) recreates the WebView, i.e. a fresh page, `loadVenue` and
 * `createView` (`custom-base-url`, `camera-projection-toggle`).
 */
@Stable
class FeatureMapState {
    var webView by mutableStateOf<WebView?>(null)
        internal set
    var loadState by mutableStateOf<MapLoadState>(MapLoadState.Loading)
        internal set
    val isMapReady: Boolean get() = loadState is MapLoadState.Ready

    /** Whether the controls panel (the FAB's bottom sheet) is shown. */
    var isSheetOpen by mutableStateOf(false)

    var baseUrl by mutableStateOf(DEFAULT_SDK_BASE_URL)
        private set
    var cameraProjection by mutableStateOf(DEFAULT_CAMERA_PROJECTION)
        private set

    private val mutableEvents = MutableSharedFlow<BridgeEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every [BridgeEvent] the page sends, on the main thread. Collect with [OnBridgeEvent]. */
    val events: SharedFlow<BridgeEvent> = mutableEvents

    internal fun emit(event: BridgeEvent) {
        mutableEvents.tryEmit(event)
    }

    fun reloadWithBaseUrl(newBaseUrl: String) {
        loadState = MapLoadState.Loading
        baseUrl = newBaseUrl
    }

    fun reloadWithCameraProjection(newProjection: String) {
        if (newProjection == cameraProjection) return
        loadState = MapLoadState.Loading
        cameraProjection = newProjection
    }
}

@Composable
fun rememberFeatureMapState(): FeatureMapState = remember { FeatureMapState() }

/**
 * Runs [onEvent] for every bridge event of type [E] for as long as the calling screen is shown —
 * whether the controls panel is open or not. Feature screens use it to feed results and SDK
 * events into their screen-level state.
 */
@Composable
inline fun <reified E : BridgeEvent> OnBridgeEvent(map: FeatureMapState, noinline onEvent: (E) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(map) {
        map.events.filterIsInstance<E>().collect { currentOnEvent(it) }
    }
}

private const val AUTH_TOKEN_LOG_TAG = "AuthToken"

/**
 * The screen shell every feature screen uses: top bar with a back button, the map (a WebView,
 * see `bridge/MapWebView.kt`) with its loading/error state, and — once the map is ready — a FAB
 * opening [sheetContent] in an opaque [ModalBottomSheet].
 *
 * The sheet's content is disposed every time it closes, so a feature keeps its state at screen
 * level (in its `XScreen()`), never in [sheetContent].
 *
 * The WebView is created for the current [mapHash], [enableShadows], [protectedMap] and
 * [FeatureMapState.baseUrl]/[FeatureMapState.cameraProjection]: when one changes, [key] gives the
 * view a new identity, so Compose disposes the old WebView and creates a new one from scratch.
 * Every WebView recreated that way (and the one left when the screen is popped) is destroyed in
 * `onRelease`, so repeated reloads don't pile up live pages, WebGL contexts or bridge callbacks.
 *
 * `auth-token` (code sample, see `sample/ProtectedMapSample.kt`): a non-null [protectedMap] loads
 * the page in protected mode; an `UnauthorizedError` is shown apart from other load errors.
 * [showControlsFab] hides the FAB for a screen with nothing to control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureMapScreen(
    map: FeatureMapState,
    feature: Feature,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mapHash: String = DemoMap.MAP_HASH,
    enableShadows: Boolean = false,
    protectedMap: ProtectedMapOptions? = null,
    showControlsFab: Boolean = true,
    sheetContent: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(feature.titleRes)) },
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
        floatingActionButton = {
            if (showControlsFab && map.isMapReady) {
                FloatingActionButton(onClick = { map.isSheetOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tune),
                        contentDescription = stringResource(R.string.open_controls_content_description),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val pageOptions = MapPageOptions(
                mapHash = mapHash,
                baseUrl = map.baseUrl,
                enableShadows = enableShadows,
                cameraProjection = map.cameraProjection,
                isProtected = protectedMap != null,
            )
            key(pageOptions, protectedMap) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        val bridge = NativeBridge(
                            protectedMapOptionsJson = protectedMap?.toJson(),
                            onLoaded = { map.loadState = MapLoadState.Ready },
                            onLoadFailed = { message, isAuthorizationError ->
                                map.loadState = if (isAuthorizationError) {
                                    Log.w(AUTH_TOKEN_LOG_TAG, "UnauthorizedError: $message")
                                    val text = context.getString(R.string.auth_token_unauthorized_error)
                                    MapLoadState.Error("$text\n($message)")
                                } else {
                                    MapLoadState.Error(message)
                                }
                            },
                            onEvent = map::emit,
                        )
                        createMapWebView(context, pageOptions, bridge).also { map.webView = it }
                    },
                    onRelease = { it.destroy() },
                )
            }

            when (val state = map.loadState) {
                is MapLoadState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is MapLoadState.Error -> Text(
                    text = stringResource(R.string.map_load_error, state.message),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                )

                is MapLoadState.Ready -> Unit
            }
        }
    }

    if (map.isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { map.isSheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            sheetContent()
        }
    }
}
