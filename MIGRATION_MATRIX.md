# Skript → Java Migration Matrix

Source snapshot audited: `pet.tar.gz` (64 modules, ~47,404 lines).

| Skript area | Java target | Status |
|---|---|---|
| `00_core` identity/stats/custom registry | registry + data + PetManager | Core ported |
| `05_custom` 5 custom pet definitions | `pets.yml` | Definitions ported; unique skills pending |
| `10_commands` | command package | Core/admin commands ported |
| `12_ui` hybrid Bedrock UI | vanilla inventory GUI | Core GUI ported |
| `15_security` | runtime guards/listeners | Partial |
| `20_ai` follow/basic attack/protection | PetManager + listener | Core behavior ported |
| `25_capture` | capture service | Planned v0.3 |
| `26_petopia` | Petopia service/economy | Planned v0.3 |
| `30_ranked` | ranked service | Planned v0.4 |
| `35_engine` battle/status/threat | combat engine | Planned v0.2/v0.4 |
| `38_party` | party adapter | Planned v0.4 |
| `39_lifestyle` | lifestyle service | Planned v0.6 |
| `40_battle` | battle engine + GUI | Planned v0.4 |
| `45_vfx` | VFX service | Planned v0.2 |
| `50_progression` capsule/tower/mastery/relic | progression/tower services | Data core partial; feature port v0.5 |
| `53_research` | research service | Planned v0.6 |
| `54_achievements` | achievement service | Planned v0.6 |
| `55_bond` | bond service | Data schema ported; mechanics v0.6 |
| `56_challenges` | challenge service | Planned v0.5 |
| `57_missions` | mission service | Planned v0.6 |
| `58_wiki` | in-game wiki | Planned v0.6 |
| `59_cosmetics` | model/cosmetic adapter | Planned v0.6 |
| `60/61_tournament` | tournament service | Planned v0.7 |
| `62_expedition` | expedition/work services | Planned v0.6 |
| `63_contracts` | contracts service | Planned v0.6 |
| `70_team` | team battle/loadout | Planned v0.4 |
| `75_hud` | HUD adapter | Planned v0.4/v0.7 |
| `80_runtime` | lifecycle/watchdogs | Core lifecycle partial |
| `85/86_admin` | recovery/admin diagnostics | Partial |
| `90/91_telemetry` | telemetry | Planned v0.7 |
| `92_testing` | JUnit/integration tests | Planned continuously |
| `93_security` | exploit audit | Planned before v1.0 |
| `94_stability` | soak test tooling | Planned before v1.0 |
