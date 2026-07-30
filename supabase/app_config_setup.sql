-- ════════════════════════════════════════════════════════════
--  app_config — таблица конфигурации / версий приложения
--  Выполнить один раз в Supabase SQL Editor
-- ════════════════════════════════════════════════════════════

-- 1. Создать таблицу (если её ещё нет)
create table if not exists public.app_config (
    id            text        primary key,          -- 'android'
    version_code  integer     not null default 1,   -- увеличивай при каждом релизе
    version_name  text        not null default '1.0.0',
    download_url  text        not null default '',  -- прямая ссылка на APK
    release_notes text        not null default '',  -- что нового (показывается в диалоге)
    force_update  boolean     not null default false, -- true = нельзя закрыть диалог
    updated_at    timestamptz not null default now()
);

-- 2. Добавить колонки если таблица уже существовала без них
alter table public.app_config
    add column if not exists download_url  text        not null default '',
    add column if not exists release_notes text        not null default '',
    add column if not exists force_update  boolean     not null default false,
    add column if not exists updated_at    timestamptz not null default now();

-- 3. Вставить начальную запись (поменяй значения под свою версию)
insert into public.app_config (id, version_code, version_name, download_url, release_notes, force_update)
values (
    'android',
    1,                              -- ← твой текущий versionCode из build.gradle
    '1.0.0',                        -- ← твой текущий versionName
    '',                             -- ← URL для скачивания APK (можно оставить пустым)
    '',                             -- ← описание релиза
    false
)
on conflict (id) do nothing;       -- не перезаписывать если уже есть

-- 4. Триггер: автоматически обновлять updated_at при каждом UPDATE
create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_app_config_updated_at on public.app_config;
create trigger trg_app_config_updated_at
    before update on public.app_config
    for each row execute function public.set_updated_at();

-- 5. RLS: все могут читать, никто кроме service_role не пишет
alter table public.app_config enable row level security;

drop policy if exists "app_config_read" on public.app_config;
create policy "app_config_read"
    on public.app_config for select
    using (true);          -- публично читаемая (анонимные пользователи тоже могут)

-- ════════════════════════════════════════════════════════════
--  КАК ВЫПУСТИТЬ НОВУЮ ВЕРСИЮ:
--
--  update public.app_config
--  set version_code  = 2,
--      version_name  = '1.1.0',
--      release_notes = '• Исправлен краш при входе\n• Новый экран аналитики',
--      download_url  = 'https://example.com/carcost-1.1.0.apk',
--      force_update  = false
--  where id = 'android';
--
--  → Webhook сработает автоматически → Edge Function разошлёт пуш всем
-- ════════════════════════════════════════════════════════════
