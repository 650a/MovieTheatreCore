# MovieTheatreCore

MovieTheatreCore is a commercial, closed-source multi-room cinema plugin for Minecraft servers. It lets you build theatres with multiple screens, schedule shows, and stream media across your network with reliable, container-friendly dependency management.

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
- Audio resource pack generation with optional embedded pack server.
- Compatible with Minecraft 1.8.8 (Java 17) through the latest releases.

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

### VPS / Dedicated server

1. **Install the plugin** into `plugins/` and start the server.
2. **Dependency installer** will stage binaries in `plugins/MovieTheatreCore/runtime/bin`.
3. **Confirm** with `/mtc deps status` and proceed to create screens.

### YouTube cookies (optional)

If YouTube playback requires cookies, export a cookies file and set:

```yaml
youtube:
  cookies-path: "plugins/MovieTheatreCore/cookies.txt"
```

MovieTheatreCore passes `--cookies <path>` to the resolver when the file exists.

### Audio resource packs (high level)

When `audio.enabled: true`, MovieTheatreCore generates an on-demand resource pack with per-media audio chunks. You can:

- Provide a static pack URL (set `resource_pack.url`), **or**
- Enable the built-in pack server (`resource_pack.server.enabled: true`).

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

### Updater 404 / missing release asset

- Ensure the update URL is set to the official marketplace download endpoint.
- Run `/mtc update check` to see the URL and status.
- If you do not want auto-updates, set `general.auto-update: false`.
