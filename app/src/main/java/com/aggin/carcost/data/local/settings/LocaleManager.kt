package com.aggin.carcost.data.local.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Язык интерфейса.
 *
 * Выбор языка хранится отдельно от системного и применяется подменой
 * конфигурации у контекста — и у activity, и у Application. Второе важнее, чем
 * кажется: уведомления, напоминания о ТО и еженедельная сводка собираются в
 * фоновых задачах, у которых своего activity нет. Без подмены на уровне
 * приложения экраны переводились бы, а уведомления приходили по-русски.
 *
 * Системная настройка «язык приложения» (Android 13 и новее) остаётся рабочей:
 * когда свой выбор не сделан, берётся язык системы, и приложение объявляет
 * поддерживаемые языки в locales_config.xml.
 *
 * Хранилище здесь — SharedPreferences, а не DataStore, которым пользуется
 * остальное приложение: значение нужно прочитать синхронно в attachBaseContext,
 * до того как появится хоть какая-то корутина.
 */
object LocaleManager {

    private const val PREFS = "locale_prefs"
    private const val KEY_LANGUAGE = "app_language"

    /** Язык системы — значение по умолчанию */
    const val SYSTEM = ""

    /**
     * Поддерживаемые языки.
     *
     * Название каждого написано на нём самом: человек, ищущий свой язык в
     * списке, ищет знакомое слово, а не его перевод на язык, которого он может
     * не знать.
     */
    val supported: List<Language> = listOf(
        Language(SYSTEM, null),
        Language("ru", "Русский"),
        Language("en", "English"),
        Language("be", "Беларуская"),
        Language("kk", "Қазақша")
    )

    /**
     * @param nativeName название языка на нём самом. У пункта «как в системе»
     *   своего языка нет — его подпись берётся из ресурсов и переводится.
     */
    data class Language(val tag: String, val nativeName: String?)

    /**
     * Настройки языка.
     *
     * НЕ через applicationContext: этот метод вызывается из attachBaseContext
     * у Application, а там приложение ещё не присоединено и applicationContext
     * равен null — обращение к нему роняло запуск. Базовый контекст, который
     * система передаёт в attachBaseContext, к настройкам обращаться уже умеет.
     */
    private fun prefs(context: Context) =
        (context.applicationContext ?: context)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Выбранный язык: тег вида «ru», либо пустая строка для системного */
    fun current(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM

    /**
     * Запоминает выбор. Применяется он пересозданием activity — подмена
     * конфигурации на живом контексте до уже отрисованных экранов не доходит.
     */
    fun setLanguage(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /**
     * Контекст с нужным языком. Вызывается из attachBaseContext.
     *
     * Всё тело под перехватом намеренно: это самая ранняя точка запуска
     * приложения, до которой не доходит ни один обработчик ошибок. Любой сбой
     * здесь — и приложение не стартует вовсе, показав системное «приложение
     * остановлено». Язык интерфейса такой цены не стоит: не получилось —
     * работаем на языке системы.
     */
    fun wrap(base: Context): Context = try {
        val tag = current(base)
        if (tag == SYSTEM) {
            base
        } else {
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)

            val configuration = Configuration(base.resources.configuration)
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            base.createConfigurationContext(configuration)
        }
    } catch (e: Exception) {
        android.util.Log.w("LocaleManager", "Не удалось применить язык: ${e.message}")
        base
    }

    /** Название выбранного языка для показа в настройках */
    fun currentName(context: Context): String =
        supported.firstOrNull { it.tag == current(context) }?.nativeName
            ?: context.getString(com.aggin.carcost.R.string.language_system)
}
