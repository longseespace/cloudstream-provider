# KKPhim Genres Provider

A small, transparent CloudStream provider backed only by KKPhim's public API. The home page is organized by genre instead of content type or country.

## Scope

- Genre rows from `GET /v1/api/the-loai/{slug}`
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

The live check selects a current Horror title and strictly verifies the genre, detail, episode, and search API contracts. It also probes the first HLS playlist for diagnostics, but a temporary player-CDN 404/403 is advisory because it is independent of KKPhim's API contract.

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
