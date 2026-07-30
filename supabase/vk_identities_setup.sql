-- ════════════════════════════════════════════════════════════
--  vk_identities — маппинг VK ID → auth.users
--  Выполнить один раз в Supabase SQL Editor
--
--  Таблицу заполняет только Edge Function vk-auth (service_role).
--  Клиент может лишь прочитать свою собственную запись.
-- ════════════════════════════════════════════════════════════

create table if not exists public.vk_identities (
    vk_user_id    bigint      primary key,                                   -- id пользователя во ВКонтакте
    user_id       uuid        not null references auth.users(id) on delete cascade,
    vk_email      text,                                                      -- реальный email из VK (может быть null)
    first_name    text,
    last_name     text,
    avatar_url    text,
    created_at    timestamptz not null default now(),
    last_login_at timestamptz not null default now()
);

-- Один VK-аккаунт ↔ один пользователь Supabase
create unique index if not exists vk_identities_user_id_uq
    on public.vk_identities(user_id);

alter table public.vk_identities enable row level security;

-- Пользователь видит только свою запись.
-- Политик на INSERT/UPDATE/DELETE нет вообще → anon и authenticated писать не могут.
-- service_role обходит RLS целиком, поэтому Edge Function работает без политик.
drop policy if exists "vk_identities_self_read" on public.vk_identities;
create policy "vk_identities_self_read"
    on public.vk_identities for select
    using (auth.uid() = user_id);

-- ════════════════════════════════════════════════════════════
--  ПРЕДУСЛОВИЯ ДЛЯ РАБОТЫ VK-ВХОДА:
--
--  1. dev.vk.com → создать приложение, платформа Android
--     • package name:  com.aggin.carcost
--     • SHA-256 отпечатки ОБОИХ keystore (debug и release)
--     • Redirect URI:  vk<client_id>://vk.ru/blank.html
--     • Разрешить scope email
--
--  2. Секреты Edge Function:
--     supabase secrets set VK_CLIENT_ID=xxx VK_CLIENT_SECRET=yyy \
--         --project-ref <project-ref>
--
--  3. Деплой (--no-verify-jwt обязателен: вызывающий не аутентифицирован):
--     supabase functions deploy vk-auth --no-verify-jwt --project-ref <project-ref>
--
--  4. Auth → Providers → Email должен быть включён:
--     admin.generateLink({type:'magiclink'}) вернёт 422 если его выключить.
--
--  5. local.properties:
--     vk.client_id=...
--     vk.client_secret=...
-- ════════════════════════════════════════════════════════════
