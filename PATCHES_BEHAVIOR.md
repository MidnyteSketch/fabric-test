# Patches Behavior & Personality Reference

**Purpose:** Working design reference for the Patches Minecraft Fabric
mod. It records how Patches is intended to think, act, emote, and
interact with the player and world.

**Status:** IMPLEMENTED = present in the mod; ESTABLISHED = agreed
design; TENTATIVE = needs more design/testing; FUTURE = longer-term
possibility.

## 1. Character Foundation --- ESTABLISHED

Patches is a unique friendly Creeper companion, not a generic tameable
species. He is a runt, about 1.5 blocks tall. A gunpowder deficiency
prevents a normal Creeper explosion and causes the lighter heart-shaped
chest patch that contributes to his name.

He is curious, friendly, adventurous, and generally nonviolent. He
should feel like a creature accompanying the player rather than an
obedient tool.

**Core guardrail:** Patches may autonomously do things, but should
rarely inconvenience the player.

A major behavioral reference is *Hey You, Pikachu!*: not its Pikachu
design or voice gimmick, but its cadence. Patches should use small
pauses, acknowledgement, anticipation, reactions, and recovery so his
limited AI is less obvious.

General cadence: **stimulus → notice → orient → consider/anticipate →
act → react → recover**. Not every action needs every stage.

## 2. Player-Directed States

### Wandering --- IMPLEMENTED

Default autonomous roaming. Future ambient curiosity is most permissive
here.

### Following --- IMPLEMENTED AND TESTED

Feeding a Cookie toggles Wandering ↔ Following and consumes the Cookie.

The tested follow model intentionally keeps more personality than
vanilla pet following while borrowing vanilla reliability where useful.
Patches does not stay magnetically attached to the player. Instead,
Follow Urgency escalates through recognizable stages:

- **Relaxed:** within roughly 6 blocks, Patches is free to wander and
  behave naturally.
- **Attentive:** if the player begins moving away while still nearby,
  Patches watches to see whether they are actually leaving.
- **Catch Up:** at about 12 blocks, or after the player clearly commits
  to leaving, Patches commits to walking back toward them.
- **Hurry:** at about 16 blocks he uses the faster rejoin speed.
- **Warp:** at extreme separation, or after repeated failed pathfinding
  progress, he may use a safe recovery teleport.

Catch-up uses hysteresis: once committed, Patches keeps trying until he
gets within about 3 blocks rather than repeatedly stopping and falling
behind. The 25-block emergency warp remains a hard safety net for severe
separation.

**Pet-style recovery warp — IMPLEMENTED AND TESTED:** the original
fixed-offset/downward-search warp has been replaced with a vanilla-pet-like
landing search. Patches tries randomized positions near the player with
small vertical variation, requires a genuinely walkable pathfinding
location, rejects leaf landings, and checks his actual collision box
before teleporting. Ordinary recovery warps can become available from
roughly 12 blocks away after sustained lack of pathfinding progress, so
chaotic terrain such as Bastions is less likely to leave him stranded
despite being technically nearby.

While the follow goal is active, Patches temporarily removes the normal
water pathfinding penalty and restores it afterward. This mirrors useful
vanilla pet behavior: it does not force him into water, but makes a
sensible water crossing less likely to be rejected simply because water
is normally considered undesirable terrain.

**Goat Horn Recall — IMPLEMENTED AND TESTED:** while Patches is in
Following mode, the player he is following can play any Goat Horn to
issue a temporary Recall command. Recall is not a new persistent mode.
It immediately interrupts ordinary curiosity, bypasses the normal
distance thresholds, and makes Patches hustle toward the player at Hurry
speed until he is within about 3 blocks. If cramped, layered, or broken
terrain prevents meaningful progress for roughly a couple of seconds,
Recall may use the same safe pet-style recovery warp even at relatively
short raw distance. Once Patches reaches the player, Recall ends and
normal Following resumes.

This command exists for situations where geometric distance is
misleading—for example, the player and Patches may be only a few blocks
apart in a Bastion while standing on different floors or opposite sides
of an obstructing wall.

**Follow Urgency — ESTABLISHED:** Follow remains a player command state,
but the pressure to rejoin varies rather than using one rigid leash. It
considers separation, whether/how quickly separation is increasing, and
coarse travel intent. A stationary player gives Patches broad freedom;
walking or sprinting away raises urgency. Fast movement *toward* Patches
does not create catch-up urgency merely because the player is moving
quickly.

Distance remains authoritative even when ordinary movement intent cannot
explain it. Sudden displacement such as an Ender Pearl teleport, a long
fall, or another abrupt relocation can immediately create high Follow
Urgency.

Follow Urgency should continue to restrict autonomy progressively:
first discouraging new minor interests, then interrupting low-value
activities, then escalating movement from relaxed repositioning to Trot
or Dash, and finally allowing recovery/emergency warp when appropriate.
Current thresholds and speeds are tested tuning values, not permanent
character rules; future changes should preserve the visible
walk → hurry → warp progression unless testing shows a clear benefit.

### Sitting --- IMPLEMENTED

Empty-hand interaction toggles Sit/Stand and remembers whether Wander or
Follow should resume.

Sitting normally blocks movement activities. A custom temptation goal
prevents the old bug where a held Cookie moved seated Patches. A
deliberately designed seated shuffle toward a **dropped Cookie** remains
a possible future personality behavior.

### State architecture --- ESTABLISHED

Keep separate: 1. **Player command state:** Wander / Follow / Sit. 2.
**Current activity:** roaming, curiosity, Discovery leading, future
sleep, etc. 3. **Short reaction/override:** food excitement, hurt,
future exhaustion, etc.

## 3. Existing Interactions --- IMPLEMENTED

Favorite food: **Cookies**. Other liked foods include Glow Berries,
Apples, and Mushroom Stew. Harmful/gross foods such as raw meats, Rotten
Flesh, Spider Eyes, and Poisonous Potatoes are rejected. Food heals;
stew returns a Bowl.

Patches has one persistent Bundle slot accepting vanilla bundles and
dyed variants. Exact stack/components/contents survive equip, removal,
save, and reload. It renders on his left side with a conditional strap.
There is no custom storage GUI.

Patches has one persistent Spyglass slot. Equipping it renders separate
lowered adventure goggles and a back strap. Sneak-empty retrieval
currently retrieves Spyglass before Bundle when both are equipped. The Spyglass now arms the first Discovery Mode while Following with the player nearby; see section 14. This addition awaits in-game testing.

## 4. Expression Framework --- IMPLEMENTED FOUNDATION

Current runtime expressions: Default, Surprised, Laugh, Mouth Open,
Hurt, Resting.

Current examples: - nearby held Cookie/temptation → Surprised - Cookie
fed → Laugh (\~20 ticks) - other liked food → Mouth Open (\~12 ticks) -
damage → Hurt - sitting → Resting - otherwise → Default

Available source art also includes additional mouth/tongue, lidded-eye,
Shocked, Bursting, Sad, Miserable, barely-open, sleeping, and heart
variants. Add runtime expressions when behavior demonstrates a need
rather than merely to use all artwork.

## 5. How Patches "Thinks" --- ESTABLISHED

Avoid an entangled flowchart of situation-specific if/thens.

Preferred architecture:

**Perception → evaluation/motivation → activity → expressive
performance**

-   **Perception:** What opportunities/stimuli exist?
-   **Evaluation:** Which matters enough compared with the current
    activity?
-   **Activity:** What goal is being pursued?
-   **Expression:** How does Patches visibly communicate the decision?

Before adding a special-case rule, try to represent the behavior using
perception, significance, novelty/saturation, memory, current
commitment, player distance/movement, and reusable micro-actions. If
repeated cases cannot fit, add a new **general concept**, not another
narrow exception.

A large human-readable catalog of interests is acceptable. A large
procedural maze of contextual exceptions is not.

## 6. Interest / Motivation Model --- ESTABLISHED CONCEPT

Broad significance examples:

-   **Passing:** familiar flowers, ordinary objects/creatures; mostly
    idle amusement.
-   **Interesting:** unfamiliar plants/blocks/creatures; worth a small
    detour.
-   **Exciting:** rare/unusual things, Cookies, notable encounters; can
    interrupt relaxed Following.
-   **Discovery:** exposed valuable ore, spawners, unresolved generated
    loot, etc.; Patches actively involves the player.
-   **Urgent:** damage, explicit commands, future survival concerns;
    overrides ordinary interests.

Potential interests should preferably be data-driven using a few
dimensions:

-   **Novelty:** How unusual is this experience right now?
-   **Intrinsic interest:** How much does Patches personally care?
-   **Player value:** Is another instance useful enough to tell the
    player about?
-   **Hazard:** Does it deserve attention/caution because it is
    dangerous?

Add dimensions only when real behaviors cannot be cleanly represented
otherwise.

Context changes thresholds. Wandering permits minor interests; relaxed
Following permits fewer; Following a moving player suppresses most minor
interests; committed catch-up suppresses still more. Strong discoveries
can compete with relaxed Following. Commands/danger override ordinary
curiosity.

## 7. Novelty and Environmental Saturation --- IMPLEMENTED FOUNDATION; IN-GAME TESTING PENDING

The first version supplements, rather than replaces, exact/UUID/material memories.
It tracks 15 fixed categories with scores capped at 8, adding 1 only on completed
curiosity and decaying 1 per 5 minutes of world game time. Scores at/below 3 have
no influence. Maximum deferral chances are Low 65%, Medium 30%, High 5%.
A category gets one decision per 10-second window, so nearby individuals and
repeated scans cannot bypass a deferral by rerolling. This is short-term runtime
state: it resets on reload; all existing saved memories remain authoritative.
Sugar retains its existing cooldown-reset behavior and also prints current
familiarity and Discovery readiness. It does not erase memories or familiarity.
The broader examples below remain design goals, not additional implemented targets.


Patches should develop familiarity with **experiences**, not merely
remember individual blocks.

Repeated/current exposure reduces novelty so ubiquitous features become
background. An underground lava lake may be noteworthy and prompt a
cautious surprised reaction. In the Nether, enormous repeated exposure
should quickly saturate lava novelty so Patches does not react to every
nearby lava block. This should emerge from saturation, not a special
"ignore Nether lava" rule.

The same idea applies to flowers in flower-rich areas, ice in icy
environments, mushrooms where ubiquitous, or amethyst inside a geode.
Novelty may recover after enough time away.

**Novelty is not usefulness.** Diamond Ore can become familiar while a
newly found vein remains valuable to the player. Thus reduced novelty
need not prevent repeated useful discoveries.

Patches should perceive spatially grouped **features/experiences**, not
every block. A vein should be one discovery; a lava lake/ocean should be
one environmental phenomenon. Use inexpensive grouping rather than
sophisticated geological analysis.

The system must remain bounded and efficient: controlled perception
intervals/radii, candidate consolidation, and small recent-memory
structures rather than continuous world databases.

## 8. Memory --- ESTABLISHED CONCEPT

Memory should be bounded.

**Type familiarity:** Patches has experience with a type/family; mainly
affects novelty.

**Specific known discoveries:** a small bounded list of significant
locations already handled, conceptually dimension + position/region +
broad target identity. This prevents a decorative Diamond Ore block in
Patches' home from being shown to the player constantly. Roughly 16--32
entries has been discussed as a possible starting range, not a final
constant.

**Recently rejected discoveries:** if Patches persistently tries to show
something and the player clearly walks away/declines, suppress that
target for a while so it is not immediately rediscovered.

**Active curiosity memory:** current/queued targets and pathing retry
state are short-lived. Future sleep should clear these as a daily reset.

Appropriate general familiarity can survive sleep/save/reload.

## 9. Ambient Curiosity --- IMPLEMENTED AND TESTED

Ambient Curiosity is normal personality, not a special mode. During
Wandering, and more selectively while Following, Patches can notice
low-stakes nearby interests, approach, inspect, react, remember, and
resume.

The shared curiosity controller now has three tested significance tiers:

- **Low:** Flowers and simple nearby natural interests. Patches notices,
  approaches, and inspects them without demanding the player's attention.
- **Medium:** shared-attention finds such as ordinary Axolotls, Sniffers,
  Wandering Traders, Pink Sheep, unresolved archaeology blocks, and unopened
  generated loot containers. Patches inspects them and then invites the player
  to see what he found. Entity familiarity is generally remembered by UUID;
  block curiosities use an appropriate canonical block identity.
- **High:** exposed Diamond/Deepslate Diamond Ore, Emerald/Deepslate Emerald
  Ore, Ancient Debris, Blue Axolotls, and successfully recognized trapped
  Allays. Patches keeps his Surprised
  expression through inspection, turns to the player, performs repeated
  pairs of short beckoning hops with target look-backs, and on player arrival
  celebrates with the Laugh/Tongue face and a full-body spin. Valuable-block
  familiarity is remembered by a small discovery area and kept separate by
  material family, so a remembered Diamond find does not suppress a nearby
  Emerald or Ancient Debris discovery. Blue Axolotls override the ordinary
  Axolotl Medium rule and use the same UUID familiarity identity.

Medium outranks Low during the interruptible early stages, and High
outranks both. Meaningful inspection has commitment/hysteresis so Patches
does not constantly switch targets. Follow Hurry/emergency behavior can
still interrupt curiosity. These three tiers are the working baseline;
add another tier only if future interests reveal a genuine behavioral
gap rather than merely needing different content.

Ambient ore curiosity must only notice meaningfully exposed ore; it
must not function as an X-ray detector. Generic player storage is not an
ambient target. **Unresolved generated-loot containers are implemented and
tested as Medium curiosities.** Patches only reacts while the vanilla loot
table is still unresolved, so ordinary/player-used storage is ignored and an
unopened generated container stops qualifying as soon as its loot is
generated. This works for both block containers and container vehicles.

Typical lifecycle: **notice → consider → approach → inspect →
react/share if appropriate → remember → resume**

Curiosity target identity is intentionally flexible: what Patches perceives,
what he paths toward, and what he remembers do not always have to be the same
object or position. Tested examples include Big Dripleaf (visible top vs rooted
plant memory), double Chests (visible half vs whole-container identity), and
trapped Allays (visible entity vs exterior observation point vs entity UUID).

Recent implemented/tested additions:

- Simple visible Low block curiosities: Firefly Bush, Small Dripleaf, Big
  Dripleaf, Frogspawn, Turtle Eggs, and Sniffer Eggs. Big Dripleaf separates
  its visible leafy interaction target from its rooted plant memory identity.
- Medium Sniffer curiosity and unresolved Suspicious Sand/Gravel archaeology.
  Archaeology only qualifies while the generated brushable loot table remains
  unresolved, which successfully distinguishes generated archaeology from
  ordinary Creative/player-placed suspicious blocks.
- Medium Wandering Trader curiosity with UUID memory remains the tested baseline.
  The new caravan attention refinement (in-game testing pending) occasionally
  glances at visible Trader Llamas currently leashed to this Trader within 8 blocks
  during inspection/invite/share. No llama targets or memories are created.
- High valuable-block curiosity generalized from Diamond to Emerald and Ancient
  Debris, with per-material area familiarity.
- High Blue Axolotl override using the same UUID memory system as ordinary
  Axolotls.
- Medium unopened generated-loot block containers and container vehicles,
  including canonical whole-double-Chest memory and UUID vehicle memory.
- Medium Pink Sheep curiosity. Patches uses the sheep's current pink wool
  state and UUID memory; naturally pink and player-dyed pink sheep are
  intentionally indistinguishable because vanilla does not retain provenance.
- High trapped-Allay curiosity for the tested Pillager Outpost wooden cage
  case. Patches can perceive an empty-handed Allay through fence cage material,
  finds a reachable exterior observation point instead of trying to enter the
  cell, then uses the urgent High-priority beckoning hops to call the player
  over. If the Allay ceases to be confined during the sequence, Patches treats
  that as a successful rescue and reacts with Joy before remembering the Allay.
  Woodland Mansion stone prisons are currently an acknowledged unsupported
  edge case; attempted broader detection was rolled back rather than weakening
  the confinement/visibility rules globally.

### Planned Ambient Curiosity Roster — ESTABLISHED, PROVISIONAL

This is the current content pool to implement gradually rather than all at
once. Membership and exact tier may be adjusted after in-game testing. Most
curiosities should require Patches to be able to meaningfully **see** the
target before it can become a candidate; block curiosities should not work
through solid walls or act as an X-ray detector. Reusable visibility/exposure
logic is preferred over one-off checks.

- **Low:** Flowers; exposed Firefly Bushes; Small Dripleaf; Big Dripleaf;
  Amethyst Geodes as a grouped feature rather than individual crystals;
  Frogspawn; Turtle Eggs; Sniffer Eggs.
- **Medium:** Axolotls; Sniffers; Wandering Traders; Pink Sheep; unopened
  generated-loot containers; Suspicious Sand; Suspicious Gravel.
- **High:** Diamond Ore; Deepslate Diamond Ore; Emerald Ore; Deepslate
  Emerald Ore; Ancient Debris; Blue Axolotls; trapped Allays where the
  confinement can be recognized cleanly.

Special implementation notes:

- Firefly Bush curiosity should require the bush itself to be exposed/visible,
  so deliberately buried bushes used only to emit particles do not attract
  Patches toward inaccessible blocks.
- Small Dripleaf and other vertically connected same-block plants should resolve
  to one canonical plant identity. Big Dripleaf should likewise resolve the
  visible `big_dripleaf` top through its connected `big_dripleaf_stem` column to
  the lowest stem/root position, so arbitrarily tall versions remain one plant.
- Wandering Traders are Medium entity curiosities and can initially reuse the
  shared-attention routine. The new refinement alternates attention toward associated leashed Trader Llamas while retaining the Trader as the encounter identity; detached/other traders' llamas are ignored.
- Geodes are now implemented as Low grouped features (in-game testing pending).
  A visible Amethyst/Budding Amethyst block must seed recognition; basalt never
  does. A bounded connected inner-shell check requires at least 24 blocks, six
  ordered amethyst/calcite/basalt samples, and three layer directions. It rejects
  incomplete/unloaded or oversized features (2048 blocks, 24-block axis span).
  A reachable, walkable observation point with multiple interior views is selected;
  Patches looks among up to six points using the existing Low emotional cadence.
  One dimension-scoped feature extent is saved, with substantial overlap matching
  to tolerate small mining changes. No natural-vs-built provenance exists.
- Pink Sheep use current vanilla wool color only. There is no maintained
  natural-vs-dyed provenance, so any sheep whose current color is pink can
  qualify. Memory is by UUID.
- Trapped Allays are situation-dependent High curiosities rather than a generic
  "Allay is interesting" rule. The implemented/tested case is the Pillager
  Outpost wooden cage: the Allay must be empty-handed, visibly confined behind
  recognized cage material, and Patches must be able to select a reachable
  observation point outside the enclosure. Patches then urgently beckons the
  player rather than celebrating. If confinement disappears during the
  interaction, Patches reacts happily to the rescue and remembers that Allay by
  UUID. Woodland Mansion stone prison rooms are not currently supported; do not
  broaden detection heuristics merely to force those cells to qualify.
- Unopened generated containers are state-dependent Medium curiosities: the
  ordinary chest/barrel/container-vehicle form is not itself interesting; the
  unresolved generated-loot state is. Block containers use vanilla
  `RandomizableContainerBlockEntity` loot-table state; container vehicles use
  `ContainerEntity` loot-table state. Patches must be able to see the target.
  Double Chests are treated as one discovery: he may interact with whichever
  visible half he noticed, but both halves resolve to one canonical memory
  identity, so he does not immediately re-examine the partner half. Vehicle
  containers are remembered by UUID.
- Blue Axolotls classify as High before the general Axolotl Medium rule. This
  variant-priority override is implemented and tested: when an ordinary and a
  Blue Axolotl are both available, the Blue one wins the initial selection.
- High ore curiosities require meaningful visibility/exposure. Diamond,
  Emerald, and Ancient Debris memories are tracked separately by material
  family while retaining the tested nearby-area suppression behavior.

The roster is also intended to exercise the later novelty system: abundant
flowers and plants should saturate strongly; grouped creatures should avoid
repeating full routines for every nearby individual; familiar decorative ores
should fade into background; and state-dependent curiosities can retain value
without making every ordinary container interesting.

A minor interest is easy for player movement to pull him away from. A
strong interest can interrupt relaxed Following.

## 10. Reusable Expressive Micro-Actions --- ESTABLISHED

Keep a small reusable vocabulary rather than bespoke animations for
every circumstance.

-   **Curious Notice:** pause, head toward stimulus, curious attention;
    common while idle.
-   **Startled Notice:** abrupt halt/head snap/surprised reaction;
    common when already moving or surprised.
-   **Consider:** watch briefly before committing.
-   **Inspect:** focused looking with small head
    movements/repositioning.
-   **Look to Player:** deliberately transfer attention from subject to
    player.
-   **Present / Show Off:** communicate a find. Variants can be
    pleasant, excited, hazardous/uncertain, or deliberate Discovery
    presentation.
-   **Dash / Hurry:** energetic movement for catch-up, Cookie
    excitement, urgent/exciting opportunities.
-   **Settle:** short recovery into ordinary behavior instead of an
    instant state snap.
-   **Disappointed:** brief unhappy response before abandoning an
    unreachable/ignored target.
-   **Relaxed micro-idles:** irregular glances at player/ground/behind,
    stance/head shifts, brief lidded/closed eyes, watching nearby
    activity.

Many can initially be timing + head/body orientation + speed + existing
expressions rather than major new skeletal animations.

## 11. Cadence, Commitment, and Interruptibility --- ESTABLISHED

Patches should acknowledge transitions rather than snap between states.

Micro-actions should not be rigid sequences, but some need a brief
readable **commitment window** so behavior does not twitch between
notice/cancel/notice. Low-priority stimuli can wait; damage, commands,
or severe separation can override faster.

Persistence scales with significance. A flower can be abandoned quickly
when the player leaves. A diamond discovery can receive multiple
attempts to beckon/show the player before Patches concludes they are not
participating.

Persistence itself should communicate how important Patches believes the
find is.

## 12. Shared Attention --- ESTABLISHED

A characteristic interaction should be:

**Patches ↔ interesting thing ↔ player**

If the player approaches while Patches is inspecting something, Patches
can notice them, look from target to player and back, then Present/Show
Off appropriately. This makes the event feel shared rather than like an
environmental scanner routine.

## 13. Movement Intent --- ESTABLISHED CONCEPT

Use a few readable movement intentions: - **Amble:** ordinary
wandering/relaxed investigation. - **Trot:** purposeful approach. -
**Dash:** excited/urgent movement and catch-up.

Initially these can differ mainly through navigation speed, head/body
behavior, expression, and transition timing. Add specialized animation
only if needed.

## 14. Discovery Mode --- IMPLEMENTED FIRST VERSION; IN-GAME TESTING PENDING

Equipped Spyglass + Following intentionally arms exploration; a new lead requires
a live followed player within 6 blocks and no committed Follow/Recall rejoin.
No new equipment interaction, geometry, item, or animation is introduced.

The search covers 18 blocks horizontally (spherical distance limit), ±4 vertically,
at most once per 5 seconds when curiosity is eligible. Only existing valuable
blocks, unresolved archaeology, generated loot containers, and loot vehicles
qualify. Existing exact identities, unresolved-loot rules and visibility remain
required. Ambient High curiosities retain precedence. Spawners are not implemented.

Patches paths to a reachable observation location, pauses at 8-block player
separation, beckons with existing short paired hops/look-backs, and resumes within
4.5 blocks. At the destination he uses the existing Medium or High inspection and
sharing sequence. Catch-up at 12 blocks cancels the lead before existing Follow
recovery/warp is relevant. Follow tuning is unchanged.

A lead ends on command/equipment change, sitting/sleep, player loss/death/dimension
change, hurt, higher-priority goal takeover, lost visibility, clear player movement
away, four failed/no-progress path checks, 10 seconds waiting without the player,
or a 60-second overall budget. Unfinished targets are not remembered. Existing
curiosity cooldown applies afterward. Active pursuit state is never saved.

Limitations: no searches for hidden targets or exploration around unseen corners;
strict loss-of-sight cancellation is intentional. Sleep has no new implementation;
the existing sleeping state is merely a cancellation gate. Detached Trader Llamas
have no guaranteed Trader association and are not inferred from proximity alone.

## 15. Sleep / Daily Rhythm --- ESTABLISHED FUTURE

Patches should eventually settle/sleep at night without skipping night.

Completing sleep clears active/queued curiosity and Discovery
pursuits/pathing retry state, providing a guaranteed daily reset.
Appropriate long-lived familiarity survives.

A richer daily-life simulation involving comfort, weather, and downtime
is possible later, but should not become maintenance-heavy visible
meters by default.

## 16. Combat / Self-Preservation --- ESTABLISHED FUTURE

Patches should not hunt or independently initiate violence. Hostile mobs
should generally not target him. Normal Creepers should be neutral. Cats
may eventually prompt cautious curiosity rather than panic.

If attacked, prioritize retreat/self-preservation.

While Following, Patches may eventually assist the player using his
deficient Creeper burst: small firework-like effect, modest
intended-target damage/knockback, no terrain/fire/item destruction,
harmless to player/friendly pets/Patches, followed by visible exhaustion
and a longer cooldown.

## 17. Future Daily-Life Possibilities

**Social behavior --- FUTURE:** richer reactions to Creepers, cats,
player activity, unfamiliar/passive creatures, reunion after separation,
player sleeping/eating/idling.

The player should be an attention target, not merely a navigation
anchor.

**Bundle autonomy --- TENTATIVE FUTURE:** Patches might eventually
collect tightly constrained harmless/appropriate objects he becomes
interested in if the Bundle has room. This must not become item theft or
inconvenience.

**Weather/comfort --- FUTURE:** rain, time, activity, and environment
may influence choices. Prefer hidden behavioral weighting over
Tamagotchi-style chores/meters.

## 18. Possible Story / Episode Mode --- FUTURE

If the mod grows, it may include focused "episodes" that play out in the
player's own world and showcase Patches' capabilities.

Preferred architecture: an episode acts as a **director over the normal
simulation**, arranging/identifying situations and weighting
opportunities while Patches uses his ordinary behavior vocabulary.
Episodes should demonstrate capabilities that remain possible
organically afterward.

The companion-focused daily-life feeling is partly inspired by *Hey You,
Pikachu!* without copying its character, setting, or voice-command
mechanics.


## 18A. Charged Patches --- FUTURE

Patches should eventually be chargeable by lightning like a Creeper. This
is a character state, not a curiosity target. While charged he should feel
noticeably over-energized: faster movement, livelier behavior, and a
distinct charged appearance/texture in addition to the electrical effect.

His next mini-burst should discharge the charged state. The charged burst
may reach roughly ordinary Creeper-blast damage while preserving Patches'
companion-safe rules: no block destruction, no terrain damage, no damage
to the player/friendly companions, and the effect remains a larger, more
flashy firework-like burst rather than a normal destructive explosion.
Exact damage, radius, visuals, and charged movement values are tuning
questions for the later combat/effects pass.

## 19. Recommended First Behavior Prototype --- ESTABLISHED NEXT TARGET

Before cataloging hundreds of curiosity targets, prove one complete
interaction loop:

1.  Patches is Following.
2.  One test category of world interest is perceptible.
3.  Idle/relaxed notice uses Curious Notice.
4.  Moving notice uses Startled Notice.
5.  Patches Considers, approaches, and Inspects.
6.  If player approaches, he Looks to Player and Presents the find.
7.  If player leaves, he notices.
8.  For a minor interest, he abandons and catches up.
9.  Catch-up remains committed until sufficiently close/player slows.
10. Patches Settles back into ordinary Following.

If this feels alive, additional interests become primarily data/content
expansion rather than repeated AI redesign.

## 20. Preferred Development Order

1.  Behavior architecture + expressive micro-action prototype.
2.  Ambient Curiosity + improved Following.
3.  Discovery Mode / Spyglass.
4.  Sleep / daily reset.
5.  Combat / burst / exhaustion.
6.  Expand social/daily-life/content behaviors as useful.
7.  Potential Story/Episode system much later.

## 21. Technical Baseline --- IMPLEMENTED AND TESTED

Current target: - Minecraft Java 26.3 - Fabric - Java 25 - Fabric Loader
0.19.3 - Fabric Loom 1.17.20 - Fabric API 0.160.5+26.3 - Gradle 9.5.1
for current Loom environment

The 26.3 migration compiled successfully and was tested in-game with
existing behavior functioning.

A temporary testing utility is implemented for curiosity iteration: feeding
Patches **Sugar** is accepted regardless of current HP, consumes one Sugar
(except in Creative), and immediately clears the current curiosity cooldown
without changing the normal cooldown duration. It also makes the next scan
eligible immediately. This is a debug/testing affordance rather than a normal
character behavior requirement.

Existing visual/model principles include the custom six-part model,
64×32 texture, custom scaled proportions, reduced walking intensity,
stylized sitting pose, dedicated face shell, conditional Bundle strap,
and conditional Spyglass goggles/strap.

## 21A. Exploration Milestone Validation

The source now contains the bounded exploration additions above. They are not
promoted to IMPLEMENTED AND TESTED until in-game validation. See
`EXPLORATION_MILESTONE.md` for exact scope, assumptions, limits, and the test plan.
`gradle build` runs deterministic grouping/familiarity checks as part of `check`.

## 22. Maintenance Rules

This is a **working behavior specification**, not a transcript.

1.  Record decisions that materially affect how Patches behaves.
2.  Mark speculative ideas Tentative or Future.
3.  Promote to Established only after deliberate acceptance.
4.  Mark Implemented only when present in the mod.
5.  Preserve example scenarios when they expose architectural
    requirements.
6.  Prefer general principles over enumerating edge-case fixes.
7.  Update this document when implementation intentionally diverges.
8.  Periodically snapshot stable milestones into GitHub, but do not
    require a GitHub commit for every edit.

The goal is for future design/development passes to understand both
**what Patches does** and **why he is designed that way**.
