# Saya Math Studio AI proxy

This Worker is the secure server-side bridge for Math Studio's optional conversational tutor.
The Android app sends only the problem and its local calculation to this HTTPS endpoint. The
OpenAI API key is stored as a Cloudflare Worker secret and is never placed in the APK.

## Deploy

1. Install Wrangler and authenticate with Cloudflare.
2. From this directory run `wrangler deploy`.
3. Set the provider secret: `wrangler secret put OPENAI_API_KEY`.
4. Optionally configure the model: `wrangler secret put OPENAI_MODEL` is not required; use a Worker
   variable named `OPENAI_MODEL` if you want to override the default.
5. Copy the deployed Worker HTTPS URL into Math Studio's **Secure AI backend URL** field.

The Worker accepts POST JSON with `problem` and `calculation`, and returns `{"answer":"..."}`.
Do not put an OpenAI key in Android resources, BuildConfig, preferences, or source code.
Provider usage is billed to the account owning the key. Add Cloudflare rate limits/authentication
before sharing the endpoint publicly; the sample CORS policy is intentionally permissive for
first setup and should be tightened to the app's trusted origin when practical.
