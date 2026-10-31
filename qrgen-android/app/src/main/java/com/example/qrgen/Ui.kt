package com.example.qrgen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var inputCaption by remember { mutableStateOf("КОНТРОЛЛЕРУ №42") }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        HomeScreen(
            loading = state is UiState.Loading,
            error = (state as? UiState.Error)?.message,
            link = inputLink,
            onLinkChange = { inputLink = it },
            caption = inputCaption,
            onCaptionChange = { inputCaption = it },
            onPay = { vm.payCustom(inputLink, inputCaption) },
        )
        if (state is UiState.Ready) {
            TicketOverlay(ticket = state.ticket, onClose = vm::close)
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
    onPay: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Генератор билетов",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.padding(top = 24.dp),
        )

        Text(
            "Введите данные для генерации QR-кода и настройки подписи.",
            color = Gray,
            fontSize = 15.sp,
        )

        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            label = { Text("Ссылка / Данные для QR-кода") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            label = { Text("Текст под QR-кодом") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Button(
            onClick = onPay,
            enabled = !loading && link.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
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
    var drag by remember { mutableFloatStateOf(0f) }

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

        // Шапка на фоне
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
                    fontSize = lerp(18.sp, 22.sp, t),
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
                    .size(lerp(220.dp, minOf(330.dp, width - 56.dp), t))
                    .background(Color.White),
            )

            // Пользовательский текст под QR-кодом в строгом оформлении
            Text(
                text = ticket.customCaption,
                fontSize = lerp(14.sp, 16.sp, t),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp),
            )

            Column(
                Modifier.padding(top = 16.dp),
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

/** Пока карточка открыта — яркость экрана на максимум, потом возвращаем как было. */
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
