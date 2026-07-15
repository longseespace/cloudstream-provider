# Repository instructions

## Product boundary

This repository contains one minimal CloudStream provider: `KKPhimGenresProvider`.

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

If an Android device is connected, also run:

```bash
./gradlew KKPhimGenresProvider:deployWithAdb
```

Do not claim casting or downloading is verified unless it was exercised in the CloudStream app on a device.

## Releases

- Increment the integer `version` in `KKPhimGenresProvider/build.gradle.kts` for every published update.
- Keep `internalName` stable by retaining the module name.
- Never commit credentials or generated Gradle/Android build directories.
