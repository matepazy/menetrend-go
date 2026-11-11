# MenetrendGO

<!-- impeccable:product-schema 1 -->

## Platform
android

## Implementation
Kotlin and Jetpack Compose, with the official Google Maps SDK for the default map and bundled Leaflet for railway and custom tile layers.

## Product
A Hungarian transit app for journey planning, live vehicles, stops and departures. MÁV data is requested directly on the device. Existing functionality also includes saved searches and passes.

## Current feature scope
The user requested tappable stations and stops on the live map, live departure boards in a drawer, a persistent map visibility setting, and Android home-screen departure widgets for individual stops or stations. The existing app design must be preserved.

## Constraints
Keep provider station identity, distinguish scheduled and real-time information, support loading, empty and failed requests, and verify the implementation on an Android emulator.
