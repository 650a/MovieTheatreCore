# MovieTheatreCore Manual Test Plan

## Prerequisites
- A test server with MovieTheatreCore installed.
- At least one screen created (`/mtc screen create <name> <width> <height>`).
- At least one media entry in the library.

---

## 1) Video plays without the resource pack (video only)
1. Set **no** public pack URL:
   ```yaml
   resource_pack:
     server:
       public-url: ""
     url: ""
   pack:
     public-base-url: ""
   ```
2. Reload or restart the server.
3. Run: `/mtc play <screen> media <media>`
4. Confirm:
   - Item frames animate with video frames.
   - Players receive the message: “Audio requires the resource pack; video will still play.”

---

## 2) Video plays with resource pack enabled (video + audio)
1. Configure a public pack host:
   ```yaml
   resource_pack:
     server:
       public-url: "https://pack.example.com"
   ```
2. Ensure `/pack.zip` is reachable on that host.
3. Run: `/mtc play <screen> media <media>`
4. Confirm:
   - Player receives resource pack prompt once.
   - Video renders on item frames.
   - Audio plays for players that accepted the pack.

---

## 3) Pack apply cooldown prevents reinstall spam
1. Ensure `resource_pack.apply-cooldown-seconds` is `60`.
2. Start playback and enter/leave the audio radius repeatedly.
3. Confirm:
   - Pack prompt is **not** re-sent every tick.
   - Pack is re-sent only after cooldown, URL/SHA change, or rejoin.

---

## 4) Multiple screens operate independently
1. Create at least three screens.
2. Start playback on each screen with different media.
3. Confirm:
   - Each screen animates independently.
   - Players only see/hear the screen they are near.
