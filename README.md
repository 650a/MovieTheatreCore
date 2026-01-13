# MovieTheatreCore

MovieTheatreCore is a commercial, closed-source multi-room cinema plugin for Minecraft servers. It lets you build theatres with multiple screens, schedule shows, and stream media across your network with reliable, container-friendly dependency management.

## User Guide

See the full [MovieTheatreCore User Guide](docs/USER_GUIDE.md) for installation, setup, and command examples.

## Commercial License & Ownership

MovieTheatreCore is proprietary software owned by 650a. Usage is licensed per server/network and is subject to the terms in [`LICENSE`](LICENSE). License violations void permission to use the software. No redistribution, resale, modification, or public forks are permitted.

### License Summary

- Licensed per server or network instance.
- Reverse engineering, modification, redistribution, resale, and public forks are prohibited.
- Personal backups are permitted for recovery purposes only.
- License violations immediately terminate permission to use the software.

## Marketplace Usage (SpigotMC / BuiltByBit)

MovieTheatreCore is distributed only through official marketplace listings. Installations must comply with both the MovieTheatreCore license and the SpigotMC / BuiltByBit marketplace terms.

## Highlights

- Multi-room theatres with scheduled shows and audience zones.
- Screen, media, and playback management from a single command surface.
- Automatic, offline-friendly dependency installer for ffmpeg/ffprobe/yt-dlp/deno.
- Automatic resource pack generation with a single rolling pack for all media.
- Compatible with Minecraft 1.8.8 (Java 17) through the latest releases.

## Permissions

MovieTheatreCore enforces permissions on every command and GUI action:

- `movietheatrecore.command` — Access `/mtc`.
- `movietheatrecore.admin` — Reload and admin tool access (`/mtc admin`).
- `movietheatrecore.screen.manage` — Create/delete screens and change scale.
- `movietheatrecore.playback` — Play/pause/stop playback.
- `movietheatrecore.media.manage` — View/manage cached media entries.
- `movietheatrecore.media.admin` — Add/remove media URLs and play direct URLs.

## QUICKSTART

### Pterodactyl/Wings (container)

1. **Install the plugin jar**
   - Download from the official marketplace listing and place `MovieTheatreCore-<version>.jar` (or `MovieTheatreCore.jar`) in `plugins/`.

2. **Disk space**
   - Minimum free space: **512 MB** (configurable via `dependencies.install.min-free-mb`).
   - Recommended: **2 GB+** free to allow ffmpeg/ffprobe downloads and media cache growth.

3. **Start the server once**
   - MovieTheatreCore will create `plugins/MovieTheatreCore/` and install runtime binaries into:
     - `plugins/MovieTheatreCore/runtime/bin` (default)
     - If the plugin folder is mounted `noexec`, it falls back to the exec-safe path in `dependencies.install.exec-directory` (default: `/home/container/MovieTheatreCore/runtime/bin`).
   - Temporary files are written to `plugins/MovieTheatreCore/tmp` (or `advanced.tmp-dir`).

4. **Verify dependencies**
   - Run `/mtc deps status` to confirm ffmpeg/ffprobe/yt-dlp/deno.

5. **Configure the public pack URL (required)**
   - Create a pack subdomain (e.g., `pack.yourdomain.example`) with HTTPS.
   - Set it in `configuration.yml`:
     ```yaml
     pack:
       public-base-url: "https://pack.yourdomain.example"
     ```
   - The pack will be served at `https://pack.yourdomain.example/pack.zip`.

### VPS / Dedicated server

1. **Install the plugin** into `plugins/` and start the server.
2. **Dependency installer** will stage binaries in `plugins/MovieTheatreCore/runtime/bin`.
3. **Confirm** with `/mtc deps status` and proceed to create screens.

### YouTube cookies (optional)

If YouTube playback is blocked, export cookies from your browser and save them as:

```
plugins/MovieTheatreCore/youtube-cookies.txt
```

MovieTheatreCore will warn you if the cookies are expired and continue to run yt-dlp in tiered mode.

### Resource packs (fully automatic)

MovieTheatreCore automatically extracts audio for new media and builds a single rolling resource pack. Players download it from:

```
{pack.public-base-url}/pack.zip
```

Set the base URL in `configuration.yml`:

```yaml
pack:
  public-base-url: "https://pack.yourdomain.example"
```

The built-in pack server is still used internally, but the **public URL must be HTTPS** and reachable by players.

## Auto-installed Dependencies

MovieTheatreCore can automatically download and manage runtime binaries for:

- **ffmpeg / ffprobe**
- **yt-dlp**
- **deno**

These binaries are installed into `plugins/MovieTheatreCore/runtime/bin` (or the configured exec-safe path) when auto-install is enabled.

## External Binaries Disclaimer

MovieTheatreCore ships with an installer for third-party tools, but those binaries are provided by their respective authors. Your use of ffmpeg/ffprobe, yt-dlp, and deno is governed by their own licenses and terms. You are responsible for compliance and any required redistribution notices in your environment.

## TROUBLESHOOTING

### “No space left on device”

- Ensure the plugin data directory is on a volume with enough space.
- Increase free space or lower caches.
- Adjust `dependencies.install.min-free-mb` if you have limited disk.
- Set `advanced.tmp-dir` to a larger, persistent disk path (avoid small `/tmp` tmpfs volumes).

### “Permission denied” / “noexec”

- If the plugin folder is mounted `noexec`, MovieTheatreCore falls back to `dependencies.install.exec-directory`.
- Make sure the fallback path supports execution and is writable.
- You can override it in `configuration.yml`:

```yaml
dependencies:
  install:
    exec-directory: "/home/container/MovieTheatreCore/runtime/bin"
```

### Resource pack not downloading

- Confirm `pack.public-base-url` is set to a **public HTTPS** URL.
- Run `/mtc debug pack` to see pack size, SHA1, and curl test output.
- Verify the pack URL returns a ZIP:
  ```
  curl -I https://your-pack-domain.example/pack.zip
  ```

### Updater 404 / missing release asset

- Ensure the update URL is set to the official marketplace download endpoint.
- Run `/mtc update check` to see the URL and status.
- If you do not want auto-updates, set `general.auto-update: false`.
