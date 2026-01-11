# A LESS SHITTY VERSION OF THE ORIGINAL MEDIAPLAYER WITH MANY BUG FIXES AND CHANGES

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
* `/mp play <screen> <source>` - Play a video on a screen.
* `/mp stop <screen>` - Stop playback on a screen.
* `/mp pause <screen>` - Pause playback on a screen.
* `/mp resume <screen>` - Resume playback on a screen.
* `/mp scale <screen> <fit|fill|stretch>` - Change scaling mode.
* `/mp reload` - Reload MediaPlayer screens and stop sessions.

## Permissions

* `mediaplayer.command` - Access to `/mediaplayer`.
* `mediaplayer.screen.manage` - Create/delete screens and set scaling.
* `mediaplayer.playback` - Play/pause/stop/resume.
* `mediaplayer.admin` - Reload MediaPlayer.

## Configuration keys

* `plugin.maximum-distance-to-receive` - Viewer range for playback updates.
* `plugin.screen-block` - Block used for screen structures.
* `plugin.visible-screen-frames-support` - Whether item frames are visible.
* `plugin.glowing-screen-frames-support` - Whether item frames glow.
* `plugin.delete-frames-on-loaded` - Now ignored to keep frames for scaling.

Screens store per-screen data in `screens/<uuid>/<uuid>.yml`, including:

* `screen.scale-mode` - `FIT`, `FILL`, or `STRETCH` (default: `FIT`).

## Scaling modes

* **FIT**: Preserve aspect ratio, letterbox/pillarbox as needed.
* **FILL**: Preserve aspect ratio, crop overflow to fill the screen.
* **STRETCH**: Ignore aspect ratio and fill the entire screen.

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
### When everyone is ready you can type ```vid start``` to run the video, to force starting without waiting for everyone to be ready you can type ```vid start``` anyways.
