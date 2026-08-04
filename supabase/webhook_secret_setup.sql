-- ════════════════════════════════════════════════════════════
--  x-webhook-secret для вызовов Edge Functions из БД
--
--  Функции send-push-notification и send-version-push работают под
--  service_role и обходят RLS. Раньше они не проверяли вообще ничего:
--  любой POST на send-version-push рассылал уведомление на ВСЕ устройства.
--
--  Вызывают их не Database Webhooks из дашборда, а вот эти две
--  SECURITY DEFINER функции через net.http_post.
--
--  ПЕРЕД ЗАПУСКОМ: замените __WEBHOOK_SECRET__ на реальное значение
--  (три вхождения) и убедитесь, что ровно оно же лежит в секретах
--  Edge Functions под именем WEBHOOK_SECRET.
--
--  ПОРЯДОК ДЕЙСТВИЙ:
--    1. Этот SQL (старые версии функций лишний заголовок игнорируют — ничего не ломается)
--    2. Секрет WEBHOOK_SECRET в Project Settings → Edge Functions → Secrets
--    3. Только теперь деплой новых версий обеих функций
--
--  ВНИМАНИЕ: notify_push глушит любые ошибки (EXCEPTION WHEN OTHERS THEN NULL),
--  поэтому при несовпадении секрета пуши умрут МОЛЧА. Проверять по логам функции.
--
--  ── После переезда на свой сервер ───────────────────────────────────────────
--  Адрес сменился с https://<ref>.supabase.co на http://kong:8000 — это имя
--  шлюза во внутренней сети docker. Запрос больше не выходит в интернет, чтобы
--  вернуться обратно: не зависит ни от сертификата, ни от того, резолвится ли
--  домен, и не считается внешним трафиком.
--
--  anon-ключ тоже сменился: облачный выписан другим проектом и здесь
--  недействителен. Подставлять актуальный из /opt/supabase/.env (ANON_KEY).
-- ════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION public.notify_app_update()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    PERFORM net.http_post(
        url     := 'http://kong:8000/functions/v1/send-version-push',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'apikey',       '__ANON_KEY__',
            'x-webhook-secret', '__WEBHOOK_SECRET__'
        ),
        body := jsonb_build_object(
            'type',   'UPDATE',
            'table',  'app_config',
            'record', row_to_json(NEW)::jsonb
        )
    );
    RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.notify_push(
    p_car_id text,
    p_sender_user_id text,
    p_title text,
    p_body text,
    p_table text,
    p_event_type text
)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
BEGIN
    PERFORM net.http_post(
        url := 'http://kong:8000/functions/v1/send-push-notification',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'Authorization', 'Bearer __ANON_KEY__',
            'x-webhook-secret', '__WEBHOOK_SECRET__'
        ),
        body := jsonb_build_object(
            'car_id', p_car_id,
            'sender_user_id', p_sender_user_id,
            'title', p_title,
            'body', p_body,
            'table', p_table,
            'event_type', p_event_type
        )
    );
EXCEPTION WHEN OTHERS THEN
    NULL; -- не блокируем DB операции если push упал
END;
$function$;
