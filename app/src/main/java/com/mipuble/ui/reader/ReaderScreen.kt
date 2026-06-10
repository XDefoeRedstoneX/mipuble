package com.mipuble.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipuble.domain.model.ReaderPreferences
import java.io.ByteArrayInputStream

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ApplyBrightness(state.preferences)

    ReaderContent(
        state = state,
        onEvent = viewModel::onEvent,
        readResource = viewModel::readResource,
        onBack = onBack,
    )

    if (state.showSettings) {
        ReaderSettingsSheet(
            preferences = state.preferences,
            onEvent = viewModel::onEvent,
            onDismiss = { viewModel.onEvent(ReaderEvent.CloseSettings) },
        )
    }
}

/**
 * Overrides the window's brightness while reading, in exact 0.01 steps, and
 * restores the system default when leaving the screen. This window-attribute
 * approach is what lets the reader go far dimmer (and more precisely) than the
 * OS quick-settings slider allows.
 */
@Composable
private fun ApplyBrightness(preferences: ReaderPreferences) {
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(preferences.followSystemBrightness, preferences.brightnessPercent) {
        val window = activity?.window ?: return@LaunchedEffect
        window.attributes = window.attributes.apply {
            screenBrightness = if (preferences.followSystemBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                (preferences.brightnessPercent / 100f).coerceIn(0.01f, 1f)
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderContent(
    state: ReaderState,
    onEvent: (ReaderEvent) -> Unit,
    readResource: (String) -> ByteArray?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            AnimatedVisibility(visible = state.showControls) {
                TopAppBar(
                    title = {
                        Text(state.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onEvent(ReaderEvent.OpenSettings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Reading settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = state.showControls && state.error == null && !state.isLoading) {
                BottomAppBar {
                    IconButton(
                        onClick = { onEvent(ReaderEvent.PreviousChapter) },
                        enabled = state.hasPrevious,
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous chapter")
                    }
                    Text(
                        text = "${state.currentChapter + 1} / ${state.chapterCount}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = { onEvent(ReaderEvent.NextChapter) },
                        enabled = state.hasNext,
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next chapter")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.chapterUrl != null -> ChapterWebView(
                    chapterUrl = state.chapterUrl,
                    preferences = state.preferences,
                    readResource = readResource,
                    onToggleControls = { onEvent(ReaderEvent.ToggleControls) },
                )
            }
        }
    }
}

/**
 * Hosts a WebView that streams chapters from the open EPUB and themes them by
 * injecting an override stylesheet into the served HTML bytes — no JavaScript
 * is enabled. Font scaling uses WebView.textZoom; theme/spacing changes reload
 * the current chapter so the fresh stylesheet is injected.
 */
@Composable
private fun ChapterWebView(
    chapterUrl: String,
    preferences: ReaderPreferences,
    readResource: (String) -> ByteArray?,
    onToggleControls: () -> Unit,
) {
    val backgroundArgb = ReaderThemeColors.of(preferences.theme).background.toArgb()
    // Read latest values inside the long-lived WebViewClient/listener closures.
    val css = rememberUpdatedState(readerOverrideCss(preferences))
    val toggle = rememberUpdatedState(onToggleControls)

    // A change to these requires re-injecting CSS, i.e. reloading the chapter.
    val cssSignature = "${preferences.theme}:${preferences.lineSpacingPercent}"

    val client = remember(readResource) {
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url
                if (url.host != EpubWebViewBridge.HOST) return null
                val path = url.path ?: return null
                if (!path.startsWith(EpubWebViewBridge.PATH_PREFIX)) return null

                val entry = path.removePrefix(EpubWebViewBridge.PATH_PREFIX)
                val bytes = readResource(entry) ?: return null
                val mime = EpubWebViewBridge.mimeTypeFor(entry)

                if (mime == "text/html") {
                    val html = injectStylesheet(String(bytes, Charsets.UTF_8), css.value)
                    return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
                }
                return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = client
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                val gestureDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            toggle.value.invoke()
                            return true
                        }
                    },
                )
                // Returning false lets the WebView keep handling scroll gestures.
                setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
            }
        },
        update = { webView ->
            webView.settings.textZoom = preferences.fontScalePercent
            webView.setBackgroundColor(backgroundArgb)

            val needsReload = webView.getTag(R_CSS) != cssSignature
            when {
                webView.url != chapterUrl -> webView.loadUrl(chapterUrl)
                needsReload -> webView.reload()
            }
            webView.setTag(R_CSS, cssSignature)
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Inserts the override stylesheet just before </head> (or prepends if absent). */
private fun injectStylesheet(html: String, css: String): String {
    val style = "<style id=\"mipuble-overrides\">$css</style>"
    val headClose = html.indexOf("</head>", ignoreCase = true)
    return if (headClose >= 0) {
        html.substring(0, headClose) + style + html.substring(headClose)
    } else {
        style + html
    }
}

// Stable view tag id for tracking the injected stylesheet signature.
private const val R_CSS = 0x6D69_7002

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
