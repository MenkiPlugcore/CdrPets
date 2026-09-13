# CdrPets

Standalone Minecraft pet plugin by **CADERA**, migrated from the MENKIESTES Pet System Skript codebase.

> Status: **v0.1.0-alpha — Core Foundation**. The original archive contains 64 Skript modules / ~47k lines, so the migration is intentionally modular instead of copying the old monolith into Java.

## Target

- Paper 1.21.4
- Java 21
- No Skript dependency
- Java + Bedrock friendly inventory UI

## Implemented in v0.1.0-alpha

- 20 original progression pets + 5 custom/admin-only pets
- Pet registry in `pets.yml`
- Per-player YAML persistence
- Summon / dismiss / recall
- FOLLOW / DEFEND / STAY modes
- Basic PvE defend targeting and pet attacks
- One-pet-per-owner runtime guarantee
- PersistentDataContainer identity tags
- Startup/chunk orphan-pet cleanup
- Collection GUI using vanilla inventory packets
- Level, EXP, Energy, Evolution, Essence, Mastery and Bond persistence fields
- Evolution I / Final Evolution material flow
- Special Icarus evolution requirements
- Admin unlock/lock, level/evolution, Essence/material tools
- Legacy Skript `variables.csv` importer for core progression data
- Atomic-ish player save strategy (temp + replace fallback)
- GitHub Actions compile/build pipeline

## Core commands

- `/pet` — open collection GUI
- `/pet pilih <pet>`
- `/pet summon [pet]`
- `/pet dismiss`
- `/pet recall`
- `/pet mode <follow|defend|stay>`
- `/pet info [pet]`
- `/pet evolve`
- `/pet list`

Admin:

- `/petadmin unlock <player> <pet>`
- `/petadmin lock <player> <pet>`
- `/petadmin setlevel <player> <pet> <level>`
- `/petadmin setevolution <player> <pet> <stage>`
- `/petadmin giveessence <player> <amount>`
- `/petadmin givematerial <player> <key> <amount>`
- `/petadmin importskript [path]`
- `/petadmin cleanup`
- `/petadmin reload`
- `/petadmin debug <player>`

## Legacy migration

1. Stop using the old pet Skript before production cutover.
2. Keep a backup of `plugins/Skript/variables.csv`.
3. Install CdrPets and start the server once.
4. Run `/petadmin importskript` (default path: `plugins/Skript/variables.csv`).
5. Review `plugins/CdrPets/legacy-unmapped.csv` for keys not yet handled by the Java migration layer.
6. Test pet ownership, levels, evolution, mastery/bond and custom pets on a staging server before removing the old scripts permanently.

The importer currently maps the high-value persistent families: selected pet, mode, unlocked/favorite pets, level, EXP, Energy, Evolution, Essence, Evolution materials, Mastery and Bond progression. Unknown `mpet.*` keys are preserved for later module ports instead of silently discarded.

## Migration roadmap

### v0.2 — Skills & Combat Parity
Port active skills, Energy economy, elements, status effects, target/threat roles and custom pet mechanics.

### v0.3 — PETOPIA & Capture
Wild pet registry, hostile encounters, capture orb tiers, Alpha encounters, claim safety and Pet Dex.

### v0.4 — Battle / Ranked / Team
1v1 battle state machine, GUI/action fallback, ranked arena, party/team battle and loadout synergy.

### v0.5 — Tower Progression
Tower, Endless Tower, blessings, relic draft/codex, seasonal mutators, challenges, milestones and leaderboard rewards.

### v0.6 — Lifestyle & Meta
Bond/cosmetics, personality/memory/routine, expedition, pet work, research, achievements, missions and contracts.

### v0.7 — Tournament & Telemetry
Tournament, spectator HUD/history, battle history, balance telemetry and admin recovery monitoring.

### v1.0 — Production Cutover
Full migration verification, performance soak test, exploit audit, migration freeze and release build.

## License

CdrPets uses the **MENKIESTES SOFTWARE LICENSE v1.0**. Source available does not mean open source.
