# Changelog

## 0.3.1 — Capture

- Port Capture Orb system with Basic, Great and Master Pet Orb.
- Preserve compatibility with legacy Skript orb lore markers.
- Normal Wild Pet capture requires <=50% HP; Alpha requires <=25% HP and Great/Master Orb.
- Match original capture formula: Basic 30%, Great 55%, Master 100%, HP bonus, rarity penalty and Alpha penalty.
- Add per-Wild-Pet capture lock and atomic orb consumption/result flow.
- Unlock new species at Level 1; duplicate captures convert to Pet Essence by rarity.
- Add Alpha duplicate Essence rewards and Alpha capture progression.
- Add one-time `/petopia starter` with 3 Basic Pet Orbs.
- Add PETOPIA Dex capture counts and milestone rewards.
- Add `/petadmin giveorb`.

## 0.3.0 — PETOPIA

- Port initial PETOPIA release roster: Flamefox, Ashpup, Solchick, Dustrat, Hailhorn, Voltlet, Axibble, Budbun, Cindermite and Zapbug.
- Add bounded Wild Pet registry with PDC identity.
- Natural spawn checks every 30 seconds, 12% roll, global cap 40 and near-player cap 2 by default.
- Habitat-aware spawn selection for Overworld, Nether and End.
- Add hostile Wild Pet aggro/strike runtime with DEFEND-pet retaliation hook.
- Add Wild Pet despawn lifecycle and portal/orphan cleanup.
- Add Alpha encounter roll (30/1000), HP x1.85, +5 encounter levels and longer despawn.
- Add discovery tracking, `/petopia`, `/petopia dex`, `/petopia near`.
- Add `/petadmin spawnwild` and `/petadmin cleanupwild`.

## 0.2.1 — Combat Safety

- Add combat-world blacklist.
- Add hard friendly-fire/player/ArmorStand protection for active pet attacks.
- Cap direct pet damage through the safety layer.
- Keep active companion entities protected from arbitrary external damage.
- Add `/petadmin combatdebug` and `/petadmin clearcooldowns`.
- Add cooldown/status diagnostics for live troubleshooting.

## 0.2.0 — Skills & Combat Core

- Port Skill 1, Skill 2 and Ultimate framework for the 20 original progression pets.
- Port Skill 3/4 support for custom pets that had them in Skript.
- Add Icarus, Voxaur, The Warden, Vengeance Minion and Menkibun custom PvE handlers.
- Add persistent Energy consumption plus configurable passive Energy regeneration.
- Add atomic runtime skill cooldowns and level requirements.
- Add Burn, Poison, Bleed, Freeze, Stun, Root, Slow, Silence and Weakness registry.
- Add DoT runtime, control effects, heal, regeneration, resistance, lifesteal and temporary damage modifiers.
- Add elemental side-effects for Fire, Ice, Lightning, Water, Nature and Earth.
- Add pet basic-attack passives including Icarus Bleed/Energy/Lifesteal and Vengeance Blood Retribution.
- Add `/pet skill`, `/pet skills` and `/pet energy`.

## 0.1.0-alpha — Core Foundation

- Initialize standalone CdrPets Paper plugin.
- Port registry for 20 base pets and 5 custom pets.
- Add persistent player data for ownership/progression.
- Add summon, dismiss, recall and mode runtime.
- Add PDC pet identity and orphan cleanup.
- Add basic PvE defend AI loop.
- Add Java/Bedrock-safe inventory collection GUI.
- Add evolution and Icarus special evolution requirements.
- Add admin management commands.
- Add legacy Skript variables.csv importer.
- Add GitHub Actions build workflow.
