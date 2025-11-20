package com.aggin.carcost.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExportService(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    fun exportToCsv(
        car: Car,
        expenses: List<Expense>,
        reminders: List<MaintenanceReminder>
    ): File {
        val fileName = "CarCost_${car.brand}_${car.model}_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        file.bufferedWriter().use { writer ->
            writer.write("CarCost - Экспорт данных\n")
            writer.write("Дата экспорта: ${dateFormat.format(Date())}\n\n")
            writer.write("=== ИНФОРМАЦИЯ ОБ АВТОМОБИЛЕ ===\n")
            writer.write("Марка,${car.brand}\n")
            writer.write("Модель,${car.model}\n")
            writer.write("Год,${car.year}\n")
            writer.write("Гос. номер,${car.licensePlate}\n")
            writer.write("VIN,${car.vin ?: "-"}\n")
            writer.write("Текущий пробег,${car.currentOdometer} км\n\n")
            writer.write("=== РАСХОДЫ ===\n")
            writer.write("Дата,Категория,Сумма (₽),Пробег (км),Описание\n")
            expenses.forEach { expense ->
                writer.write(
                    "${dateOnlyFormat.format(Date(expense.date))}," +
                            "${expense.category.name}," +
                            "${expense.amount}," +
                            "${expense.odometer}," +
                            "\"${expense.description ?: "-"}\"\n"
                )
            }
            writer.write("\nНАПОМИНАНИЯ О ТЕХОБСЛУЖИВАНИИ\n")
            writer.write("Тип ТО,Последняя замена (км),Интервал (км),Следующая замена (км)\n")
            reminders.forEach { reminder ->
                writer.write(
                    "${reminder.type.displayName}," +
                            "${reminder.lastChangeOdometer}," +
                            "${reminder.intervalKm}," +
                            "${reminder.nextChangeOdometer}\n"
                )
            }
        }
        return file
    }

    /**
     * Экспорт данных автомобиля в PDF
     */
    fun exportToPdf(
        car: Car,
        expenses: List<Expense>,
        reminders: List<MaintenanceReminder>
    ): File {
        val fileName = "CarCost_${car.brand}_${car.model}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(null), fileName)

        val pdfWriter = PdfWriter(file)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        // --- ЗАГРУЗКА ШРИФТОВ ИЗ ПАПКИ RES/RAW ---
        val regularFontStream = context.resources.openRawResource(R.raw.roboto_regular)
        val boldFontStream = context.resources.openRawResource(R.raw.roboto_bold)
        val regularFontBytes = regularFontStream.readBytes()
        val boldFontBytes = boldFontStream.readBytes()
        regularFontStream.close()
        boldFontStream.close()

        val regularFontProgram = FontProgramFactory.createFont(regularFontBytes)
        val boldFontProgram = FontProgramFactory.createFont(boldFontBytes)
        val regularFont = PdfFontFactory.createFont(regularFontProgram)
        val boldFont = PdfFontFactory.createFont(boldFontProgram)
        // --- КОНЕЦ БЛОКА ЗАГРУЗКИ ---

        document.add(Paragraph("CarCost - Отчет по автомобилю").setFont(boldFont).setFontSize(20f).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("Дата создания: ${dateFormat.format(Date())}").setFont(regularFont).setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("\n"))

        document.add(Paragraph("ИНФОРМАЦИЯ ОБ АВТОМОБИЛЕ").setFont(boldFont).setFontSize(14f))
        val carInfoTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        carInfoTable.addCell(Cell().add(Paragraph("Марка и модель").setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph("${car.brand} ${car.model}").setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph("Год выпуска").setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph(car.year.toString()).setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph("Гос. номер").setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph(car.licensePlate).setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph("Текущий пробег").setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph("${car.currentOdometer} км").setFont(regularFont)))
        document.add(carInfoTable)
        document.add(Paragraph("\n"))

        document.add(Paragraph("ПОСЛЕДНИЕ РАСХОДЫ").setFont(boldFont).setFontSize(14f))
        val expensesTable = Table(UnitValue.createPercentArray(floatArrayOf(20f, 25f, 20f, 15f, 20f))).useAllAvailableWidth()
        expensesTable.addHeaderCell(Cell().add(Paragraph("Дата").setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph("Категория").setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph("Сумма (₽)").setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph("Пробег").setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph("Описание").setFont(boldFont)))
        expenses.sortedByDescending { it.date }.take(15).forEach { expense ->
            expensesTable.addCell(Cell().add(Paragraph(dateOnlyFormat.format(Date(expense.date))).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(expense.category.name).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f", expense.amount)).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph("${expense.odometer} км").setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(expense.description ?: "-").setFont(regularFont)))
        }
        document.add(expensesTable)
        document.add(Paragraph("\n"))

        if (reminders.isNotEmpty()) {
            document.add(Paragraph("НАПОМИНАНИЯ О ТЕХОБСЛУЖИВАНИИ").setFont(boldFont).setFontSize(14f))
            val remindersTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 25f, 25f, 20f))).useAllAvailableWidth()
            remindersTable.addHeaderCell(Cell().add(Paragraph("Тип ТО").setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph("Последняя (км)").setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph("Следующая (км)").setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph("Осталось (км)").setFont(boldFont)))
            reminders.forEach { reminder ->
                val remaining = reminder.nextChangeOdometer - car.currentOdometer
                remindersTable.addCell(Cell().add(Paragraph(reminder.type.displayName).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph(reminder.lastChangeOdometer.toString()).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph(reminder.nextChangeOdometer.toString()).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph("$remaining км").setFont(regularFont)))
            }
            document.add(remindersTable)
        }

        document.close()
        return file
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".csv") -> "text/csv"
                file.name.endsWith(".pdf") -> "application/pdf"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CarCost - Экспорт данных")
            putExtra(Intent.EXTRA_TEXT, "Отчет по автомобилю из приложения CarCost")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(intent, "Отправить отчет").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }
}