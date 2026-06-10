package com.mipuble.ui.reader

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.ByteArrayInputStream

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderContent(
        state = state,
        onEvent = viewModel::onEvent,
        readResource = viewModel::readResource,
        onBack = onBack,
    )
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
            TopAppBar(
                title = {
                    Text(
                        text = state.bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library")
                    }
                },
            )
        },
        bottomBar = {
            if (state.error == null && !state.isLoading) {
                BottomAppBar {
                    IconButton(
                        onClick = { onEvent(ReaderEvent.PreviousChapter) },
                        enabled = state.hasPrevious,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous chapter")
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
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next chapter")
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
                    readResource = readResource,
                )
            }
        }
    }
}

/**
 * Hosts a WebView and intercepts every request under the virtual epub origin,
 * answering it with bytes streamed straight from the open EPUB zip. The
 * WebView never sees the network or the filesystem.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChapterWebView(
    chapterUrl: String,
    readResource: (String) -> ByteArray?,
) {
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
                return WebResourceResponse(
                    EpubWebViewBridge.mimeTypeFor(entry),
                    "UTF-8",
                    ByteArrayInputStream(bytes),
                )
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
            }
        },
        update = { webView ->
            if (webView.url != chapterUrl) {
                webView.loadUrl(chapterUrl)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
