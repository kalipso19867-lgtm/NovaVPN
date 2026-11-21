# MyGPT-160 Cloudflare Worker proxy

This Worker keeps the Cloudflare API token on Cloudflare instead of exposing it in the Android app.

## Deploy once

1. Open Cloudflare Dashboard → Workers & Pages → Create → Worker.
2. Name it `mygpt160-ai`, create/deploy the starter Worker.
3. Open the Worker → Edit code. Replace the starter code with `cloudflare-worker/worker.js` from this repository and Deploy.
4. Open Worker Settings → Variables and Secrets. Add these as **Secrets**:
   - `CF_ACCOUNT_ID` — your Cloudflare Account ID.
   - `CF_API_TOKEN` — API token with permission **Account / Workers AI / Read**.
5. Save and redeploy if prompted.
6. Copy the Worker URL, for example `https://mygpt160-ai.<your-subdomain>.workers.dev`.
7. In MyGPT-160 → Settings → Provider choose Cloudflare Workers AI. Paste the Worker URL (without `/chat`) and save.

The app calls `POST <Worker URL>/chat`. The Worker calls the Cloudflare Workers AI REST API.

## Security

Never put the Cloudflare API token in the app, GitHub, or a public message. Store it only as a Worker Secret. Anyone who learns the public Worker URL may be able to consume its AI quota; restrict access or add rate limiting before public distribution.

The current Worker permits requests from the Android WebView origin `https://appassets.androidplatform.net` and localhost development origins. If testing the hosted website, add its exact origin to the `allowed` set in `worker.js`.
