package com.example.qrgen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Билет, который отображается в приложении. */
data class Ticket(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val amountTenge: Int = 80,
    /** Содержимое QR-кода (ссылка или токен). */
    val token: String,
    val customCaption: String = "КОНТРОЛЛЕРУ №42"
)

/**
 * Интерфейс репозитория оплаты.
 */
interface PaymentRepository {
    suspend fun pay(): Ticket
}

/** Заглушка для репозитория. */
class FakePaymentRepository : PaymentRepository {
    override suspend fun pay(): Ticket {
        delay(500) // имитация сетевого запроса
        val id = UUID.randomUUID().toString().take(8).uppercase()
        val now = System.currentTimeMillis()
        val expires = now + 15 * 60 * 1000L
        return Ticket(
            id = id,
            issuedAtMillis = now,
            amountTenge = 80,
            token = "https://avtobys.kz/ticket/$id",
            customCaption = "КОНТРОЛЛЕРУ №42"
        )
    }
}

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Ready(val ticket: Ticket) : UiState
    data class Error(val message: String) : UiState
}

class TicketViewModel : ViewModel() {
    private val repo: PaymentRepository = FakePaymentRepository()

    var state: UiState by mutableStateOf(UiState.Idle)
        private set

    /** Создание билета с кастомными данными из полей ввода */
    fun payCustom(link: String, caption: String) {
        val id = UUID.randomUUID().toString().take(8).uppercase()
        state = UiState.Ready(
            Ticket(
                id = id,
                issuedAtMillis = System.currentTimeMillis(),
                amountTenge = 80,
                token = link.ifBlank { "https://avtobys.kz" },
                customCaption = caption.ifBlank { "КОНТРОЛЛЕРУ №42" }
            )
        )
    }

    /** Стандартный процесс оплаты через репозиторий */
    fun pay() {
        if (state is UiState.Loading) return
        state = UiState.Loading
        viewModelScope.launch {
            state = try {
                UiState.Ready(repo.pay())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error("Не удалось выполнить оплату. Попробуйте ещё раз.")
            }
        }
    }

    /** Сброс состояния (закрытие карточки билета) */
    fun close() {
        state = UiState.Idle
    }
}
