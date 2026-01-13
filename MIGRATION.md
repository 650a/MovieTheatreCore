# Migration Guide

## Overview

This release introduces a simplified command set, a new playback pipeline with a screen state machine, and explicit scaling modes.

## Command changes

* **New root command:** `/movietheatrecore` (aliases `/mtc`, `/theatre`, `/mp`).
* Legacy commands such as `/video`, `/screen`, `/image`, and their GUI variants are no longer registered.

Use:

* `/mtc screen create <name> <w> <h>`
* `/mtc screen delete <name>`
* `/mtc screen list`
* `/mtc media add <name> <url>`
* `/mtc media remove <name>`
* `/mtc media list`
* `/mtc play <screen> <source>`
* `/mtc play <screen> media <name> [--noaudio]`
* `/mtc play <screen> url <url> [--noaudio]`
* `/mtc stop <screen>`
* `/mtc pause <screen>` / `/mtc resume <screen>`
* `/mtc scale <screen> <fit|fill|stretch>`
* `/mtc reload`

## Screen scaling

* Each screen now stores a `screen.scale-mode` key in `screens/<uuid>/<uuid>.yml`.
* Default is **FIT** (preserves aspect ratio with letterboxing).
* Scaling math now uses exact crop/scale calculations to avoid off-by-one borders.

## Resource usage

* Frame deletion is now disabled to preserve scaling quality across screens. The `plugin.delete-frames-on-loaded` configuration value is ignored.

## GUI changes

* The Screen Manager GUI is available via `/mtc screen list`.
* The Now Playing GUI is accessible by clicking a screen entry.
* GUI actions now enforce the same permissions as the commands.

## What you need to do

1. Update permissions to include the new nodes:
   * `movietheatrecore.command`
   * `movietheatrecore.screen.manage`
   * `movietheatrecore.playback`
   * `movietheatrecore.admin`
   * `movietheatrecore.media.manage`
   * `movietheatrecore.media.admin`
2. (Optional) Set scaling mode per screen with `/mtc scale <screen> <fit|fill|stretch>`.
3. Configure `media.allowed-domains` (or `sources.allowed-domains`) before using URL-based media.
4. If you want vanilla audio, enable `audio.enabled` and provide `resource_pack.url`.
5. No configuration migration is required beyond ensuring `screen.scale-mode` exists (auto-added on load).

## Configuration format update

MovieTheatreCore now uses a simplified configuration structure with grouped sections:

* `general` for plugin behavior and limits.
* `video` for screen rendering options.
* `sources` for media download controls.
* `audio` for audio slicing settings.
* `resource_pack` for the pack hosting URL and SHA1.
* `advanced` for legacy/rare toggles.

Legacy keys like `plugin.langage`, `plugin.maximum-distance-to-receive`, and `media.allowed-domains` remain supported.
On load, MovieTheatreCore maps existing legacy keys into the new structure and preserves your values.

### Dependency + allowlist updates

* `sources.allowlist-mode` is new and defaults to `OFF` (no blocking). Existing configs with `sources.allowed-domains`
  are migrated to `sources.allowlist-mode: STRICT` so behavior remains unchanged.
* `sources.deno-path` is new (optional). If set, MovieTheatreCore uses it for yt-dlp JS challenges.
* If `sources.youtube-resolver-path` is empty, MovieTheatreCore now downloads and stages `yt-dlp` automatically.

### Required changes

* If you use the allowlist, explicitly set `sources.allowlist-mode: STRICT` and list domains in `sources.allowed-domains`.
* For YouTube reliability, set `sources.youtube-cookies-path` to a valid cookies file (see README).

### Required changes

* If you use audio, set `resource_pack.url`. When it is empty, MovieTheatreCore logs
  `audio enabled but no pack host-url configured` and disables audio gracefully.
* The default language is now English (`general.language: EN`). If the configured translation file
  is missing, MovieTheatreCore falls back to English and logs a warning.
