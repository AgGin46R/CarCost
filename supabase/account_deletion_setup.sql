-- ════════════════════════════════════════════════════════════
--  Удаление аккаунта: серверная часть
--
--  Уже применено к боевой базе. Файл — для истории и для
--  восстановления окружения с нуля.
--
--  Само удаление живёт не здесь, а в Edge Function
--  supabase/functions/delete-account/index.ts: снести строку в
--  auth.users можно только по service_role, а он не должен
--  попадать в приложение ни при каких условиях.
-- ════════════════════════════════════════════════════════════

-- ── Почему удаление вообще пришлось писать руками ────────────────────────────
-- Ни одна таблица в public не ссылается на auth.users внешним ключом. Проверено:
--
--   select tc.table_name, ccu.table_name as parent
--   from information_schema.table_constraints tc
--   join information_schema.constraint_column_usage ccu
--     on ccu.constraint_name = tc.constraint_name
--   where tc.constraint_type = 'FOREIGN KEY';
--
-- Единственный родитель — cars, и он каскадный. То есть удаление пользователя
-- само по себе не забирает ничего: остаются машины, расходы, сообщения, файлы.
-- Всё перечислено в Edge Function поимённо.

-- ── Сводка «что исчезнет» ────────────────────────────────────────────────────
-- Нужна, чтобы предупреждение перед удалением называло числа, а не общие слова.
-- Особенно важен other_participants: у машины каскадное удаление, и владелец,
-- удаляя аккаунт, стирает историю ещё и тем, кто ездит с ним вместе. Без этой
-- цифры он нажимает вслепую.
--
-- Считать на клиенте нельзя: участников чужих машин он через RLS не видит.
--
-- Параметров нет вовсе — чей аккаунт считать, берётся из auth.uid(), подставить
-- чужой id невозможно.
create or replace function public.account_deletion_summary()
returns json
language sql
security definer
set search_path to 'public'
as $$
  with own as (
    select id from cars where user_id = auth.uid()::text
  )
  select json_build_object(
    'owned_cars', (select count(*) from own),
    'expenses',   (select count(*) from expenses where car_id in (select id from own)),
    'shared_cars', (
      select count(*) from own o
      where exists (
        select 1 from car_members m
        where m.car_id = o.id and m.user_id <> auth.uid()::text
      )
    ),
    'other_participants', (
      select count(distinct m.user_id) from car_members m
      where m.car_id in (select id from own) and m.user_id <> auth.uid()::text
    )
  );
$$;

revoke all on function public.account_deletion_summary() from public;
revoke execute on function public.account_deletion_summary() from anon;
grant execute on function public.account_deletion_summary() to authenticated;

-- ── Проверка ────────────────────────────────────────────────────────────────
-- set_config и вызов обязаны быть в ОДНОМ операторе: при is_local = true
-- настройка живёт до конца транзакции, а каждый отдельный запрос выполняется
-- в своей.
--
--   with impersonate as (
--     select set_config('role','authenticated',true),
--            set_config('request.jwt.claims', '{"sub":"<uuid>"}', true)
--   )
--   select (select 1 from impersonate), public.account_deletion_summary();
--
-- На боевых данных вернуло:
--   {"owned_cars":4,"expenses":18,"shared_cars":3,"other_participants":2}
