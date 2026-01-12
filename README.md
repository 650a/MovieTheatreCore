# The fork that makes MediaPlayer into what it always should've been

<h1 align="center">
  <img src="https://i.postimg.cc/gj8Pj7mb/icon.png" alt="MediaPlayer">
</h1>

Allows you to play and use various medias such as videos on Minecraft.

Allows you to play and use various medias such as videos with audio on Minecraft (1.7-1.21).
For better performance, set network-compression-threshold to -1 in server.properties

Videos : https://youtu.be/LYVOkX7uQ5M

Livestreams : https://youtu.be/swcMQTto5rI

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

Screen list entries open a Screen Control GUI with Play/Stop/Pause/Resume and scaling shortcuts.

## Permissions

* `mediaplayer.command` - Access to `/mediaplayer`.
* `mediaplayer.screen.manage` - Create/delete screens and set scaling.
* `mediaplayer.playback` - Play/pause/stop/resume.
* `mediaplayer.admin` - Reload MediaPlayer.
* `mediaplayer.media.manage` - List cached media.
* `mediaplayer.media.admin` - Add/remove URL media and play direct URLs.

## Minimal configuration

```yaml
general:
  language: EN

sources:
  allowlist-mode: OFF
  allowed-domains:
    - "example.com"
  youtube-cookies-path: ""

audio:
  enabled: false

resource_pack:
  url: ""
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
* `sources.youtube-cookies-path` - Cookies file for YouTube bot checks.
* `sources.youtube-extra-args` - Extra yt-dlp arguments.
* `audio.enabled` - Enable audio resource pack generation (vanilla clients).
* `audio.chunk-seconds` - Chunk size for audio slicing.
* `audio.codec` - Audio codec (vorbis default).
* `audio.sample-rate` - Audio sample rate (48000 default).
* `resource_pack.url` - External host URL for packs (required when audio is enabled).
* `resource_pack.sha1` - Last generated pack SHA1.
* `advanced.delete-frames-on-loaded` - Legacy toggle, rarely needed.
* `advanced.delete-video-on-loaded` - Legacy toggle, rarely needed.
* `advanced.detect-duplicated-frames` - Experimental frame deduplication.
* `advanced.ressemblance-to-skip` - Threshold for deduplication skip.
* `sources.deno-path` - Optional deno path for yt-dlp JS challenges.

Screens store per-screen data in `screens/<uuid>/<uuid>.yml`, including:

* `screen.scale-mode` - `FIT`, `FILL`, or `STRETCH` (default: `FIT`).

## Scaling modes

* **FIT**: Preserve aspect ratio, letterbox/pillarbox as needed.
* **FILL**: Preserve aspect ratio, crop overflow to fill the screen.
* **STRETCH**: Ignore aspect ratio and fill the entire screen.

## Pterodactyl noexec staging

MediaPlayer always stages binaries to `/tmp/mediaplayer/bin` before execution. This avoids `noexec` mounts in
`plugins/MediaPlayer` on Pterodactyl/Wings. If `/tmp` is also `noexec`, `/mp diagnose` will report the error and you
must provide an executable temp path via `-Djava.io.tmpdir=/path`.

## YouTube cookies

YouTube often requires a cookies file for yt-dlp. Set `sources.youtube-cookies-path` to a Netscape-format cookies
file and keep it readable by the server. MediaPlayer passes `--cookies <path>` when the file exists.

## Diagnose command

Run `/mp diagnose` to verify OS/arch, executable staging status, detected binary versions (ffmpeg/ffprobe/yt-dlp/deno),
cookies file state, and the last resolver exit code/stderr.

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
Audio also requires `resource_pack.url` to be configured (players must accept the server resource pack).
If `resource_pack.url` is empty, MediaPlayer logs a warning and disables audio gracefully.
Large videos can bloat resource packs; prefer short clips or host packs externally.
