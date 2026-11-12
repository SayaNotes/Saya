export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json; charset=utf-8"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "POST") return new Response(JSON.stringify({ error: "POST required" }), { status: 405, headers: cors });
    if (env.MATH_STUDIO_ACCESS_TOKEN && request.headers.get("Authorization") !== "Bearer " + env.MATH_STUDIO_ACCESS_TOKEN) return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: cors });
    if (!env.OPENAI_API_KEY) return new Response(JSON.stringify({ error: "Server AI key is not configured" }), { status: 503, headers: cors });
    try {
      const body = await request.json();
      const problem = String(body.problem || "").slice(0, 4000);
      const calculation = String(body.calculation || "").slice(0, 8000);
      if (!problem.trim()) return new Response(JSON.stringify({ error: "Problem is required" }), { status: 400, headers: cors });
      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: { "Authorization": "Bearer " + env.OPENAI_API_KEY, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-4.1-mini",
          instructions: "You are Saya Math Studio, a careful mathematics tutor. Explain the method step by step, define symbols, check the calculation supplied, and clearly state assumptions. If the supplied numerical result is approximate, say so. Never invent a result.",
          input: "Problem: " + problem + "\nCurrent calculation from Saya's local math engine: " + calculation
        })
      });
      const data = await upstream.json();
      if (!upstream.ok) return new Response(JSON.stringify({ error: data.error?.message || "AI provider request failed" }), { status: upstream.status, headers: cors });
      const answer = (data.output || []).flatMap(item => item.content || []).filter(part => part.type === "output_text").map(part => part.text).join("\n").trim();
      if (!answer) return new Response(JSON.stringify({ error: "AI provider returned no text" }), { status: 502, headers: cors });
      return new Response(JSON.stringify({ answer }), { headers: cors });
    } catch (error) {
      return new Response(JSON.stringify({ error: String(error?.message || error) }), { status: 500, headers: cors });
    }
  }
};
