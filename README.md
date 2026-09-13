# RenegadeFin

An experimental native Jellyfin client for Fire TV and Android TV. RenegadeFin replaces the earlier Jellyfin Web TV wrapper in this repository. It is an unofficial fork of Jellyfin for Android TV, based on stable v0.19.10 (`984181a3d6ab14e9a6d2dcc850c582e1c138bd95`).

![RenegadeFin](design/renegadefin/banner.svg)

## In this preview

- Native Compose home with horizontal shelves, featured artwork, bounded image requests and D-pad selection.
- Charcoal, orange and amber design; original fin/flame/play mark; 16:9 Fire TV banner.
- Upstream native playback, search, library browsing, subtitles and audio controls.
- Manage profiles from Home: fresh administrator password confirmation, create separate Jellyfin users, enable/disable profiles, choose libraries and age ratings, select an avatar, allowed/blocked tags and unrated categories, review changes, and verify saved policies.
- Profiles are real Jellyfin users with separate watch history and server-enforced permissions. Bonfire is not required. Switching profiles always requires fresh sign-in (password or Quick Connect), including returning to a parent account. Sign-in uses Jellyfin's normal authentication; a profile password is not a separate local PIN.

This is not yet household sub-profiles under one account. A companion plugin for household delegation and a separate PIN flow is future work. The native profile editor currently requires a Jellyfin administrator. Administrator accounts cannot be edited through it. New profiles start disabled with no library access until an administrator configures and enables them. If creation is interrupted, check the Users page in Jellyfin before retrying.

## Install

Download the RenegadeFin preview APK from this repository's Releases page and sideload it with Downloader. This uses a separate package from Jellyfin and the old web wrapper. Connect to your server and sign in. The profile icon switches accounts; Manage profiles edits restrictions after administrator confirmation.

APK builds are debug signed previews. Do not assume two CI builds have compatible signing keys. A production signing key and device testing are needed before a stable release.

## Compatibility and limits

Server metadata plugins continue to provide their data through Jellyfin. Features needing client controls require explicit integration. Web-only themes, injected menus and CSS do not run in this native app. This release does not promise universal plugin support.

Home is newly implemented; detail/library/player screens retain the upstream TV implementation. Profile creation, parental controls, account switching and playback need end-to-end testing against your server before relying on this preview for children. No Fire TV hardware is attached to the build runner. Compile success does not establish smoothness, HDR/audio compatibility or all server-version behaviour.

## Build

Install JDK 21 and Android SDK 36. Run:

```sh
./gradlew :app:assembleDebug -Pjellyfin.version=0.2.0-preview.1
```

The APK is under `app/build/outputs/apk/debug`. `.github/workflows/renegadefin.yml` builds source and publishes prereleases only after successful compilation. The source, upstream attribution and GPL-2.0 license are included. Jellyfin and Jellywatch are independent projects; neither endorses this client. Jellywatch's public Player page was consulted for feature/layout inspiration, not copied source or assets.
