# Reserve

> A privacy-first, offline expense tracker with local SMS automation and a premium minimalist design.

Reserve is a single-user Android expense tracker that keeps every rupee on your device. There is no account to create, no cloud sync, no ads and no telemetry. Your financial data never leaves the device — the only network request the app makes is an optional HTTPS check of GitHub Releases for a newer version (disableable in Settings).

Built with Kotlin, Jetpack Compose, Material 3, Room + SQLCipher, DataStore and Glance.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=flat-square" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-0FB70F?style=flat-square" />
  <img alt="Update check only" src="https://img.shields.io/badge/network-update%20check%20only-0FB70F?style=flat-square" />
</p>

---

## ✨ Key Features

- **🔐 Encrypted database (SQLCipher, 16 KB aligned)** — The Room database is encrypted with SQLCipher 4 (AES-256). The passphrase is generated on-device with `SecureRandom` and sealed using AES-256-GCM in `EncryptedSharedPreferences`, backed by the Android Keystore. No passphrase is hardcoded or committed. Native libraries are built with 16 KB ELF page alignment (`0x4000`), verified with `readelf` and `zipalign -P 16`.
- **📩 Automatic SMS capture** — On first grant, Reserve reads your whole SMS inbox, recognises bank debit/credit alerts, creates the matching bank accounts automatically, assigns categories where the merchant is obvious, updates balances, and suppresses duplicates with a SHA-256 fingerprint. New messages are captured live by a broadcast receiver. Everything is parsed on-device; the app remains fully usable if SMS permission is denied.
- **🎨 Jetpack Compose UI** — 100% Compose with Material 3. Inter typography, tabular numerals, a floating pill bottom bar, a pager-driven tab shell and a calm, minimal Ente-inspired design system.
- **🧩 Glance home-screen widget** — A responsive Glance widget with compact, standard and expanded layouts. It shows the Reserve mark, balance, month-to-date spending/income where space allows, recent activity, and a one-tap add shortcut — all masked when hide-amounts is on.
- **🗂️ Daily local backups** — A full JSON backup is written every 24 hours to private internal storage. Only the newest 5 Reserve backups are kept, the latest is preselected in Restore, and any compatible backup file can still be imported through the system picker. Nothing is uploaded.
- **📴 Local-first architecture** — Room is the single source of truth; UI state is derived through Kotlin `Flow`. CSV import/export and local JSON backup/restore use the system document picker — no servers involved.

### Also included

- 💰 Current balance front and centre on the dashboard, with the month's spend, income and net below it
- 🙈 One-tap hide-amounts mode on the dashboard (also a Settings switch) that masks every balance and transaction amount, including the widget
- 💸 Expenses, income and account-to-account transfers (transfers are net-zero and excluded from budgets)
- 🏷️ Custom categories with colour + icon, safe re-parenting to the matching *Unrecognized* category on deletion
- 📊 Strict monthly budgets with no rollover, plus category donut and 6-month trend charts
- 🔁 Recurring monthly rules that generate ordinary, editable transactions
- 🔒 Optional biometric / device-credential app lock with a rotation-safe timeout
- 🌗 Light, dark and system themes with a custom accent-colour picker

---

## 🔐 Privacy & Security

| Guarantee | How it is enforced |
|---|---|
| No background data collection | The only network access is an optional HTTPS GET to the GitHub Releases API to check for updates. No analytics, no crash reports, no telemetry. |
| Encrypted at rest | SQLCipher-encrypted Room database; random 256-bit passphrase stored via `EncryptedSharedPreferences`. |
| SMS stays on-device | `SmsParser` and `SmsImporter` read bank messages locally. The receiver is protected by `android.permission.BROADCAST_SMS`, so only the system can invoke it. |
| No duplicate imports | Every message is hashed (sender + timestamp + body) and recorded in `sms_imports`; `INSERT OR IGNORE` makes overlap between the live receiver and inbox backfill harmless. |
| No tracking | No analytics, advertising, crash-reporting or attribution SDKs. |
| No accounts | Nothing to sign up for and no identifiers to leak. |
| Local exports only | CSV / JSON files are written through the Storage Access Framework to a location you choose. Daily automatic backups are written only to the app's private internal storage; the newest 5 are kept and older Reserve backups are pruned. Nothing is uploaded. |

### Local backups

Reserve writes one backup every 24 hours to its private internal storage. Each backup is a JSON file containing every ledger row (including Trash tombstones), all accounts and their opening balances, categories, budgets, recurring rules **and all DataStore settings**. Only the newest 5 Reserve backups are kept; older ones are deleted automatically, and files not created by Reserve are never touched. The restore picker lists local backups newest-first with the latest preselected, and can also import any compatible backup file through the system document picker. Restoring replaces ledger data and settings together.

SMS permissions (`RECEIVE_SMS`, `READ_SMS`) are optional and currently requested on first launch. Granting `READ_SMS` starts the one-time inbox backfill automatically; later launches only re-read a three-day overlap. Denying them only disables automatic SMS capture — transactions can still be added manually, and a scan can be started later from Settings.

### How SMS capture works

1. **Backfill** — after permission is granted (or on the next launch), the importer queries `Telephony.Sms.Inbox` oldest-first and parses every message.
2. **Recognition** — a message must contain a transaction verb (*debited*, *credited*, *spent*, *withdrawn*, *paid*, *sent*, *transferred*, *received*, *deposited*, *refunded*) plus a currency amount (`₹`, `Rs`, `INR`, or a bare amount after the verb). OTPs, promos, balance-only alerts and personal phone numbers are rejected.
3. **Accounts** — the sender ID and message body identify the bank (`HDFC Bank`, `State Bank of India`, …); the last four digits of the card/account are kept when present. Missing accounts are created automatically.
4. **Posting** — the row is written through `ReserveRepository` in the same transaction that updates the account balance, so history and balances never disagree.
5. **Live capture** — `SmsReceiver` uses `goAsync()` and the same importer, so a new alert lands in the ledger within moments.

Amounts are parsed with `BigDecimal` into `Long` paise — never `Double`.

---

## 🎨 Design System

Reserve follows the visual language of [Ente](https://ente.io) — calm surfaces, restrained green accents and generous whitespace:

- **Tokens** — `fillBase`, `fillLight`, `fillDark`, `fillDarker`, `textBase`, `textLight`, `strokeFaint`, `primaryDefault` (`#0FB70F` light / `#067018` dark) and `warningDefault`.
- **Type** — bundled Inter (Regular / Medium / SemiBold), tabular figures for every monetary value.
- **Shape & spacing** — 20 dp XL radii, 16 dp L cards, an 8 dp spacing grid and hairline 1 dp strokes instead of shadows.
- **Motion** — 150–200 ms fades and pager easing; no gradients, gamification or true black in light mode.
- **Money correctness** — every amount is stored as `Long` minor units (paise); `Float`/`Double` are never used for balances.

---

## 🏗️ Architecture

```
app/src/main/java/com/app/reserve/
├── core/
│   ├── application/      ReserveApplication + manual AppContainer (DI)
│   ├── backup/           Daily backup scheduler, worker and rotation
│   ├── data/             ReserveRepository — transactional ledger operations
│   ├── database/         Room entities, DAOs, SQLCipher database, DbSecurity
│   ├── datastore/        Theme, accent, lock and backup preferences
│   ├── designsystem/     Colour tokens, type, components, category icons
│   ├── domain/           Money, Dates, BudgetCalculator, LedgerMath, CSV, backup
│   └── util/             Icon switching
├── feature/
│   ├── add/              Keypad add/edit transaction sheet
│   ├── budgets/          Monthly budget editor and progress
│   ├── home/             Dashboard
│   ├── insights/         Donut, top categories, trend
│   ├── manage/           Accounts, categories, recurring rules
│   ├── settings/         Settings, theme, lock, import/export, restore picker
│   ├── sms/              Live receiver + inbox importer
│   ├── transactions/     Searchable, filterable history
│   └── widget/           Glance app widget
└── ui/                   Navigation shell, bottom bar, app-lock screen
```

- **UI** — Jetpack Compose + Material 3, Navigation Compose and `HorizontalPager`.
- **State** — unidirectional data flow with `StateFlow` / `Flow` and activity-scoped ViewModels.
- **Persistence** — Room 2.8 with SQLCipher, DataStore Preferences, `Long` minor units.
- **DI** — a small manual `AppContainer`; no reflection-based framework.
- **Background work** — `BroadcastReceiver` + `goAsync()` for live SMS, a startup inbox backfill, WorkManager for daily local backups, and Glance for widget updates.

---

## 🚀 Build & Test

### Requirements

- JDK 17 or newer
- Android SDK with compileSdk 37
- Android device or emulator running API 26+

Create `local.properties` with your SDK path (it is git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

### Commands

```bash
# Build the debug APK
./gradlew assembleDebug

# Unit tests (money, budgets, transfers, CSV, backup, amount input)
./gradlew testDebugUnitTest

# Android Lint
./gradlew lintDebug

# Install on a connected device
./gradlew installDebug

# Compose instrumentation smoke tests (uninstalls the app afterwards)
./gradlew connectedDebugAndroidTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Test coverage

- **80 unit tests** — exact minor-unit money formatting/parsing, budget maths and all-expenses budgets, transfer neutrality, CSV round-trip, v1/v2/v3 backup validation including settings and opening balance, amount-input behaviour, and 22 SMS parsing cases (bank formats, credit-card alerts, Indian digit grouping, balance isolation, OTP/promo/personal-message rejection, fingerprint stability).
- **3 instrumentation tests** — Compose smoke checks for the transaction row and transactions screen, plus a regression test that asserts `ReserveApplication` is registered in the manifest.

---

## 👤 Author

**Niraj Hariyani** — [@FulllOfHope](https://github.com/FulllOfHope)

---

## 📄 License

Shared for personal and educational use. No warranty is provided; review the code before relying on it for financial records.

---

## 💛 Design Credits

Reserve's visual language, colour tokens and component rhythm are an homage to Ente's beautifully restrained product design.

> *Design inspired by the exceptional [Ente design team](https://github.com/Ente/Ente) 💛*
