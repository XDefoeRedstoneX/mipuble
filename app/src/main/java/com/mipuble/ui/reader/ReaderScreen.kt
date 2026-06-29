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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
    // Real page position within the current chapter, reported by the WebView
    // once it has laid the content out. (current, total); total 0 = not yet known.
    var pageInfo by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(state.chapterUrl) { pageInfo = 0 to 0 }

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
                    val (page, pages) = pageInfo
                    Text(
                        text = if (pages > 0) {
                            "Ch ${state.currentChapter + 1}/${state.chapterCount} · p. $page/$pages"
                        } else {
                            "Ch ${state.currentChapter + 1} / ${state.chapterCount}"
                        },
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
                    onEvent = onEvent,
                    onPageInfo = { current, total -> pageInfo = current to total },
                )
            }
        }
    }
}

/**
 * WebView subclass that exposes the protected vertical scroll range so the
 * reader can paginate by viewport, and reports the current page/total whenever
 * it scrolls.
 */
private class PagingWebView(context: Context) : WebView(context) {
    /** Invoked with (currentPage, totalPages) on scroll and after layout. */
    var onMetrics: ((current: Int, total: Int) -> Unit)? = null

    /** Farthest the content can scroll vertically, in px. */
    fun maxScrollY(): Int = (computeVerticalScrollRange() - height).coerceAtLeast(0)

    /** Current page and total, derived from the real rendered content height. */
    fun pageMetrics(): Pair<Int, Int> {
        val h = height
        if (h <= 0) return 1 to 1
        val total = ceil(computeVerticalScrollRange().toFloat() / h).toInt().coerceAtLeast(1)
        val current = (Math.round(scrollY.toFloat() / h) + 1).coerceIn(1, total)
        return current to total
    }

    fun reportMetrics() {
        val (current, total) = pageMetrics()
        onMetrics?.invoke(current, total)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        reportMetrics()
    }
}

/**
 * Hosts a WebView that streams chapters from the open EPUB and themes them by
 * injecting an override stylesheet into the served HTML bytes — no JavaScript
 * is enabled. Font scaling uses WebView.textZoom; theme/font/spacing/page-mode
 * changes reload the current chapter so the fresh stylesheet is injected.
 *
 * Page-turn modes (both use the same normal vertical reflow):
 * - SCROLL: native vertical scrolling; taps toggle the chrome.
 * - PAGED: this layer consumes touches and snap-scrolls one viewport per swipe
 *   (no CSS columns). Swiping past the first/last screen crosses chapters
 *   (landing on the *last* page when going backwards).
 */
@Composable
private fun ChapterWebView(
    chapterUrl: String,
    preferences: ReaderPreferences,
    readResource: (String) -> ByteArray?,
    onEvent: (ReaderEvent) -> Unit,
    onPageInfo: (current: Int, total: Int) -> Unit,
) {
    val pageInfoListener = rememberUpdatedState(onPageInfo)
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
    // Page-turn mode is NOT here: it only changes gesture handling, not the CSS.
    val cssSignature =
        "${preferences.theme}:${preferences.lineSpacingPercent}:${preferences.font}"

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
                val paging = view as? PagingWebView ?: return
                // Once the content is actually laid out, (a) land on the last
                // page if we entered the chapter backwards, and (b) report the
                // real page count for the bottom bar.
                paging.postVisualStateCallback(
                    0,
                    object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (jumpToLastPage.value && isPaged.value) {
                                jumpToLastPage.value = false
                                paging.scrollTo(0, paging.maxScrollY())
                            }
                            paging.reportMetrics()
                        }
                    },
                )
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
                onMetrics = { current, total -> pageInfoListener.value(current, total) }

                val webView = this

                fun turnPage(forward: Boolean) {
                    val h = webView.height
                    if (h <= 0) return
                    val max = webView.maxScrollY()
                    when {
                        forward && webView.scrollY >= max ->
                            events.value(ReaderEvent.NextChapter)

                        !forward && webView.scrollY <= 0 -> {
                            jumpToLastPage.value = true
                            events.value(ReaderEvent.PreviousChapter)
                        }

                        else -> {
                            val target = webView.scrollY + if (forward) h else -h
                            webView.scrollTo(0, target.coerceIn(0, max))
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
                            // Swipe left or up = next page; right or down = previous.
                            val horizontal = kotlin.math.abs(velocityX) > kotlin.math.abs(velocityY)
                            val forward = if (horizontal) velocityX < 0 else velocityY < 0
                            turnPage(forward)
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
