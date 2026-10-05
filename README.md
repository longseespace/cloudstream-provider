# CloudStream Providers: KKPhim Genres, MotChill, and NguonC Genres

## NguonC Genres

`NguonCGenresProvider` is an independent API-backed extension for
<https://phim.nguonc.com/api-document>. It includes recently updated titles and
all 22 genres listed in NguonC's documentation navigation. The public API currently
documents no genre-taxonomy endpoint, so those genre slugs are kept in the module.
The adult genre follows CloudStream's adult setting; adult-tagged details are
blocked when disabled. Catalog/search responses currently omit category metadata,
so they cannot be completely filtered for adult content before opening details.

- Catalog pagination uses `paginate.current_page` / `total_page`.
- Search, metadata, cast names, posters and episodes come from the API.
- Matching episodes are grouped across Vietsub, Thuyết Minh and Lồng Tiếng servers.
- Card badges show language and available episode counts, with CAM when supplied.
- TMDB/IMDb IDs are retained for tracking, with no external metadata requests.
- Playback reloads episode data, then obtains fresh standard HLS grants from the
  public StreamC bootstrap/issue flow. Direct API HLS/MP4 links are also supported;
  other embeds go through CloudStream's installed extractors.
- Browser-verification requirements, blocked hosts and upstream failures are
  reported rather than treated as playable links. One failed server does not
  prevent another from working.

Build: `./gradlew NguonCGenresProvider:make makePluginsJson`

Tests: `./gradlew NguonCGenresProvider:testDebugUnitTest`

Opt-in live integration check (pagination, search, movie/series metadata, grouped
episodes, fresh HLS playlists and media segments):
`NGUONC_LIVE_TEST=1 ./gradlew NguonCGenresProvider:testDebugUnitTest --rerun-tasks`

The release script includes the plugin in `dist/`; the existing Build workflow
automatically discovers the module. These files are not published until the
changes are pushed and the workflow succeeds. Local/API tests do not verify
on-device playback, casting or downloading.

## MotChill

`MotChillProvider` is a separate extension for `https://motphimchilll.fun`.
It includes new movies, series, movies, theatrical releases, dubbed titles, and
the genres listed in the site's navigation (the 18+ row follows CloudStream settings).
Rows paginate using the site's next-page links. Search, posters, descriptions,
cast, and episode lists are parsed from public HTML, without TMDB or API keys.

Episodes are sorted numerically and grouped across language servers. Playback
supports direct HLS/MP4, KKPhim player wrappers, and VSmov HLS with external
Vietnamese/English subtitles. Other embeds are passed to CloudStream's extractors;
their support depends on the app. URLs are resolved when playing rather than
storing CDN links in the catalog. Unavailable servers cannot be repaired by the plugin.

Build: `./gradlew MotChillProvider:make makePluginsJson`

Live contract check (including Lanterns season 1):
`MOTCHILL_LIVE_TEST=1 ./gradlew MotChillProvider:testDebugUnitTest --rerun-tasks`

The same release script and existing repository URL distribute both extensions.
Adding the module locally does not publish it: push the changes to `main` and wait
for the Build workflow before refreshing the repository in CloudStream.

## KKPhim Genres

A small, transparent CloudStream provider backed only by KKPhim's public API. The home page is organized by genre instead of content type or country.

## Scope

- Genre rows from `GET /v1/api/the-loai/{slug}`
- The `Phim 18+` row and adult-tagged results follow CloudStream's adult-content setting
- Paginated home sections
- Search from `GET /v1/api/tim-kiem`
- Details and episodes from `GET /phim/{slug}`
- Direct HLS playback from KKPhim's `link_m3u8` values
- Multiple playback servers such as Vietsub, Thuyết Minh, and Lồng Tiếng
- KKPhim-provided TMDB and IMDb IDs retained for CloudStream tracking
- No TMDB requests, API keys, remote configuration, obfuscation, or installation checks

## Requirements

- JDK 17
- Android SDK with API 35
- `curl` and `jq` for the live API contract check
- ADB and CloudStream installed for device deployment

## Verify

Run the pure parsing tests and live KKPhim contract test:

```bash
./gradlew test
./scripts/check-api.sh
```

The live check verifies that the provider's 26 genres still match KKPhim's taxonomy, then selects a current Horror title and strictly verifies the genre, detail, episode, and search API contracts. It also probes the first HLS playlist for diagnostics, but a temporary player-CDN 404/403 is advisory because it is independent of KKPhim's API contract.

## Build

```bash
./gradlew KKPhimGenresProvider:make
./gradlew makePluginsJson
```

The extension is written under `KKPhimGenresProvider/build/`, while the generated catalog is written to `build/plugins.json`.

To build a release bundle with repository-specific URLs:

```bash
./scripts/build-release.sh longseespace/cloudstream-provider
```

The resulting `.cs3`, `plugins.json`, and `repo.json` are placed in `dist/`.

## Test on Android

Connect an Android device or emulator with ADB, install CloudStream, then run:

```bash
./gradlew KKPhimGenresProvider:deployWithAdb
```

Manually verify:

1. Genre rows load and paginate, especially Kinh Dị, Hành Động, and Tình Cảm.
2. Posters appear for both relative and absolute KKPhim image paths.
3. Vietnamese search returns playable results.
4. A movie exposes every available language server.
5. A series displays one episode entry with multiple selectable servers rather than duplicate episodes.
6. Playback, seeking, casting, and downloading work for an HLS title.
7. `Phim 18+` is hidden when adult content is disabled and appears after enabling it in CloudStream settings.

## Publish as a CloudStream repository

`repo.json` is the URL users add to CloudStream. It points to `plugins.json`, which points to the compiled `.cs3`.

The included GitHub Actions workflow creates or replaces the `builds` branch automatically. Create a public GitHub repository, add it as `origin`, and push `main`:

```bash
git remote add origin git@github.com:longseespace/cloudstream-provider.git
git push -u origin main
```

The workflow explicitly requests `contents: write` permission. After its Build run succeeds, users can add this repository URL to CloudStream:

```text
https://raw.githubusercontent.com/longseespace/cloudstream-provider/builds/repo.json
```

## API and content note

This project consumes URLs returned by KKPhim without bypassing access controls. Availability through an API does not establish redistribution rights for every listed title; distribute and use the provider only where authorized.
