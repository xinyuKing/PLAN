# Plan Reminder

A native Android app for local planning and reminders.

## What it does

- Add a plan with:
  - task / practice
  - location
  - start time
- Save data locally with Room
- Trigger a local notification 10 minutes before the plan starts
- Re-schedule reminders after device reboot or app update
- Works without any backend service

## Tech stack

- Kotlin
- Jetpack Compose
- Room
- AlarmManager
- Notification channels

## Project structure

- `app/src/main/java/com/example/planreminder/MainActivity.kt`
  - app entry point and permission flow
- `app/src/main/java/com/example/planreminder/PlanViewModel.kt`
  - plan creation, deletion, and reminder scheduling
- `app/src/main/java/com/example/planreminder/data/*`
  - local database layer
- `app/src/main/java/com/example/planreminder/reminder/*`
  - alarm scheduling, notification receiver, reboot recovery
- `app/src/main/java/com/example/planreminder/ui/*`
  - Compose UI

## Notes

- Reminder lead time is fixed at 10 minutes.
- If a plan is created less than 10 minutes before it starts, the app schedules the reminder as soon as possible.
- On Android 13+, users need notification permission.
- On Android 12+, exact alarms may require special user approval for best timing accuracy.

## How to run

1. Open the folder in Android Studio.
2. Make sure your machine has an Android SDK installed.
3. Let Android Studio sync Gradle.
4. Run the `app` configuration on an emulator or physical device.

## Validation status in this workspace

- Project files were generated successfully.
- Gradle wrapper files were added.
- Full build verification was not completed here because this machine does not currently have a working Android SDK setup, and Gradle distribution download from `services.gradle.org` timed out during CLI validation.
"# PLAN" 
