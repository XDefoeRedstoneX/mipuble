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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipuble.domain.model.PageTurnMode
import com.mipuble.domain.model.ReaderPreferences
import java.io.ByteArrayInputStream

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ApplyBrightness(state.preferences)
    ApplyStatusBarIcons(ReaderThemeColors.of(state.preferences.theme).background)

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

/**
 * Matches the status-bar icon style (light/dark) to the reader page while this
 * screen shows, and restores the previous style on exit. Without it, a dark
 * reader theme under system-light mode gets invisible dark icons (the reader
 * background draws edge-to-edge under the bar, unlike the system-mode-driven
 * Material background it replaced).
 */
@Composable
private fun ApplyStatusBarIcons(pageBackground: Color) {
    val view = LocalView.current
    val darkIcons = pageBackground.luminance() > 0.5f

    DisposableEffect(view, darkIcons) {
        val window = view.context.findActivity()?.window
            ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = darkIcons
        onDispose { controller.isAppearanceLightStatusBars = previous }
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
    // Chrome is tinted from the page colors (not Material) so it reads over the
    // page in every theme. It floats over a full-bleed WebView, so toggling the
    // controls never changes the content size — the WebView never reflows.
    val colors = ReaderThemeColors.of(state.preferences.theme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(color = colors.link)
            state.error != null -> Text(
                text = state.error,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text,
            )
            state.chapterUrl != null -> ChapterWebView(
                chapterUrl = state.chapterUrl,
                preferences = state.preferences,
                readResource = readResource,
                onEvent = onEvent,
            )
        }

        AnimatedVisibility(
            visible = state.showControls,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        state.bookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ReaderEvent.OpenSettings) }) {
                        Icon(
                            painter = painterResource(com.mipuble.R.drawable.ic_tune),
                            contentDescription = "Display settings",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.background.copy(alpha = 0.88f),
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.text,
                    actionIconContentColor = colors.text,
                ),
            )
        }

        AnimatedVisibility(
            visible = state.showControls && state.error == null && !state.isLoading,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomBar(
                colors = colors,
                currentChapter = state.currentChapter,
                chapterCount = state.chapterCount,
                hasPrevious = state.hasPrevious,
                hasNext = state.hasNext,
                onPrevious = { onEvent(ReaderEvent.PreviousChapter) },
                onNext = { onEvent(ReaderEvent.NextChapter) },
            )
        }
    }
}

/**
 * The slim reading rail: prev/next chevrons flanking a fine chapter-progress
 * track, over a soft bottom gradient of the page color. Tinted from the page
 * colors so it reads on any theme.
 */
@Composable
private fun ReaderBottomBar(
    colors: ReaderThemeColors,
    currentChapter: Int,
    chapterCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val fraction = if (chapterCount > 0) {
        ((currentChapter + 1f) / chapterCount).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, colors.background)),
            )
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Chapter ${currentChapter + 1} of $chapterCount",
            style = MaterialTheme.typography.labelMedium,
            color = colors.text.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Previous chapter",
                    tint = colors.text.copy(alpha = if (hasPrevious) 1f else 0.3f),
                )
            }
            ChapterRail(
                fraction = fraction,
                colors = colors,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next chapter",
                    tint = colors.text.copy(alpha = if (hasNext) 1f else 0.3f),
                )
            }
        }
    }
}

/** A 4dp progress track with a knob at [fraction] (0..1) of the way through. */
@Composable
private fun ChapterRail(
    fraction: Float,
    colors: ReaderThemeColors,
    modifier: Modifier = Modifier,
) {
    val filled = fraction.coerceIn(0f, 1f)
    val railShape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier.height(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(railShape)
                .background(colors.text.copy(alpha = 0.2f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(filled)
                .height(4.dp)
                .clip(railShape)
                .background(colors.link),
        )
        // Bias maps 0..1 to the ends of the rail with the knob fully inside.
        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = filled * 2f - 1f, verticalBias = 0f))
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.link),
        )
    }
}

/** WebView subclass solely to expose the protected horizontal scroll range. */
private class PagingWebView(context: Context) : WebView(context) {
    fun horizontalRange(): Int = computeHorizontalScrollRange()

    fun maxScrollX(): Int = (horizontalRange() - width).coerceAtLeast(0)
}

/**
 * Hosts a WebView that streams chapters from the open EPUB and themes them by
 * injecting an override stylesheet into the served HTML bytes — no JavaScript
 * is enabled. Font scaling uses WebView.textZoom; theme/font/spacing/page-mode
 * changes reload the current chapter so the fresh stylesheet is injected.
 *
 * Page-turn modes:
 * - SCROLL: native vertical scrolling; taps toggle the chrome.
 * - PAGED: the chapter is laid out in screen-width CSS columns; this layer
 *   consumes all touches and turns horizontal flings into one-screen jumps
 *   via scrollTo. Swiping past the first/last column crosses chapters
 *   (landing on the *last* page when going backwards).
 */
@Composable
private fun ChapterWebView(
    chapterUrl: String,
    preferences: ReaderPreferences,
    readResource: (String) -> ByteArray?,
    onEvent: (ReaderEvent) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val backgroundArgb = ReaderThemeColors.of(preferences.theme).background.toArgb()
    // Read latest values inside the long-lived WebViewClient/listener closures.
    val css = rememberUpdatedState(readerOverrideCss(preferences))
    val events = rememberUpdatedState(onEvent)
    val isPaged = rememberUpdatedState(preferences.pageTurnMode == PageTurnMode.PAGED)
    // Set when the user swipes back across a chapter boundary: the previous
    // chapter should open on its last page, not its first.
    val jumpToLastPage = remember { mutableStateOf(false) }

    // A change to these requires re-injecting CSS, i.e. reloading the chapter.
    val cssSignature =
        "${preferences.theme}:${preferences.lineSpacingPercent}:${preferences.font}:${preferences.pageTurnMode}"

    val client = remember(readResource) {
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                // This runs on a WebView worker thread; an uncaught throw here
                // crashes the whole app, so everything is guarded.
                return try {
                    val url = request.url
                    if (url.host != EpubWebViewBridge.HOST) return null
                    val path = url.path ?: return null

                    // Bundled reader typefaces, served from app assets.
                    if (path.startsWith(EpubWebViewBridge.FONT_PATH_PREFIX)) {
                        val name = path.removePrefix(EpubWebViewBridge.FONT_PATH_PREFIX)
                        val bytes = runCatching {
                            appContext.assets.open("fonts/$name").use { it.readBytes() }
                        }.getOrNull() ?: return null
                        return WebResourceResponse(
                            EpubWebViewBridge.mimeTypeFor(name),
                            null,
                            ByteArrayInputStream(bytes),
                        )
                    }

                    if (!path.startsWith(EpubWebViewBridge.PATH_PREFIX)) return null
                    val entry = path.removePrefix(EpubWebViewBridge.PATH_PREFIX)
                    val bytes = readResource(entry) ?: return null
                    val mime = EpubWebViewBridge.mimeTypeFor(entry)

                    if (mime == "text/html") {
                        val html = injectStylesheet(String(bytes, Charsets.UTF_8), css.value)
                        WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
                    } else {
                        WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(bytes))
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("MipubleReader", "intercept failed for ${request.url}", e)
                    null
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Entering a chapter backwards: land on its last page. The
                // visual-state callback fires once the columns are laid out.
                if (jumpToLastPage.value && isPaged.value) {
                    jumpToLastPage.value = false
                    val paging = view as? PagingWebView ?: return
                    paging.postVisualStateCallback(
                        0,
                        object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                paging.scrollTo(paging.maxScrollX(), 0)
                            }
                        },
                    )
                }
            }
        }
    }

    AndroidView(
        factory = { context ->
            PagingWebView(context).apply {
                webViewClient = client
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                val webView = this

                fun turnPage(forward: Boolean) {
                    val width = webView.width
                    if (width <= 0) return
                    val max = webView.maxScrollX()
                    when {
                        forward && webView.scrollX >= max ->
                            events.value(ReaderEvent.NextChapter)

                        !forward && webView.scrollX <= 0 -> {
                            jumpToLastPage.value = true
                            events.value(ReaderEvent.PreviousChapter)
                        }

                        else -> {
                            val target = webView.scrollX + if (forward) width else -width
                            webView.scrollTo(target.coerceIn(0, max), 0)
                        }
                    }
                }

                val gestureDetector = GestureDetector(
                    context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                            events.value(ReaderEvent.ToggleControls)
                            return true
                        }

                        override fun onFling(
                            e1: MotionEvent?,
                            e2: MotionEvent,
                            velocityX: Float,
                            velocityY: Float,
                        ): Boolean {
                            if (!isPaged.value) return false
                            if (kotlin.math.abs(velocityX) <= kotlin.math.abs(velocityY)) return false
                            // Finger moving left (negative velocity) = next page.
                            turnPage(forward = velocityX < 0)
                            return true
                        }
                    },
                )
                // SCROLL: return false so the WebView keeps native scrolling.
                // PAGED: consume everything; pages only move via turnPage().
                setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    isPaged.value
                }
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
        // System-bar padding keeps the first/last lines clear of the status bar
        // and gesture area (the old Scaffold insets did this). It's a constant
        // inset, so toggling the chrome overlay still never resizes the WebView.
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
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
