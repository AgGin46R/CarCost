/**
 * send-version-push — Supabase Edge Function
 *
 * Вызывается триггером public.notify_app_update при UPDATE на таблице app_config.
 * Рассылает FCM data-сообщение с type="new_version" всем
 * пользователям у которых есть токен в user_push_tokens.
 *
 * Секреты (Dashboard → Settings → Edge Functions → Secrets):
 *   FIREBASE_SERVICE_ACCOUNT — JSON сервисного аккаунта Firebase
 *   WEBHOOK_SECRET           — общий секрет с public.notify_app_update
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl          = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey   = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const serviceAccountJson   = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')!

const supabase = createClient(supabaseUrl, supabaseServiceKey)

function base64url(data: Uint8Array): string {
  return btoa(String.fromCharCode(...data))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function encodeBase64url(str: string): string {
  return base64url(new TextEncoder().encode(str))
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '')
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes.buffer
}

async function createJwt(sa: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header  = encodeBase64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = encodeBase64url(JSON.stringify({
    iss: sa.client_email,
    sub: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud:  'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }))
  const input = `${header}.${payload}`
  const key = await crypto.subtle.importKey(
    'pkcs8', pemToArrayBuffer(sa.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign'],
  )
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(input))
  return `${input}.${base64url(new Uint8Array(sig))}`
}

async function getAccessToken(sa: Record<string, string>): Promise<string> {
  const jwt  = await createJwt(sa)
  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  })
  const data = await resp.json()
  if (!data.access_token) throw new Error(`OAuth2 error: ${JSON.stringify(data)}`)
  return data.access_token
}

async function sendFcm(
  projectId: string,
  accessToken: string,
  token: string,
  data: Record<string, string>,
): Promise<void> {
  const resp = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        message: {
          token,
          data,
          android: { priority: 'HIGH', ttl: '86400s' },
        },
      }),
    },
  )
  if (!resp.ok) {
    const err = await resp.text()
    if (!err.includes('UNREGISTERED') && !err.includes('NOT_FOUND')) {
      console.warn(`FCM error for token ${token.slice(0, 12)}…: ${err}`)
    }
  }
}

const webhookSecret = Deno.env.get('WEBHOOK_SECRET') ?? ''

function secretMatches(provided: string): boolean {
  if (!webhookSecret || provided.length !== webhookSecret.length) return false
  let diff = 0
  for (let i = 0; i < webhookSecret.length; i++) {
    diff |= webhookSecret.charCodeAt(i) ^ provided.charCodeAt(i)
  }
  return diff === 0
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', {
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-webhook-secret',
      },
    })
  }

  if (!secretMatches(req.headers.get('x-webhook-secret') ?? '')) {
    console.warn('Rejected call with missing/invalid x-webhook-secret')
    return new Response(JSON.stringify({ error: 'unauthorized' }), { status: 401 })
  }

  try {
    const body = await req.json()
    const record = body.record

    const versionName  = String(record?.version_name  ?? '')
    const releaseNotes = String(record?.release_notes ?? '')
    const versionCode  = Number(record?.version_code  ?? 0)

    if (!versionName) {
      return new Response(JSON.stringify({ error: 'version_name missing' }), { status: 400 })
    }

    console.log(`New version detected: ${versionName} (code ${versionCode})`)

    const { data: tokenRows, error: tokenErr } = await supabase
      .from('user_push_tokens')
      .select('token')

    if (tokenErr) throw tokenErr

    const tokens: string[] = (tokenRows ?? []).map((r: { token: string }) => r.token)

    if (tokens.length === 0) {
      console.log('No FCM tokens registered — nobody to notify')
      return new Response(JSON.stringify({ sent: 0 }), { status: 200 })
    }

    const sa          = JSON.parse(serviceAccountJson)
    const accessToken = await getAccessToken(sa)
    const projectId   = sa.project_id

    const fcmData: Record<string, string> = {
      type:          'new_version',
      version_name:  versionName,
      version_code:  String(versionCode),
      release_notes: releaseNotes,
    }

    const BATCH = 500
    let sent = 0
    for (let i = 0; i < tokens.length; i += BATCH) {
      const batch = tokens.slice(i, i + BATCH)
      await Promise.allSettled(
        batch.map((token) => sendFcm(projectId, accessToken, token, fcmData))
      )
      sent += batch.length
    }

    console.log(`Version push sent to ${sent} devices`)
    return new Response(
      JSON.stringify({ sent, version: versionName }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )
  } catch (err) {
    console.error('send-version-push error:', err)
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 })
  }
})
