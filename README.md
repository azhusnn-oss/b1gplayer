# B1G Player

An Android / Android TV IPTV player that supports both ways providers hand out access:
a plain **M3U playlist** URL, or **Xtream Codes** credentials (host, username, password).

## Why the project is split this way

Both login modes are reduced to one interface, `ContentSource`, before anything else
sees them. The UI, storage, favourites and playback never branch on which kind of
account is in use, and adding a third provider later means adding one implementation
and changing nothing else.

```
:core   pure Kotlin/JVM — no Android dependencies, unit-tested on the JVM
        model/    normalised Category, LiveChannel, VodItem, Series, Episode, EpgEntry
        m3u/      streaming M3U/M3U8 parser
        xtream/   player_api.php client + URL builder
        login/    login-form validation
        source/   ContentSource interface, M3u and Xtream implementations

:app    Android — Compose UI, Media3/ExoPlayer playback, encrypted credential storage
```

Keeping the IPTV logic out of `:app` is deliberate: parsing and API quirks are where
the bugs live, and they are testable in milliseconds without an emulator.

## Building

Requires JDK 17+ and the Android SDK (API 35). Android Studio writes `local.properties`
for you; otherwise copy `local.properties.example` and point `sdk.dir` at your SDK.

```bash
./gradlew :core:test        # the IPTV logic — no Android SDK needed
./gradlew :app:assembleDebug
```

## What the two login modes do

### M3U playlist

The playlist is downloaded once and parsed incrementally, because real playlists run
to hundreds of thousands of lines. The parser handles the dialects that actually turn
up: `#EXTGRP` group lines, `#EXTVLCOPT` and `#KODIPROP` header directives, headers
appended to the URL after a `|`, commas inside quoted attributes, and a UTF-8 BOM.
Rows are classified as live / movie / series by their `/live/`, `/movie/` and
`/series/` path segments, which is how Xtream panels export them.

### Xtream Codes

`GET /player_api.php?username=U&password=P` validates the account and reports its
expiry, connection limit and allowed output formats. Categories and listings come from
`get_live_categories`, `get_live_streams`, `get_vod_streams`, `get_series`,
`get_series_info` and `get_short_epg`.

Playback URLs are **built, not returned** — the panel only gives out ids:

| Kind | URL |
|---|---|
| Live | `{base}/live/{user}/{pass}/{stream_id}.m3u8` (or `.ts`) |
| Movie | `{base}/movie/{user}/{pass}/{stream_id}.{container_extension}` |
| Episode | `{base}/series/{user}/{pass}/{episode_id}.{container_extension}` |
| Guide | `{base}/xmltv.php?username=U&password=P` |

Panels are inconsistent about JSON types — `stream_id` is a number on one server and a
string on the next, `rating` may be `""`, `"0"`, `0` or `7.4`, and absent values arrive
as `null`, `""` or the literal string `"null"` — so every field goes through a lenient
serializer and one odd row cannot fail a whole response. Short-EPG titles and
descriptions are base64-encoded and are decoded on the way through.

## Two things that bite everyone

1. **Headers travel with the stream.** Many providers reject playback that does not
   carry the same `User-Agent` (and sometimes `Referer`) the playlist was fetched with,
   so `StreamRequest` carries headers alongside the URL and `PlayerActivity` feeds them
   to an OkHttp data source. A global user-agent is not enough.
2. **Live streams are usually raw MPEG-TS over HTTP, not HLS.** Media3 handles both;
   this is the reason the project is native Android rather than a web view.

## Credential storage

Xtream credentials *and* playlist URLs are stored in `EncryptedSharedPreferences` — a
playlist URL grants exactly the same access the login does, so it gets the same
treatment.

## Status

Working end to end: login for both modes, category browsing, search, series drill-down,
and playback with per-stream headers.

Not built yet, in the order worth doing them:

- XMLTV guide parsing and a now/next EPG strip (the fetch URLs are already resolved)
- Favourites and resume points (`LiveChannel.id` is already stable across refreshes)
- Persisting the parsed playlist to Room, so a large M3U survives process death
- Android TV D-pad focus handling and a leanback-shaped browse layout
- Catch-up playback via `XtreamUrls.timeshift`, for channels whose `tv_archive` is set
- Picture-in-picture (the manifest already declares it)

## Legal note

The app ships with no channel list and no provider. It plays sources the user supplies
and has an account for.
