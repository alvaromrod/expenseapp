import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { JWT } from "https://esm.sh/google-auth-library@8.7.0"

serve(async (req) => {
  try {
    const payload = await req.json()
    const expense = payload.record

    console.log(`Processing notification for expense: ${expense.id}`)

    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // 1. Get recipients (other group members)
    const { data: members, error: membersError } = await supabase
      .from('group_members')
      .select('user_id')
      .eq('group_id', expense.group_id)
      .neq('user_id', expense.paid_by)

    if (membersError) throw membersError
    if (!members || members.length === 0) {
      console.log('No other group members to notify.')
      return new Response('No recipients', { status: 200 })
    }

    // 2. Get tokens for recipients
    const { data: users, error: usersError } = await supabase
      .from('users')
      .select('fcm_token, name')
      .in('id', members.map(m => m.user_id))

    if (usersError) throw usersError

    const tokens = users?.filter(u => u.fcm_token).map(u => u.fcm_token)
    if (!tokens || tokens.length === 0) {
      console.log('No recipients have FCM tokens registered.')
      return new Response('No tokens', { status: 200 })
    }

    // 3. Prepare FCM v1 credentials
    const secretContent = Deno.env.get('FIREBASE_SERVICE_ACCOUNT')
    if (!secretContent) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT secret is not set in Supabase.')
    }

    let credentials
    try {
      credentials = JSON.parse(secretContent)
    } catch (e) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT secret is not valid JSON.')
    }

    if (!credentials.client_email || !credentials.private_key || !credentials.project_id) {
      throw new Error('FIREBASE_SERVICE_ACCOUNT is missing required fields (client_email, private_key, or project_id).')
    }

    console.log(`Using Firebase Project: ${credentials.project_id}`)

    const client = new JWT(
      credentials.client_email,
      undefined,
      // Fix potential escaping issues from manual secret pasting
      credentials.private_key.replace(/\\n/g, '\n').trim(),
      ['https://www.googleapis.com/auth/cloud-platform']
    )
    const token = await client.getAccessToken()

    // 4. Send notifications
    const senderName = (await supabase
      .from('users')
      .select('name')
      .eq('id', expense.paid_by)
      .single()).data?.name || 'Someone'

    for (const fcmToken of tokens) {
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${credentials.project_id}/messages:send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token.token}`,
        },
        body: JSON.stringify({
          message: {
            token: fcmToken,
            notification: {
              title: 'New Expense',
              body: `${senderName} added: ${expense.name} (${expense.amount} ${expense.currency})`,
            },
            data: {
              expense_id: expense.id,
              group_id: expense.group_id
            }
          },
        }),
      })
      const resData = await res.json()
      console.log(`FCM response: ${JSON.stringify(resData)}`)
    }

    return new Response('Notifications processed successfully', { status: 200 })
  } catch (err) {
    console.error(`Error: ${err.message}`)
    return new Response(JSON.stringify({ error: err.message }), {
      headers: { 'Content-Type': 'application/json' },
      status: 500,
    })
  }
})
