# Patches — Fabric 26.2 — Test 1

First clean-room rebuild of the old Patches mod for Minecraft Java 26.2 using Fabric.

## Test 1 behavior

- Patches is a separate passive/pathfinding entity, 1.50 blocks tall.
- `/summon patches:patches` spawns him.
- A Patches Spawn Egg is registered in the Spawn Eggs creative tab.
- He wanders, looks at players, and is tempted by held Cookies.
- Right-click with a Cookie while wandering: Patches eats it and follows that player.
- Right-click with another Cookie while following: Patches eats it and returns to wandering.
- Right-click with an empty hand: toggles Sitting. Standing restores the state he had before sitting.
- Apples, Glow Berries, and Mushroom Stew heal him when injured.
- Rotten Flesh, Spider Eyes, Poisonous Potatoes, and raw meats are refused.
- His mode and following-player UUID persist through save/load.
- He does not despawn from distance.
- Basic head tracking and four-leg Creeper-style walking animation are included.

## Deliberately NOT in Test 1

- Curiosity/discovery AI
- Combat burst and exhaustion
- Expression texture swapping/blinking
- Bundle equipment
- Home position/radius
- Sleep behavior
- Advanced sitting animation
- Follow teleport/cross-dimensional catch-up

## Build requirements

Minecraft 26.2 requires Java 25. This project follows the Fabric 26.2 example-project versions:

- Fabric Loader 0.19.3
- Fabric Loom 1.17-SNAPSHOT
- Fabric API 0.156.0+26.2

This archive does not include the binary Gradle wrapper JAR. Import it as a Gradle project with a Java 25 Gradle installation, or copy these sources into a fresh Fabric 26.2 template project. Then run `gradle build` / the IDE Gradle build task.

The built mod JAR will appear in `build/libs/`.

## Art source

`art_source/Patches.bbmodel` is the supplied current Blockbench model. The runtime texture is at `src/main/resources/assets/patches/textures/entity/patches.png`.
