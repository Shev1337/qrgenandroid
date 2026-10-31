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

/** Билет, который возвращает бэкенд после оплаты. */
data class Ticket(
    val id: String,
    val issuedAtMillis: Long,
    val amountTenge: Int,
    /** Содержимое QR-кода. В боевой версии это подписанный токен от сервера. */
    val token: String,
    val customCaption: String
)

/**
 * Единственная точка, которую нужно заменить при подключении сервера:
 * реализация должна обратиться к платёжному API и вернуть билет с токеном,
 * подписанным на стороне сервера. Приложение само билеты не выдаёт.
 */
interface PaymentRepository {
    suspend fun pay(): Ticket
}

/** Заглушка для макета: ничего не оплачивает, токен явно помечен как DEMO. */
class FakePaymentRepository : PaymentRepository {
    override suspend fun pay(): Ticket {
        delay(700) // имитация сетевого запроса
        val id = UUID.randomUUID().toString().take(8).uppercase()
        val now = System.currentTimeMillis()
        val expires = now + 15 * 60 * 1000L
        return Ticket(
            id = id,
            issuedAtMillis = now,
            amountTenge = 80,
            token = "DEMO-TICKET:$id:$expires",
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

    fun payCustom(link: String, caption: String) {
        state = UiState.Ready(
            Ticket(
                token = link,
                customCaption = caption
            )
        )
    }

    fun close() {
        state = UiState.Idle
    }

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

    fun close() {
        state = UiState.Idle
    }
}
