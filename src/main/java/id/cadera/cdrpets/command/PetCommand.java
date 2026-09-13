package id.cadera.cdrpets.command;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.combat.SkillDefinition;
import id.cadera.cdrpets.combat.SkillService;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.model.PetMode;
import id.cadera.cdrpets.model.SkillSlot;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.ui.PetMenu;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public final class PetCommand implements CommandExecutor, TabCompleter {
    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final PetManager manager;
    private final PetMenu menu;
    private final SkillService skills;

    public PetCommand(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store, PetManager manager, PetMenu menu, SkillService skills) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.manager = manager;
        this.menu = menu;
        this.skills = skills;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only. Use /petadmin from console.");
            return true;
        }
        if (!player.hasPermission("cdrpets.use")) {
            plugin.message(player, "&cKamu tidak memiliki izin menggunakan CdrPets.");
            return true;
        }

        PlayerPetData data = store.get(player.getUniqueId());
        data.lastKnownName(player.getName());
        if (args.length == 0) {
            menu.open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "gui", "storage", "collection", "koleksi" -> menu.open(player);
            case "help", "bantuan" -> help(player);
            case "list", "daftar" -> list(player, data);
            case "select", "pilih" -> select(player, data, join(args, 1));
            case "summon", "panggil" -> {
                if (args.length > 1) {
                    String petId = registry.normalize(join(args, 1));
                    if (petId == null || !data.isUnlocked(petId)) {
                        plugin.message(player, "&cPet tidak ditemukan atau belum terbuka.");
                        return true;
                    }
                    data.selectedPet(petId);
                    store.save(data);
                }
                manager.summon(player);
            }
            case "dismiss", "simpan" -> manager.dismiss(player, true, false);
            case "recall", "kembali" -> manager.recall(player);
            case "mode" -> mode(player, args);
            case "info", "stats" -> info(player, data, args.length > 1 ? join(args, 1) : data.selectedPet());
            case "evolve", "evolusi" -> evolve(player, data);
            case "skill", "cast" -> useSkill(player, args);
            case "skills", "skillinfo" -> skillInfo(player, data);
            case "energy" -> plugin.message(player, "&7Energy &f" + data.selectedPet() + "&7: &e" + data.progress(data.selectedPet()).energy() + "&7/" + plugin.getConfig().getInt("progression.energy-max", 100));
            default -> plugin.message(player, "&cSubcommand tidak ditemukan. Gunakan &f/pet help&c.");
        }
        return true;
    }

    private void select(Player player, PlayerPetData data, String raw) {
        if (raw.isBlank()) {
            plugin.message(player, "&cGunakan: /pet pilih <nama pet>");
            return;
        }
        String petId = registry.normalize(raw);
        if (petId == null) {
            plugin.message(player, "&cPet tidak ditemukan.");
            return;
        }
        if (!data.isUnlocked(petId)) {
            plugin.message(player, "&cPet tersebut masih terkunci.");
            return;
        }
        data.selectedPet(petId);
        store.save(data);
        PetDefinition pet = registry.get(petId);
        plugin.message(player, "&aPet utama dipilih: " + pet.displayName(data.progress(petId).evolution()));
        if (data.active()) manager.summon(player);
    }

    private void mode(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "&7Mode saat ini: &f" + store.get(player.getUniqueId()).mode().name());
            plugin.message(player, "&7Pilihan: &ffollow&7, &fdefend&7, &fstay");
            return;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        PetMode mode = switch (raw) {
            case "follow" -> PetMode.FOLLOW;
            case "defend" -> PetMode.DEFEND;
            case "stay" -> PetMode.STAY;
            default -> null;
        };
        if (mode == null) {
            plugin.message(player, "&cGunakan: /pet mode <follow/defend/stay>");
            return;
        }
        manager.setMode(player, mode);
    }

    private void info(Player player, PlayerPetData data, String raw) {
        String petId = registry.normalize(raw);
        if (petId == null) {
            plugin.message(player, "&cPet tidak ditemukan.");
            return;
        }
        PetDefinition pet = registry.get(petId);
        PetProgress p = data.progress(petId);
        int stage = Math.min(p.evolution(), pet.maxEvolution());
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&b&lPET INFORMATION"));
        player.sendMessage(Colors.color("&7Pet: " + pet.displayName(stage)));
        player.sendMessage(Colors.color("&7ID: &f" + pet.id()));
        player.sendMessage(Colors.color("&7Rarity: " + pet.rarity()));
        player.sendMessage(Colors.color("&7Role: " + pet.role()));
        player.sendMessage(Colors.color("&7Element: &f" + pet.element().toUpperCase(Locale.ROOT)));
        player.sendMessage(Colors.color("&7Level: &f" + p.level() + " &8• &7EXP: &f" + Math.round(p.exp())));
        player.sendMessage(Colors.color("&7Evolution: &f" + stage + "&7/" + pet.maxEvolution()));
        player.sendMessage(Colors.color("&7HP: &c" + round(manager.totalHealth(pet, p.level(), stage))));
        player.sendMessage(Colors.color("&7Basic damage: &c" + round(manager.totalDamage(pet, p.level(), stage)) + " hearts"));
        player.sendMessage(Colors.color("&7Energy: &e" + p.energy() + "&7/" + plugin.getConfig().getInt("progression.energy-max", 100)));
        player.sendMessage(Colors.color("&7Mastery: &6M" + p.masteryLevel() + " &8• &7Bond: &d" + p.bondLevel()));
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void evolve(Player player, PlayerPetData data) {
        PetDefinition pet = registry.get(data.selectedPet());
        if (pet == null) return;
        PetProgress p = data.progress(pet.id());
        int stage = Math.min(p.evolution(), pet.maxEvolution());
        if (stage >= pet.maxEvolution()) {
            plugin.message(player, "&ePet ini sudah mencapai evolusi maksimal.");
            return;
        }

        if ("icarus".equals(pet.id())) {
            if (stage == 0) {
                if (p.level() < 25 || data.essence() < 100 || data.evolutionMaterial("icarus_feather") < 1) {
                    plugin.message(player, "&cIcarus Emberwing membutuhkan Lv.25 + 100 Essence + 1 Ember Feather.");
                    return;
                }
                data.essence(data.essence() - 100);
                data.addEvolutionMaterial("icarus_feather", -1);
                p.evolution(1);
            } else {
                if (p.level() < 50 || p.masteryLevel() < 10 || data.essence() < 300 || data.evolutionMaterial("icarus_feather") < 3 || data.evolutionMaterial("icarus_ascendant_core") < 1) {
                    plugin.message(player, "&cSolar Ascendant membutuhkan Lv.50 + M10 + 300 Essence + 3 Ember Feather + 1 Ascendant Core.");
                    return;
                }
                data.essence(data.essence() - 300);
                data.addEvolutionMaterial("icarus_feather", -3);
                data.addEvolutionMaterial("icarus_ascendant_core", -1);
                p.evolution(2);
            }
        } else if (stage == 0) {
            int requiredLevel = plugin.getConfig().getInt("progression.evolution-stage-1-level", 10);
            if (p.level() < requiredLevel) {
                plugin.message(player, "&cEvolution I membutuhkan level &f" + requiredLevel + "&c.");
                return;
            }
            if (data.evolutionMaterial("shard") < 5) {
                plugin.message(player, "&cEvolution I membutuhkan 5 Evolution Shard. Tersimpan: &f" + data.evolutionMaterial("shard"));
                return;
            }
            data.addEvolutionMaterial("shard", -5);
            p.evolution(1);
        } else {
            int requiredLevel = plugin.getConfig().getInt("progression.evolution-stage-2-level", 30);
            if (p.level() < requiredLevel) {
                plugin.message(player, "&cFinal Evolution membutuhkan level &f" + requiredLevel + "&c.");
                return;
            }
            if (data.evolutionMaterial(pet.id()) < 1) {
                plugin.message(player, "&cFinal Evolution membutuhkan 1 Evolution Core untuk pet ini.");
                return;
            }
            data.addEvolutionMaterial(pet.id(), -1);
            p.evolution(Math.min(2, pet.maxEvolution()));
        }

        store.save(data);
        plugin.message(player, "&d&lPET EVOLUTION SUCCESS! &7Bentuk baru: " + pet.displayName(p.evolution()));
        if (data.active()) manager.summon(player);
    }


    private void useSkill(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "&cGunakan: /pet skill <1|2|3|4|ultimate>");
            return;
        }
        SkillSlot slot = SkillSlot.parse(args[1]);
        if (slot == null) {
            plugin.message(player, "&cSlot skill tidak valid.");
            return;
        }
        skills.use(player, slot);
    }

    private void skillInfo(Player player, PlayerPetData data) {
        String petId = data.selectedPet();
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&b&lSKILLS &8• &f" + petId));
        for (SkillDefinition skill : skills.skillsFor(petId)) {
            long cooldown = skills.cooldownRemaining(player.getUniqueId(), petId, skill.slot());
            String cd = cooldown > 0 ? " &8• &cCD " + String.format(Locale.US, "%.1fs", cooldown / 20.0) : " &8• &aREADY";
            player.sendMessage(Colors.color("&f" + skill.slot().key() + ". " + skill.name() + " &8• &e" + skill.energyCost() + " Energy &8• &7Lv." + skill.requiredLevel() + cd));
            player.sendMessage(Colors.color("   &8↳ &7" + skill.description()));
        }
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void list(Player player, PlayerPetData data) {
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&b&lCDRPETS COLLECTION &8• &f" + registry.size() + " PET"));
        for (PetDefinition pet : registry.all()) {
            PetProgress p = data.progress(pet.id());
            String status = data.isUnlocked(pet.id()) ? "&a✔" : "&c✘";
            player.sendMessage(Colors.color(status + " " + pet.displayName(Math.min(p.evolution(), pet.maxEvolution())) + " &8[" + pet.rarity() + "&8] &7Lv." + p.level()));
        }
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void help(Player player) {
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&b&lCDRPETS &7— Core Commands"));
        player.sendMessage(Colors.color("&f/pet &8- &7Buka collection GUI"));
        player.sendMessage(Colors.color("&f/pet pilih <pet> &8- &7Pilih pet"));
        player.sendMessage(Colors.color("&f/pet summon [pet] &8- &7Panggil pet"));
        player.sendMessage(Colors.color("&f/pet dismiss &8- &7Simpan pet"));
        player.sendMessage(Colors.color("&f/pet recall &8- &7Panggil pet ke posisi kamu"));
        player.sendMessage(Colors.color("&f/pet mode <follow/defend/stay>"));
        player.sendMessage(Colors.color("&f/pet info [pet] &8- &7Lihat stats"));
        player.sendMessage(Colors.color("&f/pet evolve &8- &7Evolusi pet terpilih"));
        player.sendMessage(Colors.color("&f/pet skill <1|2|3|4|ultimate> &8- &7Gunakan skill PvE"));
        player.sendMessage(Colors.color("&f/pet skills &8- &7Lihat skill, cost, dan cooldown"));
        player.sendMessage(Colors.color("&f/pet energy &8- &7Lihat Energy pet"));
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private static String join(String[] args, int from) {
        if (from >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static String round(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            return filter(args[0], List.of("menu", "list", "pilih", "summon", "dismiss", "recall", "mode", "info", "evolve", "skill", "skills", "energy", "help"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("skill") || args[0].equalsIgnoreCase("cast"))) {
            return filter(args[1], List.of("1", "2", "3", "4", "ultimate"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mode"))) {
            return filter(args[1], List.of("follow", "defend", "stay"));
        }
        if (args.length == 2 && Set.of("pilih", "select", "summon", "info", "stats").contains(args[0].toLowerCase(Locale.ROOT))) {
            PlayerPetData data = store.get(player.getUniqueId());
            List<String> ids = registry.all().stream().map(PetDefinition::id).filter(id -> data.isUnlocked(id) || args[0].equalsIgnoreCase("info")).toList();
            return filter(args[1], ids);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, Collection<String> values) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
