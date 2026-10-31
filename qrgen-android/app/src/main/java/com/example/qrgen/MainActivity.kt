import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

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
    // Менеджеры для управления фокусом и клавиатурой
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
            onClick = {
                // Прячем клавиатуру и сбрасываем фокус перед открытием билета
                focusManager.clearFocus()
                keyboardController?.hide()
                
                onPay()
            },
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
