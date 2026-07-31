-- ════════════════════════════════════════════════════════════
--  Роли участников на уровне базы + приём приглашения по токену
--
--  Уже применено к боевой базе через MCP. Файл — для истории и
--  для восстановления окружения с нуля.
-- ════════════════════════════════════════════════════════════

-- ── Почему понадобились новые хелперы ────────────────────────────────────────
-- Существующие is_car_member / is_car_owner смотрят только в car_members. А строка
-- участника создаётся лишь когда владелец откроет экран «Участники»
-- (ensureOwnerRegistered). У всех остальных её нет.
--
-- Последствие было живым багом: политики maintenance_reminders опирались на голый
-- is_car_member, поэтому создатель автомобиля НЕ ВИДЕЛ собственные напоминания о ТО
-- и не мог их синхронизировать. Проверено на боевых данных: у автомобиля
-- 691e5013 напоминание в таблице есть, а под ролью создателя select возвращал 0.
--
-- Новые хелперы считают создателя автомобиля владельцем независимо от car_members.

create or replace function public.is_car_participant(p_car_id text)
returns boolean language sql security definer set search_path to 'public' as $$
  select exists (
    select 1 from cars where id = p_car_id and user_id = auth.uid()::text
  ) or exists (
    select 1 from car_members where car_id = p_car_id and user_id = auth.uid()::text
  );
$$;

create or replace function public.can_manage_car(p_car_id text)
returns boolean language sql security definer set search_path to 'public' as $$
  select exists (
    select 1 from cars where id = p_car_id and user_id = auth.uid()::text
  ) or exists (
    select 1 from car_members
    where car_id = p_car_id and user_id = auth.uid()::text and role = 'OWNER'
  );
$$;

-- Сейчас в политиках не используется: ролевые ограничения на запись откачены.
-- Оставлена на случай, если разграничение понадобится вернуть.
create or replace function public.can_service_car(p_car_id text)
returns boolean language sql security definer set search_path to 'public' as $$
  select public.can_manage_car(p_car_id) or exists (
    select 1 from car_members
    where car_id = p_car_id and user_id = auth.uid()::text and role = 'MECHANIC'
  );
$$;

-- ── Матрица прав ─────────────────────────────────────────────────────────────
--   всё, что касается ведения машины  — любой участник
--   участники и удаление автомобиля   — только владелец
--
-- Ролевые ограничения на запись были и откачены: в общей машине запись добавляет
-- тот, кто сейчас на сервисе или заправке, а не тот, у кого нужная роль. Отказы
-- при этом молча проглатывались приложением, и запись оставалась только на
-- устройстве. Роли остались пометкой «кто чем занимается».

-- ТО
drop policy if exists "reminders_select_members" on public.maintenance_reminders;
drop policy if exists "reminders_insert_members" on public.maintenance_reminders;
drop policy if exists "reminders_update_members" on public.maintenance_reminders;
drop policy if exists "reminders_delete_members" on public.maintenance_reminders;

create policy "reminders_select" on public.maintenance_reminders
    for select using (is_car_participant(car_id));
create policy "reminders_insert" on public.maintenance_reminders
    for insert with check (is_car_participant(car_id));
create policy "reminders_update" on public.maintenance_reminders
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "reminders_delete" on public.maintenance_reminders
    for delete using (is_car_participant(car_id));

-- Документы
drop policy if exists "Members can manage car_documents" on public.car_documents;
create policy "documents_select" on public.car_documents
    for select using (is_car_participant(car_id));
create policy "documents_insert" on public.car_documents
    for insert with check (is_car_participant(car_id));
create policy "documents_update" on public.car_documents
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "documents_delete" on public.car_documents
    for delete using (is_car_participant(car_id));

-- Страховки
drop policy if exists "Members can manage insurance_policies" on public.insurance_policies;
create policy "insurance_select" on public.insurance_policies
    for select using (is_car_participant(car_id));
create policy "insurance_insert" on public.insurance_policies
    for insert with check (is_car_participant(car_id));
create policy "insurance_update" on public.insurance_policies
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "insurance_delete" on public.insurance_policies
    for delete using (is_car_participant(car_id));

-- Жидкости
drop policy if exists "Members can manage fluid levels" on public.fluid_levels;
create policy "fluids_select" on public.fluid_levels
    for select using (is_car_participant(car_id));
create policy "fluids_insert" on public.fluid_levels
    for insert with check (is_car_participant(car_id));
create policy "fluids_update" on public.fluid_levels
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "fluids_delete" on public.fluid_levels
    for delete using (is_car_participant(car_id));

-- Бюджеты и цели — деньги, ими распоряжается владелец
drop policy if exists "Members can manage category_budgets" on public.category_budgets;
create policy "budgets_select" on public.category_budgets
    for select using (is_car_participant(car_id));
create policy "budgets_insert" on public.category_budgets
    for insert with check (is_car_participant(car_id));
create policy "budgets_update" on public.category_budgets
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "budgets_delete" on public.category_budgets
    for delete using (is_car_participant(car_id));

drop policy if exists "Members can manage savings_goals" on public.savings_goals;
create policy "goals_select" on public.savings_goals
    for select using (is_car_participant(car_id));
create policy "goals_insert" on public.savings_goals
    for insert with check (is_car_participant(car_id));
create policy "goals_update" on public.savings_goals
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "goals_delete" on public.savings_goals
    for delete using (is_car_participant(car_id));

-- Инциденты и поездки остаются общими, но переводятся на creator-aware хелпер,
-- иначе у создателя без строки в car_members была бы та же дыра, что была с ТО
drop policy if exists "Members can manage car_incidents" on public.car_incidents;
create policy "incidents_all" on public.car_incidents
    for all using (is_car_participant(car_id)) with check (is_car_participant(car_id));

drop policy if exists "Members can manage gps_trips" on public.gps_trips;
create policy "trips_all" on public.gps_trips
    for all using (is_car_participant(car_id)) with check (is_car_participant(car_id));

-- ── Приём приглашения ────────────────────────────────────────────────────────
-- Клиентским кодом это сделать нельзя: чтобы политика пустила, она должна знать
-- про токен, а токен — как раз то, что предъявляет клиент. Отсюда SECURITY DEFINER.
--
-- Приглашения по ссылке создаются с пустым invited_email, и прежний путь через
-- сверку с auth.email() для них не работал вовсе.
--
-- Возвращает json, а не таблицу: при RETURNS TABLE(car_id ...) имя выходной колонки
-- конфликтует с car_members.car_id в on conflict — «column reference is ambiguous».
--
-- Код приглашения короткий (8 символов) и вводится руками, поэтому регистр,
-- дефисы и пробелы значения не имеют. Точное совпадение проверяется первым —
-- оно покрывает старые приглашения с UUID-токенами.

drop function if exists public.accept_invitation(text);

create or replace function public.accept_invitation(p_token text)
returns json
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_inv   car_invitations%rowtype;
    v_uid   text := auth.uid()::text;
    v_email text := coalesce(auth.email(), '');
    v_now   bigint := (extract(epoch from now()) * 1000)::bigint;
    v_norm  text := upper(regexp_replace(coalesce(p_token, ''), '[^a-zA-Z0-9]', '', 'g'));
begin
    if v_uid is null or v_uid = '' then
        raise exception 'not_authenticated';
    end if;
    if v_norm = '' then
        raise exception 'invitation_not_found';
    end if;

    select * into v_inv from car_invitations where token = p_token;
    if not found then
        select * into v_inv from car_invitations
        where upper(regexp_replace(token, '[^a-zA-Z0-9]', '', 'g')) = v_norm;
    end if;
    if not found then
        raise exception 'invitation_not_found';
    end if;
    if v_inv.accepted_at is not null then
        raise exception 'invitation_already_used';
    end if;
    if v_inv.expires_at < v_now then
        raise exception 'invitation_expired';
    end if;

    insert into car_members (car_id, user_id, email, role, joined_at)
    values (v_inv.car_id, v_uid, v_email, v_inv.role, v_now)
    on conflict (car_id, user_id) do update set role = excluded.role;

    update car_invitations set accepted_at = v_now where token = v_inv.token;

    return json_build_object('car_id', v_inv.car_id, 'role', v_inv.role);
end;
$$;

revoke all on function public.accept_invitation(text) from public;
grant execute on function public.accept_invitation(text) to authenticated;
