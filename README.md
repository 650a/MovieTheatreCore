# The fork that makes MediaPlayer into what it always should've been

<h1 align="center">
  <img src="https://i.postimg.cc/gj8Pj7mb/icon.png" alt="MediaPlayer">
</h1>

Allows you to play and use various medias such as videos on Minecraft.

Allows you to play and use various medias such as videos with audio on Minecraft (1.7-1.21).
For better performance, set network-compression-threshold to -1 in server.properties

Videos : COMING SOON

Livestreams : COMING SOON

## Commands

Root command: `/mediaplayer` (alias `/mp`).

* `/mp screen create <name> <w> <h>` - Create a screen in front of the player.
* `/mp screen delete <name>` - Delete a screen and its files.
* `/mp screen list` - Open the Screen Manager GUI.
* `/mp media add <name> <url>` - Download and cache media from an allowed URL.
* `/mp media remove <name>` - Remove a cached media entry.
* `/mp media list` - List cached media entries.
* `/mp play <screen> <source>` - Play a video on a screen.
* `/mp play <screen> media <name> [--noaudio]` - Play cached media.
* `/mp play <screen> url <url> [--noaudio]` - Play a direct URL (admin-only).
* `/mp stop <screen>` - Stop playback on a screen.
* `/mp pause <screen>` - Pause playback on a screen.
* `/mp resume <screen>` - Resume playback on a screen.
* `/mp scale <screen> <fit|fill|stretch>` - Change scaling mode.
* `/mp reload` - Reload MediaPlayer screens and stop sessions.
* `/mp diagnose` - Show dependency and resolver status.
* `/mp pack status` - Show pack server status and current pack URL/SHA1.
* `/mp pack rebuild` - Force a pack rebuild.
* `/mp pack url` - Print pack URL + SHA1.
* `/mp theatre room create <name> [screen...]` - Create a theatre room (optional screen list).
* `/mp theatre room delete <name>` - Delete a theatre room.
* `/mp theatre schedule add <room> <HH:MM> <mediaId> [repeat=daily|weekly|none]` - Schedule a show.
* `/mp theatre schedule remove <room> <index|id>` - Remove a scheduled show.
* `/mp theatre schedule list <room>` - List scheduled shows.
* `/mp theatre play <room> <mediaId>` - Start a show in a room.
* `/mp theatre stop <room>` - Stop a show in a room.
* `/mp theatre doctor` - Theatre self-check and dependency status.

Screen list entries open a Screen Control GUI with Play/Stop/Pause/Resume and scaling shortcuts.

## Permissions

* `mediaplayer.command` - Access to `/mediaplayer`.
* `mediaplayer.screen.manage` - Create/delete screens and set scaling.
* `mediaplayer.playback` - Play/pause/stop/resume.
* `mediaplayer.admin` - Reload MediaPlayer.
* `mediaplayer.media.manage` - List cached media.
* `mediaplayer.media.admin` - Add/remove URL media and play direct URLs.
* `mediaplayer.pack.manage` - View/rebuild pack status and URL.
* `mediaplayer.theatre.room.create` - Create theatre rooms.
* `mediaplayer.theatre.room.delete` - Delete theatre rooms.
* `mediaplayer.theatre.schedule.add` - Add theatre schedule entries.
* `mediaplayer.theatre.schedule.remove` - Remove theatre schedule entries.
* `mediaplayer.theatre.schedule.list` - List theatre schedule entries.
* `mediaplayer.theatre.play` - Start a theatre show.
* `mediaplayer.theatre.stop` - Stop a theatre show.
* `mediaplayer.theatre.doctor` - Run theatre diagnostics.

## Minimal configuration

```yaml
general:
  language: EN

sources:
  allowlist-mode: OFF
  allowed-domains:
    - "example.com"

dependencies:
  prefer-system:
    ffmpeg: true
    ffprobe: true
    yt-dlp: true
    deno: true
  paths:
    ffmpeg: ""
    ffprobe: ""
    ytdlp: ""
    deno: ""
  install:
    directory: ""
    auto-install: true
    auto-update: true
    update-check-hours: 24

youtube:
  use-js-runtime: true
  cookies-path: "plugins/MediaPlayer/cookies.txt"
  require-cookies: false

audio:
  enabled: false

theatre:
  enabled: true
  max-shows: 5
  tick-interval: 1
  audio-update-interval: 1
  audience-check-interval: 20
  default-zone-radius: 16
  schedule-check-interval-seconds: 30

resource_pack:
  url: ""
  server:
    enabled: true
    bind: "0.0.0.0"
    port: 8123
    public-url: ""
```

## Configuration keys

* `general.maximum-distance-to-receive` - Viewer range for playback updates.
* `general.maximum-playing-videos` - Max simultaneous playback sessions.
* `general.maximum-loading-videos` - Max concurrent media loads.
* `general.remove-screen-structure-on-restart` - Remove screens on restart.
* `video.screen-block` - Block used for screen structures.
* `video.visible-screen-frames-support` - Whether item frames are visible.
* `video.glowing-screen-frames-support` - Whether item frames glow.
* `sources.allowlist-mode` - `OFF` (no blocking) or `STRICT` (enforce allowlist).
* `sources.allowed-domains` - URL whitelist (only used when allowlist-mode is `STRICT`).
* `sources.max-download-mb` - Maximum download size.
* `sources.download-timeout-seconds` - Download timeout per URL.
* `sources.cache-max-gb` - LRU cache size cap.
* `sources.youtube-resolver-path` - Optional yt-dlp resolver path.
* `sources.youtube-cookies-path` - Legacy cookies file path (prefer `youtube.cookies-path`).
* `sources.youtube-extra-args` - Extra yt-dlp arguments.
* `dependencies.prefer-system.ffmpeg` - Prefer system ffmpeg when valid.
* `dependencies.prefer-system.ffprobe` - Prefer system ffprobe when valid.
* `dependencies.prefer-system.yt-dlp` - Prefer system yt-dlp when valid.
* `dependencies.prefer-system.deno` - Prefer system deno when valid.
* `dependencies.paths.ffmpeg` - Optional override path for ffmpeg.
* `dependencies.paths.ffprobe` - Optional override path for ffprobe.
* `dependencies.paths.ytdlp` - Optional override path for yt-dlp.
* `dependencies.paths.deno` - Optional override path for deno.
* `dependencies.install.directory` - Override install directory (defaults to `/tmp/mediaplayer/bin`).
* `dependencies.install.auto-install` - Automatically download missing dependencies.
* `dependencies.install.auto-update` - Auto-update downloaded dependencies.
* `dependencies.install.update-check-hours` - Hours between update checks.
* `youtube.use-js-runtime` - Enable Deno JS runtime for yt-dlp.
* `youtube.cookies-path` - Cookies file for YouTube bot checks.
* `youtube.require-cookies` - Require cookies file before resolving YouTube URLs.
* `audio.enabled` - Enable audio resource pack generation (vanilla clients).
* `audio.chunk-seconds` - Chunk size for audio slicing.
* `audio.codec` - Audio codec (vorbis default).
* `audio.sample-rate` - Audio sample rate (48000 default).
* `theatre.enabled` - Enable theatre mode features.
* `theatre.max-shows` - Maximum concurrent theatre shows.
* `theatre.tick-interval` - Tick interval for theatre show monitoring.
* `theatre.audio-update-interval` - Audio listener refresh interval (ticks).
* `theatre.audience-check-interval` - Audience zone refresh interval (ticks).
* `theatre.default-zone-radius` - Default audio zone radius when room zone is unset.
* `theatre.schedule-check-interval-seconds` - Schedule polling interval.
* `resource_pack.url` - External host URL for packs when internal server is disabled.
* `resource_pack.sha1` - Last generated pack SHA1.
* `resource_pack.assets-hash` - Hash of current audio assets (used for rebuild detection).
* `resource_pack.last-build` - Timestamp (ms) of last pack build.
* `resource_pack.server.enabled` - Enable internal pack server.
* `resource_pack.server.bind` - Bind address for internal pack server.
* `resource_pack.server.port` - Bind port for internal pack server.
* `resource_pack.server.public-url` - Public base URL override (reverse proxy/domain).
* `advanced.delete-frames-on-loaded` - Legacy toggle, rarely needed.
* `advanced.delete-video-on-loaded` - Legacy toggle, rarely needed.
* `advanced.detect-duplicated-frames` - Experimental frame deduplication.
* `advanced.ressemblance-to-skip` - Threshold for deduplication skip.
* `sources.deno-path` - Optional deno path for yt-dlp JS challenges.

Screens store per-screen data in `screens/<uuid>/<uuid>.yml`, including:

* `screen.scale-mode` - `FIT`, `FILL`, or `STRETCH` (default: `FIT`).
* `screen.audio.radius` - Audio radius around the speaker location.
* `screen.audio.speaker.*` - Speaker location (world/x/y/z). Defaults to screen center.

## Theatre mode

Theatre mode adds rooms with isolated audio zones and scheduled shows. Rooms map to one or more existing screens.
Create rooms, assign screens (optional during creation), then schedule shows or start them manually.

Rooms and schedules are persisted in `plugins/MediaPlayer/theatre/rooms.yml` and `plugins/MediaPlayer/theatre/schedules.yml`.

### Example rooms.yml

```yaml
rooms:
  4f6aa5d4-1f1e-4ed9-9c32-3b3f7cc33c4a:
    name: MainHall
    screens:
      - ScreenA
      - ScreenB
    audio-zone:
      world: world
      x: 100.5
      y: 64.0
      z: -30.5
      radius: 20
    seats:
      - "world,101.0,64.0,-29.0"
      - "world,102.0,64.0,-29.0"
```

### Example schedules.yml

```yaml
rooms:
  4f6aa5d4-1f1e-4ed9-9c32-3b3f7cc33c4a:
    entries:
      5d6f8b07-4f86-4e4f-b9b5-1c5d64f4e7ac:
        media-id: trailer
        repeat: DAILY
        enabled: true
        next-run: "2025-01-01 18:30"
        last-triggered: "2024-12-31 18:30"
```

## Scaling modes

* **FIT**: Preserve aspect ratio, letterbox/pillarbox as needed.
* **FILL**: Preserve aspect ratio, crop overflow to fill the screen.
* **STRETCH**: Ignore aspect ratio and fill the entire screen.

## Zero-config YouTube setup

MediaPlayer can download yt-dlp, Deno, ffmpeg, and ffprobe automatically on first use. By default it installs to
`/tmp/mediaplayer/bin` (Pterodactyl-friendly, no root required) and falls back to the plugin `bin` directory if it is
executable. Use `/mp deps status` to see which binaries were selected and their versions, and `/mp deps reinstall` to
force re-downloads.

Binary selection order is:
1) Config override path
2) System PATH
3) MediaPlayer-managed cache directory

## Pterodactyl noexec installs

MediaPlayer prefers `/tmp/mediaplayer/bin` to avoid `noexec` mounts in `plugins/MediaPlayer` on Pterodactyl/Wings.
If both `/tmp` and the plugin directory are `noexec`, `/mp deps status` will report the error and you must provide an
executable path via `dependencies.install.directory`.

## YouTube cookies

YouTube often requires a cookies file for yt-dlp. Set `youtube.cookies-path` to a Netscape-format cookies
file and keep it readable by the server. MediaPlayer passes `--cookies <path>` when the file exists and can
require cookies when `youtube.require-cookies` is enabled.

## Diagnose command

Run `/mp deps status` or `/mp diagnose` to verify OS/arch, install directory status, detected binary versions
(ffmpeg/ffprobe/yt-dlp/deno), cookies file state, and the last resolver exit code/stderr.

## Testing

* Unit tests: `mvn test` (covers scaling math).
* Integration: run a Paper test server, place a 5x3 screen, and run the acceptance commands in order:
  1. `/mp play <screen> <video>` with `FIT`.
  2. `/mp scale <screen> fill` and replay.
  3. `/mp stop <screen>` then replay.


### How to use MediaPlayer API

First of all, you need to add MediaPlayer.jar plugin as a library in your Java project, and add it as a plugin
into you server plugin folder.

### Brows into plugin informations with MediaPlayerAPI class :

First step : ```import fr.xxathyx.mediaplayer.api.MediaPlayerAPI;```

Then call ```getPlugin``` method in order to gain access to live running plugin informations.

### A player interacts with a screen, use the PlayerInteractScreenEvent to detect it :

You will need to : ```import fr.xxathyx.mediaplayer.api.listeners.PlayerInteractScreenEvent```

Then you can access informations such as the screen that as been touched with ```getScreen```,
the player itself : ```getPlayer``` and the click location with ```getCursorX```, ```getCursorY```.

Notice that those integers represent the real location of the click according to the size of the content displayed insed it.


### How to have audio :

In order to have audio, users must simply set their 'Server Resource Pack' to ```Prompt``` or ```Enabled```.
Vanilla audio playback is **opt-in** and requires `audio.enabled: true` so MediaPlayer can generate resource packs.
Audio uses the internal pack server by default (`resource_pack.server.enabled: true`) and exposes the pack at `/pack.zip`.
If the internal server is disabled, configure `resource_pack.url` to an externally hosted pack.
If no pack URL is available, MediaPlayer logs a warning and disables audio gracefully.
Large videos can bloat resource packs; prefer short clips or host packs externally.

## Troubleshooting audio packs

* If `/mp pack status` shows the server is not running, verify the bind/port settings and look for "port already in use" logs.
* Behind a reverse proxy, set `resource_pack.server.public-url` so clients receive the correct URL.
* If audio is disabled, pack commands still work but playback will skip audio.
