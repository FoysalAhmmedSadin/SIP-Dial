# SIP Dial

**SIP Dial** is an open-source, Material Design 3 Android SIP softphone & hybrid SIM/VoIP dialer built with Kotlin, Jetpack Compose, and the Linphone SDK (`5.3.105`).

## Features

- **Multi-Account SIP Engine**: Register and manage multiple SIP accounts (`UDP`, `TCP`, `TLS`, optional outbound proxy, custom Auth ID, configurable registration expiry), with instant top-bar account switching.
- **Google Phone-Inspired 4-Tab Workspace**:
  - **Favorites**: Speed-dial cards with per-contact preferred call route (`Ask`, `SIP`, `SIM`).
  - **Recents**: Grouped call log syncing SIP and device SIM calls (`All`, `SIP`, `SIM` + `All`, `Outgoing`, `Received`, `Missed` filters), with long-press multi-select delete and call details timeline.
  - **Contacts**: Device contacts plus Client & Office CRM tabs with one-tap WhatsApp / Telegram messenger detection and launch.
  - **Keypad**: T9 predictive contact matching, paste/copy handling, one-tap star favorite, DTMF audible feedback, USSD & device dial code support, and instant SIP/SIM line routing.
- **Dynamic Appearance Engine**:
  - 4 theme modes: **System**, **Light**, **Dark**, and true **AMOLED (`#000000`)**.
  - 8 accent colors: Green, Blue, Indigo, Purple, Coral Red, Orange, Teal, Pink.
  - Adjustable app font scale (`Normal`, `Large`, `Extra Large`), persisted via Jetpack DataStore with zero startup flash.
- **Full In-Call & Telecom Experience**:
  - Dedicated active SIP call screen and SIM `InCallService` dialer with mute, speakerphone, Bluetooth/earpiece audio route picker, hold, DTMF dialpad, WAV call recording, and real-time jitter/MOS/codec telemetry.
  - Floating incoming/ongoing call overlay, Android Call Redirection service (`Always SIP`, `Ask`, `SIM Only`), and SIM Call Forwarding configurator.
- **Security, Recording & Encrypted Backups**:
  - SIP passwords encrypted at rest with Android Keystore AES-256-GCM.
  - Optional TLS certificate verification & SRTP media encryption.
  - Built-in call recording manager with waveform-style seek bar and `FileProvider` sharing.
  - Portable `.sipbak` local and Google Drive cloud backups (Call History, SIP Accounts, Contacts & Favorites, and App Settings/BlockList) encrypted with PBKDF2-HMAC-SHA256 (150,000 iterations) + AES-256-GCM and optional BiometricPrompt vault storage.

## Building

```bash
gradle assembleDebug
gradle :app:testDebugUnitTest
```

## Developer & License

- **Developer**: Foysal Ahmmed Sadin (<foysal24x7@gmail.com>)
- **License**: GNU General Public License v3.0 (`LICENSE`)
- **Attribution**: Powered by the Linphone Android SDK by Belledonne Communications.

## Huawei AppGallery release

The Android application uses package ID `com.sipdial.app`, minimum Android API 24, target API 36, and release version `3.0.0` (version code `3`).

### CI release signing

The GitHub Actions workflow builds and tests a debug APK on every run. To enable signed release APK and AAB generation, add these repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: Base64-encoded upload keystore file.
- `RELEASE_STORE_PASSWORD`: Keystore password.
- `RELEASE_KEY_PASSWORD`: Key password.

The release signing configuration currently expects the key alias `upload`. Keep the same signing key for every future update of this application. Never commit the keystore or passwords to the repository.

A signed APK and AAB are generated only when all signing secrets are configured. A version tag such as `v3.0.0` also publishes those signed outputs as a GitHub Release.

### Huawei device compatibility note

The app includes Google Play Services authentication dependencies and documents Google Drive backup. Those integrations must be tested or replaced with Huawei-compatible alternatives before claiming full HMS-only compatibility. Core SIP registration and calling should be validated on a Huawei device without Google Mobile Services. This release workflow does not itself replace Google APIs or certify AppGallery compliance.
