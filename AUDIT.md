# MediaPlayer Audit

## Build/Test Status

* `mvn test` passes (ScalingMath unit tests).

## Root Causes

### BUG A — Replay requires screen recreation

**Root cause:** The playback session cleanup only cancelled the tick task and did not cancel in-flight async render jobs. It also always reloaded the screen thumbnail on stop, even when the stop was triggered as part of a “stop → play” restart. In-flight render jobs and thumbnail resets could still run after a new session started, causing stale map renderers and buffers to overwrite the new session output, making it appear that the screen would not replay.  
**Location:** `fr.xxathyx.mediaplayer.playback.PlaybackSession#stop`, `#tick`, `#renderFrame`, and the restart path in `PlaybackManager#start` (previously invoked `stop` with thumbnail refresh).

### BUG B — Scaling regression (videos no longer fill screens)

**Root cause:** The scaling math introduced rounding-based crop sizes (FIT/FILL) that could produce off-by-one crop widths/heights for common aspect ratios (e.g., 16:9 to 5x3 maps). This resulted in subtle borders and imperfect fill, especially when the destination dimensions were not multiples of the source aspect.  
**Location:** `fr.xxathyx.mediaplayer.render.ScalingMath#computeTransform` and `FrameScaler#scale` (git blame shows the regression introduced with the recent refactor adding `ScalingMath`).

## Fix Summary

* Added a `Scheduler` helper to keep Bukkit API calls on the main thread and to warn on misuse.
* Implemented a `PlaybackState` lifecycle and made `PlaybackSession#stop` idempotent.
* Tracked and cancelled async render tasks on stop/end to prevent stale frames from updating after a new session starts.
* Avoided thumbnail refresh on restart by allowing `PlaybackManager#start` to stop without showing thumbnails.
* Updated scaling math to use deterministic crop/scale calculations (ceil/floor) and refreshed tests with required sizing cases.
* Added a media library/cache layer with URL ingestion and LRU eviction plus opt-in audio pack generation for vanilla clients.

## Thread-Safety & Lifecycle Notes

* All Bukkit interactions from rendering are now routed through the scheduler to ensure main-thread execution.
* All session tasks are cancelled on stop/end, and resource-pack servers are closed per session to avoid leaks.

## Dependency/Resolver Audit (Pre-change)

### Current configuration keys + defaults

Configuration is generated in `fr.xxathyx.mediaplayer.configuration.Configuration#setup` (not from a bundled `config.yml`). Defaults currently include:  
`general.auto-update`=true, `general.auto-update-libraries`=true, `general.update-url`=`plugin.getDescription().getWebsite()`, `general.force-permissions`=true, `general.external-communication`=true, `general.packet-compression`=true, `general.alternative-server`="none", `general.language`="EN" (overridden by `Host` country code when available), `general.ping-sound`=true, `general.verify-files-on-load`=true, `general.save-streams`=false, `general.maximum-distance-to-receive`=10, `general.maximum-playing-videos`=5, `general.maximum-loading-videos`=1, `general.remove-screen-structure-on-restart`=false, `general.remove-screen-structure-on-end`=false.  
`video.screen-block`="BARRIER", `video.visible-screen-frames-support`=false, `video.glowing-screen-frames-support`=false.  
`sources.allowed-domains`=[], `sources.max-download-mb`=1024, `sources.download-timeout-seconds`=30, `sources.cache-max-gb`=5, `sources.youtube-resolver-path`="", `sources.youtube-cookies-path`="", `sources.youtube-extra-args`=[], `sources.ffprobe-path`="plugins/MediaPlayer/bin/ffprobe", `sources.ffmpeg-path`="plugins/MediaPlayer/bin/ffmpeg".  
`audio.enabled`=false, `audio.chunk-seconds`=2, `audio.codec`="vorbis", `audio.sample-rate`=48000.  
`resource_pack.url`="", `resource_pack.sha1`="".  
`advanced.delete-frames-on-loaded`=false, `advanced.delete-video-on-loaded`=false, `advanced.detect-duplicated-frames`=false, `advanced.ressemblance-to-skip`=100, `advanced.system`=`System.getSystemType().toString()`.  
**Locations:** `src/fr/xxathyx/mediaplayer/configuration/Configuration.java#setup`.

### Current resolver implementations + external process execution

* YouTube resolver: `fr.xxathyx.mediaplayer.media.MediaManager#resolveUrl` runs a configured resolver binary path (`sources.youtube-resolver-path`) using `ProcessBuilder`, with optional `--cookies` and extra args. It captures stdout/stderr after `waitFor()` to obtain a direct URL.  
* ffprobe metadata: `fr.xxathyx.mediaplayer.ffmpeg.FFprobeService#probe` builds a CLI call to `plugin.getFfprobe().getExecutablePath()` and executes it with `ProcessBuilder` to parse JSON metadata.  
* ffmpeg processing: `fr.xxathyx.mediaplayer.tasks.TaskAsyncLoadVideo#run` and `fr.xxathyx.mediaplayer.stream.Stream#start` build `ProcessBuilder` commands to the ffmpeg executable, and `fr.xxathyx.mediaplayer.audio.AudioPackManager#buildAudioPack` uses `ProcessBuilder` for audio pack generation.  
* Permissions hack: `fr.xxathyx.mediaplayer.video.Video#generateVideo` and `fr.xxathyx.mediaplayer.tasks.TaskAsyncLoadVideo#run` call `Runtime.getRuntime().exec("chmod -R 777 ...")` on ffmpeg/ffprobe files before running them.

### Expected binaries + current expected paths

* ffmpeg: `fr.xxathyx.mediaplayer.ffmpeg.Ffmpeg#getExecutablePath` checks `sources.ffmpeg-path` or defaults to `<pluginData>/libraries/ffmpeg` and falls back to `"ffmpeg"` on PATH.  
* ffprobe: `fr.xxathyx.mediaplayer.ffmpeg.Ffprobe#getExecutablePath` checks `sources.ffprobe-path` or defaults to `<pluginData>/libraries/ffprobe` and falls back to `"ffprobe"` on PATH.  
* YouTube resolver: expected via `sources.youtube-resolver-path` and invoked directly in `MediaManager#resolveUrl` (no default bundled binary).  
**Locations:** `src/fr/xxathyx/mediaplayer/ffmpeg/Ffmpeg.java#getExecutablePath`, `src/fr/xxathyx/mediaplayer/ffmpeg/Ffprobe.java#getExecutablePath`, `src/fr/xxathyx/mediaplayer/media/MediaManager.java#resolveUrl`.

### Known failure vectors (observed in reports)

* **NoClassDefFoundError net.bramp.ffmpeg.FFprobe:** The current codebase no longer uses the bramp FFprobe Java wrapper (replaced by `FFprobeService` calling the CLI). If older jars still include references, the stack trace would surface on metadata probing. Relevant metadata probing happens in `fr.xxathyx.mediaplayer.ffmpeg.FFprobeService#probe` and call sites in `fr.xxathyx.mediaplayer.video.Video#generateVideo`.  
* **Permission denied (noexec plugin dir):** ffmpeg/ffprobe are downloaded to `<pluginData>/libraries/` by `Main#onEnable` (async download in `fr.xxathyx.mediaplayer.Main#onEnable`) and executed from there; noexec mounts will prevent execution even if chmod is attempted.  
* **Read-only rootfs:** library downloads to `<pluginData>/libraries/` in `Ffmpeg#download`/`Ffprobe#download` fail when plugin data dir is read-only.  
* **YAML wildcard entry `"*.domain.com"` breaking Bukkit YAML:** `Configuration#getConfigFile` loads YAML and logs an `InvalidConfigurationException`. If `sources.allowed-domains` contains unquoted wildcard entries, Bukkit YAML can throw parsing errors, causing config load errors and downstream `MediaManager#isAllowedUrl` checks to misbehave or be skipped.  
**Locations:** `src/fr/xxathyx/mediaplayer/Main.java#onEnable`, `src/fr/xxathyx/mediaplayer/ffmpeg/Ffmpeg.java#download`, `src/fr/xxathyx/mediaplayer/ffmpeg/Ffprobe.java#download`, `src/fr/xxathyx/mediaplayer/configuration/Configuration.java#getConfigFile`, `src/fr/xxathyx/mediaplayer/media/MediaManager.java#isAllowedUrl`.
