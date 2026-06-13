# Changelog

## v0.27.0

- Bumped Android application version to `0.27.0` / version code `29`.
- Redesigned theme to Forest Green/Sage color scheme with full Typography definition.
- Added message search with highlight and navigation in chat screen.
- Added swipe-to-delete for conversations and delete confirmation for messages.
- Added typing indicator animation with reduce-motion accessibility support.
- Added assistant/user avatars and copy state feedback (copied/failed).
- Refactored conversations list with preview (last message + relative time), empty state guidance, and provider availability prompt.
- Split image generation screen into dedicated Form and Library components.
- Improved navigation transitions with refined animation parameters.
- Added ConversationWithPreview DAO query for efficient conversation list loading.
- Added SyntaxHighlighter for code blocks in markdown rendering.
- Removed deprecated WorkbenchComponents, StateComponents, InlineImageBubble.

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
- Stopped committing stale diagnostic CI logs.
- Added APK signature verification to the Android release workflow before publishing.
