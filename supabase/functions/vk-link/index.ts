/**
 * vk-link — Supabase Edge Function
 *
 * Привязывает аккаунт ВКонтакте к УЖЕ существующему пользователю.
 *
 * Без этого вход через VK всегда создавал нового пользователя: человек с
 * аккаунтом по email, нажавший «Войти через VK», попадал в пустой аккаунт
 * без своих машин.
 *
 * Отличие от vk-auth: эта функция аутентифицирована. Кто привязывает —
 * берётся ИСКЛЮЧИТЕЛЬНО из JWT в заголовке Authorization, никогда из тела.
 * Что привязывается — исключительно из ответа VK.
 *
 * Деплой (verify_jwt включён, в отличие от vk-auth):
 *   supabase functions deploy vk-link --project-ref <ref>
 *
 * Секреты: VK_CLIENT_ID (общий с vk-auth)
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl        = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const supabaseAnonKey    = Deno.env.get('SUPABASE_ANON_KEY')!
const vkClientId         = Deno.env.get('VK_CLIENT_ID')!

const admin = createClient(supabaseUrl, supabaseServiceKey, {
  auth: { autoRefreshToken: false, persistSession: false },
})

const VK_USER_INFO_URL = 'https://id.vk.ru/oauth2/user_info'
const VK_API_VERSION   = '5.220'

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

interface VkProfile {
  vkUserId: number
  firstName: string
  lastName: string
  email: string | null
  avatarUrl: string | null
}

/** Идентична проверке в vk-auth: client_id доказывает, что токен выпущен для нас */
async function fetchVkProfile(accessToken: string, deviceId: string): Promise<VkProfile> {
  const url = `${VK_USER_INFO_URL}?client_id=${encodeURIComponent(vkClientId)}&v=${VK_API_VERSION}`

  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ access_token: accessToken, device_id: deviceId }),
  })

  const raw = await resp.text()
  if (!resp.ok) throw new Error(`VK user_info HTTP ${resp.status}`)

  let body: Record<string, unknown>
  try {
    body = JSON.parse(raw)
  } catch {
    throw new Error('VK user_info returned non-JSON')
  }

  if (body.error) throw new Error(`VK user_info error: ${String(body.error)}`)

  const user = body.user as Record<string, unknown> | undefined
  if (!user) throw new Error(`VK user_info has no "user" field, keys: ${Object.keys(body).join(',')}`)

  const rawId = user.user_id ?? user.id
  const vkUserId = Number(rawId)
  if (!rawId || !Number.isFinite(vkUserId) || vkUserId <= 0) {
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

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: CORS_HEADERS })
  }

  if (req.method !== 'POST') {
    return jsonResponse({ error: 'method_not_allowed' }, 405)
  }

  try {
    // 1. КТО привязывает — только из JWT
    const authHeader = req.headers.get('Authorization') ?? ''
    if (!authHeader.startsWith('Bearer ')) {
      return jsonResponse({ error: 'unauthorized' }, 401)
    }

    const caller = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { autoRefreshToken: false, persistSession: false },
    })

    const { data: userData, error: userErr } = await caller.auth.getUser()
    const currentUserId = userData?.user?.id
    if (userErr || !currentUserId) {
      return jsonResponse({ error: 'unauthorized' }, 401)
    }

    // 2. ЧТО привязывается — только из ответа VK
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

    let profile: VkProfile
    try {
      profile = await fetchVkProfile(accessToken, deviceId)
    } catch (err) {
      console.error('VK token verification failed:', err)
      return jsonResponse({ error: 'vk_auth_failed' }, 401)
    }

    // 3. Проверяем, свободны ли обе стороны связки
    const { data: byVk } = await admin
      .from('vk_identities')
      .select('user_id')
      .eq('vk_user_id', profile.vkUserId)
      .maybeSingle()

    if (byVk?.user_id) {
      if (byVk.user_id === currentUserId) {
        return jsonResponse({ already_linked: true, vk_user_id: profile.vkUserId }, 200)
      }
      return jsonResponse({ error: 'vk_already_linked' }, 409)
    }

    const { data: byUser } = await admin
      .from('vk_identities')
      .select('vk_user_id')
      .eq('user_id', currentUserId)
      .maybeSingle()

    if (byUser?.vk_user_id) {
      return jsonResponse({ error: 'account_already_linked' }, 409)
    }

    // 4. Связываем
    const { error: insertErr } = await admin.from('vk_identities').insert({
      vk_user_id: profile.vkUserId,
      user_id:    currentUserId,
      vk_email:   profile.email,
      first_name: profile.firstName,
      last_name:  profile.lastName,
      avatar_url: profile.avatarUrl,
    })

    if (insertErr) {
      // Гонка: кто-то успел занять vk_user_id или user_id между проверкой и вставкой
      if (insertErr.code === '23505') {
        return jsonResponse({ error: 'vk_already_linked' }, 409)
      }
      throw insertErr
    }

    console.log(`VK linked: vk_id=${profile.vkUserId} → user=${currentUserId}`)

    return jsonResponse({
      linked: true,
      vk_user_id: profile.vkUserId,
      first_name: profile.firstName,
      last_name:  profile.lastName,
    }, 200)
  } catch (err) {
    console.error('vk-link error:', err)
    return jsonResponse({ error: 'internal_error' }, 500)
  }
})
