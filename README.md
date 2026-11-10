# Reserve

A quiet, offline-first expense tracker for Android. Reserve keeps every rupee on
your device: there is no account, no cloud, no ads, no telemetry and no network
permission.

## Features (v1)

- Expenses, income and account-to-account transfers
- Categories with colour and icon, re-parenting to **Uncategorized** on delete
- Monthly budgets with strict, non-rolling limits (transfers never count)
- Recurring monthly entries
- Dashboard with month switcher, spending, income, net and balances
- Transaction history with search, type filters and swipe-to-delete with Undo
- Insights: category donut, top categories and a six-month trend line
- CSV import/export and a complete local JSON backup/restore
- Optional biometric app lock
- Light and dark themes using the exact Reserve palette

## Privacy

`AndroidManifest.xml` declares no `INTERNET` permission, and the dependency list
contains no networking SDK. CSV and backup files are written only where you pick
them through the system file picker. Android cloud backup is explicitly disabled
for Reserve's database and preferences.

## Money

Every amount is stored as a `Long` in minor units (paise). Formatting and
parsing live in `core/domain/Money.kt`; no `Float` or `Double` is used for money
arithmetic. `Float` appears only for drawing progress bars and charts.

## Build

Requirements: JDK 17+ and an Android SDK with API 37.

```bash
export JAVA_HOME=/opt/android-studio/jbr
export PATH=$JAVA_HOME/bin:$PATH
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

- `MoneyTest` – formatting, grouping, parsing and exact storage
- `AmountInputTest` – keypad behaviour and minor-unit conversion
- `BudgetCalculatorTest` – monthly sums, category spend, all-expense budgets,
  transfer exclusion, trend series and net worth
- `LedgerMathTest` – income/expense deltas and transfer neutrality (including
  self-transfer no-op)
- `CsvManagerTest` – export/parse round-trip with quoted notes and bad rows
- `BackupManagerTest` – JSON round-trip and version rejection
- `ComposeSmokeTest` (androidTest) – renders a transaction row and the
  transactions screen with fixed data

## Structure

```
core/
  application/   manual DI container
  data/          repository (single source of truth for balances)
  database/      Room entities, DAOs, database
  datastore/     theme and lock preferences
  designsystem/  colours, type, shared components
  domain/        money, dates, budgets, ledger math, CSV, backup
feature/
  add, home, insights, ledger, manage, settings, transactions, budgets
ui/              navigation shell, bottom bar, biometric lock
```
