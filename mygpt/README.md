# MyGPT 160 — Android-first AI chat

A lightweight installable PWA with 160 specialist personas. All personas share one selected model endpoint; they are not 160 independent model instances.

## Run
Open `index.html` from a static host (GitHub Pages recommended). On Android Chrome use menu → Install app / Add to Home screen.

## Connect the free model
Open Settings → get a free API key from https://aistudio.google.com/app/apikey → paste it into the app. Default model: `gemini-2.5-flash`. Free quotas, availability and model names are controlled by Google and can change. No API key is bundled in this repository. The key is saved in browser local storage on the device.

## Privacy and limitations
- Messages are sent to Google Gemini when you use the app.
- This static demo stores conversation state only in memory; refresh clears chat.
- Never commit an API key to GitHub.
- It is not ChatGPT/GPT-6 and cannot reproduce proprietary OpenAI models.
- No unlimited free inference is promised.

## Publish
Enable GitHub Pages for the `mygpt` folder (or use the included Pages workflow). The app can then be installed as a PWA on Android.
