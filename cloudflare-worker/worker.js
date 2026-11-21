export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowed = new Set([
      "https://appassets.androidplatform.net",
      "http://localhost",
      "https://localhost"
    ]);
    // Permit the hosted app when it is served from a Cloudflare Workers subdomain.
    const isAllowedOrigin = allowed.has(origin) || /^https:\/\/[a-z0-9-]+\.workers\.dev$/i.test(origin);
    const headers = {
      "Access-Control-Allow-Origin": isAllowedOrigin ? origin : "null",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Vary": "Origin"
    };
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    const url = new URL(request.url);
    if (url.pathname !== "/chat" || request.method !== "POST")
      return Response.json({ success: false, error: "Use POST /chat" }, { status: 404, headers });
    if (!env.CF_ACCOUNT_ID || !env.CF_API_TOKEN)
      return Response.json({ success: false, error: "Worker secrets CF_ACCOUNT_ID and CF_API_TOKEN are not configured" }, { status: 500, headers });
    try {
      const body = await request.json();
      const model = typeof body.model === "string" && /^@[a-z0-9._/-]+$/i.test(body.model)
        ? body.model : "@cf/meta/llama-3.1-8b-instruct";
      if (!Array.isArray(body.messages) || body.messages.length === 0)
        return Response.json({ success: false, error: "messages must be a non-empty array" }, { status: 400, headers });
      const upstream = await fetch(
        `https://api.cloudflare.com/client/v4/accounts/${env.CF_ACCOUNT_ID}/ai/run/${model}`,
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${env.CF_API_TOKEN}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({ messages: body.messages })
        }
      );
      const data = await upstream.json();
      if (!upstream.ok || data.success === false) {
        const message = data.errors?.map(e => e.message).join("; ") || `Cloudflare API error ${upstream.status}`;
        return Response.json({ success: false, error: message }, { status: upstream.status, headers });
      }
      return Response.json({
        success: true,
        response: data.result?.response || data.result?.output_text || ""
      }, { headers });
    } catch (error) {
      return Response.json({ success: false, error: error.message || "Worker request failed" }, { status: 500, headers });
    }
  }
};