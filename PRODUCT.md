# MenetrendGO

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin Android application with Jetpack Compose and Material 3. The device calls MÁVPlusz OTP directly over HTTPS, reusing hovamegy.hu's query and normalization architecture with a device-local cache. No separate backend is required. Implementation minimum: Android 9 (API 28).

## Users

People planning Hungarian public transport journeys and locating nearby MÁV vehicles.

## Product Purpose

Find a journey, inspect its timetable and live vehicle position, and keep track of pass expiration with fewer steps than the supplied menetrendek.hu references.

## Capabilities and Constraints

- Package: hu.matepazy.menetrendgo.
- Bottom destinations: Tervezés (default), Térkép, Bérletek (proposed name), Beállítások.
- Timetable search, service details and nearby vehicle map.
- In-app updates use the public `matepazy/menetrend-go` GitHub Releases feed: automatic checks are on by default, with manual checking, stable or stable-and-beta channels, release notes, selectable APK versions and Android-approved installation. Frissítések is the last row of the main Settings top group, directly below Adatok és előzmények. Successful live download and installation remain unverified while the release endpoint returns HTTP 404.
- Refresh every 60–90 seconds; implementation target 75 seconds.
- User confirmed first-version support for ország- and vármegyebérlet.
- Local passes can also be recorded with a city name and expiry reminders. Local service acceptance is unverified, so recording one does not confirm journey coverage.
- Store passes and expiry dates locally, provide expiration notifications, and display eligibility against the selected service. Unknown eligibility must remain unknown rather than imply permission to travel.
- No official affiliation or live-data accuracy claims.

## Brand Commitments

Fully rounded, slightly liquid-glass appearance. Reference screenshots provide layout ideas only; the interface must be significantly simpler and original. Hungarian interface.

## Evidence on Hand

Five supplied reference screenshots and the public hovamegy.hu backend source. No runnable Android application existed at intake.

## Product Principles

- One clear primary action per screen.
- Separate scheduled times, live observations, stale data and unavailable data.
- Make unknown pass eligibility explicit.
- Respect native Android navigation, accessibility and system settings.

## Open Decisions

Release signing and APK export are implemented; public update delivery still needs a reachable GitHub release with an APK. The user requires standalone device operation without a separate server. User chose image concept approval, then approved concept A with recents and a separate departures list. Implemented reminder lead times are three and one days before expiry, then the morning after expiry, at 09:00 Budapest time.
