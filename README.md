# PaperPlay

A music player for Android and desktop that keeps your music in **your own Telegram bot and chats**. You bring a Telegram
API id, API hash and bot token; PaperPlay scans your chats, builds a library and plays the songs. Nothing is sent to any
other service.

Built with Kotlin and Compose Multiplatform.

> **Status:** everything compiles and the apps build in CI, but most features have not yet been tried on a real device.
> [`CHECKLIST.md`](CHECKLIST.md) lists what is done, half done and not started.

## What it does

**Your library**
- Reads songs from the chats and channels you choose. Scan, full rescan, forum topics, and a **Repair library** page that
  re-reads every chat (with extra bots and your own account too) and never deletes anything by itself.
- Your own account can be added (phone, code, password or QR) to index chats, copy songs into the main chat, and play
  songs the bot cannot see.
- Find and delete duplicate songs, move songs between topics, upload songs from your phone or desktop.
- Search by title, artist, album, genre and year. Albums with the same name are split by artist.

**Playing**
- Android uses Media3; desktop bundles libvlc, so nothing else needs installing.
- Queue editing, play next, shuffle and repeat, sleep timer, speed, a 10-band equalizer with presets, loudness
  levelling, and restore of your last session.
- Synced lyrics (tap a line to jump) and plain lyrics that scroll along; format badge and an output-info dialog.
- Android: media notification, **standby mode** (a dark clock screen with the song), volume follows the system volume.
- Desktop: now playing view, full-screen player, always-on-top mini player, tray menu, media keys, keyboard shortcuts.

**Info, covers and mixes**
- Song info from MusicBrainz, Deezer, iTunes and TheAudioDB, plus tags and covers read from the audio files.
- Lyrics from LRCLIB, NetEase, Kugou, QQ Music and lyrics.ovh.
- Daily mixes, genre and weather mixes, listening statistics, ListenBrainz.
- Import Spotify playlists (public link, or log in with your own Spotify client id).

**Several devices**
- Local sync between your own devices on the same network: encrypted, paired with a code, merges your library both ways.
- Linked devices show what is playing and can play, pause and skip.
- **Network play**: let other people on your Wi-Fi join a listening session with a PIN or link.

**Backup**
- Save everything to a zip (optionally password protected) and restore it on a new device. Favorites, plays, playlists
  and edited tags also go to your Telegram Saved Messages.

**Look and feel**
- Material or glass looks, many backgrounds (cover, gradient, wallpaper, animated), accent colour from the cover,
  Material 3 Expressive or Tabler icons.
- Pages slide and fade, the full player is a sheet that follows your finger and Android's back swipe, lists glide, and
  covers and titles fade between songs. **Settings > Style > Reduce motion** turns all of it off.

## Get the apps

Download from the **Releases** page: stable releases (`v1.3.0`) and numbered pre-releases (`v1.3.1-beta.1`). Each has:

| File | What it is |
|---|---|
| `PaperPlay-<version>-android-release.apk` | Android app, signed with the key stored in the repository secrets |
| `PaperPlay-<version>-windows-x64.zip` and `-setup.exe` | Windows app: zip and setup exe |
| `PaperPlay-<version>-linux-x64.tar.gz` | Linux app |
| `SHA256SUMS.txt` | checksums of the files above (the in-app updater checks them) |

Every push to `main` or `beta` is also built by GitHub Actions; the debug Android APK is only a workflow artifact
(`PaperPlay-Android-debug`).

The Windows and Linux builds only include TDLib (the Telegram library) when the repository has a release tagged `deps`
with a `tdlib-windows.zip`; without it they build but cannot reach Telegram.

The workflow is `.github/workflows/build.yml`. The tests are switched off in it because one test in `:core:jvmTest` hangs
on the runner.

## Build it yourself

You need JDK 17. Gradle itself runs on a JDK 25 daemon (`gradle/gradle-daemon-jvm.properties`) that it downloads the
first time, so the first build needs internet access. Run from the repository root:

```powershell
.\gradlew.bat :androidApp:assembleDebug          # Android debug APK
.\gradlew.bat :desktopApp:run                    # run the desktop app
.\gradlew.bat :core:jvmTest :player:jvmTest      # tests
```

The debug APK ends up in `androidApp\build\outputs\apk\debug\`. To install it on a phone: `adb devices`, then
`adb -s <serial> install -r <apk>`.

### Desktop installer and release builds

`scripts\release.ps1` builds the signed Android release, installs it when exactly one `adb` device is connected, builds the
desktop app, runs Inno Setup (`desktopApp\installer\PaperPlay.iss`) and copies `PaperPlay-<version>-setup.exe` and
`PaperPlay-<version>.apk` to Downloads. Use `-SkipAndroid` or `-SkipDesktop` to do half.

```powershell
powershell -File scripts\release.ps1
```

Versions live in `packageVersion` in `desktopApp\build.gradle.kts` and `AppVersion` in the `.iss` file (desktop), and
`versionName` / `versionCode` in `androidApp\build.gradle.kts`. Packaging needs a full JDK with `jpackage`.

### Desktop playback runtime (libvlc)

The bundled libvlc files are large, so they are not in Git. For a local desktop build, copy them once from a VLC 3.x
install:

```powershell
$vlc = "C:\Program Files\VideoLAN\VLC"
$dst = "desktopApp\src\jvmMain\resources\vlc\windows"
Copy-Item "$vlc\libvlc.dll", "$vlc\libvlccore.dll" "$dst\" -Force
foreach ($dir in "audio_output","audio_filter","audio_mixer","packetizer","stream_filter","stream_extractor","misc","keystore") {
    New-Item -ItemType Directory -Force "$dst\plugins\$dir" | Out-Null
    Get-ChildItem "$vlc\plugins\$dir" -Filter "*.dll" | ForEach-Object { Copy-Item $_.FullName "$dst\plugins\$dir\" -Force }
}
```

The curated `codec`, `demux` and `access` plugin subsets are copied by name (see the git history). If VLC is installed
but the bundled folder is missing, the app falls back to the system install. CI downloads VLC itself.

## Releases and versions

Every push to `beta` publishes a numbered pre-release (for example `v1.3.0-beta.7`), and a push to `main` publishes the
stable release (`v1.3.0`). The version and the change log come from the commit subjects, so nothing is bumped by hand:

| Subject starts with | Effect |
|---|---|
| `feat:` | minor bump, listed under **Added** |
| `fix:` or `perf:` | patch bump, listed under **Fixed** or **Improved** |
| `feat!:`, or `BREAKING CHANGE` in the message | major bump, listed under **Breaking changes** |
| `docs:` | listed under **Documentation**, does not release on its own |
| `chore:` `ci:` `build:` `style:` `test:` `refactor:` | hidden, never release on their own |
| no prefix | patch bump, listed under **Other changes** |

Android and desktop share one version, and the Android `versionCode` is the commit count. Every release has its notes on
the GitHub Releases page, and each stable release is also added to [`CHANGELOG.md`](CHANGELOG.md). The logic lives in
`scripts/release/release.py` (run `python3 scripts/release/test_release.py` for its tests).

Beta numbers follow the last stable release that `beta` contains. After `beta` is merged into `main` (which publishes the
stable release), merge `main` back into `beta`; until then the next beta would count again from the old version and sort
below the stable one, so the updater would not offer it.

## Updating

**Settings > About** checks GitHub Releases for a newer version (every time the app starts, or
when you tap *Check for updates*), shows what changed, and offers a dialog at launch when a newer version exists.

- **Channels:** stable by default; set *Update channel* to *Beta pre-releases* to also get pre-releases.
- **Safety:** files are only downloaded from this project's releases, checked against the release's `SHA256SUMS.txt`, and
  never installed until you tap Install. Android also refuses an update signed with a different key.
- **Android:** *Installation method* is *System installer* (Android asks you to confirm) or *Shizuku* (no confirmation
  screen; Shizuku must be running and must allow PaperPlay, and the row shows its state with a button to fix it). If
  PaperPlay is not yet allowed to install apps, it opens that settings screen and carries on when you allow it.
- **Windows:** the downloaded setup is started. **Linux:** the release page opens.

## Logging out

**Settings > Telegram** has four separate actions: *Log out* (your account if logged in, then the main bot), *Log out the
bot only*, *Log out the account only*, and *Log out and delete all data*, which removes the library, settings, covers,
cached songs, extra bots and every login from the device and starts over from the tour. Your Telegram chats are never
touched. Save a backup first (Settings > Storage and backup) if you may want the library back.

## Configuration

You provide your own Telegram API id, API hash and bot token in the first-run setup or Settings. They are stored on the
device only: in the Android Keystore on Android, and with Windows DPAPI on Windows (plain files on Linux and macOS).

## Project layout

| Module | Contents |
|---|---|
| `core` | database, Telegram client, library logic, sync, backup |
| `player` | playback engines and the queue |
| `ui` | the shared Compose screens, theme and motion (`ui/.../theme/Motion.kt`) |
| `androidApp` | the Android app |
| `desktopApp` | the desktop app (Windows and Linux) |

The shared UI has `@Preview` functions for phone and desktop sizes (`ui/.../PreviewSupport.kt` has the fake data). Screens
that need the database or Telegram have no preview yet.

## Known limitations

- **Other bots' posts:** a bot cannot see songs another bot posted as itself in a group (channels are fine). Repair can read
  with every extra bot and with your own account to find them.
- **Not tried on a device yet:** system volume on Windows, the tray, borderless full screen, the themed Spotify login page,
  the device popups, reading with extra bots, the new animations, installing an update through Shizuku, and Log out and
  delete all data.
- **Memory:** the desktop app uses about 300 to 400 MB at rest, mostly the graphics driver and class data. Each extra bot is
  its own Telegram session started at launch.
- **Not built yet:** playback handoff between devices, automatic filing, backup to a Telegram topic, a live equalizer level
  meter, macOS packaging.
- **Written but not wired up:** `sync/WorkOwner.kt` (splitting background work between linked devices) and
  `recs/Suggestions.kt` (a "neighbours" view).
- **Secrets on Linux and macOS** are stored as plain files in the app's data folder.
