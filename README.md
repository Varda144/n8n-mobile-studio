# n8n Mobile Studio

Local n8n + OpenCode on Android — no Termux, no cloud.

## Install
Download APK from Actions → install → tap START.

## How it works
- Termux debs (nodejs 22.11.0) fetched for your ABI (aarch64/arm/x86_64), extracted via Ar+XZ to app's private usr/
- npm install n8n 1.28.0 + OpenCode stub
- n8n on 127.0.0.1:5678, OpenCode on 8080, WebView + external browser share same instance
- Terminal uses same env (node/npm on PATH, LD_LIBRARY_PATH set)

## UI
Neo-Brutalist • Dark OLED (#0F172A) • Monospace • Sharp 0px corners • 48dp targets
