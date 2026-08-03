/**
 * vk-auth — Supabase Edge Function
 *
 * Мост между VK ID и Supabase Auth: у GoTrue нет провайдера VK,
 * поэтому вход через ВКонтакте делается так:
 *
 *   1. Android получает VK access_token через VK ID SDK
 *   2. POST сюда { vk_access_token, device_id }
 *   3. Мы проверяем токен в VK (id.vk.ru/oauth2/user_info с нашим client_id —
 *      токен, выпущенный для чужого приложения, будет отвергнут)
 *   4. Находим или создаём пользователя в auth.users
 *   5. Возвращаем hashed_token магической ссылки
 *   6. Android зовёт supabase.auth.verifyEmailOtp(MAGIC_LINK, tokenHash) —
 *      GoTrue сам создаёт и сохраняет сессию
 *
 * Деплой (--no-verify-jwt обязателен — вызывающий не аутентифицирован):
 *   supabase functions deploy vk-auth --no-verify-jwt --project-ref <ref>
 *
 * Секреты (Dashboard → Settings → Edge Functions → Secrets):
 *   VK_CLIENT_ID     — App ID приложения на dev.vk.com
 *   VK_CLIENT_SECRET — «Защищённый ключ» приложения
 *
 * ВАЖНО: vk_user_id и email берутся ТОЛЬКО из ответа VK.
 * Всё, что пришло в теле запроса кроме самого токена, игнорируется.
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl        = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const vkClientId         = Deno.env.get('VK_CLIENT_ID')!

const supabase = createClient(supabaseUrl, supabaseServiceKey, {
  auth: { autoRefreshToken: false, persistSession: false },
})

const VK_USER_INFO_URL = 'https://id.vk.ru/oauth2/user_info'
const VK_API_VERSION   = '5.220'

/** Домен для синтетических адресов пользователей без email в VK */
const SYNTHETIC_EMAIL_DOMAIN = 'vk.carcost.app'

const MAX_BODY_BYTES  = 4096
const MAX_TOKEN_CHARS = 2048

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

const JSON_HEADERS = { ...CORS_HEADERS, 'Content-Type': 'application/json' }

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS })
}

// ── Примитивный rate limit ───────────────────────────────────────────────────
// Живёт в памяти изолята: сбрасывается на холодном старте и не общий между
// изолятами. Это не защита от распределённой атаки, а отсечка тупого флуда —
// каждый запрос сюда стоит нам вызова в VK.

const RATE_WINDOW_MS = 60_000
const RATE_MAX_HITS  = 10
const rateBuckets = new Map<string, number[]>()

function isRateLimited(ip: string): boolean {
  const now = Date.now()
  const hits = (rateBuckets.get(ip) ?? []).filter((t) => now - t < RATE_WINDOW_MS)
  hits.push(now)
  rateBuckets.set(ip, hits)
  if (rateBuckets.size > 5000) rateBuckets.clear() // страховка от роста памяти
  return hits.length > RATE_MAX_HITS
}

// ── VK ────────────────────────────────────────────────────────────────────────

interface VkProfile {
  vkUserId: number
  firstName: string
  lastName: string
  email: string | null
  avatarUrl: string | null
}

/**
 * Проверяет access_token в VK и возвращает профиль.
 * client_id в запросе гарантирует, что токен выпущен именно для нашего приложения.
 * Бросает исключение на любой отказ — детали остаются в логах, наружу уходит 401.
 */
async function fetchVkProfile(accessToken: string, deviceId: string): Promise<VkProfile> {
  const url = `${VK_USER_INFO_URL}?client_id=${encodeURIComponent(vkClientId)}&v=${VK_API_VERSION}`

  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ access_token: accessToken, device_id: deviceId }),
  })

  const raw = await resp.text()
  if (!resp.ok) {
    throw new Error(`VK user_info HTTP ${resp.status}`)
  }

  let body: Record<string, unknown>
  try {
    body = JSON.parse(raw)
  } catch {
    throw new Error('VK user_info returned non-JSON')
  }

  if (body.error) {
    throw new Error(`VK user_info error: ${String(body.error)}`)
  }

  const user = body.user as Record<string, unknown> | undefined
  if (!user) {
    throw new Error(`VK user_info has no "user" field, keys: ${Object.keys(body).join(',')}`)
  }

  const rawId = user.user_id ?? user.id
  const vkUserId = Number(rawId)
  if (!rawId || !Number.isFinite(vkUserId) || vkUserId <= 0) {
    // Если это когда-нибудь сработает — VK поменял формат ответа.
    // Логируем только ИМЕНА полей, не значения.
    throw new Error(`VK user_info has no usable user id, user keys: ${Object.keys(user).join(',')}`)
  }

  const email = typeof user.email === 'string' && user.email.trim() ? user.email.trim() : null
  const avatar = typeof user.avatar === 'string' && user.avatar.trim() ? user.avatar.trim() : null

  return {
    vkUserId,
    firstName: String(user.first_name ?? ''),
    lastName:  String(user.last_name ?? ''),
    email,
    avatarUrl: avatar,
  }
}

// ── Пользователь Supabase ────────────────────────────────────────────────────

function syntheticEmail(vkUserId: number): string {
  return `vk${vkUserId}@${SYNTHETIC_EMAIL_DOMAIN}`
}

function displayName(profile: VkProfile): string {
  return `${profile.firstName} ${profile.lastName}`.trim()
}

function isEmailTakenError(error: { message?: string; status?: number } | null): boolean {
  if (!error) return false
  const msg = (error.message ?? '').toLowerCase()
  return msg.includes('already been registered') ||
         msg.includes('already registered') ||
         msg.includes('already exists')
}

async function createAuthUser(profile: VkProfile, email: string) {
  return await supabase.auth.admin.createUser({
    email,
    email_confirm: true,
    user_metadata: {
      vk_id:      profile.vkUserId,
      vk_email:   profile.email,
      full_name:  displayName(profile),
      avatar_url: profile.avatarUrl,
    },
    // Внимание: GoTrue это переопределяет и всё равно ставит provider = 'email'
    // (провайдер выводится из типа identity). Признак VK-аккаунта ищите
    // в user_metadata.vk_id — приложение проверяет именно его.
    app_metadata: { provider: 'vk', providers: ['vk'] },
  })
}

/**
 * Возвращает [userId, email, isNewUser].
 *
 * Про захват аккаунта: если email из VK уже занят другим пользователем —
 * мы НЕ линкуем VK к нему, а создаём отдельного пользователя с синтетическим
 * адресом. VK не гарантирует, что email подтверждён, поэтому линковка по email
 * означала бы возможность угнать чужой Google-аккаунт.
 */
async function resolveUser(profile: VkProfile): Promise<[string, string, boolean]> {
  const { data: identity, error: identityErr } = await supabase
    .from('vk_identities')
    .select('user_id')
    .eq('vk_user_id', profile.vkUserId)
    .maybeSingle()

  if (identityErr) throw identityErr

  if (identity?.user_id) {
    const { data: existing, error: getErr } = await supabase.auth.admin.getUserById(identity.user_id)
    if (getErr) throw getErr
    if (!existing?.user?.email) {
      throw new Error('Linked auth user has no email')
    }

    await supabase.from('vk_identities').update({
      last_login_at: new Date().toISOString(),
      first_name:    profile.firstName,
      last_name:     profile.lastName,
      avatar_url:    profile.avatarUrl,
      vk_email:      profile.email,
    }).eq('vk_user_id', profile.vkUserId)

    return [existing.user.id, existing.user.email, false]
  }

  // Первый вход: пробуем настоящий email, при конфликте — синтетический
  let email = profile.email ?? syntheticEmail(profile.vkUserId)
  let { data: created, error: createErr } = await createAuthUser(profile, email)

  if (createErr && profile.email && isEmailTakenError(createErr)) {
    console.warn(`VK email already taken by another account, falling back to synthetic (vk_id=${profile.vkUserId})`)
    email = syntheticEmail(profile.vkUserId)
    ;({ data: created, error: createErr } = await createAuthUser(profile, email))
  }

  if (createErr) throw createErr
  if (!created?.user) throw new Error('createUser returned no user')

  const userId = created.user.id

  const { error: insertErr } = await supabase.from('vk_identities').insert({
    vk_user_id: profile.vkUserId,
    user_id:    userId,
    vk_email:   profile.email,
    first_name: profile.firstName,
    last_name:  profile.lastName,
    avatar_url: profile.avatarUrl,
  })

  if (insertErr) {
    // 23505 — параллельный первый вход с двух устройств успел вставить строку.
    // Побеждает тот, кто вставился; лишнего пользователя удаляем.
    if (insertErr.code === '23505') {
      await supabase.auth.admin.deleteUser(userId).catch(() => {})
      const { data: winner } = await supabase
        .from('vk_identities')
        .select('user_id')
        .eq('vk_user_id', profile.vkUserId)
        .single()
      if (!winner?.user_id) throw insertErr
      const { data: winnerUser } = await supabase.auth.admin.getUserById(winner.user_id)
      if (!winnerUser?.user?.email) throw insertErr
      return [winnerUser.user.id, winnerUser.user.email, false]
    }
    // Пользователь создан, но маппинга нет — при следующем входе создастся дубль.
    // Откатываем создание, чтобы этого не случилось.
    await supabase.auth.admin.deleteUser(userId).catch(() => {})
    throw insertErr
  }

  return [userId, email, true]
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  if (req.method !== 'POST') {
    return jsonResponse({ error: 'method_not_allowed' }, 405)
  }

  const ip = req.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ?? 'unknown'
  if (isRateLimited(ip)) {
    return jsonResponse({ error: 'rate_limited' }, 429)
  }

  try {
    const raw = await req.text()
    if (raw.length > MAX_BODY_BYTES) {
      return jsonResponse({ error: 'payload_too_large' }, 413)
    }

    let payload: Record<string, unknown>
    try {
      payload = JSON.parse(raw)
    } catch {
      return jsonResponse({ error: 'bad_request' }, 400)
    }

    const accessToken = typeof payload.vk_access_token === 'string' ? payload.vk_access_token.trim() : ''
    const deviceId    = typeof payload.device_id === 'string' ? payload.device_id.trim() : ''

    if (!accessToken || accessToken.length > MAX_TOKEN_CHARS) {
      return jsonResponse({ error: 'bad_request' }, 400)
    }

    // Проверка токена в VK — единственный якорь доверия во всём потоке
    let profile: VkProfile
    try {
      profile = await fetchVkProfile(accessToken, deviceId)
    } catch (err) {
      console.error('VK token verification failed:', err)
      return jsonResponse({ error: 'vk_auth_failed' }, 401)
    }

    const [userId, email, isNewUser] = await resolveUser(profile)

    // ── Профиль: имя и аватар из ВКонтакте ──────────────────────────────────
    //
    // Раньше avatar_url попадал только в user_metadata и только при СОЗДАНИИ
    // пользователя (createAuthUser). При повторных входах обновлялась одна
    // vk_identities, а таблица users не трогалась вовсе — и фото профиля не
    // появлялось никогда. Проверено на боевых данных: у VK-пользователя аватар
    // в vk_identities был, а users.photo_url оставался пустым.
    //
    // ВАЖНО: photo_url и display_name здесь НЕ переписываются. Их ведёт триггер
    // sync_vk_profile_to_user (см. supabase/vk_profile_sync.sql): он ставит
    // аватар из ВК, только пока пользователь не выбрал своё фото в приложении.
    // Безусловный upsert отсюда затирал бы выбор человека при каждом входе.
    //
    // Ошибка здесь не должна ломать вход — человек уже прошёл проверку в VK,
    // и отказать ему из-за картинки было бы неправильно.
    try {
      await supabase.from('users').upsert({
        id:            userId,
        email,
        last_login_at: Date.now(),
      })

      // Метаданные сессии читает приложение сразу после входа, до первой
      // синхронизации. Здесь свежий аватар уместен: это сведения из ВК как
      // таковые, а не поле профиля, которым распоряжается пользователь.
      await supabase.auth.admin.updateUserById(userId, {
        user_metadata: {
          vk_id:      profile.vkUserId,
          vk_email:   profile.email,
          full_name:  displayName(profile),
          avatar_url: profile.avatarUrl,
        },
      })
    } catch (err) {
      console.error('Не удалось обновить профиль (вход продолжается):', err)
    }

    // Магическая ссылка — способ выдать клиенту сессию, не пересылая токены руками
    const { data: link, error: linkErr } = await supabase.auth.admin.generateLink({
      type: 'magiclink',
      email,
    })
    if (linkErr) throw linkErr

    const hashedToken = link?.properties?.hashed_token
    if (!hashedToken) {
      throw new Error('generateLink returned no hashed_token')
    }

    console.log(`VK auth ok: vk_id=${profile.vkUserId}, user=${userId}, new=${isNewUser}`)

    return jsonResponse({ hashed_token: hashedToken, is_new_user: isNewUser }, 200)
  } catch (err) {
    console.error('vk-auth error:', err)
    return jsonResponse({ error: 'internal_error' }, 500)
  }
})
