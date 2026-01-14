# MovieTheatreCore User Guide

MovieTheatreCore lets you build in-game cinemas with multiple screens, schedule shows, and play synchronized video + audio for nearby players. Video always renders on item frames without a resource pack; the pack is optional and only required for enhanced audio.

---

## 1) Installation (all environments)

1. **Install the plugin**
   - Place `MovieTheatreCore.jar` in your server’s `plugins/` directory.

2. **Start the server once**
   - MovieTheatreCore will create `plugins/MovieTheatreCore/` and install runtime dependencies.

3. **Verify dependencies**
   - Run:
     ```
     /mtc deps status
     ```
   - Ensure **ffmpeg** and **ffprobe** are available.

---

## 2) Pterodactyl/Wings requirements (resource pack hosting for audio)

MovieTheatreCore serves a **single rolling resource pack** that includes audio for all media. Players download it from a **public** HTTP(S) URL you provide (audio only; video works without it).

### Requirements
- You **must** provide a public HTTP(S) URL for the pack if you want audio.
- The pack must be reachable at:
  ```
  https://your-pack-domain.example/pack.zip
  ```

### Recommended setup (NGINX + certbot)
1. Open or proxy the internal pack server port (default `8123`).
2. Put NGINX in front of it and terminate HTTPS.
3. Install a certificate with certbot.
4. Set a public base URL in the config (see priority below).

**Example NGINX config**
```
server {
  listen 80;
  server_name pack.yourdomain.example;
  location / {
    return 301 https://$host$request_uri;
  }
}

server {
  listen 443 ssl http2;
  server_name pack.yourdomain.example;

  ssl_certificate /etc/letsencrypt/live/pack.yourdomain.example/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/pack.yourdomain.example/privkey.pem;

  location /pack.zip {
    proxy_pass http://127.0.0.1:8123/pack.zip;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
}
```

**Example certbot command**
```
sudo certbot --nginx -d pack.yourdomain.example
```

**Config key (required)**
```yaml
resource_pack:
  server:
    public-url: "https://pack.yourdomain.example"
  url: ""
pack:
  public-base-url: ""
```

> Use the base HTTP(S) URL (no trailing slash required). The plugin appends `/pack.zip`.

> The plugin runs behind NAT in Pterodactyl/Wings. **Do not** use `localhost`, `0.0.0.0`, or private IPs for the public URL.

---

## 3) DNS setup for the pack subdomain (audio)

1. Create a DNS record (A or CNAME) for a subdomain, e.g.:
   - `pack.yourdomain.example` → your proxy/server IP
2. Configure HTTPS on that subdomain.
3. Set a public pack URL base (it will serve `/pack.zip`).

---

## 4) Create a screen

```
/mtc screen create Lobby 6 4
/mtc screen list
```

### GUI method (recommended)

1. Run `/mtc admin` to receive your bound Admin Wand.
2. Right-click with the wand to open the Admin GUI.
3. Click **Screens** → **Create Screen**.
4. Follow the wizard prompts (name → width → height).

---

## 5) Add media

Supported media sources:
- **YouTube links** (via yt-dlp)
- **Direct MP4 / WEBM URLs**
- **MediaFire direct download links** (must point directly to the file; some links are blocked or require a direct link)
- **M3U8 livestreams** (direct playlist URLs)
- **Local files** (host them over HTTPS and use the direct URL; file paths are not supported)

Examples:

**YouTube**
```
/mtc media add Trailer yt dQw4w9WgXcQ
```

**Direct MP4/WEBM**
```
/mtc media add Intro https://example.com/intro.mp4
```

**MediaFire direct download**
```
/mtc media add Clip https://download.mediafire.com/.../clip.webm
```

**Livestream (M3U8)**
```
/mtc play Lobby url https://example.com/live/stream.m3u8
```

---

## 6) Play media

```
/mtc play Lobby media Trailer
```

### GUI method (recommended)

1. Open the Admin GUI with the Admin Wand.
2. Click **Media** → **Add Media** and follow the wizard.
3. Click **Screens** → pick a screen → **Assign Media**.
4. Click **Playback Controls** → **Play**.

---

## 7) Video + audio behavior (automatic)

- **Audio is always automatic.** There is no config toggle and no manual audio commands.
- If a video contains audio, MovieTheatreCore extracts it and syncs it with the video.
- Audio is heard **only by players within range** of the screen.
- Video always renders, even if the resource pack fails or is disabled.

If a video has **no audio stream**, it will play silently (video still works).

---

## 8) Resource packs (optional, fully automatic for audio)

MovieTheatreCore builds **one rolling resource pack** for **all media** (audio only).

It automatically rebuilds when:
- Media is added
- Media is removed
- Media is updated

Rules:
- The pack is applied **only** when a player is within range of an active screen **and** a video is playing.
- If the pack fails to download, video keeps playing and admins are notified (audio is skipped).
- The pack server **bind address** is not the public URL. Configure the public URL separately.

Public URL priority (first non-empty wins):
1. `resource_pack.server.public-url`
2. `pack.public-base-url`
3. `resource_pack.url` (legacy)

Pack URL format (required for audio):
```
https://your-pack-domain.example/pack.zip
```

Set the base URL in one of the keys above and the plugin will append `/pack.zip`.
Make sure the selected host serves `/pack.zip` over HTTP(S).

Debug command:
```
/mtc debug pack
```

Health check for the embedded server:
```
curl http://127.0.0.1:8123/health
```

---

## 9) YouTube cookies (optional)

If YouTube playback is blocked, export cookies from your browser and save them as:

```
plugins/MovieTheatreCore/youtube-cookies.txt
```

MovieTheatreCore will:
- Detect expired/invalid cookies.
- Warn admins with a clear fix message.
- Fall back to non-cookie mode if `youtube.require-cookies` is `false`.

---

## 9) Admin GUI

Run:
```
/mtc admin
```

You receive a **bound admin tool** (bone):
- Cannot be dropped
- Cannot be moved
- Right-click opens the admin GUI

From the GUI you can:
- Create/list/delete screens
- Add media URLs
- Assign media to screens
- Play/pause/stop playback
- Toggle stretch/fill modes
- Set audio radius

---

## 10) YouTube cookies (reliable workflow)

If YouTube blocks playback:

1. Export cookies from your browser.
2. Save them to:
   ```
   plugins/MovieTheatreCore/youtube-cookies.txt
   ```

Notes:
- Cookies expire regularly; re-export as needed.
- If cookies are missing or expired, MovieTheatreCore will warn you and fall back to non-cookie mode unless `youtube.require-cookies: true`.

---

## 11) Troubleshooting (copy/paste)

**Check dependency status**
```
/mtc deps status
```

**Check resource pack status**
```
/mtc pack status
```

**Full pack diagnostics**
```
/mtc debug pack
```

**Test the pack URL from your server**
```
curl -I https://your-pack-domain.example/pack.zip
```

**Common problems**

- **“Pack URL not configured.”**
  - Set `resource_pack.server.public-url`, `pack.public-base-url`, or `resource_pack.url` to a public HTTP(S) pack URL base.

- **“Pack URL returned HTML.”**
  - You used a share page. Use a direct HTTP(S) URL to `/pack.zip`.

- **“Pack failed (DECLINED/FAILED_DOWNLOAD).”**
  - The player declined or could not download the pack. Fix the HTTP(S) URL and try again.

- **“Pack URL points to 0.0.0.0 / private IP.”**
  - `0.0.0.0` is a bind address and will never work for clients. Set a public HTTP(S) domain in `resource_pack.server.public-url`, `pack.public-base-url`, or `resource_pack.url`.

- **“502 Bad Gateway” from NGINX**
  - Ensure the embedded pack server is running (`resource_pack.server.enabled: true`) and NGINX proxies to `http://127.0.0.1:8123/pack.zip`.
  - Confirm the internal port is reachable from your proxy container/host.

- **SSL / certificate errors**
  - Make sure the pack host has a valid HTTPS certificate (certbot or your CDN). Clients will reject invalid SSL.

- **“Media is loading. Try again shortly.”**
  - The video is still processing; wait and retry.

- **Video not rendering**
  - Ensure a player is within render range (default: `general.maximum-distance-to-receive`).
  - Check screen diagnostics: `/mtc debug screen <screenName>`.
  - Verify item frames still exist and the screen was not broken.
  - Confirm media finished loading (watch console logs or run `/mtc media list`).

---

## 12) Files created by MovieTheatreCore

All files are stored under `plugins/MovieTheatreCore/`:

- `configuration/configuration.yml` — Main settings
- `translations/` — Language files
- `videos/` — Video metadata and frames
- `screens/` — Screen definitions
- `cache/videos/` — Media download cache
- `resourcepacks/pack.zip` — Generated resource pack
- `audio/` — Audio chunks
- `theatre/` — Theatre rooms & schedules
- `tmp/` — Temporary files

---

> On startup, MovieTheatreCore automatically copies this guide to `plugins/MovieTheatreCore/USER_GUIDE.md` if it is missing.
