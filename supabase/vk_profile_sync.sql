-- ════════════════════════════════════════════════════════════════════════════
--  Имя и фото профиля для входящих через ВКонтакте
--
--  Уже применено к боевой базе через MCP. Файл — для истории и восстановления
--  окружения с нуля.
-- ════════════════════════════════════════════════════════════════════════════

-- ── Задача первая: фото вообще не появлялось ────────────────────────────────
-- Edge Function vk-auth клала avatar_url в user_metadata и только при СОЗДАНИИ
-- пользователя. При повторных входах обновлялась одна vk_identities, а таблица
-- users не трогалась. Проверено на боевых данных: у VK-пользователя аватар в
-- vk_identities был, а users.photo_url оставался пустым.
--
-- Триггер выбран вместо правки функции потому, что чинит и уже вошедших, и не
-- зависит от того, какая версия функции развёрнута на проекте.

-- ── Задача вторая: не затирать выбор пользователя ───────────────────────────
-- Первая версия триггера ставила фото из ВК на КАЖДОМ входе. Человек загружал
-- своё фото в CarCost, а следующий вход через ВК возвращал вкшный аватар — со
-- стороны это выглядит как «приложение не запоминает фото».
--
-- Отличаем своё от вкшного без лишних колонок: в vk_identities лежит аватар,
-- известный на прошлый вход. Совпадает с текущим фото — значит его поставили
-- мы, можно обновить. Отличается — это выбор пользователя, не трогаем.

create or replace function public.sync_vk_profile_to_user()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  prev_avatar text;
begin
  -- При вставке прошлого аватара нет: заполняем только пустое место
  prev_avatar := case when tg_op = 'UPDATE' then old.avatar_url else null end;

  update public.users u
     set photo_url = case
           when u.photo_url is null or u.photo_url = '' then new.avatar_url
           when prev_avatar is not null and u.photo_url = prev_avatar then new.avatar_url
           else u.photo_url
         end,
         -- Имя тоже только в пустое: человек мог переименоваться в приложении
         display_name = coalesce(
           nullif(u.display_name, ''),
           nullif(trim(coalesce(new.first_name, '') || ' ' || coalesce(new.last_name, '')), '')
         )
   where u.id::text = new.user_id::text;

  return new;
end;
$$;

drop trigger if exists trg_sync_vk_profile on public.vk_identities;
create trigger trg_sync_vk_profile
  after insert or update of avatar_url, first_name, last_name
  on public.vk_identities
  for each row
  execute function public.sync_vk_profile_to_user();

-- Разовая заливка для вошедших до появления триггера
update public.users u
   set photo_url = vi.avatar_url,
       display_name = coalesce(
         nullif(u.display_name, ''),
         nullif(trim(coalesce(vi.first_name, '') || ' ' || coalesce(vi.last_name, '')), '')
       )
  from public.vk_identities vi
 where u.id::text = vi.user_id::text
   and vi.avatar_url is not null
   and vi.avatar_url <> ''
   and (u.photo_url is null or u.photo_url = '');

-- ── Кто ещё НЕ должен писать в эти поля ─────────────────────────────────────
-- Раньше их переписывали на каждом входе, затирая выбор пользователя:
--   • SupabaseAuthRepository.signInWithVk — upsert с photo_url из метаданных
--   • LoginViewModel / RegisterViewModel  — локальная запись из метаданных
--   • vk-auth/index.ts                    — upsert профиля
-- Все переведены на чтение из таблицы users; писать эти два поля вправе только
-- сам пользователь (через «Изменить фото») и этот триггер.

-- ── Проверено после применения, в откатываемой транзакции ───────────────────
--   вкшный аватар обновился на новый вкшный                     → да
--   фото, выбранное пользователем, пережило вход через ВК       → да
--   пользователей без фото при наличии аватара в vk_identities  → 0
