package fr.luteal.core.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

internal class PdfPageWriter(
    private val document: PdfDocument,
    private val pageWidth: Int,
    private val pageHeight: Int,
    private val margin: Float
) {
    val contentWidth: Float = pageWidth - margin * 2
    private val footerY = pageHeight - margin - 15f
    private var pageNumber = 0
    private var currentPage: PdfDocument.Page? = null

    lateinit var canvas: Canvas
        private set
    var y = margin

    val titlePaint = paint(16f, bold = true, color = "#235B4E")
    val headerPaint = paint(12f, bold = true, color = "#235B4E")
    val textPaint = paint(10f, bold = false, color = "#1A1C1E")
    val secondaryPaint = paint(8.5f, bold = false, color = "#55585E")
    val linePaint = Paint().apply {
        color = Color.parseColor("#E2E2E6")
        strokeWidth = 1f
    }

    fun start() {
        pageNumber = 0
        newPage()
    }

    fun newPage() {
        currentPage?.let { finishCurrentPage() }
        pageNumber++
        currentPage = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        canvas = currentPage!!.canvas
        y = margin + 10f
        if (pageNumber > 1) {
            canvas.drawText("Endoluteal", margin, y, secondaryPaint)
            y += 14f
        }
    }

    fun ensureSpace(needed: Float) {
        if (y + needed > footerY - 12f) newPage()
    }

    fun line(text: String, paint: Paint = textPaint) {
        wrap(text, paint, contentWidth).forEach { row ->
            ensureSpace(paint.textSize + 8f)
            canvas.drawText(row, margin, y, paint)
            y += paint.textSize + 6f
        }
    }

    fun finishDocument() {
        finishCurrentPage()
    }

    private fun finishCurrentPage() {
        val page = currentPage ?: return
        val disclaimer =
            "Document descriptif établi à partir des saisies. Ne constitue pas un avis médical."
        canvas.drawLine(margin, footerY - 5f, margin + contentWidth, footerY - 5f, linePaint)
        canvas.drawText(disclaimer, margin, footerY + 8f, secondaryPaint)
        canvas.drawText(pageNumber.toString(), margin + contentWidth - 15f, footerY + 8f, secondaryPaint)
        document.finishPage(page)
        currentPage = null
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun paint(size: Float, bold: Boolean, color: String) = Paint().apply {
        this.color = Color.parseColor(color)
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        isAntiAlias = true
    }
}
