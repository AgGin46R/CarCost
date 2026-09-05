package com.aggin.carcost.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aggin.carcost.R
import com.aggin.carcost.data.local.database.entities.Car
import com.aggin.carcost.data.local.database.entities.Expense
import com.aggin.carcost.data.local.database.entities.ExpenseCategory
import com.aggin.carcost.data.local.database.entities.MaintenanceReminder
import com.aggin.carcost.data.local.database.AppDatabase
import com.aggin.carcost.domain.fuel.FuelConsumptionCalculator
import com.aggin.carcost.util.CurrencyUtils
import com.itextpdf.io.font.FontProgramFactory
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.kernel.font.PdfFont
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


    /**
     * Паспорт автомобиля — вся его история одним документом.
     *
     * Отличается от обычного отчёта назначением, а не объёмом. Отчёт человек
     * смотрит сам; паспорт он отдаёт — покупателю при продаже, сервису при
     * первом визите, страховой при разборе. Поэтому здесь есть то, чего в
     * отчёте нет: VIN, срок владения, история происшествий, состояние
     * регламентных работ на сегодня, документы и полисы.
     *
     * Всё берётся из базы прямо здесь: у паспорта восемь источников данных, и
     * протаскивать их восемью параметрами через экран — приглашение однажды
     * забыть один из них и молча отдать покупателю документ без половины
     * истории.
     *
     * @return готовый файл, либо null если такой машины нет
     */
    suspend fun exportVehiclePassport(carId: String): File? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val car = db.carDao().getCarById(carId) ?: return@withContext null

        val expenses = db.expenseDao().getExpensesByCarIdSync(carId)
        val reminders = db.maintenanceReminderDao().getRemindersByCarIdSync(carId)
        val documents = db.carDocumentDao().getDocumentsByCarIdSync(carId)
        val policies = db.insurancePolicyDao().getPoliciesForCarSync(carId)
        val incidents = db.carIncidentDao().getIncidentsByCarIdSync(carId)
        val tyres = db.tyreSetDao().getByCarIdSync(carId)

        val fileName = "CarCost_passport_${car.brand}_${car.model}_${System.currentTimeMillis()}.pdf"
            .replace(' ', '_')
        val file = File(context.getExternalFilesDir(null), fileName)

        val document = Document(PdfDocument(PdfWriter(file)), PageSize.A4)
        val regularFont = loadFont(R.raw.roboto_regular)
        val boldFont = loadFont(R.raw.roboto_bold)

        // Символ берётся из валюты машины, а не подставляется рублём: паспорт
        // машины, купленной в евро, с рублёвыми суммами вводил бы в заблуждение
        val currency = CurrencyUtils.symbol(car.currency)
        fun money(amount: Double) = "%.2f $currency".format(Locale.US, amount)

        fun heading(text: String) {
            document.add(
                Paragraph(text).setFont(boldFont).setFontSize(14f).setMarginTop(14f)
            )
        }

        fun body(text: String, size: Float = 10f) {
            document.add(Paragraph(text).setFont(regularFont).setFontSize(size))
        }

        fun rows(vararg pairs: Pair<String, String>) {
            val table = Table(UnitValue.createPercentArray(floatArrayOf(38f, 62f)))
                .useAllAvailableWidth()
            pairs.forEach { (label, value) ->
                table.addCell(Cell().add(Paragraph(label).setFont(boldFont).setFontSize(10f)))
                table.addCell(Cell().add(Paragraph(value).setFont(regularFont).setFontSize(10f)))
            }
            document.add(table)
        }

        // ── Обложка ─────────────────────────────────────────────────────────
        document.add(
            Paragraph("${car.brand} ${car.model}")
                .setFont(boldFont).setFontSize(26f).setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph(context.getString(R.string.passport_subtitle, car.year, car.licensePlate))
                .setFont(regularFont).setFontSize(13f).setTextAlignment(TextAlignment.CENTER)
        )

        // Фотография — украшение, а не содержание. Не открылась (файл удалён,
        // разрешение отозвано, формат не тот) — документ выходит без неё
        car.photoUri?.let { uri ->
            try {
                val bytes = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                    ?.use { it.readBytes() }
                if (bytes != null) {
                    document.add(
                        Image(ImageDataFactory.create(bytes))
                            .setAutoScale(true)
                            .setMaxHeight(260f)
                            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("ExportService", "Фото в паспорт не попало: ${e.message}")
            }
        }

        document.add(
            Paragraph(context.getString(R.string.passport_generated, dateFormat.format(Date())))
                .setFont(regularFont).setFontSize(9f).setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(10f)
        )

        // ── Основные сведения ───────────────────────────────────────────────
        heading(context.getString(R.string.passport_section_vehicle))
        val ownedDays = ((System.currentTimeMillis() - car.purchaseDate) / 86_400_000L)
            .coerceAtLeast(0)
        val ownedYears = ownedDays / 365
        val ownedMonths = (ownedDays % 365) / 30
        rows(
            context.getString(R.string.export_marka_i_model) to "${car.brand} ${car.model}",
            context.getString(R.string.export_god_vypuska) to car.year.toString(),
            context.getString(R.string.export_gos_nomer) to car.licensePlate,
            context.getString(R.string.passport_vin) to (car.vin ?: "—"),
            context.getString(R.string.passport_color) to (car.color ?: "—"),
            context.getString(R.string.export_tekuschiy_probeg) to
                context.getString(R.string.home_km, car.currentOdometer),
            context.getString(R.string.passport_purchase_date) to
                dateOnlyFormat.format(Date(car.purchaseDate)),
            context.getString(R.string.passport_owned_for) to
                context.getString(R.string.passport_years_months, ownedYears, ownedMonths)
        )

        // ── Деньги ──────────────────────────────────────────────────────────
        val total = expenses.sumOf { it.amount }
        val drivenSincePurchase = car.purchaseOdometer?.let { car.currentOdometer - it }
            ?.takeIf { it > 0 }
        heading(context.getString(R.string.passport_section_money))
        rows(
            *listOfNotNull(
                car.purchasePrice?.let {
                    context.getString(R.string.passport_purchase_price) to money(it)
                },
                context.getString(R.string.passport_spent_total) to money(total),
                context.getString(R.string.export_kol_vo_zapisey) to expenses.size.toString(),
                drivenSincePurchase?.let {
                    context.getString(R.string.passport_driven_by_owner) to
                        context.getString(R.string.home_km, it)
                },
                drivenSincePurchase?.takeIf { total > 0 }?.let {
                    context.getString(R.string.passport_cost_per_km) to money(total / it)
                }
            ).toTypedArray()
        )

        // Расход топлива считается по заправкам до полного бака, поэтому у
        // машины с двумя-тремя записями его просто нет — и это честнее, чем
        // средняя цифра из справочника
        FuelConsumptionCalculator.average(expenses)?.let { avg ->
            body(context.getString(R.string.passport_fuel_average, "%.1f".format(avg)))
        }

        // ── Обслуживание ────────────────────────────────────────────────────
        val service = expenses.filter {
            it.category == ExpenseCategory.MAINTENANCE || it.category == ExpenseCategory.REPAIR
        }.sortedByDescending { it.date }

        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        heading(context.getString(R.string.passport_section_service))
        if (service.isEmpty()) {
            body(context.getString(R.string.passport_nothing_recorded))
        } else {
            val table = Table(UnitValue.createPercentArray(floatArrayOf(14f, 13f, 13f, 30f, 30f)))
                .useAllAvailableWidth()
            listOf(
                R.string.cardetail_data,
                R.string.home_probeg,
                R.string.goals_summa,
                R.string.cardetail_opisanie,
                R.string.passport_works_and_shop
            ).forEach {
                table.addHeaderCell(
                    Cell().add(Paragraph(context.getString(it)).setFont(boldFont).setFontSize(9f))
                )
            }
            service.forEach { e ->
                fun cell(text: String) = Cell().add(
                    Paragraph(text).setFont(regularFont).setFontSize(9f)
                )
                table.addCell(cell(dateOnlyFormat.format(Date(e.date))))
                table.addCell(cell(context.getString(R.string.home_km, e.odometer)))
                table.addCell(cell(money(e.amount)))
                table.addCell(cell(e.description ?: "—"))
                table.addCell(
                    cell(
                        listOfNotNull(e.maintenanceParts, e.workshopName)
                            .joinToString(", ").ifBlank { "—" }
                    )
                )
            }
            document.add(table)
        }

        // ── Регламент на сегодня ────────────────────────────────────────────
        if (reminders.isNotEmpty()) {
            heading(context.getString(R.string.passport_section_upcoming))
            val table = Table(UnitValue.createPercentArray(floatArrayOf(40f, 20f, 20f, 20f)))
                .useAllAvailableWidth()
            listOf(
                R.string.export_tip_to,
                R.string.export_poslednyaya_km,
                R.string.export_sleduyuschaya_km,
                R.string.export_ostalos_km
            ).forEach {
                table.addHeaderCell(
                    Cell().add(Paragraph(context.getString(it)).setFont(boldFont).setFontSize(9f))
                )
            }
            reminders.forEach { r ->
                fun cell(text: String) = Cell().add(
                    Paragraph(text).setFont(regularFont).setFontSize(9f)
                )
                val remaining = r.nextChangeOdometer - car.currentOdometer
                table.addCell(cell(context.getString(r.type.displayNameRes)))
                table.addCell(cell(r.lastChangeOdometer.toString()))
                table.addCell(cell(r.nextChangeOdometer.toString()))
                // Просроченное показываем как просроченное, а не отрицательным
                // числом километров — покупатель должен это заметить
                table.addCell(
                    cell(
                        if (remaining < 0) {
                            context.getString(R.string.passport_overdue, -remaining)
                        } else {
                            context.getString(R.string.home_km, remaining)
                        }
                    )
                )
            }
            document.add(table)
        }

        // ── Шины ────────────────────────────────────────────────────────────
        if (tyres.isNotEmpty()) {
            heading(context.getString(R.string.passport_section_tyres))
            val table = Table(UnitValue.createPercentArray(floatArrayOf(34f, 20f, 20f, 26f)))
                .useAllAvailableWidth()
            listOf(
                R.string.tyres_field_name,
                R.string.tyres_field_size,
                R.string.passport_tyre_km,
                R.string.passport_tyre_state
            ).forEach {
                table.addHeaderCell(
                    Cell().add(Paragraph(context.getString(it)).setFont(boldFont).setFontSize(9f))
                )
            }
            tyres.forEach { t ->
                fun cell(text: String) = Cell().add(
                    Paragraph(text).setFont(regularFont).setFontSize(9f)
                )
                table.addCell(cell("${t.name} (${context.getString(t.season.labelRes)})"))
                table.addCell(cell(t.size ?: "—"))
                table.addCell(cell(context.getString(R.string.home_km, t.kmWith(car.currentOdometer))))
                table.addCell(
                    cell(
                        context.getString(
                            if (t.isInstalled) R.string.tyres_installed_now
                            else R.string.passport_tyre_stored
                        )
                    )
                )
            }
            document.add(table)
        }

        // ── Происшествия ────────────────────────────────────────────────────
        heading(context.getString(R.string.passport_section_incidents))
        if (incidents.isEmpty()) {
            // Отсутствие происшествий — самостоятельный факт, ради которого
            // покупатель этот документ и просит. Пропускать раздел нельзя
            body(context.getString(R.string.passport_no_incidents))
        } else {
            val table = Table(UnitValue.createPercentArray(floatArrayOf(15f, 20f, 45f, 20f)))
                .useAllAvailableWidth()
            listOf(
                R.string.cardetail_data,
                R.string.passport_incident_type,
                R.string.cardetail_opisanie,
                R.string.passport_repair_cost
            ).forEach {
                table.addHeaderCell(
                    Cell().add(Paragraph(context.getString(it)).setFont(boldFont).setFontSize(9f))
                )
            }
            incidents.sortedByDescending { it.date }.forEach { i ->
                fun cell(text: String) = Cell().add(
                    Paragraph(text).setFont(regularFont).setFontSize(9f)
                )
                table.addCell(cell(dateOnlyFormat.format(Date(i.date))))
                table.addCell(cell(context.getString(i.type.displayNameRes)))
                table.addCell(cell(i.description))
                table.addCell(cell(i.repairCost?.let { money(it) } ?: "—"))
            }
            document.add(table)
        }

        // ── Документы и страховки ───────────────────────────────────────────
        if (documents.isNotEmpty() || policies.isNotEmpty()) {
            heading(context.getString(R.string.passport_section_documents))
            val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 45f, 25f)))
                .useAllAvailableWidth()
            listOf(
                R.string.passport_doc_type,
                R.string.passport_doc_title,
                R.string.passport_doc_valid_until
            ).forEach {
                table.addHeaderCell(
                    Cell().add(Paragraph(context.getString(it)).setFont(boldFont).setFontSize(9f))
                )
            }
            fun cell(text: String) = Cell().add(
                Paragraph(text).setFont(regularFont).setFontSize(9f)
            )
            documents.forEach { d ->
                table.addCell(cell(context.getString(d.type.displayNameRes)))
                table.addCell(cell(d.title))
                table.addCell(cell(d.expiryDate?.let { dateOnlyFormat.format(Date(it)) } ?: "—"))
            }
            policies.forEach { pol ->
                val label = when (pol.type) {
                    "OSAGO" -> context.getString(R.string.insurance_osago)
                    "KASKO" -> context.getString(R.string.insurance_kasko)
                    else -> context.getString(R.string.profile_strahovka)
                }
                table.addCell(cell(label))
                table.addCell(
                    cell(listOf(pol.company, pol.policyNumber).filter { it.isNotBlank() }
                        .joinToString(", ").ifBlank { "—" })
                )
                table.addCell(cell(dateOnlyFormat.format(Date(pol.endDate))))
            }
            document.add(table)
        }

        document.add(
            Paragraph(context.getString(R.string.passport_footer))
                .setFont(regularFont).setFontSize(8f)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(20f)
        )

        document.close()
        file
    }

    /** Шрифт с кириллицей из res/raw */
    private fun loadFont(resId: Int): PdfFont {
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        return PdfFontFactory.createFont(FontProgramFactory.createFont(bytes))
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