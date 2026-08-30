package com.aggin.carcost.data.local.database.entities

import androidx.annotation.StringRes
import com.aggin.carcost.R

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AchievementType(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String
) {
    FIRST_EXPENSE(R.string.ach_pervyy_rashod, R.string.achd_dobavte_pervyy_rashod, "🎉"),
    EXPENSES_10(R.string.ach_10_rashodov, R.string.achd_dobavte_10_rashodov, "📝"),
    EXPENSES_50(R.string.ach_poltinnik, R.string.achd_dobavte_50_rashodov, "🏅"),
    EXPENSES_100(R.string.ach_sotnya_zapisey, R.string.achd_dobavte_100_rashodov, "💯"),
    ECO_DRIVER(R.string.ach_eko_voditel, R.string.achd_rashod_topliva_nizhe_srednego_3, "🌿"),
    BUDGET_MASTER(R.string.ach_master_byudzheta, R.string.achd_ne_prevyste_byudzhet_3_mesyatsa_podryad, "💰"),
    REGULAR_MAINTENANCE(R.string.ach_pedant_to, R.string.achd_proydite_5_planovyh_to_vovremya, "🔧"),
    FIRST_DOCUMENT(R.string.ach_arhivarius, R.string.achd_dobavte_pervyy_dokument, "📄"),
    TRIP_TRACKER(R.string.ach_gps_treker, R.string.achd_zapishite_pervuyu_poezdku_po_gps, "📍"),
    SAVINGS_GOAL_COMPLETE(R.string.ach_tsel_dostignuta, R.string.achd_dostignite_pervoy_tseli_nakopleniya, "🏆"),
    YEAR_OWNER(R.string.ach_god_s_nami, R.string.achd_ispolzuyte_prilozhenie_god, "🎂"),
    // Новые достижения
    MULTI_CAR(R.string.ach_avtopark, R.string.achd_dobavte_2_i_bolee_avtomobilya, "🚘"),
    FUEL_VETERAN(R.string.ach_zavsegdatay_azs, R.string.achd_zapravtes_20_raz, "⛽"),
    NIGHT_DRIVER(R.string.ach_nochnoy_voditel, R.string.achd_dobavte_rashod_posle_23_00, "🌙"),
    PHOTO_COLLECTOR(R.string.ach_fotograf, R.string.achd_prikrepite_chek_k_10_rashodam, "📸"),
    WORKSHOP_REGULAR(R.string.ach_postoyannyy_klient, R.string.achd_posetite_odin_avtoservis_5_raz, "🔩"),
    HIGH_MILEAGE(R.string.ach_stotysyachnik, R.string.achd_probeg_avto_dostig_100_000_km, "💫")
}

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val type: AchievementType,
    val unlockedAt: Long = System.currentTimeMillis(),
    val metadata: String? = null
)
