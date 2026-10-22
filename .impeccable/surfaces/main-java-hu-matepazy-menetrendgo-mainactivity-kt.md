---
version: 1
slug: "main-java-hu-matepazy-menetrendgo-mainactivity-kt"
primary_target: "app/src/main/java/hu/matepazy/menetrendgo/MainActivity.kt"
related_targets: ["app/src/main/java/hu/matepazy/menetrendgo/PlanningScreens.kt","app/src/main/java/hu/matepazy/menetrendgo/UtilityScreens.kt","app/src/main/java/hu/matepazy/menetrendgo/NearbyScreen.kt","app/src/main/assets/map.html"]
---

# Android app · Operate

Approved reference: `.impeccable/mocks/concept-a.png`. User approved search-first plus recent searches, with results moved to a separate screen containing a long departure list. All controls and panels must remain fully rounded. Sample departure cards in the comp are not home-screen requirements and are never seeded as real data.

Composition: Hová indulunk? with its pass-reminder action, one milky search panel, two rounded station controls, native swap icon, rounded date/time control, full-width pill search button; recent searches below. Results open separately with route/time cards, bus/rail filters and additional-result loading. Journey detail, full stop list and nearby map continue the same world. Four bottom destinations remain visible; the user-requested rounded floating bar highlights the complete icon-and-label group. Expanded displays use a rail.

Comp pixel samples: ground #ebf5fb, panel #eaf3fb, action #3675f0. Compose roles use the same icy-blue family with a slightly darker #2864D9 primary to improve white action-label contrast. Roboto via Android's Material type scale. Panels 32dp, fields 28dp, pill actions; soft tonal depth instead of backdrop-blurred text. Native Android system bars replace the generated comp's inaccurate iOS chrome.

Asset inventory: typography, controls, rounded panels and navigation are semantic Compose; icons are Material vector icons; launcher is an authored VectorDrawable. The map alone uses bundled Leaflet with runtime OpenStreetMap tiles. No generated image is shipped as interface content. Mockup prompts and approvals remain with each image.

Verification scope: Android phone plus expanded-width and larger-font checks on the emulator, light/dark. No claim about hardware performance, exact alarm delivery or complete fare entitlement. Unknown service eligibility remains explicit.
