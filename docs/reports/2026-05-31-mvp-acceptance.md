# MVP Acceptance Report

Date: 2026-05-31

## Verification Commands

- Android: `.\gradlew.bat testDebugUnitTest lint assembleDebug --no-daemon --stacktrace` passed.
- Gateway: `cd gateway && go test ./...` passed.
- Emulator smoke: installed `app-debug.apk` on `sdk_gphone64_x86_64` Android 15 and launched `com.aichat.workbench/.MainActivity` successfully.
- Cold-start smoke: `adb shell am start -W -n com.aichat.workbench/.MainActivity` after force-stop measured `TotalTime` 5427 ms and 5873 ms on debug build, which fails the 1.5 s target.
- Release cold-start smoke: built `assembleRelease`, signed a copy with the local debug keystore, installed it on the same emulator, and measured `TotalTime` 3646 ms and 2558 ms after force-stop, still above the 1.5 s target.
- ART-compiled release smoke: after `adb shell cmd package compile -m speed -f com.aichat.workbench`, release cold start measured `TotalTime` 1202 ms and 969 ms after force-stop, meeting the 1.5 s target.

## Requirement Coverage

### Section 6 Scope

| Requirement | Status | Evidence |
| --- | --- | --- |
| Android native app | Pass | `:app` Kotlin/Compose module builds successfully. |
| OpenAI Provider | Pass | `OpenAiChatProvider` tests cover Responses API and Chat Completions fallback parsing. |
| OpenAI-compatible Provider | Pass | Chat Completions-compatible request and SSE tests pass. |
| Text chat, streaming, stop, retry | Pass | `ChatViewModel`, `SendMessageUseCase`, and unit tests cover streaming success/failure; UI exposes stop/retry actions. |
| Local history, Provider config, Prompt | Pass | Room entities/repositories and `AiChatDatabaseTest` cover persistence. |
| Markdown, code blocks, tables, LaTeX fallback | Pass | Markdown parser/renderer tests cover headings, code, GFM tables, LaTeX markers, and Mermaid fallback. |
| Image generation | Pass | Image provider/use case tests cover request parsing, saving, history, and errors. |
| Optional gateway | Pass | Gateway health, manifest, search, and sandbox tests pass; App gateway client tests pass. |
| News/web search tool | Pass | `/v1/search`, Android search UI, source links, structured errors, and ToolResult persistence implemented. |
| Small code validation tool | Pass | `/v1/sandbox/run`, Docker runner, timeout/truncation, Android sandbox UI, and ToolResult persistence implemented. |

### Section 7 Functional Requirements

| Area | Status | Evidence / Limit |
| --- | --- | --- |
| 7.1 Native basic experience | Pass with profile caveat | Compose screens build; async network/database calls are coroutine-based. Emulator smoke launched successfully; ART-compiled release meets cold-start target, while raw post-install release does not. |
| 7.2 Chat capability | Pass with known limit | Create/rename/delete/archive, edit resend, retry, stop, copy, context clear, session prompt/model/params, temporary/sensitive flags implemented. In-conversation search is represented by the Tools search workflow, not embedded directly inside chat. |
| 7.3 Display capability | Pass with known limit | Markdown, code blocks, tables, LaTeX fallback, Mermaid fallback, image results, and source links implemented. Native Mermaid preview and reasoning collapse remain deferred. |
| 7.4 Model and Provider | Pass | OpenAI/OpenAI-compatible config, Base URL, key, headers, model list, default model, favorites, request timeout/cancel path, and `/models` connectivity test implemented. |
| 7.5 Image generation | Pass | Text-to-image, size/quality/count fields, history, thumbnails, regenerate/reuse/share/save/clear implemented. |
| 7.6 News search | Pass with adapter limit | Structured source results and UI links implemented. Gateway currently ships a mock search adapter placeholder; real provider wiring is deferred to deployment configuration. |
| 7.7 Code validation | Pass | Python sandbox via Docker with network disabled, CPU/memory/pid limits, timeout, output truncation, stdout/stderr/exit code/duration display, and confirmation. |
| 7.8 Tools/plugins | Pass with P2 deferrals | Built-in and gateway tool descriptors, permission levels, confirmation, structured errors, and result storage implemented. MCP and custom tools are P2. |
| 7.9 Local data | Pass | Local Room persistence, encrypted API keys, backup export/import, clear data, temp/sensitive export exclusion, schema versioning implemented. |
| 7.10 Productivity entry | Pass with P2 deferral | Home links chat, prompts, image generation, tools/search/sandbox, settings. Document summary is P2. |

### Section 8 Non-Functional Requirements

| Area | Status | Evidence / Limit |
| --- | --- | --- |
| 8.1 Performance | Partial | Build/lint pass and images use thumbnails. ART-compiled release cold start meets target; raw post-install release exceeds target. Long reply, long scroll, and live cancel behavior still need device measurement. |
| 8.2 Lightweight | Pass | Base chat, Provider config, Prompt, history, and image generation are local/client-side; gateway is optional. |
| 8.3 Privacy/security | Pass with review note | API keys use SecretStore and are omitted from export; sensitive headers are stripped; search/sandbox require confirmation. Static grep found no app logging of API keys. |
| 8.4 Maintainability | Pass | UI/domain/data/provider/tool/gateway boundaries exist; Provider, Tool, Room, Markdown, image, gateway, and backup behavior have unit tests. |

### Section 11 MVP Priority

| Priority | Status |
| --- | --- |
| P0 | Pass: native app, Provider config, text chat, streaming, retry/stop, history, encrypted API keys, Markdown/code/table rendering, image generation. |
| P1 | Pass with known limits: search, code sandbox, gateway interfaces, tool confirmation/results, Provider connectivity test, and import/export are implemented. |
| P2 | Deferred: MCP, image editing, native Mermaid preview, long conversation compression, file summary, local model/Ollama. |

### Section 12 Acceptance Standards

| Standard | Status | Evidence / Limit |
| --- | --- | --- |
| Add OpenAI-compatible Provider and complete text chat | Covered by unit tests and UI build; live provider call not run. |
| Streaming and stop generation | Covered by use case/ViewModel code and tests; live provider call not run. |
| Retry failed message | Implemented in chat UI/ViewModel. |
| Markdown, code block, table, image display | Covered by renderer tests and image feature build. |
| Generate image and save history | Covered by image provider/use case tests. |
| Reopen app preserves history/config | Covered by Room repository tests; no device restart smoke test run. |
| Cold start below 1.5 s | Pass after ART speed compilation: release measured 1202/969 ms after force-stop. Raw post-install release measured 3646/2558 ms and needs baseline/profile follow-up. |
| Clear local data | Covered by backup clear tests. |
| API key absent from normal logs | Static source check found no app logging of API keys; export tests verify no key in backup JSON. |
| Gateway optional for base chat | App chat path calls Provider directly; gateway code is only used by Tools. |
| Gateway manifest/search/sandbox | Covered by Gateway and Android client tests. |
| Search failure does not fabricate sources | UI shows structured error and clears result list on failure. |
| Sandbox timeout is structured | Gateway tests cover timeout result; Android client parses sandbox result. |

### Section 13 Risk Controls

| Risk | Status | Evidence |
| --- | --- | --- |
| Code execution risk | Pass | Execution only via Gateway Docker sandbox; Android App only calls `/v1/sandbox/run`. |
| Search hallucination risk | Pass | Search results are structured and rendered separately with links; failure shows structured error. |
| OpenAI-compatible differences | Partial | Chat Completions compatibility and SSE parsing covered; provider capability probing is deferred. |
| Native rendering complexity | Pass with P2 deferral | Markdown/table/code/LaTeX fallback done; high-quality Mermaid preview deferred. |
| Image size risk | Pass | Original and thumbnail paths stored separately; history uses thumbnails; clear deletes files. |
| Gateway complexity risk | Pass | Gateway only handles search and sandbox; base chat does not depend on it. |

## Known Limits

- Raw post-install release cold start exceeds 1.5 s until ART profile compilation; baseline/profile packaging should be improved before distribution.
- Long conversation scrolling, long reply streaming, and live cancel behavior were not measured.
- Real search provider adapter is not wired; the Gateway currently exposes a mock adapter behind the search interface.
- Native Mermaid preview, MCP, image editing, long-context compression, file summary, and local model integration remain P2.
