# MovieTheatreCore User Guide

## What MovieTheatreCore is
MovieTheatreCore lets you build in‑game movie theatres with one or more screens, then play videos and schedule shows for groups of players. It solves the “how do we host synchronized video for players” problem by handling downloads, playback, and (optional) audio resource packs in one plugin.

## Supported Minecraft versions
- **Legacy:** 1.8.8
- **Modern:** 1.13+ through the latest releases

> The plugin supports legacy clients while also working on modern Paper/Spigot servers.

## Installation

### Paper (recommended)
1. Download `MovieTheatreCore-<version>.jar` from the official marketplace.
2. Place the jar in your server’s `plugins/` folder.
3. Start the server once to generate the data folder.
4. Run `/mtc deps status` to verify ffmpeg/ffprobe/yt-dlp/deno.

### Spigot
1. Download the jar from the official marketplace.
2. Place it in `plugins/` and start the server.
3. Run `/mtc deps status` and confirm dependencies are installed.

### Pterodactyl (or any container host)
1. Upload the jar to `plugins/` and start the server once.
2. Make sure you have **at least 512 MB free disk**, recommended **2 GB+**.
3. Run `/mtc deps status` to confirm dependencies.
4. If your container mounts `plugins/` as `noexec`, set an exec‑safe path in `configuration.yml`:
   ```yaml
   dependencies:
     install:
       exec-directory: "/home/container/MovieTheatreCore/runtime/bin"
   ```

## First-time setup checklist
1. **Start the server once** so the plugin creates its folders.
2. **Verify dependencies** with `/mtc deps status`.
3. **Create a screen** with `/mtc screen create`.
4. **Add media** with `/mtc media add`.
5. **Test playback** with `/mtc play <screen> media <name>`.
6. (Optional) **Enable audio** by setting `audio.enabled: true` and configuring `resource_pack`.

## Command reference

### `/mtc`
Shows the full command list.

**Example:**
```
/mtc
```

### `/mtc screen`
Create, list, and delete screens.

**Create a screen**
```
/mtc screen create Lobby 6 4
```

**List screens (opens GUI for players)**
```
/mtc screen list
```

**Delete a screen**
```
/mtc screen delete Lobby
```

### `/mtc media`
Manage the media library (downloaded URLs).

**Add media from a direct URL**
```
/mtc media add Trailer https://example.com/trailer.mp4
```

**Add media from YouTube by ID**
```
/mtc media add Trailer yt dQw4w9WgXcQ
```

**Add media with a custom ID**
```
/mtc media add Trailer https://example.com/trailer.mp4 --id trailer-2024
```

**List media**
```
/mtc media list
```

**Remove media**
```
/mtc media remove Trailer
```

### `/mtc theatre`
Create theatre rooms, schedule shows, and play a room’s show.

**Create a room with multiple screens**
```
/mtc theatre room create MainRoom ScreenA ScreenB ScreenC
```

**Schedule a show**
```
/mtc theatre schedule add MainRoom 19:30 Trailer repeat=daily
```

**List schedules**
```
/mtc theatre schedule list MainRoom
```

**Play a room immediately**
```
/mtc theatre play MainRoom Trailer
```

**Stop a room**
```
/mtc theatre stop MainRoom
```

## Step-by-step example: building a theatre with multiple screens
1. **Create screens**
   ```
   /mtc screen create ScreenA 8 4
   /mtc screen create ScreenB 8 4
   /mtc screen create ScreenC 8 4
   ```
2. **Create a theatre room and assign screens**
   ```
   /mtc theatre room create MainRoom ScreenA ScreenB ScreenC
   ```
3. **Add your media**
   ```
   /mtc media add Feature https://example.com/feature.mp4
   ```
4. **Schedule a daily show**
   ```
   /mtc theatre schedule add MainRoom 20:00 Feature repeat=daily
   ```
5. **Start it right now (optional)**
   ```
   /mtc theatre play MainRoom Feature
   ```

## How video, audio, and resource packs work (high level)
- **Video:** The plugin downloads the media URL into its cache, prepares it with ffprobe, and renders it on the screen’s map grid.
- **Audio (optional):** If `audio.enabled: true`, MovieTheatreCore creates audio chunks and builds a resource pack that clients can download.
- **Resource packs:** You can host the pack yourself (`resource_pack.url`) or enable the built‑in pack server (`resource_pack.server.enabled: true`).

## Common errors and fixes

**“URL not allowed by sources.allowlist-mode (STRICT).”**
- Add the domain to `sources.allowed-domains` or set `sources.allowlist-mode: OFF`.

**“YouTube resolver not available.”**
- Run `/mtc deps status` and make sure `yt-dlp` is installed or auto‑install is enabled.

**“Pack URL not configured.”**
- Set `resource_pack.url` **or** enable the built‑in pack server in `resource_pack.server.enabled`.

**“Media is loading. Try again shortly.”**
- The plugin is still building metadata for that video. Wait a moment and retry.

**“Unknown screen / unknown media / unknown room.”**
- Use `/mtc screen list` or `/mtc media list` to confirm names, and double‑check the room name in your commands.

## Performance and hardware recommendations
- **CPU:** Video processing is CPU‑intensive. A modern multi‑core CPU is recommended.
- **Disk:** Use an SSD and keep **2 GB+** free for cached downloads and generated packs.
- **Memory:** Ensure your server has enough headroom for video processing alongside other plugins.
- **Network:** For large audiences, host the resource pack on a stable, fast URL.

## Files and folders MovieTheatreCore creates
All files live under `plugins/MovieTheatreCore/` unless configured otherwise.

- `configuration/configuration.yml` — Main plugin settings.
- `translations/EN.yml` — Language file.
- `videos/` — Video metadata and extracted frames.
- `screens/` — Screen definitions.
- `cache/videos/` — Download cache for media URLs.
- `resourcepacks/` — Generated audio packs.
- `audio/` — Audio chunks for media.
- `theatre/` — Theatre rooms and schedules.
- `tmp/` — Temporary files for downloads and processing.

## Need more help?
- Run `/mtc diagnose` to review environment and dependency status.
- Check `/mtc deps status` and `/mtc pack status` for dependency and pack health.
