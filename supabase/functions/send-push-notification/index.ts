import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const supabaseUrl = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const fcmServerKey = Deno.env.get('FCM_SERVER_KEY')!

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

    // Get all members of this car
    const { data: members, error: membersError } = await supabase
      .from('car_members')
      .select('user_id')
      .eq('car_id', car_id)

    if (membersError) {
      console.error('Error fetching members:', membersError)
      return new Response(JSON.stringify({ error: membersError.message }), { status: 500 })
    }

    // Exclude sender — they don't need a notification for their own action
    const recipientIds = (members ?? [])
      .map((m: { user_id: string }) => m.user_id)
      .filter((id: string) => id !== sender_user_id)

    if (recipientIds.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: 'no recipients' }), { status: 200 })
    }

    // Fetch FCM tokens for all recipients
    const { data: tokens, error: tokensError } = await supabase
      .from('user_push_tokens')
      .select('token')
      .in('user_id', recipientIds)

    if (tokensError) {
      console.error('Error fetching tokens:', tokensError)
      return new Response(JSON.stringify({ error: tokensError.message }), { status: 500 })
    }

    const fcmTokens = (tokens ?? []).map((t: { token: string }) => t.token)

    if (fcmTokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0, reason: 'no fcm tokens registered' }), { status: 200 })
    }

    // Send via FCM Legacy HTTP API (supports multicast up to 1000 tokens)
    const fcmPayload = {
      registration_ids: fcmTokens,
      notification: {
        title,
        body,
        sound: 'default',
      },
      data: {
        car_id,
        table: tableName,
        event_type,
      },
      priority: 'high',
      content_available: true,
    }

    const fcmResponse = await fetch('https://fcm.googleapis.com/fcm/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `key=${fcmServerKey}`,
      },
      body: JSON.stringify(fcmPayload),
    })

    const fcmResult = await fcmResponse.json()
    console.log(`FCM sent to ${fcmTokens.length} devices:`, JSON.stringify(fcmResult))

    return new Response(
      JSON.stringify({ sent: fcmTokens.length, fcm: fcmResult }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  } catch (err) {
    console.error('Edge Function error:', err)
    return new Response(JSON.stringify({ error: String(err) }), { status: 500 })
  }
})
