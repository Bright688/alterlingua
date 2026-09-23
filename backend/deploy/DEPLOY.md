# Deploying the AlterLingua backend on a Contabo VPS

The backend runs as two containers: the API and Caddy (HTTPS in front of it). It stores nothing (no database yet), so there is nothing to back up.
Nothing here needs your Mistral key or API token to be pasted anywhere except the server's own `.env` file.

## What you need
1. **A Contabo VPS** running Ubuntu 24.04 (the smallest plan is enough for a pilot) and the root login Contabo emails you.
2. **A host name that points at the server.** Best: a domain or sub-domain (for example `api.yourdomain.com`) with an **A record** to the server's IP. No domain? Use `<ip-with-dashes>.sslip.io` (for IP 203.0.113.7 that is `203-0-113-7.sslip.io`): it resolves by itself and Caddy can get a real certificate for it.
3. **A Mistral API key** from console.mistral.ai, and **training turned off** on it (below).
4. **An API token** the app will use: run `openssl rand -hex 32` on your computer and keep the result private.

## One-time server setup (as root, over SSH)
```bash
adduser deploy && usermod -aG sudo deploy          # a normal user for day-to-day work
# copy your SSH public key for that user, then log in as it and turn off password login:
#   sudo nano /etc/ssh/sshd_config   ->  PasswordAuthentication no ; PermitRootLogin no ; then: sudo systemctl restart ssh
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw --force enable
sudo apt update && sudo apt -y upgrade && sudo apt -y install unattended-upgrades
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker deploy   # log out and in again afterwards
```

## Put the backend on the server (from your computer)
```bash
rsync -av --exclude .venv --exclude .env --exclude __pycache__ --exclude .pytest_cache \
  ~/Desktop/alterlingua/backend/ deploy@YOUR_SERVER_IP:~/alterlingua/backend/
```

## Configure and start (on the server)
```bash
cd ~/alterlingua/backend/deploy
cp .env.production.example .env && chmod 600 .env
nano .env        # fill ALTERLINGUA_DOMAIN, ALTERLINGUA_API_TOKENS and ALTERLINGUA_MISTRAL_API_KEY
docker compose --env-file .env up -d --build
docker compose logs -f api                    # Ctrl+C to stop watching
```
The API refuses to start in production without `ALTERLINGUA_API_TOKENS`.

## Check it works (from your computer)
```bash
curl https://YOUR_HOST/health                                   # {"status":"ok", ... "translation_provider":"mistral"}
curl -i -X POST https://YOUR_HOST/v1/translate -H 'content-type: application/json' \
  -d '{"text":"Are you coming tomorrow?","target":"fr"}'        # 401: no token
curl -X POST https://YOUR_HOST/v1/translate -H 'content-type: application/json' -H 'Authorization: Bearer YOUR_TOKEN' \
  -d '{"text":"Are you coming tomorrow?","source":"auto","target":"fr"}'   # a real translation
```
Use sample text only while testing (see Privacy).

## Point the app at it
Put these two lines in `~/.gradle/gradle.properties` on your computer (never in the repository):
```
alterlingua.translationBaseUrl=https://YOUR_HOST
alterlingua.releaseBackendUrl=https://YOUR_HOST
alterlingua.apiToken=YOUR_TOKEN
```
Then rebuild and install. The debug build works with `https://` addresses as well as `adb reverse`.

## Updating
Repeat the `rsync` command, then on the server: `cd ~/alterlingua/backend/deploy && docker compose --env-file .env up -d --build`.

## Privacy: read before real messages go through it
- **Mistral's free plan may train on what you send.** The free "Experiment" plan can use inputs and outputs for model training by default; you must turn that off in the Mistral console (Admin Console, Privacy). Zero data retention is only offered on the paid pay-as-you-go plan and only for some endpoints (chat completions among them; check audio). Mistral keeps inputs and outputs for up to 30 days to watch for abuse unless zero retention is on. **Until training is off, use sample text only.** Please verify these terms on Mistral's pages yourself: they change.
- **This server keeps no messages, transcripts or audio.** Logs hold the method, path, status and timing only (no text, no query strings). Temporary audio is deleted right after use.
- **The API token inside the app can be extracted** by someone determined, so it is a first guard and the per-token rate limit is the second. Real user accounts come with the authentication milestone.
- Mistral text-to-speech has no Chinese or Japanese voice, so speaking a translation into 中文 or 日本語 answers with the normal "no voice" error.
