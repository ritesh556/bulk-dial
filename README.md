# RITESH Dialer

Native Android CSV call-queue app.

## Features

- Animated RITESH splash screen
- CSV import
- Start / Pause / Resume / Stop / Retry / Skip buttons
- In-call screen now shows the next queued number
- In-call Skip This button to end the current call and move to the next number
- In-call loudspeaker toggle
- Default-dialer request button
- Call-end detection through `InCallService`
- Auto-dials the next queued number after the current call disconnects
- Consent confirmation checkbox
- Skips rows with `consent=false`
- Blocks common emergency numbers from queue dialing

## CSV format

Recommended:

```csv
name,phone,consent
Ritesh,+9779812345678,true
Asha,+9779800000000,true
Someone,+9779811111111,false
```

A one-column CSV also works:

```csv
+9779812345678
+9779800000000
```

Before starting the queue, the user must check the permission confirmation box.

## How to open

1. Unzip this folder.
2. Open Android Studio.
3. Choose **Open** and select the `RiteshAutoDialer` folder.
4. Let Gradle sync.
5. Run on a real Android phone with a SIM. Calling features will not work correctly on most emulators.
6. In the app, tap **Make This App Default Dialer**.
7. Import your CSV and start the queue.

## Important note

Use this app only for people who agreed to receive calls from you. The app includes consent checks and manual controls, but you are responsible for following local calling rules.
