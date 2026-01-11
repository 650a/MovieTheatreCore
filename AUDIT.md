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

## Thread-Safety & Lifecycle Notes

* All Bukkit interactions from rendering are now routed through the scheduler to ensure main-thread execution.
* All session tasks are cancelled on stop/end, and resource-pack servers are closed per session to avoid leaks.
