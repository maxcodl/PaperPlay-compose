# Changelog

All notable changes to PaperPlay are listed here, newest first. Each stable release adds its section automatically.

## [1.3.0] - 2026-11-14

### Added
- Compact About page with drop-down rows for the update channel and installation method ([`111eced`](https://github.com/maxcodl/PaperPlay-compose/commit/111eced1349e77c0374146f8b4b7492336f28acd))
- Real app icon with a plane-hits-the-note animation, animated tour background, About card on Profile ([`32e150b`](https://github.com/maxcodl/PaperPlay-compose/commit/32e150b76cf79e8bc8b51b38bbbbed229596f246))
- Settings grouped under headings with About last; About shows the installation method with Shizuku below ([`d3a9a6b`](https://github.com/maxcodl/PaperPlay-compose/commit/d3a9a6b58c6ded7f81620da2ed3d67f40fa74917))
- Log out options (everything, bot only, account only) and log out with delete all data ([`e84d831`](https://github.com/maxcodl/PaperPlay-compose/commit/e84d8317d34bb094af254f22bd31d46f6e433281))
- Optional Shizuku installer for updates, with permission prompts and toasts ([`84b797d`](https://github.com/maxcodl/PaperPlay-compose/commit/84b797d6e66243b5b935b6c8bd8188d91969604f))
- Install updates on Windows by running the downloaded setup; Linux opens the release page ([`fdb136d`](https://github.com/maxcodl/PaperPlay-compose/commit/fdb136d6158e9e4ff875ea16c7d35a9c1637e3f7))
- Open the install permission screen at once and continue the update when it is allowed ([`e6837b8`](https://github.com/maxcodl/PaperPlay-compose/commit/e6837b891d1ce91bf880132493d3daea3c21eec6))
- Install updates on Android through the package installer, with a permission check ([`bd80e0d`](https://github.com/maxcodl/PaperPlay-compose/commit/bd80e0da076083bdccd3f7fac62176136950cde5))
- About page with update check, update choices and the Made by Max footer ([`69cc5ec`](https://github.com/maxcodl/PaperPlay-compose/commit/69cc5ecf38abbdd21ab2932d0eb7a01bd18304c0))
- Update logic - versions, the GitHub release feed and an update service ([`aede1b0`](https://github.com/maxcodl/PaperPlay-compose/commit/aede1b090413594bc5bd351290aba51b436bb28b))
- Automatic versions, releases and change logs from the commit subjects ([`229efc2`](https://github.com/maxcodl/PaperPlay-compose/commit/229efc24495dba09e543763fd8cf59a93a77f575))

### Fixed
- Import the state delegates the drop-down row needs ([`6d941db`](https://github.com/maxcodl/PaperPlay-compose/commit/6d941db36f74535aea572e27213399b1861f57f6))
- A running Shizuku counts as installed even when the package lookup is hidden ([`928410c`](https://github.com/maxcodl/PaperPlay-compose/commit/928410c84ffb5ca6cea21da9d5523aedc39c2c8c))
- Pass the update service to the Profile screen after the positional arguments ([`ee9b88e`](https://github.com/maxcodl/PaperPlay-compose/commit/ee9b88e703cd75b449c843171eb0de022ae6f888))
- A mention of BREAKING CHANGE in prose no longer counts as a major bump ([`8e1a546`](https://github.com/maxcodl/PaperPlay-compose/commit/8e1a546dbc63732be138eef72115431ee0f57433))

### Documentation
- Checklist entries for logout options, grouped Settings, About and the new logo ([`262f8a1`](https://github.com/maxcodl/PaperPlay-compose/commit/262f8a1faa02a6d366f6398b36167fd8d1554cbf))
- Describe in-app updates and update the checklist ([`bf102a3`](https://github.com/maxcodl/PaperPlay-compose/commit/bf102a3c35826abcb674646858ab3cebf3fe7fd9))
- Rewrite the README, update the checklist ([`4fae430`](https://github.com/maxcodl/PaperPlay-compose/commit/4fae4306dd39237efbf8bc72e43c3370d4953ac8))
- Motion work, CI state, open items ([`5656ac2`](https://github.com/maxcodl/PaperPlay-compose/commit/5656ac287c8e1b0d291ad9804211ef3eaefe2317))

### Other changes
- Smoother UI: press feedback, title and cover crossfades, gliding grids, tabs keep their scroll ([`e650ade`](https://github.com/maxcodl/PaperPlay-compose/commit/e650ade4c3a284dacdcdb84e8e2a1d68aed06548))
- Windows build: also run createDistributable so the zip step finds the app folder ([`a411086`](https://github.com/maxcodl/PaperPlay-compose/commit/a411086213a7720af8ea3fc7aa20257fa0739ab4))
- Desktop motion: sidebar, panel, hover, overlay and tray menu ([`31e6dd5`](https://github.com/maxcodl/PaperPlay-compose/commit/31e6dd52b23c767825302c3879229b32eab0ab89))
- Lists glide, covers fade in, nav pill uses the shared spring ([`15fccc6`](https://github.com/maxcodl/PaperPlay-compose/commit/15fccc6347ee5589d1bb541f0ffd72bfc573c93e))
- Screens slide and fade between pages instead of switching instantly ([`723628e`](https://github.com/maxcodl/PaperPlay-compose/commit/723628ef4e0da077261b9e9993d0bcf2b0a3ac74))
- Full player slides up and down, follows the finger and the back swipe ([`ee60f8d`](https://github.com/maxcodl/PaperPlay-compose/commit/ee60f8d232ed3879ae78263077ef45bbb6e864f6))
- Motion tokens and a Reduce motion setting ([`100b17d`](https://github.com/maxcodl/PaperPlay-compose/commit/100b17d91eb427b7348ceb81b27037eaf1699fc9))
- Announce each paired device once per run, not every 20-second dial ([`79a694d`](https://github.com/maxcodl/PaperPlay-compose/commit/79a694d491d6c92cc5bdb2b53430b05b895c3eb9))
- Windows build: a missing TDLib release no longer fails the step ([`966e52b`](https://github.com/maxcodl/PaperPlay-compose/commit/966e52b93938c2e5afe948b7e95da51ca7289343))
- Build the Android debug and release APKs and the Windows and Linux apps ([`056120b`](https://github.com/maxcodl/PaperPlay-compose/commit/056120b8fe1bf84231a6911d9827285dd6415383))
- Workflow reads the signing secrets under the names the other repo uses ([`c9fed95`](https://github.com/maxcodl/PaperPlay-compose/commit/c9fed953de14f17f3ddeb61bb2d33b10e18e7d86))
- Respect upload back-off, count real listening time, refuse delete without a main chat; update docs ([`b9e70bc`](https://github.com/maxcodl/PaperPlay-compose/commit/b9e70bc482032339ee6c1accbd7846428ff8860a))
- Restore while connected: shut everything down first and wait for TDLib's receive loop ([`d2457e8`](https://github.com/maxcodl/PaperPlay-compose/commit/d2457e8dc2598e2c6d50f1738daf552e83ce2e25))
- Checklist: audit of what the code contains ([`42d9aae`](https://github.com/maxcodl/PaperPlay-compose/commit/42d9aaec2ecc958ec9a391015c62a238179b7783))
- Lock the release-group cache and time out idle loopback logins ([`42aa3f6`](https://github.com/maxcodl/PaperPlay-compose/commit/42aa3f6985a920e5307947f423b969a17244b2ac))
- Fix keep-running setting, pairing lockout counters, shared import race and range parsing ([`b3001cf`](https://github.com/maxcodl/PaperPlay-compose/commit/b3001cfc84b5e6c4282d7f561e0c86abcce93658))
- Workflow builds only the Android debug APK for now ([`691a094`](https://github.com/maxcodl/PaperPlay-compose/commit/691a09470a396d8162e34f8c816ba3364464b4a9))
- Backup: background and standby settings follow the layout choice ([`fa77b73`](https://github.com/maxcodl/PaperPlay-compose/commit/fa77b7307975eeae71feb13ec3c832d83e18ea1c))

**Full changes:** [e7a6417...v1.3.0](https://github.com/maxcodl/PaperPlay-compose/compare/e7a6417...v1.3.0)
