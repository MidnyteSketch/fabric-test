# Exploration & Curiosity milestone

Base: `7f3472e6b187587fc855d8571a9eecfd427d6ab3` (Minecraft 26.3).
Status: implemented; compilation and automated checks are recorded in the PR/Actions run.
All new in-game behavior still requires the following play test.

## Implementation changes

- Geodes: visible amethyst/budding-amethyst seeds only, bounded connected inner-shell recognition, ordered calcite/basalt layer checks, reachable collision-safe observation points, multiple visible interior gazes, original Low emotion/timing sequence, one saved dimension-scoped feature identity. Small shell mining changes retain memory through 65% extent overlap. No provenance tracking.
- Trader caravan: only visible nearby Trader Llamas currently leashed to the target Trader are included in occasional 20-game-tick gazes, spaced 60–100 game ticks apart. Inspection/invite/share retain their existing player-attention alternation. Trader UUID is the sole encounter memory; no-llama behavior uses the original gaze.
- Familiarity: 15 fixed runtime categories, score 0–8, first 3 points free of suppression, one point per completed encounter, one point decay per five minutes. Maximum deferral: Low 65%, Medium 30%, High 5%. One random decision per category per ten seconds prevents candidate/scan reroll spam. Existing exact and material-family memories are checked first and are unchanged. Reload clears this secondary layer only.
- Discovery: equipped Spyglass while Following enables useful-target searches up to 18 blocks (vertical ±4), every five seconds when eligible. Targets are existing valuable blocks, unresolved archaeology, generated containers and vehicles. No spawners or other new categories. Ambient High targets keep precedence; far High candidates precede Medium. Block leads use accessible observation points; vehicles remain UUID targets. Existing Medium/High inspection and sharing sequences finish the encounter.
- Lead control: start within 6 player blocks and outside committed Follow/Recall; wait/beckon at 8, resume within 4.5. Abort at 12 player blocks, clear departure, lost sight, invalid target/player, hurt, mode/equipment changes, sitting/sleep, higher-priority takeover, four failed/no-progress path checks, 10-second wait or 60-second overall timeout. Abandoned leads remain unremembered and receive the existing normal curiosity cooldown.
- Debug: existing Sugar reset also reports familiarity scores and Discovery readiness. Logs identify geode recognition/observation, caravan gaze, familiarity decision odds, lead/wait/resume/stop reasons. No new debug item or player interaction.
- Validation: deterministic tests run from `gradle explorationChecks` and automatically during `gradle build`. No additional testing dependency.

## Exact files changed

- `src/main/java/com/midnyte/patches/entity/ai/PatchesCuriosityGoal.java`
- `src/main/java/com/midnyte/patches/entity/ai/PatchesGeode.java` (new)
- `src/main/java/com/midnyte/patches/entity/ai/PatchesFamiliarity.java` (new)
- `src/main/java/com/midnyte/patches/entity/PatchesEntity.java`
- `src/main/java/com/midnyte/patches/entity/ai/PatchesFollowGoal.java` (read-only rejoin-state accessor only)
- `src/explorationTest/java/com/midnyte/patches/entity/ai/ExplorationChecks.java` (new)
- `build.gradle` (isolated deterministic checks source set/task)
- `PATCHES_BEHAVIOR.md` (supplied behavior reference added to repo, milestone sections updated)
- `EXPLORATION_MILESTONE.md` (this report)

No model, texture, animation, rendering, food, horn, sleep, combat, hostile-relationship,
charged-state, Follow threshold/speed/warp, or existing memory serialization changes.
New geode memory adds the optional `PatchesRememberedGeodes` field; old saves default empty.

## Assumptions and deliberate limits

Equipping Spyglass is the deliberate exploration request; Following supplies the
player to lead. There is no new toggle gesture. Removing Spyglass disarms it.

Familiarity counts completed encounters, not all raw blocks/entities seen by a scan.
Five-minute decay allows ordinary cooldown-spaced repeats to accumulate. It is
short-term state rather than another persistent history; reload resets it.

Geode inference recognizes only the observed structure. Connected amethyst shells
larger than the work/span bounds, heavily dismantled shells, or unloaded boundaries
may be skipped. Two shells physically joined by amethyst can become one feature.
Large structural rebuilding can create a new extent identity. An exposed lone
crystal or decorative panel cannot qualify. Visible clusters alone are not seeds;
Patches needs sight of an amethyst/budding-amethyst inner-shell block.

The representative geode observation must offer at least two sufficiently separated
interior views. It may be outside an opening; Patches need not enter the geode.

Detached llamas are ignored because proximity cannot prove Trader association.

Discovery is conservative: it searches loaded, visible surroundings, not a route
through unseen rooms. Losing sight aborts rather than tracking through a wall.
Observation/path attempts are bounded; a very cluttered scene may defer otherwise
reachable candidates. Sleep implementation remains outside this milestone.

## Short in-game test plan

Use a copy of the tested 26.3 world. Sugar makes scans eligible but does **not** clear
exact memories, so use fresh Patches or a different target for repeatable trials.
Debug messages appear in the nearby/followed player's chat.

1. **Geodes:** approach an intact sealed natural geode: no notice. Expose the
   amethyst interior and offer walkable floor: expect one Low encounter and several
   head directions. Complete it, move to another opening, feed Sugar, save/reload:
   no new encounter for adjacent amethyst. A separate nearby geode should qualify.
   Lone amethyst, calcite-free decoration, and inaccessible slits should not lead to
   blind pathing. A built layered geode should qualify under the same observations.
2. **Trader + llamas:** use a Trader with his leashed Trader Llamas. During inspection,
   invitation and sharing, expect occasional caravan gaze logs and visible looks
   toward llamas, with player glances preserved. Test two traders close together,
   detached llamas, and a trader alone. Only the target Trader's leashed llamas count;
   after completion the Trader remains remembered as one UUID encounter.
3. **Familiarity:** complete several distinct flower encounters, using Sugar between
   completed sequences. Scores rise; after grace, logs show occasional ten-second
   deferrals. A deferred window should not reroll for each nearby flower. Compare
   new Trader/ore discoveries: Medium/High caps are weaker and priority tiers remain.
   After five minutes without completions, Sugar should report about one less score.
   Reload resets scores while exact memories still suppress previously examined finds.
4. **Discovery:** Following + Spyglass, start within 6 player blocks with exposed
   Diamond/Emerald/Debris, unresolved generated chest/double chest, archaeology, or
   loot vehicle 10–18 blocks away. Expect lead → wait near 8 player blocks → paired
   beckoning/look-back → resume within 4.5 → ordinary Medium/High shared inspection.
   Try wall-covered targets, ordinary placed chests, opened/generated-loot-resolved
   containers and brushed archaeology: these must not be leads.
5. **Interruptions/regression:** while leading, walk away, block the route, block sight,
   sit, remove Spyglass, change Follow mode, use Recall, take damage, or change dimension.
   Expect clear cancellation without a remembered unfinished target or stranded path.
   Verify ordinary Following catches up/hurries/warps as before. Briefly recheck Sugar,
   Cookie toggling, healing/stew, Bundle/Spyglass retrieval, existing flowers/Blue
   Axolotl/Pink Sheep/trapped-Allay curiosities, and save/reload of old memories.

Automated checks validate structural grouping, malformed/dimension-aware memory,
small mining changes, missing layers, panels, unloaded/oversized traversal, category
independence, grace, saturation, reroll suppression, decay and tier weighting.
They do not claim to validate rendered expression timing, live terrain navigation,
or the entire existing mod's gameplay behavior.
