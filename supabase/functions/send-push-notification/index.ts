import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

// Firebase service account JSON stored as secret FIREBASE_SERVICE_ACCOUNT
const serviceAccountJson = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')!

const supabase = createClient(supabaseUrl, supabaseServiceKey)

interface PushPayload {
  table: string
  event_type: 'INSERT' | 'UPDATE' | 'DELETE'
  record: Record<string, unknown>
  car_id: string
  sender_user_id: string
  title: string
  body: string
}

// ── FCM v1 helpers ────────────────────────────────────────────────────────────

function base64url(data: Uint8Array): string {
  return btoa(String.fromCharCode(...data))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

function encodeBase64url(str: string): string {
  return base64url(new TextEncoder().encode(str))
}

/** PEM private key → ArrayBuffer (PKCS8) */
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

/** Create a signed JWT for Google OAuth2 using the service account */
async function createJwt(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000)
  const header = encodeBase64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = encodeBase64url(JSON.stringify({
    iss: serviceAccount.client_email,
    sub: serviceAccount.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }))

  const signingInput = `${header}.${payload}`
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(serviceAccount.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(signingInput),
  )
  return `${signingInput}.${base64url(new Uint8Array(signature))}`
}

/** Exchange JWT → short-lived OAuth2 access token */
async function getAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const jwt = await createJwt(serviceAccount)
  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  })
  const data = await resp.json()
  if (!data.access_token) throw new Error(`OAuth2 error: ${JSON.stringify(data)}`)
  return data.access_token
}

/** Send one FCM v1 message to a single token */
async function sendFcmMessage(
  projectId: string,
  accessToken: string,
  fcmToken: string,
  title: string,
  body: string,
  data: Record<string, string>,
): Promise<void> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`
  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
      message: {
        token: fcmToken,
        // data-only message → onMessageReceived() fires even when app is killed
        data: { title, body, ...data },
        android: {
          priority: 'HIGH',
          ttl: '86400s',
        },
      },
    }),
  })
  if (!resp.ok) {
    const err = await resp.text()
    console.warn(`FCM send failed for token ${fcmToken.slice(0, 16)}…: ${err}`)
  }
}

// ── Main handler ──────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', {
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
      },
    })
  }

  try {
    const payload: PushPayload = await req.json()
    const { car_id, sender_user_id, title, body, table: tableName, event_type } = payload

    if (!car_id) {
      return new Response(JSON.stringify({ error: 'car_id is required' }), { status: 400 })
    }

    // Fetch FCM tokens of all car members except the sender
    const { data: members } = await supabase
      .from('car_members')
      .select('user_id')
      .eq('car_id', car_id)

    const recipientIds = (members ?? [])
      .map((m: { user_id: string }) => m.user_id)
      .filter((id: string) => id !== sender_user_id)

    if (recipientIds.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: 'no recipients' }), { status: 200 })
    }

    const { data: tokenRows } = await supabase
      .from('user_push_tokens')
      .select('token')
      .in('user_id', recipientIds)

    const fcmTokens: string[] = (tokenRows ?? []).map((t: { token: string }) => t.token)

    if (fcmTokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: 'no fcm tokens registered' }), { status: 200 })
    }

    // Obtain OAuth2 access token once, reuse for all sends
    const serviceAccount = JSON.parse(serviceAccountJson)
    const accessToken = await getAccessToken(serviceAccount)
    const projectId: string = serviceAccount.project_id

    const extraData: Record<string, string> = {
      car_id,
      table: tableName,
      event_type,
    }

    // Send to all recipients in parallel (FCM v1 is per-token)
    await Promise.allSettled(
      fcmTokens.map((token) =>
        sendFcmMessage(projectId, accessToken, token, title, body, extraData)
      ),
    )

    console.log(`FCM v1 sent to ${fcmTokens.length} devices`)
    return new Response(
      JSON.stringify({ sent: fcmTokens.length }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    )
  } catch (err) {
    console.error('Edge Function error:', err)
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 })
  }
})
