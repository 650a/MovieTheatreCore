# MovieTheatreCore User Guide

MovieTheatreCore lets you build in-game cinemas with multiple screens, schedule shows, and play synchronized video + audio for nearby players. Everything is automatic: media downloads, resource packs, and audio syncing are handled for you.

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

## 2) Pterodactyl/Wings requirements (resource pack hosting)

MovieTheatreCore serves a **single rolling resource pack** that includes audio for all media. Players download it from an HTTPS URL you provide.

### Requirements
- You **must** provide a public HTTPS URL for the pack.
- The pack must be reachable at:
  ```
  https://your-pack-domain.example/pack.zip
  ```

### Recommended setup (Pterodactyl + reverse proxy)
1. Open or proxy the internal pack server port (default `8123`).
2. Put a reverse proxy (NGINX, Caddy, Traefik, etc.) in front of it.
3. Ensure the proxy provides HTTPS.
4. Set the base URL in the config:
   ```yaml
   pack:
     public-base-url: "https://your-pack-domain.example"
   ```

> The plugin runs behind NAT in Pterodactyl/Wings. **Do not** use `localhost` or private IPs for the public URL.

---

## 3) DNS setup for the pack subdomain

1. Create a DNS record (A or CNAME) for a subdomain, e.g.:
   - `pack.yourdomain.example` → your proxy/server IP
2. Configure HTTPS on that subdomain.
3. Set `pack.public-base-url` to the **HTTPS** address.

---

## 4) Create a screen

```
/mtc screen create Lobby 6 4
/mtc screen list
```

---

## 5) Add media

Supported media sources:
- **YouTube links** (via yt-dlp)
- **Direct MP4 / WEBM URLs**
- **MediaFire direct download links** (must point directly to the file)
- **M3U8 livestreams** (direct playlist URLs)

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

---

## 7) Video + audio behavior (automatic)

- **Audio is always automatic.** There is no config toggle and no manual audio commands.
- If a video contains audio, MovieTheatreCore extracts it and syncs it with the video.
- Audio is heard **only by players within range** of the screen.
- Playback **waits until the resource pack is applied** before starting.

If a video has **no audio stream**, it will play silently (video still works).

---

## 8) Resource packs (fully automatic)

MovieTheatreCore builds **one rolling resource pack** for **all media**.

It automatically rebuilds when:
- Media is added
- Media is removed
- Media is updated

Rules:
- The pack is applied **only** when a player is within range of an active screen **and** a video is playing.
- If the pack fails to download, playback is stopped and admins are notified.
- Keep `resource_pack.server.enabled: true` (default) so the server can host the pack.

Pack URL format (required):
```
{pack.public-base-url}/pack.zip
```

Debug command:
```
/mtc debug pack
```

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
- If cookies are missing or expired, MovieTheatreCore will warn you.

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
  - Set `pack.public-base-url` to a public HTTPS domain.

- **“Pack URL returned HTML.”**
  - You used a share page. Use a direct HTTPS URL to `/pack.zip`.

- **“Pack failed (DECLINED/FAILED_DOWNLOAD).”**
  - The player declined or could not download the pack. Fix the HTTPS URL and try again.

- **“Media is loading. Try again shortly.”**
  - The video is still processing; wait and retry.

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
