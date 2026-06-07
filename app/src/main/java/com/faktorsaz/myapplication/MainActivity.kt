package com.faktorsaz.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.faktorsaz.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to generate photobook PDF") }

    // Use a persistent WebView to avoid lifecycle issues during printing
    var webView: WebView? by remember { mutableStateOf(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Photobook PDF Generator") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = statusMessage, style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(24.dp))

            if (isGenerating) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Preparing PDF components...")
            } else {
                Button(
                    onClick = {
                        isGenerating = true
                        statusMessage = "Loading website & rendering pages..."
                        webView?.loadUrl("http://66.102.139.204:8095/photobook.html")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate PDF")
                }
            }

            // Keep WebView in the hierarchy but hidden (1x1 pixel)
            // This ensures it stays alive for the PrintDocumentAdapter
            Box(modifier = Modifier.size(1.dp)) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url?.contains("photobook.html") == true) {
                                        injectPdfStylesAndPrint(view) {
                                            isGenerating = false
                                            statusMessage = "Done! Select 'Save as PDF' above."
                                        }
                                    }
                                }
                            }
                            webView = this
                        }
                    }
                )
            }
        }
    }
}

private fun injectPdfStylesAndPrint(webView: WebView?, onComplete: () -> Unit) {
    val css = """
        @media print {
            .page {
                display: block !important;
                position: relative !important;
                transform: none !important;
                opacity: 1 !important;
                page-break-after: always !important;
                width: 100vw !important;
                height: 100vh !important;
                margin: 0 !important;
                padding: 0 !important;
            }
            nav, .dots, button, #prevBtn, #nextBtn { display: none !important; }
            body {
                overflow: visible !important;
                margin: 0 !important;
                padding: 0 !important;
            }
        }
        /* Visual adjustments to ensure all content is rendered before print */
        .page {
            display: block !important;
            opacity: 1 !important;
            position: static !important;
            transform: none !important;
            margin-bottom: 0px !important;
        }
        nav, .dots, button, #prevBtn, #nextBtn { display: none !important; }
    """.trimIndent()

    val js = "var style = document.createElement('style'); style.innerHTML = `${css}`; document.head.appendChild(style);"
    webView?.evaluateJavascript(js) {
        // Wait for images and layout to settle
        webView.postDelayed({
            val printManager = webView.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Photobook")
            printManager.print("Photobook Document", printAdapter, PrintAttributes.Builder().build())
            onComplete()
        }, 2500)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit) {
    CenterAlignedTopAppBar(title = title)
}
