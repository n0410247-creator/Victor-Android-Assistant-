# VICTOR AI — Android application

Native Android app (Java, Android API 26+, package `ai.victor.app`). This is a new Android implementation informed by the desktop project, **not** a WebView or a repackaged Python app.

## Build / install

Prerequisites: JDK 17, Android SDK platform 35 and Build Tools 35, Gradle 8.9, Internet access for the Android Gradle Plugin. With `ANDROID_HOME` set:

```sh
cd android
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use the `Build VICTOR AI APK` GitHub Actions workflow. Download its `VICTOR-AI-debug-APK` artifact and install the included `app-debug.apk`. Debug APKs are signed with Android's debug key and are installable; they are not Play Store release builds.

## Getting connected

1. Open app → Connect services → AI connection. Get a GonkaRouter API key at https://gonkarouter.io/dashboard (the campaign `/icg` link is a signup page, **not** the API). Default base URL is `https://api.gonkarouter.io/v1`; the default model is `zai-org/GLM-5.3-Flash`. Use **Load available models** if your key supports `/models`, or enter an exact model ID from the provider. Save and test with a **real chat request**.
2. Optional: enable Gemini backup, add a Google Gemini key and test it. Fallback occurs only when primary fails before returning any text. Images and PDFs require Gemini, which sends the original image or a rendered preview of the **first three PDF pages** (not the full PDF). Prefix a request with `search web for` to invoke Gemini Google Search grounding; if no grounded sources are returned the app reports the failure.
3. Under Voice, add an ElevenLabs key, test the live `/v1/voices` endpoint and select a returned voice. Test selected voice actually generates/plays audio. Auto-speak is optional; speech speed 0.70–1.20× uses ElevenLabs `voice_settings.speed`. ElevenLabs has no pitch control in this API; it is explicitly shown as unsupported.

Chat requests stream into the conversation from the first returned text. Android SpeechRecognizer handles speech-to-text and requires microphone permission. ElevenLabs synthesizes spoken responses. Primary/Gemini/voice calls need Internet and valid user-provided credentials; none are bundled. Text/code/CSV/JSON attachment content (up to 110 KB) is included in the actual prompt. Images up to 4 MB and first three PDF pages go to Gemini. Generated fenced code blocks can be copied or saved through Android's Storage Access Framework (no broad storage permission). Local chats remain accessible offline.

## Security and limitations

Credentials are AES-GCM encrypted with a non-exportable Android Keystore key and are never rendered after saving; app backup is disabled. No API keys are embedded. TLS is required for the primary endpoint. Chat history and text attachments are private local files, not end-to-end encrypted. Users should not attach sensitive files unless they trust their provider. The app does **not** silently run code or perform device control. Search is only verified when Gemini grounding supplies sources. Service model/quotas and network conditions determine latency; 2 seconds is a goal, never a guaranteed or fabricated result.
