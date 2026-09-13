# Skript → Java Migration Matrix

Source snapshot audited: `pet.tar.gz` (64 modules, ~47,404 lines).

| Skript area | Java target | Status |
|---|---|---|
| `00_core` identity/stats/custom registry | registry + data + PetManager | Core ported |
| `05_custom` custom pet definitions | registry + SkillService | Core combat handlers ported |
| `10_commands` | command package | Core/admin/PETOPIA commands ported |
| `12_ui` hybrid Bedrock UI | vanilla inventory GUI | Core GUI ported |
| `15_security` | runtime guards/listeners | Combat safety partial/active |
| `20_ai` follow/basic attack/protection | PetManager + combat listeners | Ported for open-world PvE |
| `25_capture` | WildPetManager + CaptureService | Initial release ported in v0.3.1 |
| `26_petopia` | PETOPIA data/Dex/spawn | Initial release ported in v0.3.0; economy missions pending |
| `30_ranked` | ranked service | Planned v0.4.1 |
| `35_engine` battle/status/threat | combat engine | PvE status core ported; battle state/threat pending v0.4 |
| `38_party` | party adapter | Planned v0.4.2 |
| `39_lifestyle` | lifestyle service | Planned v0.6 |
| `40_battle` | battle engine + GUI | Planned v0.4.0 |
| `45_vfx` | VFX service | Lightweight particles ported; full parity pending |
| `50_progression` capsule/tower/mastery/relic | progression/tower services | Data core partial; feature port v0.5 |
| `53_research` | research service | Planned v0.6 |
| `54_achievements` | achievement service | PETOPIA milestone subset ported; full system pending |
| `55_bond` | bond service | Data schema ported; mechanics v0.6 |
| `56_challenges` | challenge service | Planned v0.5 |
| `57_missions` | mission service | Planned v0.6 |
| `58_wiki` | in-game wiki | Planned v0.6 |
| `59_cosmetics` | model/cosmetic adapter | Planned v0.6 |
| `60/61_tournament` | tournament service | Planned v0.7 |
| `62_expedition` | expedition/work services | Planned v0.6 |
| `63_contracts` | contracts service | Planned v0.6 |
| `70_team` | team battle/loadout | Planned v0.4.2 |
| `75_hud` | HUD adapter | Planned v0.4/v0.7 |
| `80_runtime` | lifecycle/watchdogs | Companion + Wild lifecycle active |
| `85/86_admin` | recovery/admin diagnostics | Core + combat/PETOPIA tools active |
| `90/91_telemetry` | telemetry | Planned v0.7 |
| `92_testing` | automated tests | Build gate active; integration suite pending |
| `93_security` | exploit audit | Combat/capture transaction safety partial |
| `94_stability` | soak test tooling | Planned before v1.0 |
