# Changelog

## v0.23.0

- Bumped Android application version to `0.23.0` / version code `24`.
- Added a Chinese design note for the image generation configuration flow refactor.
- Made image generation readiness depend on the usable stored API Key instead of only the saved key reference.
- Added regression coverage for missing image API Key readiness and successful image provider generation state.

## v0.22.0

- Bumped Android application version to `0.22.0` / version code `23`.
- Added persistent theme mode settings with System, Light, and Dark options.
- Migrated image generation preferences from SharedPreferences to DataStore with legacy migration.
- Switched OpenAI-compatible image generation requests to Retrofit.
- Replaced the conversations tab entry with the richer Home workbench entry.
- Added a Chinese architecture and code index document for quick project navigation.

## v0.21.0

- Bumped Android application version to `0.21.0` / version code `22`.
- Centralized Coil dependency versions in the Gradle version catalog.
- Expanded Android CI path filters to include the `baselineprofile` module.
- Added release tag validation so GitHub Releases must match the Android app version.
- Updated README documentation link to the existing planning document.
- Pinned the Gateway Go toolchain to `1.26.4` and stopped committing stale diagnostic CI logs.
- Added APK signature verification to the Android release workflow before publishing.
