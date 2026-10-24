# Updater verification · 2026-10-24

Scope: the native updater port from Still, within MenetrendGO's existing rounded blue Compose design. The final requested placement is the last row of the main Settings top group, directly below Adatok és előzmények; Back returns to Settings home. Automatic checks default to on, without an opt-in prompt; the initial channel is stable.

Source: `SettingsScreen.kt`, `UpdateUi.kt`, `UpdateController.kt`, `VersionUpdater.kt`, `app/build.gradle.kts` and `AndroidManifest.xml`. The implementation reads public `matepazy/menetrend-go` GitHub Releases, compares semantic versions, offers stable/beta channels and selected versions, prefers `menetrendgo-v<version>.apk` with a generic APK fallback, and includes release notes, beta acknowledgement, download progress, retry, Android permission/installer handoff and a 24-hour update deferral. Installation remains subject to Android approval and version restrictions.

Recorded implementation checks: 52 tests passed; lint reported 0 errors and 23 warnings. The signed export `artifacts/releases/menetrendgo-v1.0.0.apk` passed `apksigner` verification with v2 signing. Native review used the isolated debug `.updatepreview` package so the installed signed app was retained.

Native captures under `review/updates/`:

- `phone.png`: default-on update settings.
- `settings-home.png`, `data.png`: earlier nested hierarchy, superseded by the final main-page placement request; final placement awaits recapture.
- `beta.png`, `download-error.png`: explicitly synthetic fixtures for beta acknowledgement and download failure.
- `phone-dark-large.png`, `phone-dark-large-bottom.png`: dark appearance at 1.3 font scale, with the switch manually off.
- `tablet.png`: expanded layout, with the switch manually off.
- `check-error.png`: live release check returning HTTP 404.

Prior independent finish review: Ship at screenshot/source scope, with no material findings after the settled Data recapture. Final main-page placement awaits recapture. Native Compose was reviewed directly; no HTML/CSS detector applies.

Delivery remains incomplete: the live endpoint returned HTTP 404. A real successful APK download, permission-settings return, installer completion, physical-device behavior and TalkBack were not proven. Fixture captures establish only those rendered states. No sidecar regeneration or pre-existing `.impeccable/design.json` drift repair was included.
