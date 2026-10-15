# Agent Reach for Android

A polished, native Android companion for Agent Reach. Paste a URL, share a page from another app, or ask a research question. The app routes the request to the right source, retrieves clean text through Jina Reader/Search, and creates a source-grounded brief locally.

## What makes it smart

- **On-device intent routing** detects links and research intent for Web, GitHub, YouTube, Reddit, X, RSS, Bilibili, Xiaohongshu, WeChat, and V2EX.
- **Extractive smart briefs** rank the most useful sentences from retrieved content. They do not invent text that is absent from the source.
- **Clean reader mode** removes page clutter while retaining source links.
- **Source trail** surfaces relevant citations from search and page content.
- **Voice and Android Share support** make mobile capture fast.
- **Private library** stores recent and saved briefs locally, with no account or analytics.

## Install the ready APK

The debug-signed, sideloadable build is stored at:

```text
releases/AgentReach-1.0.0-debug.apk
```

Android 8.0 (API 26) or newer is required. Enable installation from your file manager when Android asks, then open the APK. A debug signature is suitable for testing and direct sideloading; publish through a store only after signing a release build with your own protected key.

## Privacy model

- Routing and summarization happen on the phone.
- Nothing is sent while typing.
- When **Reach** is tapped, the query or URL is sent over HTTPS to the public Jina Reader/Search service so the requested content can be retrieved.
- The research library stays in Android `SharedPreferences`; cloud backup is disabled.
- There are no ads, analytics SDKs, trackers, accounts, or embedded API keys.
- Opening an original source hands the URL to the user's browser.

## Build with Android Studio or Gradle

Requirements: JDK 17 and Android SDK 35.

```bash
cd android
./gradlew test assembleDebug
```

Output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

The app deliberately uses only Android framework APIs—there are no runtime libraries or Compose dependencies.

## Portable build (no Android SDK required)

For Linux environments with Python, Git, Node/npm, OpenSSL, and zip:

```bash
./android/build-portable.sh
```

This bootstraps a private Java runtime if needed, fetches pinned Android API stubs, builds the open-source `dx` compiler from a pinned source revision, compiles the app, creates its binary manifest, and applies v1/v2/v3 debug signatures. Caches are kept outside the repository in `~/.cache/agent-reach-android`.

To sign with a persistent PEM key and certificate:

```bash
APK_SIGNING_KEY=/secure/release-key.pem \
APK_SIGNING_CERT=/secure/release-cert.pem \
./android/build-portable.sh releases/AgentReach-release.apk
```

Do not commit a private release key.

## Code map

```text
app/src/main/java/com/agentreach/mobile/
├── MainActivity.java       Native screens and interaction flow
├── SmartRouter.java        Offline source and intent routing
├── LocalSummarizer.java    Grounded extractive summaries
├── ResearchClient.java     HTTPS Reader/Search client
├── HistoryStore.java       Local recent/saved library
├── SignalView.java         Animated reach map
└── AgentTheme.java         Visual design tokens
```

## Notes

Public reader/search services can be rate-limited or blocked by a source. The error screen always offers retry and browser fallback. Authenticated platform actions from the Python Agent Reach CLI—posting, private repositories, cookie-based access, and local upstream tools—are intentionally not exposed in this mobile app.
