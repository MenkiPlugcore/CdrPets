# CdrPets

Standalone Minecraft pet plugin by **CADERA**, migrated from the MENKIESTES Pet System Skript codebase.

Current development release: **v0.3.1 — Combat + PETOPIA + Capture**.

## Target

- Paper 1.21.4
- Java 21
- No Skript dependency
- Java + Bedrock friendly inventory UI
- PDC-based runtime identity for companion and Wild Pet entities

## Core companion system

- 20 original progression pets + 5 custom/admin-only pets.
- Summon / dismiss / recall.
- FOLLOW / DEFEND / STAY modes.
- Per-player YAML persistence.
- Level, EXP, Energy, Evolution, Essence, Mastery and Bond data.
- Evolution I / Final Evolution plus special Icarus requirements.
- Orphan companion cleanup on restart/chunk lifecycle.

## Skills & Combat

Commands:

- `/pet skill <1|2|3|4|ultimate>`
- `/pet skills`
- `/pet energy`

The Java combat core now handles Energy costs/regeneration, level gates, cooldowns, direct damage, healing, lifesteal, resistance, regeneration and status effects. Implemented statuses include Burn, Poison, Bleed, Freeze, Stun, Root, Slow, Silence and Weakness.

The original pet roles are kept: Burn DPS, Reflect Tank, Berserker, Revival, Freeze Control, Guard Breaker, Healer, Fortress Tank, Poison Debuffer and others. Custom handlers are included for Menkibun, Icarus, Voxaur, The Warden and Vengeance Minion.

## PETOPIA

Initial Wild Pet release roster:

`flamefox`, `ashpup`, `solchick`, `dustrat`, `hailhorn`, `voltlet`, `axibble`, `budbun`, `cindermite`, `zapbug`.

Default runtime parity with the Skript system:

- global Wild Pet cap: 40
- max nearby Wild Pets: 2
- natural spawn roll: 12% every 30 seconds
- normal despawn: 180 seconds
- Alpha roll: 30/1000
- Alpha HP multiplier: 1.85x
- Alpha encounter level bonus: +5
- Alpha despawn: 600 seconds

Player commands:

- `/petopia`
- `/petopia dex`
- `/petopia near`
- `/petopia starter`

## Capture

Capture Orb tiers:

- Basic Pet Orb: 30% base chance
- Great Pet Orb: 55% base chance
- Master Pet Orb: 100%

Non-Master capture requires Wild Pet HP <=50%. Alpha capture requires Great/Master Orb and <=25% HP unless using Master Orb.

The capture formula follows the legacy system: HP bonus, rarity penalty and a 25-point Alpha penalty, capped at 95% for normal encounters and 85% for Alpha encounters.

A newly captured species unlocks at Level 1. Duplicate captures convert into Pet Essence, with higher rewards for rarer and Alpha encounters. Legacy Skript Capture Orbs are recognized through their old lore markers when possible.

## Main commands

- `/pet` — collection GUI
- `/pet pilih <pet>`
- `/pet summon [pet]`
- `/pet dismiss`
- `/pet recall`
- `/pet mode <follow|defend|stay>`
- `/pet info [pet]`
- `/pet evolve`
- `/pet skill <1|2|3|4|ultimate>`
- `/pet skills`
- `/pet energy`
- `/petopia [status|dex|starter|near]`

Admin highlights:

- `/petadmin unlock <player> <pet>`
- `/petadmin lock <player> <pet>`
- `/petadmin setlevel <player> <pet> <level>`
- `/petadmin setevolution <player> <pet> <stage>`
- `/petadmin giveessence <player> <amount>`
- `/petadmin givematerial <player> <key> <amount>`
- `/petadmin combatdebug <player>`
- `/petadmin clearcooldowns <player>`
- `/petadmin spawnwild <pet> [level] [alpha]`
- `/petadmin cleanupwild`
- `/petadmin giveorb <player> <basic|great|master> <amount>`
- `/petadmin importskript [path]`
- `/petadmin cleanup`
- `/petadmin reload`
- `/petadmin debug <player>`

## Legacy migration

1. Stop the old pet Skripts before the production cutover.
2. Back up `plugins/Skript/variables.csv`.
3. Install CdrPets and start the server once.
4. Run `/petadmin importskript`.
5. Review `plugins/CdrPets/legacy-unmapped.csv`.
6. Test on staging before permanently removing the Skript modules.

The importer includes core progression plus PETOPIA starter receipt, seen/caught Dex, capture counts, Alpha Dex and capture totals. Unknown legacy keys remain available in `legacy-unmapped.csv` instead of being silently discarded.

## Next roadmap

- **v0.4.0** — Pet Battle state machine.
- **v0.4.1** — Ranked & matchmaking.
- **v0.4.2** — Party / Team Battle.
- **v0.5.x** — Tower, Endless Tower, Relics and challenges.
- **v0.6.x** — Bond, Lifestyle, Expedition, Research, Missions and Cosmetics.
- **v0.7.x** — Tournament, spectator HUD and telemetry.
- **v1.0.0** — final production cutover from Skript.

## License

CdrPets uses the **MENKIESTES SOFTWARE LICENSE v1.0**. Source available does not mean open source.
