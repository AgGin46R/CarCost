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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExportService(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val dateOnlyFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    /**
     * Выполняется на IO: чтение двух TTF целиком, парсинг шрифтов, сборка
     * таблицы по шесть ячеек на расход и запись файла раньше шли на главном
     * потоке. Индикатор «Создание файла...» не успевал отрисоваться — он
     * выставлялся в том же кадре, который и блокировался, — а на сотнях
     * записей это оборачивалось ANR.
     */
    suspend fun exportToCsv(
        car: Car,
        expenses: List<Expense>,
        reminders: List<MaintenanceReminder>
    ): File = withContext(Dispatchers.IO) {
        val fileName = "CarCost_${car.brand}_${car.model}_${System.currentTimeMillis()}.csv"
        val file = File(context.getExternalFilesDir(null), fileName)
        file.bufferedWriter().use { writer ->
            writer.write(context.getString(R.string.export_carcost_eksport_dannyh_n))
            writer.write(context.getString(R.string.export_data_eksporta_n_n, dateFormat.format(Date())))

            writer.write(context.getString(R.string.export_informatsiya_ob_avtomobile_n))
            writer.write(context.getString(R.string.export_marka_n, car.brand))
            writer.write(context.getString(R.string.export_model_n, car.model))
            writer.write(context.getString(R.string.export_god_n, car.year))
            writer.write(context.getString(R.string.export_gos_nomer_n, car.licensePlate))
            writer.write("VIN,${car.vin ?: "-"}\n")
            writer.write(context.getString(R.string.export_tekuschiy_probeg_km_n_n, car.currentOdometer))

            // Сводная статистика
            val total = expenses.sumOf { it.amount }
            writer.write(context.getString(R.string.export_svodnaya_statistika_n))
            writer.write(context.getString(R.string.export_vsego_zapisey_n, expenses.size))
            writer.write(context.getString(R.string.export_itogo_rashodov_n, String.format(Locale.US, "%.2f", total)))
            if (expenses.isNotEmpty()) {
                writer.write(context.getString(R.string.export_sredniy_rashod_n, String.format(Locale.US, "%.2f", total / expenses.size)))
            }
            writer.write(context.getString(R.string.export_npo_kategoriyam_n))
            writer.write(context.getString(R.string.export_kategoriya_summa_kol_vo_n))
            expenses.groupBy { it.category }.toSortedMap(compareBy { it.name }).forEach { (cat, list) ->
                writer.write("${cat.name},${String.format(Locale.US, "%.2f", list.sumOf { it.amount })},${list.size}\n")
            }
            writer.write("\n")

            writer.write(context.getString(R.string.export_rashody_n))
            writer.write(context.getString(R.string.export_data_kategoriya_summa_probeg_km_opisanie))
            expenses.sortedByDescending { it.date }.forEach { expense ->
                writer.write(
                    "${dateOnlyFormat.format(Date(expense.date))}," +
                    "${expense.category.name}," +
                    "${String.format(Locale.US, "%.2f", expense.amount)}," +
                    "${expense.odometer}," +
                    "\"${expense.description?.replace("\"","'") ?: "-"}\"," +
                    "\"${expense.location?.replace("\"","'") ?: "-"}\"," +
                    "\"${expense.workshopName?.replace("\"","'") ?: "-"}\"," +
                    "${expense.serviceType?.name ?: "-"}," +
                    "${expense.fuelLiters ?: "-"}," +
                    "\"${expense.maintenanceParts?.replace("\"","'") ?: "-"}\"\n"
                )
            }

            writer.write(context.getString(R.string.export_n_napominaniya_o_tehobsluzhivanii_n))
            writer.write(context.getString(R.string.export_tip_to_poslednyaya_zamena_km_interval_km))
            reminders.forEach { reminder ->
                writer.write(
                    "${context.getString(reminder.type.displayNameRes)}," +
                    "${reminder.lastChangeOdometer}," +
                    "${reminder.intervalKm}," +
                    "${reminder.nextChangeOdometer}\n"
                )
            }
        }
        file
    }

    /**
     * Экспорт данных автомобиля в PDF
     */
    /**
     * Выполняется на IO: чтение двух TTF целиком, парсинг шрифтов, сборка
     * таблицы по шесть ячеек на расход и запись файла раньше шли на главном
     * потоке. Индикатор «Создание файла...» не успевал отрисоваться — он
     * выставлялся в том же кадре, который и блокировался, — а на сотнях
     * записей это оборачивалось ANR.
     */
    suspend fun exportToPdf(
        car: Car,
        expenses: List<Expense>,
        reminders: List<MaintenanceReminder>
    ): File = withContext(Dispatchers.IO) {
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

        document.add(Paragraph(context.getString(R.string.export_carcost_otchet_po_avtomobilyu)).setFont(boldFont).setFontSize(20f).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph(context.getString(R.string.export_data_sozdaniya, dateFormat.format(Date()))).setFont(regularFont).setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
        document.add(Paragraph("\n"))

        document.add(Paragraph(context.getString(R.string.export_informatsiya_ob_avtomobile)).setFont(boldFont).setFontSize(14f))
        val carInfoTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()
        carInfoTable.addCell(Cell().add(Paragraph(context.getString(R.string.export_marka_i_model)).setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph("${car.brand} ${car.model}").setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph(context.getString(R.string.export_god_vypuska)).setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph(car.year.toString()).setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph(context.getString(R.string.export_gos_nomer)).setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph(car.licensePlate).setFont(regularFont)))
        carInfoTable.addCell(Cell().add(Paragraph(context.getString(R.string.export_tekuschiy_probeg)).setFont(boldFont)))
        carInfoTable.addCell(Cell().add(Paragraph(context.getString(R.string.home_km, car.currentOdometer)).setFont(regularFont)))
        document.add(carInfoTable)
        document.add(Paragraph("\n"))

        // Сводная статистика
        val total = expenses.sumOf { it.amount }
        if (expenses.isNotEmpty()) {
            document.add(Paragraph(context.getString(R.string.export_svodnaya_statistika)).setFont(boldFont).setFontSize(14f))
            val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
            summaryTable.addCell(Cell().add(Paragraph(context.getString(R.string.cardetail_vsego_rashodov)).setFont(boldFont)))
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f ₽", total)).setFont(regularFont)))
            summaryTable.addCell(Cell().add(Paragraph(context.getString(R.string.export_kol_vo_zapisey)).setFont(boldFont)))
            summaryTable.addCell(Cell().add(Paragraph(expenses.size.toString()).setFont(regularFont)))
            summaryTable.addCell(Cell().add(Paragraph(context.getString(R.string.analytics_sredniy_rashod)).setFont(boldFont)))
            summaryTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f ₽", total / expenses.size)).setFont(regularFont)))
            document.add(summaryTable)
            document.add(Paragraph("\n"))

            // По категориям
            document.add(Paragraph(context.getString(R.string.tco_rashody_po_kategoriyam)).setFont(boldFont).setFontSize(14f))
            val catTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 30f, 20f))).useAllAvailableWidth()
            catTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.addexp_kategoriya_2)).setFont(boldFont)))
            catTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.goals_summa)).setFont(boldFont)))
            catTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_kol_vo)).setFont(boldFont)))
            expenses.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf { e -> e.amount } }
                .forEach { (cat, list) ->
                    catTable.addCell(Cell().add(Paragraph(cat.name).setFont(regularFont)))
                    catTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f", list.sumOf { it.amount })).setFont(regularFont)))
                    catTable.addCell(Cell().add(Paragraph(list.size.toString()).setFont(regularFont)))
                }
            document.add(catTable)
            document.add(Paragraph("\n"))
        }

        document.add(Paragraph(context.getString(R.string.export_rashody)).setFont(boldFont).setFontSize(14f))
        val expensesTable = Table(UnitValue.createPercentArray(floatArrayOf(15f, 20f, 15f, 12f, 20f, 18f))).useAllAvailableWidth()
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.cardetail_data)).setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.addexp_kategoriya_2)).setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.goals_summa)).setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.home_probeg)).setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.cardetail_opisanie)).setFont(boldFont)))
        expensesTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_zapchasti_mesto)).setFont(boldFont)))
        expenses.sortedByDescending { it.date }.forEach { expense ->
            expensesTable.addCell(Cell().add(Paragraph(dateOnlyFormat.format(Date(expense.date))).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(expense.category.name).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(String.format(Locale.US, "%.2f", expense.amount)).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(context.getString(R.string.home_km, expense.odometer)).setFont(regularFont)))
            expensesTable.addCell(Cell().add(Paragraph(expense.description ?: "-").setFont(regularFont)))
            val extra = listOfNotNull(expense.maintenanceParts, expense.workshopName, expense.location)
                .firstOrNull() ?: "-"
            expensesTable.addCell(Cell().add(Paragraph(extra).setFont(regularFont)))
        }
        document.add(expensesTable)
        document.add(Paragraph("\n"))

        if (reminders.isNotEmpty()) {
            document.add(Paragraph(context.getString(R.string.export_napominaniya_o_tehobsluzhivanii)).setFont(boldFont).setFontSize(14f))
            val remindersTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 25f, 25f, 20f))).useAllAvailableWidth()
            remindersTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_tip_to)).setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_poslednyaya_km)).setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_sleduyuschaya_km)).setFont(boldFont)))
            remindersTable.addHeaderCell(Cell().add(Paragraph(context.getString(R.string.export_ostalos_km)).setFont(boldFont)))
            reminders.forEach { reminder ->
                val remaining = reminder.nextChangeOdometer - car.currentOdometer
                remindersTable.addCell(Cell().add(Paragraph(context.getString(reminder.type.displayNameRes)).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph(reminder.lastChangeOdometer.toString()).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph(reminder.nextChangeOdometer.toString()).setFont(regularFont)))
                remindersTable.addCell(Cell().add(Paragraph(context.getString(R.string.home_km, remaining)).setFont(regularFont)))
            }
            document.add(remindersTable)
        }

        document.close()
        file
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
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_carcost_eksport_dannyh))
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.export_otchet_po_avtomobilyu_iz_prilozheniya))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(intent, context.getString(R.string.bugreport_otpravit_otchet)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }
}