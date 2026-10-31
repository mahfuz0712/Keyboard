# Keyboard

Sci-fi HUD (Jarvis style) keyboard for Android 8.0+ with English, Bangla and symbols.
Same design language and architecture as the Dialer and Scanner apps (Jetpack Compose settings app, cyan/amber HUD palette, phone + tablet layouts).

- Package: `com.mux.android.keyboard` - Version: `1.0.0`

## Keyboard
- English QWERTY with number row, shift / double-tap caps lock, auto-capital, double-space full stop
- Bangla (বাং): consonants, vowels, kar signs and common conjuncts on 3 pages (key `১/৩` flips pages)
- Symbols 1/2 and 2/2, number pad layout for number / phone fields
- Hold a key for accents, symbols and digits; drag on the space bar to move the cursor; hold backspace to repeat
- PASTE button for the last copied text, HIDE button
- Key preview bubble, vibration, key sound, key height, long-press delay, cyan / amber theme

## Settings app
| Tab   | What it does |
|-------|--------------|
| SETUP | Enable the keyboard, switch to it, live status |
| TUNE  | Theme, number row, key height, vibration, sound, Bangla, auto-capital, double space |
| TEST  | Text / number / password fields to try the keyboard |
| SYS   | App info, updates, author |

## Privacy
The keyboard service never uses the network and stores nothing you type. Only the settings app can go online, to check GitHub for a new release.

## Build
Open in Android Studio, or `gradle assembleDebug` (Gradle 8.9, JDK 17). CI: `.github/workflows/build.yml`.
`app/debug.keystore` is shared with Dialer / Scanner so updates install over old builds.
Release updates are read from GitHub releases of `mahfuz0712/Keyboard` (edit `AppInfo.kt`).
