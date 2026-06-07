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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to generate photobook PDF") }

    var webView: WebView? by remember { mutableStateOf(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Photobook PDF Generator",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Please wait...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = {
                                isGenerating = true
                                statusMessage = "Loading and rendering..."
                                webView?.loadUrl("http://66.102.139.204:8095/photobook.html")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Generate PDF",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Hidden WebView
                Box(modifier = Modifier.size(1.dp).background(Color.Transparent)) {
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
                                                statusMessage = "PDF Ready! Select 'Save as PDF'."
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
        webView.postDelayed({
            val printManager = webView.context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter("Photobook")
            printManager.print("Photobook Document", printAdapter, PrintAttributes.Builder().build())
            onComplete()
        }, 2500)
    }
}
