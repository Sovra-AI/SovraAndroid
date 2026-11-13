/*
 * Pairing: the steps, a camera scanner for the desktop's QR code, and "paste a link"
 * as a fallback. The invitation link is used once, in memory, and never kept.
 */
package ai.sovra.remote.ui

import ai.sovra.remote.data.Phase
import ai.sovra.remote.data.RemoteStore
import ai.sovra.remote.protocol.Outcome
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private val STEPS = listOf(
    "Open Sovra on your computer.",
    "Click Skills, then “Use Sovra from your phone”. A square code appears.",
    "Tap “Scan the code” below and point your camera at it.",
)

class PairingNames(phone: String) {
    var phoneName by mutableStateOf(phone)
    var desktopName by mutableStateOf("My computer")
}

@Composable
private fun NameField(label: String, value: String, onChange: (String) -> Unit, hint: String? = null) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.button)
    Column(verticalArrangement = Arrangement.spacedBy(Space.snug)) {
        SText(label, style = Type.caption, tone = TextTone.Secondary)
        Box(
            Modifier.fillMaxWidth().heightIn(min = Layout.control).clip(shape).background(p.surface).border(1.dp, p.controlBorder, shape).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value, { if (it.length <= 80) onChange(it) },
                Modifier.fillMaxWidth().semantics { contentDescription = label },
                singleLine = true, textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.ring),
            )
        }
        hint?.let { SText(it, style = Type.caption, tone = TextTone.Muted) }
    }
}

@Composable
fun PairScreen(store: RemoteStore, names: PairingNames, onScan: () -> Unit, onPaired: () -> Unit) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }

    val pasteLink: () -> Unit = {
        error = null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val link = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (!link.startsWith("sovra://remote-control")) {
            error = "Your clipboard does not hold a Sovra pairing link. Copy it on your computer first."
        } else {
            busy = true
            scope.launch {
                val result = store.pair(link, names.phoneName, names.desktopName)
                busy = false
                // Do not leave a bearer invitation lying in the clipboard.
                if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                if (result is Outcome.Failed) error = result.message else onPaired()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(LocalPalette.current.canvas).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.large),
        verticalArrangement = Arrangement.spacedBy(Space.large),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = Space.extra), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.medium)) {
            Logo(72.dp)
            SText("Use Sovra from your phone", style = Type.pageTitle.copy(textAlign = TextAlign.Center))
            SText(
                "Pair once. After that, opening this app connects to your computer by itself — on Wi-Fi or mobile data.",
                style = Type.body.copy(textAlign = TextAlign.Center), tone = TextTone.Secondary,
            )
        }
        if (state.phase == Phase.Revoked) NoticeBanner(ai.sovra.remote.data.Tone.Warning, state.detail ?: "Your computer no longer recognises this phone. Pair again.")
        state.notice?.let { NoticeBanner(it.tone, it.text) { store.setNotice(null) } }
        Card {
            STEPS.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(LocalPalette.current.selection), contentAlignment = Alignment.Center) {
                        SText("${index + 1}", style = Type.caption, tone = TextTone.Link)
                    }
                    SText(step, Modifier.weight(1f))
                }
            }
        }
        error?.let { NoticeBanner(ai.sovra.remote.data.Tone.Danger, it) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SButton("Scan the code", onScan, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary, enabled = !busy)
            SButton(if (busy) "Pairing…" else "Paste a pairing link instead", pasteLink, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, enabled = !busy)
        }
        if (naming) {
            NameField("This phone's name", names.phoneName, { names.phoneName = it }, "Shown on your computer so you can recognise and forget it.")
            NameField("Your computer's name", names.desktopName, { names.desktopName = it })
        } else {
            SButton("Change names (optional)", { naming = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.Ghost, compact = true)
        }
        SText(
            "Private and encrypted from end to end. Works on Wi-Fi or mobile data while your computer is awake and Sovra is open.",
            style = Type.caption.copy(textAlign = TextAlign.Center), tone = TextTone.Muted, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Pairs with a link from the camera or a `sovra://remote-control` deep link, once. */
@Composable
fun PairingLink(store: RemoteStore, names: PairingNames, link: String, onDone: (Boolean) -> Unit) {
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(link) {
        when (val result = store.pair(link, names.phoneName, names.desktopName)) {
            is Outcome.Ok -> onDone(true)
            is Outcome.Failed -> failure = result.message
        }
    }
    Column(
        Modifier.fillMaxSize().background(LocalPalette.current.canvas).safeDrawingPadding().padding(Space.large),
        verticalArrangement = Arrangement.spacedBy(Space.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val message = failure
        if (message != null) {
            NoticeBanner(ai.sovra.remote.data.Tone.Danger, message)
            SButton("Back to pairing", { onDone(false) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Primary)
        } else {
            CircularProgressIndicator(color = LocalPalette.current.ring)
            SText("Pairing securely with your computer…", tone = TextTone.Secondary)
        }
    }
}

@Composable
fun ScanScreen(onCode: (String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!allowed) request.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        if (allowed) CameraScanner(onCode)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(Space.large),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            SText(
                if (allowed) "Point your camera at the code on your computer." else if (asked) "Sovra needs the camera to scan the code. Allow it in Settings, or paste the link instead." else "",
                style = Type.sectionTitle.copy(color = androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            SButton("Cancel", onCancel, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CameraScanner(onCode: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var found by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(
        factory = { context ->
            val view = PreviewView(context)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val reader = QRCodeReader()
                val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val plane = image.planes[0]
                        val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
                        val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                        val text = runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
                        if (text != null && text.startsWith("sovra://remote-control") && !found) {
                            found = true
                            ContextCompat.getMainExecutor(context).execute { onCode(text) }
                        }
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(context))
            view
        },
        modifier = Modifier.fillMaxSize(),
    )
}
