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
* `/mp play <screen> <source>`
* `/mp stop <screen>`
* `/mp pause <screen>` / `/mp resume <screen>`
* `/mp scale <screen> <fit|fill|stretch>`
* `/mp reload`

## Screen scaling

* Each screen now stores a `screen.scale-mode` key in `screens/<uuid>/<uuid>.yml`.
* Default is **FIT** (preserves aspect ratio with letterboxing).

## Resource usage

* Frame deletion is now disabled to preserve scaling quality across screens. The `plugin.delete-frames-on-loaded` configuration value is ignored.

## GUI changes

* The Screen Manager GUI is available via `/mp screen list`.
* The Now Playing GUI is accessible by clicking a screen entry.

## What you need to do

1. Update permissions to include the new nodes:
   * `mediaplayer.command`
   * `mediaplayer.screen.manage`
   * `mediaplayer.playback`
   * `mediaplayer.admin`
2. (Optional) Set scaling mode per screen with `/mp scale <screen> <fit|fill|stretch>`.
