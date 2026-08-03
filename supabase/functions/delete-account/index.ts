/**
 * delete-account — Supabase Edge Function
 *
 * Удаляет аккаунт вместе со всеми данными. Клиентским кодом это невозможно:
 * удаление пользователя из auth.users доступно только по service_role, а он
 * никогда не должен попадать в приложение.
 *
 * Кто удаляется — берётся ИСКЛЮЧИТЕЛЬНО из JWT в заголовке Authorization,
 * никогда из тела запроса. Иначе любой авторизованный пользователь смог бы
 * стереть чужой аккаунт, подставив чужой id.
 *
 * ── Почему нельзя просто удалить строку в auth.users ─────────────────────────
 * Ни одна таблица в public не ссылается на auth.users внешним ключом (проверено
 * по information_schema). Удаление пользователя не заберёт за собой ничего —
 * останутся машины, расходы, сообщения и файлы, просто без владельца. Поэтому
 * всё перечислено здесь руками.
 *
 * ── Что с общими машинами ────────────────────────────────────────────────────
 * Машины владельца удаляются целиком, вместе с историей всех участников: у cars
 * каскадное удаление на все дочерние таблицы. Это осознанное решение владельца
 * приложения; интерфейс обязан предупредить об этом до нажатия, назвав число
 * затронутых людей.
 *
 * ── Что с записями в ЧУЖИХ машинах ───────────────────────────────────────────
 * Расходы и сообщения, которые удаляемый оставил в машине другого человека, не
 * удаляются: иначе у постороннего молча изменились бы суммы в его собственной
 * машине. Все user_id объявлены NOT NULL, обнулить нельзя, поэтому авторство
 * переписывается на «удалённого пользователя».
 *
 * Деплой (verify_jwt включён):
 *   supabase functions deploy delete-account --project-ref <ref>
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl        = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const supabaseAnonKey    = Deno.env.get('SUPABASE_ANON_KEY')!

const admin = createClient(supabaseUrl, supabaseServiceKey, {
  auth: { autoRefreshToken: false, persistSession: false },
})

/** Автор, которого больше нет. Приложение показывает такие записи как «Удалённый пользователь». */
const TOMBSTONE_USER_ID = '00000000-0000-0000-0000-000000000000'

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const JSON_HEADERS = { ...CORS_HEADERS, 'Content-Type': 'application/json' }

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS })
}

/**
 * Устанавливает личность строго по токену.
 *
 * Тело запроса не читается вообще: у этой операции нет параметров, которые
 * пользователю было бы позволено задать.
 */
async function resolveCaller(authHeader: string | null): Promise<{ id: string; email: string | null } | null> {
  if (!authHeader?.startsWith('Bearer ')) return null

  const token = authHeader.slice('Bearer '.length).trim()
  if (!token || token.length > 4096) return null

  const asUser = createClient(supabaseUrl, supabaseAnonKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { autoRefreshToken: false, persistSession: false },
  })

  const { data, error } = await asUser.auth.getUser()
  if (error || !data?.user) return null

  return { id: data.user.id, email: data.user.email ?? null }
}

/**
 * Удаляет файлы пачками.
 *
 * Ошибки хранилища намеренно не прерывают удаление: осиротевший файл — это
 * мусор, а прерванное на середине удаление оставляет человека с наполовину
 * стёртым аккаунтом, в который уже нельзя нормально войти.
 */
async function removeFiles(bucket: string, paths: string[]): Promise<number> {
  if (paths.length === 0) return 0

  let removed = 0
  for (let i = 0; i < paths.length; i += 100) {
    const chunk = paths.slice(i, i + 100)
    const { error } = await admin.storage.from(bucket).remove(chunk)
    if (error) {
      console.error(`storage cleanup failed: ${bucket}`, error.message)
    } else {
      removed += chunk.length
    }
  }
  return removed
}

/** Все файлы внутри папки — для бакетов, где файлы разложены по id пользователя или машины */
async function listFolder(bucket: string, folder: string): Promise<string[]> {
  const { data, error } = await admin.storage.from(bucket).list(folder, { limit: 1000 })
  if (error || !data) return []
  return data.map((f) => `${folder}/${f.name}`)
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: CORS_HEADERS })
  if (req.method !== 'POST') return jsonResponse({ error: 'method_not_allowed' }, 405)

  const caller = await resolveCaller(req.headers.get('Authorization'))
  if (!caller) return jsonResponse({ error: 'unauthorized' }, 401)

  const userId = caller.id
  console.log(`delete-account: начинаю для ${userId}`)

  try {
    // ── 1. Сначала СОБИРАЕМ идентификаторы ────────────────────────────────────
    // Обязательно до удаления строк: пути к файлам в хранилище строятся из id
    // машин, расходов и инцидентов, а после каскадного удаления их будет негде взять.
    const { data: ownCars } = await admin.from('cars').select('id').eq('user_id', userId)
    const carIds: string[] = (ownCars ?? []).map((c: { id: string }) => c.id)

    let expenseIds: string[] = []
    let incidentIds: string[] = []
    if (carIds.length > 0) {
      const { data: exp } = await admin.from('expenses').select('id').in('car_id', carIds)
      expenseIds = (exp ?? []).map((e: { id: string }) => e.id)

      const { data: inc } = await admin.from('car_incidents').select('id').in('car_id', carIds)
      incidentIds = (inc ?? []).map((i: { id: string }) => i.id)
    }

    // ── 2. Файлы ──────────────────────────────────────────────────────────────
    const filesRemoved =
      (await removeFiles('avatars', await listFolder('avatars', userId))) +
      (await removeFiles('bug-reports', await listFolder('bug-reports', `bug_reports/${userId}`))) +
      (await removeFiles('car-photos', carIds.map((id) => `${id}.jpg`))) +
      (await removeFiles('car-photos', incidentIds.map((id) => `incidents/${id}.jpg`))) +
      (await removeFiles('receipts', expenseIds.map((id) => `${id}.jpg`))) +
      (await removeFiles(
        'chat_media',
        (await Promise.all(carIds.map((id) => listFolder('chat_media', id)))).flat()
      ))

    // ── 3. Машины ─────────────────────────────────────────────────────────────
    // Каскад унесёт расходы, ТО, документы, страховки, жидкости, бюджеты, цели,
    // поездки, инциденты, участников, приглашения и чат — по всем машинам сразу.
    if (carIds.length > 0) {
      const { error } = await admin.from('cars').delete().eq('user_id', userId)
      if (error) throw new Error(`не удалось удалить автомобили: ${error.message}`)
    }

    // ── 4. Следы в ЧУЖИХ машинах ──────────────────────────────────────────────
    // Здесь именно переписывание авторства, а не удаление: это данные другого
    // человека о его машине, и трогать их суммы мы не вправе.
    for (const table of ['expenses', 'chat_messages', 'maintenance_reminders', 'planned_expenses']) {
      const { error } = await admin.from(table).update({ user_id: TOMBSTONE_USER_ID }).eq('user_id', userId)
      if (error) console.error(`не удалось обезличить ${table}`, error.message)
    }

    // Реакции в чате обезличивать нельзя: на (message_id, user_id) стоит уникальность,
    // и томбстоун схлопнул бы реакции разных удалённых людей в одну
    await admin.from('chat_reactions').delete().eq('user_id', userId)

    // ── 5. Личные строки ──────────────────────────────────────────────────────
    for (const table of ['achievements', 'expense_tags', 'user_push_tokens', 'car_members']) {
      const { error } = await admin.from(table).delete().eq('user_id', userId)
      if (error) console.error(`не удалось очистить ${table}`, error.message)
    }
    await admin.from('vk_identities').delete().eq('user_id', userId)
    await admin.from('users').delete().eq('id', userId)

    // Приглашения, выписанные на его почту в чужие машины: сама почта — тоже
    // персональные данные, и оставлять её висеть после удаления аккаунта нельзя
    if (caller.email) {
      await admin.from('car_invitations').delete().eq('invited_email', caller.email)
    }

    // ── 6. Сам аккаунт — последним ────────────────────────────────────────────
    // Если упадёт что-то выше, человек ещё может войти и повторить попытку.
    // Обратный порядок оставил бы его без входа и с половиной данных на сервере.
    const { error: authError } = await admin.auth.admin.deleteUser(userId)
    if (authError) throw new Error(`не удалось удалить пользователя: ${authError.message}`)

    console.log(`delete-account: готово для ${userId}, машин ${carIds.length}, файлов ${filesRemoved}`)
    return jsonResponse({ deleted: true, cars: carIds.length, files: filesRemoved }, 200)
  } catch (e) {
    console.error('delete-account failed', e)
    return jsonResponse({ error: 'delete_failed', message: String(e) }, 500)
  }
})
