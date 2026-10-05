# Repository instructions

## Product boundary

This repository contains independent CloudStream providers: `KKPhimGenresProvider`, `MotChillProvider`, and `NguonCGenresProvider`.

NguonC uses its public JSON catalog/detail API and the public StreamC playback
bootstrap/issue protocol for embeds returned by that API. Keep its models and
playback handling in its own module, with no TMDB network requests. Preserve
language servers and resolve temporary playback grants only when playing.

The following API-only constraints apply to KKPhim. MotChill is explicitly authorized
to parse public MotPhim catalog/episode HTML and public embedded-player sources.
Keep its parsing/extraction isolated in its own module; do not change KKPhim behavior.

- Use only documented/public KKPhim API responses.
- Do not add TMDB network requests, TMDB API keys, or title-search fallbacks.
- KKPhim-supplied TMDB and IMDb IDs may be passed to CloudStream for tracking.
- Do not add remote configuration, obfuscation, repository-installation checks, ad filtering, or scraper fallbacks without explicit approval.
- Preserve direct support for every server returned by KKPhim, including Vietsub, Thuyết Minh, and Lồng Tiếng.
- Keep the provider name unique so it can coexist with `KKPhimProvider` from other repositories.

## Architecture

- `KKPhimProvider.kt`: CloudStream integration and API orchestration.
- `KKPhimModels.kt`: API response models and stream payloads.
- `KKPhimParsing.kt`: pure normalization/grouping helpers.
- `scripts/check-api.sh`: live API and HLS contract check.
- `scripts/build-release.sh`: local `.cs3` and manifest packaging.

## Required verification

For provider changes, run:

```bash
./gradlew test
./scripts/check-api.sh
./gradlew KKPhimGenresProvider:make makePluginsJson
```

For MotChill changes also run `./gradlew MotChillProvider:make` and opt-in live
checks with `MOTCHILL_LIVE_TEST=1 ./gradlew MotChillProvider:testDebugUnitTest --rerun-tasks`.
Live tests must cover catalog pagination, search, episode grouping, and actual
public HLS/subtitle URLs. Do not claim on-device playback based on these alone.

For NguonC changes, additionally run `./gradlew NguonCGenresProvider:make` and
`NGUONC_LIVE_TEST=1 ./gradlew NguonCGenresProvider:testDebugUnitTest --rerun-tasks`.
Use `NguonCGenresProvider:deployWithAdb` when testing that plugin on Android.

If an Android device is connected, also run:

```bash
./gradlew KKPhimGenresProvider:deployWithAdb
```

Do not claim casting or downloading is verified unless it was exercised in the CloudStream app on a device.

## Releases

- Increment the integer `version` in `KKPhimGenresProvider/build.gradle.kts` for every published update.
- Keep `internalName` stable by retaining the module name.
- Never commit credentials or generated Gradle/Android build directories.
