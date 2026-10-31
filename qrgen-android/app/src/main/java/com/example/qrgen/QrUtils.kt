package com.example.qrgen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {
    fun create(content: String, sizePx: Int = 800): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }
}

/** Рисует страницу квитанции (A4, ~150 dpi) с явной пометкой ДЕМО. */
fun renderReceipt(ticket: Ticket, dateText: String, qr: Bitmap): Bitmap {
    val w = 1240
    val h = 1754
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)

    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val cx = w / 2f

    p.color = Color.BLACK
    p.typeface = Typeface.DEFAULT_BOLD
    p.textSize = 56f
    c.drawText("Билет Avtobys. Оплата проезда", cx, 160f, p)

    p.color = Color.rgb(200, 0, 0)
    p.textSize = 44f
    c.drawText("ДЕМО · не является платёжным документом", cx, 240f, p)

    val side = 700
    val left = (w - side) / 2
    c.drawBitmap(qr, null, Rect(left, 320, left + side, 320 + side), null)

    p.color = Color.BLACK
    p.typeface = Typeface.DEFAULT
    p.textSize = 40f
    c.drawText("Демо-билет № ${ticket.id}", cx, 1100f, p)
    c.drawText(dateText, cx, 1170f, p)
    c.drawText("Сумма ${ticket.amountTenge} ₸", cx, 1240f, p)
    return bmp
}

fun printReceipt(context: Context, ticket: Ticket, dateText: String, qr: Bitmap) {
    val sheet = renderReceipt(ticket, dateText, qr)
    PrintHelper(context).apply { scaleMode = PrintHelper.SCALE_MODE_FIT }
        .printBitmap("Демо-квитанция ${ticket.id}", sheet)
}
