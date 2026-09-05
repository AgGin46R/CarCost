-- ════════════════════════════════════════════════════════════
--  Комплекты шин
--
--  Таблица под сущность TyreSet из приложения (миграция Room 42→43).
--  Имена колонок в snake_case — как во всех остальных таблицах,
--  сопоставление с полями сущности живёт в SupabaseTyreSetRepository.
--
--  Применено к боевой базе 5 сентября 2026 года. Файл — для истории и
--  для восстановления окружения с нуля.
-- ════════════════════════════════════════════════════════════

create table if not exists public.tyre_sets (
    id                    text primary key,
    car_id                text not null references public.cars(id) on delete cascade,
    name                  text not null,
    season                text not null default 'SUMMER',
    size                  text,
    purchase_date         bigint,
    purchase_price        double precision,
    total_km              integer not null default 0,
    installed_at_odometer integer,
    is_installed          boolean not null default false,
    storage_location      text,
    notes                 text,
    photo_uri             text,
    expected_life_km      integer,
    created_at            bigint not null,
    updated_at            bigint not null
);

create index if not exists idx_tyre_sets_car_id on public.tyre_sets(car_id);

-- Уникального индекса «один установленный комплект на машину» здесь
-- сознательно нет.
--
-- Он выглядит правильным, но означал бы отклонённую запись: два устройства
-- отправляют смену комплекта в произвольном порядке, и тот, кто приехал
-- первым с новым комплектом, получал бы отказ, пока второй не снял старый.
-- Отказ приложение засчитывает как неудачу синхронизации и повторяет — то
-- есть застревает. Одновременно отмеченные два комплекта — временное
-- расхождение, которое следующая синхронизация сводит сама; застрявшая
-- синхронизация не чинится ничем.

alter table public.tyre_sets enable row level security;

-- Права — как у остальных данных по ведению машины: любой участник.
-- Шины ставит тот, кто сейчас в шиномонтаже, а не тот, у кого нужная роль.
drop policy if exists "tyres_select" on public.tyre_sets;
drop policy if exists "tyres_insert" on public.tyre_sets;
drop policy if exists "tyres_update" on public.tyre_sets;
drop policy if exists "tyres_delete" on public.tyre_sets;

create policy "tyres_select" on public.tyre_sets
    for select using (is_car_participant(car_id));
create policy "tyres_insert" on public.tyre_sets
    for insert with check (is_car_participant(car_id));
create policy "tyres_update" on public.tyre_sets
    for update using (is_car_participant(car_id)) with check (is_car_participant(car_id));
create policy "tyres_delete" on public.tyre_sets
    for delete using (is_car_participant(car_id));

-- Полная строка в потоке изменений — иначе удаление приходит без car_id,
-- и подписка не понимает, к какой машине оно относилось
alter table public.tyre_sets replica identity full;
