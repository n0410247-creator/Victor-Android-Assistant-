VICTOR ANDROID — ARENA AI BUILD INSTRUCTIONS

PROJECT PURPOSE

This repository contains the source project of an existing desktop AI assistant called Mark-XLVII.

The purpose of this repository is NOT to create another desktop version.

The purpose is to use this repository as the functional source/reference and transform the assistant into a completely functional Android phone application called:

VICTOR

The final product must be a real installable Android APK.

---

1. SOURCE REPOSITORY RULE

Before changing or creating anything:

1. Inspect the entire repository.
2. Read the README and documentation.
3. Inspect the project structure.
4. Inspect all source files.
5. Identify the AI/LLM system.
6. Identify speech-to-text.
7. Identify text-to-speech.
8. Identify memory.
9. Identify web search.
10. Identify file handling.
11. Identify coding/developer tools.
12. Identify reminders.
13. Identify browser functionality.
14. Identify messaging functionality.
15. Identify application-launching functionality.
16. Identify screen/camera functionality.
17. Identify proactive functions.
18. Identify configuration/API handling.

Do not begin the Android implementation until the existing functionality has been analyzed.

---

2. IMPORTANT CONVERSION RULE

The existing project is a desktop application.

Do NOT simply attempt to run the desktop application on Android.

Do NOT wrap the desktop application inside a WebView.

Do NOT reproduce the desktop GUI.

Instead:

SOURCE DESKTOP ASSISTANT

↓

ANALYZE FUNCTIONALITY

↓

IDENTIFY REUSABLE LOGIC

↓

REPLACE DESKTOP-ONLY IMPLEMENTATIONS

↓

CREATE ANDROID-NATIVE IMPLEMENTATIONS

↓

VICTOR ANDROID APPLICATION

---

3. APPLICATION NAME

The final application name must be:

VICTOR

Do not use:

- Mark
- Mark-XLVII
- Jarvis
- another assistant name

The visible Android application must identify itself as VICTOR.

---

4. ANDROID-FIRST DESIGN

Create a proper Android application.

The final project must contain the Android application source required to produce an APK.

The application must work on modern Android phones.

Do not assume Windows, Linux, macOS, PyQt, PyAutoGUI, desktop keyboard control, desktop mouse control, or desktop window management.

---

5. DESKTOP FUNCTIONALITY CONVERSION

For every desktop feature found in the repository:

If Android supports the same function:

Implement the Android equivalent.

If Android supports the function differently:

Use the appropriate Android mechanism.

If Android does not allow unrestricted automation:

Implement the closest legitimate Android functionality and clearly communicate the limitation.

Never create fake functionality.

---

6. AI ENGINE

VICTOR must use a configurable AI API.

Primary AI provider:

GonkaRouter-compatible API.

Default endpoint:

https://api.gonkarouter.io/v1

The API key must NOT be hard-coded.

The user must enter the API key through VICTOR Settings.

Store the key securely.

The application must provide:

- API endpoint
- API key
- model
- save
- test connection
- clear
- connection status

---

7. ELEVENLABS

Use ElevenLabs for VICTOR voice output.

The ElevenLabs API key must also be entered by the user through Settings.

Never hard-code the private key.

Provide:

- API key
- voice selection
- voice test
- speech speed/settings

---

8. VOICE-FIRST OPERATION

The primary interaction method is voice.

Required flow:

MICROPHONE

↓

SPEECH-TO-TEXT

↓

VICTOR AI

↓

TOOLS IF REQUIRED

↓

AI RESPONSE

↓

ELEVENLABS

↓

VICTOR SPEAKS

The user must not be forced to type for normal operation.

Typing may exist as a fallback.

---

9. VICTOR HOME

Create a professional futuristic Android interface.

The central element must be a large VICTOR AI orb.

The interface should use:

- dark background
- glassmorphism
- 3D depth
- white/cloud-like orb
- subtle cyan
- blue
- subtle purple
- smooth animation
- premium technical appearance

The main screen should remain uncluttered.

---

10. VICTOR STATES

IDLE

Calm orb animation.

LISTENING

Microphone waveform becomes yellow.

THINKING

Show processing animation.

SPEAKING

Microphone waveform becomes green and the orb reacts.

ERROR

Show a clear error state.

---

11. ANDROID PERMISSIONS

Request permissions only when needed.

Potential permissions include:

- microphone
- camera
- notifications
- contacts
- phone
- files/storage
- screen capture

Do not request unnecessary permissions at first launch.

Explain why each sensitive permission is required.

---

12. FILE MANAGEMENT

Adapt the original file functionality to Android.

Support where Android permissions allow:

- creating files
- reading files
- editing files
- renaming
- moving
- copying
- deleting
- sharing
- downloading
- creating folders

Use Android's supported storage mechanisms.

Do not assume unrestricted filesystem access.

---

13. CODING

Preserve the original coding/developer-agent concept.

VICTOR should be capable of generating:

- HTML
- CSS
- JavaScript
- Python
- JSON
- other supported source files

It should be capable of creating project folders and downloadable files.

Example:

"Victor, create a website."

The application should actually create:

index.html

style.css

script.js

and provide real file actions.

Do not create fake download buttons.

---

14. WEB SEARCH

Preserve the web-search functionality.

VICTOR should be able to:

- search
- research
- retrieve information
- open sources
- provide results

If a search fails, report the failure.

Never pretend a search happened.

---

15. BROWSER

Replace desktop browser automation with Android-compatible browser functionality.

VICTOR should be able to open supported URLs and launch the user's browser.

Do not use desktop mouse coordinates.

---

16. ANDROID APPLICATION LAUNCHING

Where Android permits it, support launching installed applications.

Examples:

- Chrome
- YouTube
- Camera
- Phone
- Contacts
- Files
- supported messaging applications

If an application is unavailable, tell the user.

Never claim that an application was opened when it was not.

---

17. REMINDERS

Convert desktop reminders into Android-compatible reminders/notifications.

Support:

- one-time reminders
- repeating reminders
- morning reminders
- afternoon reminders
- evening reminders
- custom reminders

Example:

"Victor, remind me at 7 PM to call John."

The application should create a real Android reminder/notification.

---

18. MEMORY

Preserve the original memory architecture where practical.

Support:

Short-term memory

Current conversation context.

Long-term memory

Information explicitly saved for future use.

Provide controls for:

- viewing memory
- deleting memory
- clearing memory

---

19. CONTACTS AND PHONE

Where Android permissions allow:

- search contacts
- retrieve contact information
- open dialer
- prepare calls

Do not secretly access contacts.

For actions requiring confirmation, ask for confirmation.

---

20. MESSAGING

Adapt messaging functionality using Android-compatible mechanisms.

Do not use desktop automation.

Use Android intents or official/legitimate APIs where available.

If direct automation is unavailable, open the relevant application and prepare the action instead.

Never claim that a message was sent unless it actually was.

---

21. CAMERA AND VISION

Support camera input where appropriate.

The user should be able to:

- open camera
- capture an image
- send it to a compatible vision-capable AI
- receive an explanation
- optionally hear the explanation

Require explicit camera permission.

---

22. SCREEN ANALYSIS

If implemented, use Android's official screen-capture mechanism.

Require explicit user permission.

Do not secretly record or monitor the screen.

---

23. CONNECTION STATUS

Show:

Internet:

- green = connected
- red = disconnected

AI:

- green = connected
- yellow = limited/uncertain
- red = disconnected

ElevenLabs:

- green = connected
- red = disconnected

The indicators must represent real connection state.

---

24. SETTINGS

Create:

AI CONNECTION

- Provider
- Endpoint
- API key
- Model
- Test connection
- Save
- Clear

VOICE

- ElevenLabs API key
- Voice
- Speed
- Test voice

PERMISSIONS

Show the status of required Android permissions.

MEMORY

- View
- Delete
- Clear

APPEARANCE

- Theme
- Animation controls

ABOUT

- VICTOR version
- application information

---

25. SECURITY

Never place private API keys inside source code.

Never expose keys in logs.

Never commit secrets to GitHub.

Use secure Android storage.

Use HTTPS.

---

26. ERROR HANDLING

The application must handle:

- invalid API key
- no internet
- API timeout
- API unavailable
- ElevenLabs failure
- microphone failure
- permission denial
- file failure
- speech recognition failure
- unavailable application
- unsupported Android feature

The application must not crash.

---

27. PERFORMANCE

Use asynchronous networking.

Do not freeze the Android UI.

Use efficient background operations.

Release microphone/camera resources correctly.

Avoid unnecessary battery consumption.

---

28. NO FAKE FEATURES

Every visible feature must perform the action it claims to perform.

Never implement:

- fake API connection
- fake microphone
- fake AI
- fake voice
- fake download
- fake file creation
- fake reminder
- fake browser action
- fake connection status

If a feature cannot be implemented exactly because of an Android restriction, implement the closest legitimate method and explain the limitation.

---

29. FINAL REQUIREMENT

The completed project must produce a real Android APK named:

VICTOR.apk

It must be installable on an Android phone.

The project must be tested before completion.

Test:

- AI connection
- voice input
- AI response
- ElevenLabs voice
- file creation
- coding
- web search
- reminders
- permissions
- error handling
- Android installation

The GitHub repository is the source/reference project.

The written requirements in this file define how the source should be transformed into VICTOR Android.

Do not treat the repository as an instruction to preserve the desktop UI.

The final product is VICTOR Android.
