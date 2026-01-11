# Migration Guide

## Overview

This release introduces a simplified command set, a new playback pipeline with a screen state machine, and explicit scaling modes.

## Command changes

* **New root command:** `/mediaplayer` (alias `/mp`).
* Legacy commands such as `/video`, `/screen`, `/image`, and their GUI variants are no longer registered.

Use:

* `/mp screen create <name> <w> <h>`
* `/mp screen delete <name>`
* `/mp screen list`
* `/mp media add <name> <url>`
* `/mp media remove <name>`
* `/mp media list`
* `/mp play <screen> <source>`
* `/mp play <screen> media <name> [--noaudio]`
* `/mp play <screen> url <url> [--noaudio]`
* `/mp stop <screen>`
* `/mp pause <screen>` / `/mp resume <screen>`
* `/mp scale <screen> <fit|fill|stretch>`
* `/mp reload`

## Screen scaling

* Each screen now stores a `screen.scale-mode` key in `screens/<uuid>/<uuid>.yml`.
* Default is **FIT** (preserves aspect ratio with letterboxing).
* Scaling math now uses exact crop/scale calculations to avoid off-by-one borders.

## Resource usage

* Frame deletion is now disabled to preserve scaling quality across screens. The `plugin.delete-frames-on-loaded` configuration value is ignored.

## GUI changes

* The Screen Manager GUI is available via `/mp screen list`.
* The Now Playing GUI is accessible by clicking a screen entry.
* GUI actions now enforce the same permissions as the commands.

## What you need to do

1. Update permissions to include the new nodes:
   * `mediaplayer.command`
   * `mediaplayer.screen.manage`
   * `mediaplayer.playback`
   * `mediaplayer.admin`
   * `mediaplayer.media.manage`
   * `mediaplayer.media.admin`
2. (Optional) Set scaling mode per screen with `/mp scale <screen> <fit|fill|stretch>`.
3. Configure `media.allowed-domains` before using URL-based media.
4. If you want vanilla audio, enable `audio.enabled` and provide a pack host URL (or use the built-in server).
5. No configuration migration is required beyond ensuring `screen.scale-mode` exists (auto-added on load).
