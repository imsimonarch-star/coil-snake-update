# Coil Snake (Android)

Native **Kotlin + Jetpack Compose** 3D-perspective arcade snake game rewritten from the original web/WebView version of Coil Snake.

## Core Features

- **3D Perspective Floating Island Arena**: Custom perspective `Canvas` renderer with 5 progressive island themes (*Sunny Meadow*, *Coral Cove*, *Sunset Mesa*, *Crystal Cave*, and *Starfall Peak*), dynamic clouds/stars, falling-tile holes, 3D crystals/rocks, beetles, and depth-sorted spheres.
- **Game Modes**:
  - **Single Player (Solo)**: Classic endless/progressive island challenge with 10 selectable skins.
  - **2P Local**: Head-to-head on one screen with dual touch D-pads + Jump buttons (or WASD + Arrow keys on external keyboards).
  - **Phone vs Phone (Hotspot TCP)**: Local multiplayer over mobile hotspot or shared Wi-Fi using native TCP sockets on port `8765`, with automatic gateway/local IP detection.
- **Signature Mechanics**:
  - **Hop & Recharging Charges**: Up to 3 hops to leap over rocks, holes, beetles, and snakes.
  - **Coil Lasso**: Close a loop with your body to lasso fruit inside for bonus combos and multipliers.
  - **Power Orbs**: *Blaze* (smashes rocks), *Frost* (slows rival and beetles), and *Ghost* (phases through snakes).
  - **Hazards**: Crumbling tiles, rising rocks, and roaming beetles that chase and bite tails.
- **Audio**: Bundled theme loop paired with a synthesized retro PCM sound-effect engine (`AudioTrack`).

## Music Attribution

- *"Jaunty Gumption"* by Kevin MacLeod (incompetech.com) — Licensed under Creative Commons: By Attribution 4.0 License (CC BY 4.0).
