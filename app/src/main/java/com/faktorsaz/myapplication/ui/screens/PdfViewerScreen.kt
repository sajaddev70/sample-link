package com.faktorsaz.myapplication.ui.screens

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.faktorsaz.myapplication.pdf.PdfRenderManager
import com.faktorsaz.myapplication.utils.PdfDownloader
import java.io.File

sealed class PdfLoadState {
    object Loading : PdfLoadState()
    data class Success(val file: File) : PdfLoadState()
    data class Error(val message: String) : PdfLoadState()
}

@Composable
fun PdfViewerScreen(pdfUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loadState by remember { mutableStateOf<PdfLoadState>(PdfLoadState.Loading) }
    var useFallback by remember { mutableStateOf(false) }

    LaunchedEffect(pdfUrl) {
        loadState = PdfLoadState.Loading
        val fileName = "downloaded_pdf_${pdfUrl.hashCode()}.pdf"
        val downloadedFile = PdfDownloader.downloadPdf(context, pdfUrl, fileName)
        if (downloadedFile != null) {
            loadState = PdfLoadState.Success(downloadedFile)
        } else {
            loadState = PdfLoadState.Error("Failed to download PDF")
            // Optionally auto-fallback after some time or on error
        }
    }

    if (useFallback) {
        PdfWebViewFallback(pdfUrl, modifier)
    } else {
        when (val state = loadState) {
            is PdfLoadState.Loading -> {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PdfLoadState.Success -> {
                PdfRendererView(
                    file = state.file,
                    onError = { useFallback = true },
                    modifier = modifier
                )
            }
            is PdfLoadState.Error -> {
                Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(text = state.message, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            loadState = PdfLoadState.Loading
                            useFallback = false
                        }) {
                            Text("Retry Download")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { useFallback = true }) {
                            Text("Use WebView Fallback")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfRendererView(file: File, onError: () -> Unit, modifier: Modifier = Modifier) {
    val rendererManager = remember(file) {
        try {
            PdfRenderManager(file)
        } catch (e: Exception) {
            onError()
            null
        }
    }

    if (rendererManager == null) return

    val pageCount = rendererManager.pageCount
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    DisposableEffect(rendererManager) {
        onDispose {
            rendererManager.close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { index ->
                PdfPageItem(rendererManager, index)
            }
        }

        // Reset Zoom Button
        if (scale > 1f) {
            FloatingActionButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
fun PdfPageItem(rendererManager: PdfRenderManager, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember { mutableStateOf(0.707f) }

    LaunchedEffect(index) {
        val b = rendererManager.renderPage(index)
        bitmap = b
        b?.let {
            aspectRatio = it.width.toFloat() / it.height.toFloat()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${index + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } ?: CircularProgressIndicator()
        }
    }
}

@Composable
fun PdfWebViewFallback(url: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
