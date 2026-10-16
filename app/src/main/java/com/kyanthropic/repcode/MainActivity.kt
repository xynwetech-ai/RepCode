package com.kyanthropic.repcode

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import fi.iki.elonen.NanoHTTPD
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import java.io.FileInputStream

// Obsidian Indigo Palette
val BgObsidian = Color(0xFF0D1117)
val BgDarker = Color(0xFF010409)
val BgSlate = Color(0xFF161B22)
val BorderDark = Color(0xFF21262D)
val TextPrimary = Color(0xFFE6EDF3)
val TextMuted = Color(0xFF8B949E)
val AccentIndigo = Color(0xFF6366F1)
val AccentPurple = Color(0xFFA855F7)

class LocalPreviewServer(private val rootDir: File) : NanoHTTPD("127.0.0.1", 0) {
    override fun serve(session: IHTTPSession): Response {
        val uriPath = session.uri.removePrefix("/").ifEmpty { "index.html" }
        val target = File(rootDir, uriPath)
        if (!target.exists() || target.isDirectory) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found: $uriPath")
        }
        val mime = when (target.extension.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "text/plain"
        }
        return newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(target), target.length())
    }
}

class MainActivity : ComponentActivity() {
    private var previewServer: LocalPreviewServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val projectsRoot = File(filesDir, "projects").apply { mkdirs() }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgObsidian,
                    surface = BgSlate,
                    primary = AccentIndigo,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                var currentProject by remember { mutableStateOf<File?>(null) }
                var serverPort by remember { mutableStateOf(0) }

                fun openProject(dir: File) {
                    previewServer?.stop()
                    val server = LocalPreviewServer(dir)
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    previewServer = server
                    serverPort = server.listeningPort
                    currentProject = dir
                }

                if (currentProject == null) {
                    WelcomeScreen(
                        projectsRoot = projectsRoot,
                        onOpenProject = { openProject(it) }
                    )
                } else {
                    WorkspaceScreen(
                        projectDir = currentProject!!,
                        serverPort = serverPort,
                        onExit = {
                            previewServer?.stop()
                            currentProject = null
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        previewServer?.stop()
        super.onDestroy()
    }
}

@Composable
fun WelcomeScreen(projectsRoot: File, onOpenProject: (File) -> Unit) {
    var projects by remember { mutableStateOf(projectsRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()) }
    var showNewDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("my-project") }

    fun createProject(name: String) {
        val clean = name.trim().ifEmpty { "my-project" }
        val dir = File(projectsRoot, clean).apply { mkdirs() }
        File(dir, "index.html").writeText(
            """<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>$clean</title>
  <link rel="stylesheet" href="styles.css" />
</head>
<body>
  <h1>Hello from RepCode!</h1>
  <p>Edit file di sebelah kiri dan lihat hasilnya secara langsung.</p>
  <script src="script.js"></script>
</body>
</html>"""
        )
        File(dir, "styles.css").writeText(
            """body {
  font-family: system-ui, sans-serif;
  background: #0f172a; color: #e2e8f0;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  min-height: 90vh; margin: 0;
}
h1 { color: #818cf8; }"""
        )
        File(dir, "script.js").writeText("console.log('RepCode Live Server aktif!');")
        projects = projectsRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        onOpenProject(dir)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgObsidian)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(AccentIndigo, AccentPurple))),
            contentAlignment = Alignment.Center
        ) {
            Text("</>", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(16.dp))
        Text("RepCode", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("by Kyanthropic · Native Android IDE", fontSize = 13.sp, color = TextMuted)
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { showNewDialog = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Buat Project Baru", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text("RECENT WORKSPACES", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects) { dir ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSlate)
                        .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                        .clickable { onOpenProject(dir) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = AccentIndigo)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(dir.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${dir.listFiles()?.size ?: 0} items", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            containerColor = BgSlate,
            title = { Text("Project Baru", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("Nama Project") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    showNewDialog = false
                    createProject(newProjectName)
                }) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) { Text("Batal") }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorkspaceScreen(projectDir: File, serverPort: Int, onExit: () -> Unit) {
    var files by remember { mutableStateOf(projectDir.listFiles()?.sortedBy { it.name } ?: emptyList()) }
    var activeFile by remember { mutableStateOf(files.firstOrNull { it.name == "index.html" } ?: files.firstOrNull()) }
    var codeText by remember { mutableStateOf(activeFile?.readText() ?: "") }
    var sidebarOpen by remember { mutableStateOf(true) }
    var previewOpen by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var consoleLogs by remember { mutableStateOf(listOf<String>()) }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var previewKey by remember { mutableStateOf(0) }

    fun saveCurrentFile() {
        activeFile?.writeText(codeText)
        previewKey++
    }

    Column(modifier = Modifier.fillMaxSize().background(BgObsidian)) {
        // Top Workspace Dock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(BgDarker)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                    Icon(Icons.Default.Menu, contentDescription = "Explorer", tint = if (sidebarOpen) AccentIndigo else TextMuted)
                }
                Text(projectDir.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { saveCurrentFile() }) {
                    Text("Save", color = Color(0xFF3FB950), fontSize = 12.sp)
                }
                IconButton(onClick = { terminalOpen = !terminalOpen }) {
                    Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = if (terminalOpen) AccentIndigo else TextMuted)
                }
                IconButton(onClick = {
                    saveCurrentFile()
                    previewOpen = !previewOpen
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = if (previewOpen) AccentIndigo else TextMuted)
                }
                TextButton(onClick = {
                    saveCurrentFile()
                    onExit()
                }) {
                    Text("Exit", color = Color(0xFFF85149), fontSize = 12.sp)
                }
            }
        }

        // Main Content Area
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (sidebarOpen) {
                Column(
                    modifier = Modifier
                        .width(175.dp)
                        .fillMaxHeight()
                        .background(BgDarker)
                        .border(0.5.dp, BorderDark)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EXPLORER", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { newFileDialog = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(files) { file ->
                            val selected = file.absolutePath == activeFile?.absolutePath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (selected) AccentIndigo.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        saveCurrentFile()
                                        activeFile = file
                                        codeText = file.readText()
                                        editorRef?.setText(codeText)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (selected) AccentIndigo else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(file.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Editor + Preview Split/Overlay
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        CodeEditor(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            typefaceText = Typeface.MONOSPACE
                            setTextSize(14f)
                            setText(codeText)
                            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                codeText = text.toString()
                            }
                            editorRef = this
                        }
                    }
                )

                if (previewOpen) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.55f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSlate)
                            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(BgDarker).padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LIVE PREVIEW (127.0.0.1:$serverPort)", fontSize = 11.sp, color = TextMuted)
                            IconButton(onClick = { previewOpen = false }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        key(previewKey) {
                            AndroidView(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        webViewClient = WebViewClient()
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onConsoleMessage(cm: ConsoleMessage?): Boolean {
                                                cm?.let {
                                                    consoleLogs = (consoleLogs + "[${it.messageLevel()}] ${it.message()}").takeLast(30)
                                                }
                                                return true
                                            }
                                        }
                                        loadUrl("http://127.0.0.1:$serverPort/index.html")
                                    }
                                }
                            )
                        }
                        if (consoleLogs.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().height(60.dp).background(BgDarker).padding(6.dp)
                            ) {
                                Text(consoleLogs.last(), color = AccentIndigo, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Extra Symbol Bar for Mobile Coding
        val symbols = listOf("Tab", "{", "}", "(", ")", "[", "]", "<", ">", ";", "\"", "'", "=", "/", "!")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(BgSlate)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            symbols.forEach { sym ->
                TextButton(
                    onClick = {
                        val insertText = if (sym == "Tab") "  " else sym
                        editorRef?.insertText(insertText, insertText.length)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(sym, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }

    if (newFileDialog) {
        AlertDialog(
            onDismissRequest = { newFileDialog = false },
            containerColor = BgSlate,
            title = { Text("File Baru", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("Nama file (contoh: app.js)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val name = newFileName.trim()
                    if (name.isNotEmpty()) {
                        val f = File(projectDir, name).apply { writeText("") }
                        files = projectDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                        activeFile = f
                        codeText = ""
                        editorRef?.setText("")
                    }
                    newFileName = ""
                    newFileDialog = false
                }) { Text("Buat") }
            },
            dismissButton = {
                TextButton(onClick = { newFileDialog = false }) { Text("Batal") }
            }
        )
    }

    if (terminalOpen) {
        var cmdInput by remember { mutableStateOf("") }
        var termOut by remember { mutableStateOf("RepCode Native Shell (${projectDir.name})\nKetik 'ls', 'cat <file>', 'touch <file>', atau 'clear'\n") }
        AlertDialog(
            onDismissRequest = { terminalOpen = false },
            containerColor = BgDarker,
            title = { Text("Terminal — ~/projects/${projectDir.name}", color = Color(0xFF58D68D), fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    Text(termOut, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().height(160.dp))
                    OutlinedTextField(
                        value = cmdInput,
                        onValueChange = { cmdInput = it },
                        placeholder = { Text("Masukkan perintah...") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parts = cmdInput.trim().split(" ")
                    val cmd = parts.firstOrNull() ?: ""
                    val arg = parts.getOrNull(1) ?: ""
                    val res = when (cmd) {
                        "ls" -> projectDir.listFiles()?.joinToString("  ") { it.name } ?: ""
                        "pwd" -> projectDir.absolutePath
                        "touch" -> if (arg.isNotEmpty()) { File(projectDir, arg).writeText(""); files = projectDir.listFiles()?.sortedBy { it.name } ?: emptyList(); "Created $arg" } else "Usage: touch <filename>"
                        "cat" -> if (arg.isNotEmpty() && File(projectDir, arg).exists()) File(projectDir, arg).readText() else "File not found"
                        "clear" -> ""
                        else -> "Perintah: ls, pwd, touch <file>, cat <file>, clear"
                    }
                    termOut = if (cmd == "clear") "" else "$termOut\n$ $cmdInput\n$res"
                    cmdInput = ""
                }) { Text("Run") }
            },
            dismissButton = {
                TextButton(onClick = { terminalOpen = false }) { Text("Tutup") }
            }
        )
    }
}
