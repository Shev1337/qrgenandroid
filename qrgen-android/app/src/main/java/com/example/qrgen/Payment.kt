package com.example.qrgen

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedItem(
    val id: String = UUID.randomUUID().toString(),
    val link: String,
    val caption: String,
    val title: String = ""
)

data class Ticket(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(),
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val amountTenge: Int = 80,
    val token: String,
    val customCaption: String = "КОНТРОЛЛЕРУ №42"
)

interface PaymentRepository {
    suspend fun pay(): Ticket
}

class FakePaymentRepository : PaymentRepository {
    override suspend fun pay(): Ticket {
        delay(300)
        val id = UUID.randomUUID().toString().take(8).uppercase()
        val now = System.currentTimeMillis()
        return Ticket(
            id = id,
            issuedAtMillis = now,
            amountTenge = 80,
            token = "https://avtobys.kz/ticket/$id",
            customCaption = "721BG04"
        )
    }
}

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Ready(val ticket: Ticket) : UiState
    data class Error(val message: String) : UiState
}

class TicketViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: PaymentRepository = FakePaymentRepository()
    private val prefs = application.getSharedPreferences("qr_prefs", Context.MODE_PRIVATE)

    var state: UiState by mutableStateOf(UiState.Idle)
        private set

    var savedItems: List<SavedItem> by mutableStateOf(loadSavedItems())
        private set

    // Состояние для диалога подтверждения при сканировании существующего QR
    var pendingMatchItem: SavedItem? by mutableStateOf(null)
        private set

    private fun loadSavedItems(): List<SavedItem> {
        val jsonStr = prefs.getString("saved_items_json", "[]") ?: "[]"
        val list = mutableListOf<SavedItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        link = obj.getString("link"),
                        caption = obj.optString("caption", ""),
                        title = obj.optString("title", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun persistItems(items: List<SavedItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("link", item.link)
                put("caption", item.caption)
                put("title", item.title)
            }
            arr.put(obj)
        }
        prefs.edit().putString("saved_items_json", arr.toString()).apply()
        savedItems = items
    }

    fun saveCurrent(link: String, caption: String, title: String) {
        if (link.isBlank()) return
        val newItem = SavedItem(link = link, caption = caption, title = title)
        val list = savedItems.filter { it.link != link }.toMutableList()
        list.add(0, newItem)
        persistItems(list)
    }

    fun deleteItem(id: String) {
        val list = savedItems.filter { id != id } // wait, fixed below: it.id != id
        persistItems(savedItems.filter { it.id != id })
    }

    fun touchItem(item: SavedItem) {
        val list = savedItems.filter { it.id != item.id }.toMutableList()
        list.add(0, item)
        persistItems(list)
    }

    /** Обработка отсканированного QR-кода */
    fun handleScannedLink(scannedLink: String, currentCaption: String, currentTitle: String, onOpenCard: (String, String, String) -> Unit) {
        val existing = savedItems.firstOrNull { it.link == scannedLink }
        if (existing != null) {
            // Если такой QR уже есть в истории — показываем диалог подтверждения
            pendingMatchItem = existing
        } else {
            // Если новый — если были введены данные, сохраняем, и открываем
            if (currentCaption.isNotBlank() || currentTitle.isNotBlank()) {
                saveCurrent(scannedLink, currentCaption, currentTitle)
            }
            onOpenCard(scannedLink, currentCaption, currentTitle)
        }
    }

    fun acceptPendingMatch(onOpenCard: (String, String, String) -> Unit) {
        pendingMatchItem?.let { item ->
            touchItem(item)
            pendingMatchItem = null
            onOpenCard(item.link, item.caption, item.title)
        }
    }

    fun dismissPendingMatch() {
        pendingMatchItem = null
    }

    fun payCustom(link: String, caption: String, title: String = "") {
        saveCurrent(link, caption, title)
        val id = UUID.randomUUID().toString().take(8).uppercase()
        state = UiState.Ready(
            Ticket(
                id = id,
                issuedAtMillis = System.currentTimeMillis(),
                amountTenge = 80,
                token = link.ifBlank { "https://avtobys.kz" },
                customCaption = caption.ifBlank { "721BG04" }
            )
        )
    }

    fun close() {
        state = UiState.Idle
    }
}
