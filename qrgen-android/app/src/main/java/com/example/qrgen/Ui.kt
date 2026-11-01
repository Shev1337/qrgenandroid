package com.example.qrgen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val Backdrop = Color(0xFFD9DBE0)
private val Gray = Color(0xFF737373)
private val Green = Color(0xFF34C759)
private val Warn = Color(0xFFB00020)

@Composable
fun QRGenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Color.Black, onPrimary = Color.White),
        content = content,
    )
}

@Composable
fun TicketApp(vm: TicketViewModel = viewModel()) {
    val state = vm.state
    var inputLink by remember { mutableStateOf("https://avtobys.kz") }
    var inputCaption by remember { mutableStateOf("721BG04") }
    var inputTitle by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showScanner = true
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        HomeScreen(
            loading = state is UiState.Loading,
            error = (state as? UiState.Error)?.message,
            link = inputLink,
            onLinkChange = { inputLink = it },
            caption = inputCaption,
            onCaptionChange = { inputCaption = it },
            title = inputTitle,
            onTitleChange = { inputTitle = it },
            savedItems = vm.savedItems,
            onPay = { vm.payCustom(inputLink, inputCaption, inputTitle) },
            onScanClick = {
                if (hasCameraPermission) {
                    showScanner = true
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onSelectItem = { item ->
                vm.touchItem(item)
                inputLink = item.link
                inputCaption = item.caption
                inputTitle = item.title
                vm.payCustom(item.link, item.caption, item.title)
            },
            onDeleteItem = { id -> vm.deleteItem(id) }
        )

        if (state is UiState.Ready) {
            TicketOverlay(ticket = state.ticket, onClose = vm::close)
        }

        if (showScanner) {
            ScannerView(
                onScanResult = { scanned ->
                    showScanner = false
                    vm.handleScannedLink(scanned, inputCaption, inputTitle) { link, cap, ttl ->
                        inputLink = link
                        inputCaption = cap
                        inputTitle = ttl
                        vm.payCustom(link, cap, ttl)
                    }
                },
                onClose = { showScanner = false }
            )
        }

        vm.pendingMatchItem?.let { matchItem ->
            AlertDialog(
                onDismissRequest = { vm.dismissPendingMatch() },
                title = { Text("Найден сохранённый QR-код") },
                text = {
                    val displayCaption = matchItem.caption.ifBlank { matchItem.title.ifBlank { "Без подписи" } }
                    Text("Применить сохранённую подпись:\n«$displayCaption»?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        inputLink = matchItem.link
                        inputCaption = matchItem.caption
                        inputTitle = matchItem.title
                        vm.acceptPendingMatch { link, cap, ttl ->
                            vm.payCustom(link, cap, ttl)
                        }
                    }) {
                        Text("Использовать", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissPendingMatch() }) {
                        Text("Отмена", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    loading: Boolean,
    error: String?,
    link: String,
    onLinkChange: (String) -> Unit,
    caption: String,
    onCaptionChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    savedItems: List<SavedItem>,
    onPay: () -> Unit,
    onScanClick: () -> Unit,
    onSelectItem: (SavedItem) -> Unit,
    onDeleteItem: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Генератор билетов",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(top = 16.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = link,
                onValueChange = onLinkChange,
                label = { Text("Ссылка / Данные для QR-кода") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            IconButton(
                onClick = onScanClick,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFF2F2F7), RoundedCornerShape(14.dp))
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = "Сканировать QR", tint = Color.Black)
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Текст сверху (необязательно)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            label = { Text("Подпись под QR-кодом") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                onPay()
            },
            enabled = !loading && link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFBFBFBF),
            ),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Сформировать билет", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (error != null) Text(error, color = Warn, fontSize = 14.sp)

        if (savedItems.isNotEmpty()) {
            Text("Сохранённые", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(top = 4.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(savedItems, key = { it.id }) { item ->
                    SavedRow(item = item, onClick = { onSelectItem(item) }, onDelete = { onDeleteItem(item.id) })
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val primary = item.caption.ifBlank { item.title.ifBlank { item.link } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF2F2F7))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (primary != item.link) {
                Text(item.link, fontSize = 12.sp, color = Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Gray, modifier = Modifier.size(18.sp.value.dp))
        }
    }
}

@Composable
private fun ScannerView(onScanResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                processImageProxy(imageProxy) { resultText ->
                                    if (!scanned) {
                                        scanned = true
                                        onScanResult(resultText)
                                    }
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalyzer
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            }
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Закрыть сканер", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onDetected: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { value ->
                        onDetected(value)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {}
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun TicketOverlay(ticket: Ticket, onClose: () -> Unit) {
    MaxBrightnessEffect()
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val qrBitmap = remember(ticket.token) { QrGenerator.create(ticket.token) }
    val qrImage: ImageBitmap = remember(qrBitmap) { qrBitmap.asImageBitmap() }
    val dateText = remember(ticket.issuedAtMillis) {
        SimpleDateFormat("d MMMM yyyy HH:mm", Locale("ru", "RU")).format(Date(ticket.issuedAtMillis))
    }

    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Backdrop)) {
        val full = maxHeight
        val width = maxWidth
        val collapsedH = full * 0.8f
        val baseH = if (expanded) full else collapsedH
        val dragDp = with(LocalDensity.current) { drag.toDp() }
        val targetH = (baseH - dragDp).coerceIn(full * 0.5f, full)
        val h by animateDpAsState(
            targetValue = if (dragging) targetH else baseH,
            animationSpec = if (dragging) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
            label = "cardHeight",
        )
        val t = ((h - collapsedH) / (full - collapsedH)).coerceIn(0f, 1f)

        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Билет Avtobys. Оплата проезда", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(60.dp))
            Text("Ваш платеж принят", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }

        val cardShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(h)
                .shadow(12.dp, cardShape)
                .background(Color.White, cardShape)
                .pointerInput(expanded) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val dy = drag
                            if (expanded) {
                                if (dy > 100.dp.toPx()) expanded = false
                            } else if (dy < -50.dp.toPx()) {
                                expanded = true
                            } else if (dy > 130.dp.toPx()) {
                                onClose()
                            }
                            drag = 0f
                        },
                        onDragCancel = { dragging = false; drag = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                    )
                }
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .background(Color(0xFFCCCCCC), CircleShape)
                    .clickable { expanded = !expanded },
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Покажите QR-код контроллеру",
                    fontSize = lerp(16.sp, 20.sp, t),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.Black)
                }
            }

            Spacer(Modifier.weight(1f))

            Image(
                bitmap = qrImage,
                contentDescription = "QR-код билета",
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(lerp(200.dp, minOf(300.dp, width - 56.dp), t))
                    .background(Color.White),
            )

            Text(
                text = "Покажите QR-код контроллеру",
                fontSize = lerp(12.sp, 14.sp, t),
                color = Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = ticket.customCaption,
                fontSize = lerp(18.sp, 24.sp, t),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
            )

            Column(
                Modifier.padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(dateText, fontSize = lerp(12.sp, 14.sp, t), color = Color.Black)
                Text("Сумма ${ticket.amountTenge} ₸", fontSize = lerp(12.sp, 14.sp, t), fontWeight = FontWeight.Light, color = Color.Black)
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ActionButton(Icons.Outlined.Receipt, "Квитанция") {
                    printReceipt(context, ticket, dateText, qrBitmap)
                }
                ActionButton(Icons.Filled.Share, "Поделиться") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Билет Avtobys №${ticket.id}: ${ticket.token}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, color = Color.Black)
    }
}

@Composable
private fun MaxBrightnessEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness
        if (window != null) {
            window.attributes = window.attributes.also { it.screenBrightness = 1f }
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.also {
                    it.screenBrightness = original ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
