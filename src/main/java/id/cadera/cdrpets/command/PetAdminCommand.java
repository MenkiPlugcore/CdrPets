package id.cadera.cdrpets.command;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.combat.CombatStatusService;
import id.cadera.cdrpets.combat.SkillService;
import id.cadera.cdrpets.capture.CaptureService;
import id.cadera.cdrpets.capture.OrbType;
import id.cadera.cdrpets.petopia.WildPetManager;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.migration.SkriptVariablesImporter;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public final class PetAdminCommand implements CommandExecutor, TabCompleter {
    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final PetManager manager;
    private final SkriptVariablesImporter importer;
    private final CombatStatusService statuses;
    private final SkillService skills;
    private final WildPetManager wilds;
    private final CaptureService capture;

    public PetAdminCommand(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store, PetManager manager, SkriptVariablesImporter importer, CombatStatusService statuses, SkillService skills, WildPetManager wilds, CaptureService capture) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.manager = manager;
        this.importer = importer;
        this.statuses = statuses;
        this.skills = skills;
        this.wilds = wilds;
        this.capture = capture;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cdrpets.admin")) {
            plugin.message(sender, "&cKamu tidak memiliki permission &fcdrpets.admin&c.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> {
                    plugin.reloadConfig();
                    registry.load();
                    plugin.message(sender, "&aConfig dan pet registry berhasil di-reload.");
                }
                case "cleanup" -> {
                    int removed = manager.cleanupOrphans();
                    plugin.message(sender, "&aCleanup selesai. &f" + removed + " &aorphan pet dihapus.");
                }
                case "unlock", "give" -> unlock(sender, args, true);
                case "lock", "remove" -> unlock(sender, args, false);
                case "setlevel" -> setLevel(sender, args);
                case "setevolution" -> setEvolution(sender, args);
                case "giveessence" -> giveEssence(sender, args);
                case "givematerial" -> giveMaterial(sender, args);
                case "saveall" -> {
                    store.saveAll();
                    plugin.message(sender, "&aSemua data cache disimpan.");
                }
                case "debug" -> debug(sender, args);
                case "combatdebug" -> combatDebug(sender, args);
                case "clearcooldowns" -> clearCooldowns(sender, args);
                case "spawnwild" -> spawnWild(sender, args);
                case "cleanupwild" -> cleanupWild(sender);
                case "giveorb" -> giveOrb(sender, args);
                case "importskript" -> importSkript(sender, args);
                default -> plugin.message(sender, "&cSubcommand admin tidak dikenal. /petadmin help");
            }
        } catch (IllegalArgumentException ex) {
            plugin.message(sender, "&cInput tidak valid: &f" + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().severe("/petadmin failed: " + ex.getMessage());
            ex.printStackTrace();
            plugin.message(sender, "&cOperasi gagal. Cek console untuk detail.");
        }
        return true;
    }

    private void unlock(CommandSender sender, String[] args, boolean unlock) {
        if (args.length < 3) throw new IllegalArgumentException("/petadmin " + (unlock ? "unlock" : "lock") + " <player> <pet>");
        OfflinePlayer target = findPlayer(args[1]);
        String petId = registry.normalize(join(args, 2));
        if (petId == null) throw new IllegalArgumentException("Pet tidak ditemukan.");
        PlayerPetData data = store.get(target.getUniqueId());
        data.lastKnownName(target.getName());
        if (unlock) data.unlock(petId); else data.lock(petId);
        if (!data.isUnlocked(data.selectedPet())) data.selectedPet("flamefox");
        store.save(data);
        plugin.message(sender, (unlock ? "&aUnlocked " : "&eLocked ") + "&f" + petId + " &7untuk &f" + displayName(target) + "&7.");
        Player online = target.getPlayer();
        if (online != null) plugin.message(online, unlock ? "&aPet baru terbuka: &f" + petId : "&ePet dikunci oleh admin: &f" + petId);
    }

    private void setLevel(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("/petadmin setlevel <player> <pet> <level>");
        OfflinePlayer target = findPlayer(args[1]);
        String petId = registry.normalize(args[2]);
        if (petId == null) throw new IllegalArgumentException("Pet tidak ditemukan.");
        int level = Math.max(1, Math.min(plugin.getConfig().getInt("progression.level-cap", 50), Integer.parseInt(args[3])));
        PlayerPetData data = store.get(target.getUniqueId());
        data.progress(petId).level(level);
        store.save(data);
        plugin.message(sender, "&aLevel &f" + petId + " &auntuk &f" + displayName(target) + " &adiubah ke &f" + level + "&a.");
    }

    private void setEvolution(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("/petadmin setevolution <player> <pet> <stage>");
        OfflinePlayer target = findPlayer(args[1]);
        String petId = registry.normalize(args[2]);
        PetDefinition pet = petId == null ? null : registry.get(petId);
        if (pet == null) throw new IllegalArgumentException("Pet tidak ditemukan.");
        int stage = Math.max(0, Math.min(pet.maxEvolution(), Integer.parseInt(args[3])));
        PlayerPetData data = store.get(target.getUniqueId());
        data.progress(petId).evolution(stage);
        store.save(data);
        plugin.message(sender, "&aEvolution &f" + petId + " &adiubah ke stage &f" + stage + "&a.");
        if (target.isOnline() && data.active() && petId.equals(data.selectedPet())) manager.summon(target.getPlayer());
    }

    private void giveEssence(CommandSender sender, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("/petadmin giveessence <player> <amount>");
        OfflinePlayer target = findPlayer(args[1]);
        long amount = Long.parseLong(args[2]);
        PlayerPetData data = store.get(target.getUniqueId());
        data.essence(data.essence() + amount);
        store.save(data);
        plugin.message(sender, "&aEssence &f" + displayName(target) + " &asekarang &d" + data.essence() + "&a.");
    }

    private void giveMaterial(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("/petadmin givematerial <player> <key> <amount>");
        OfflinePlayer target = findPlayer(args[1]);
        String key = args[2].toLowerCase(Locale.ROOT);
        long amount = Long.parseLong(args[3]);
        PlayerPetData data = store.get(target.getUniqueId());
        data.addEvolutionMaterial(key, amount);
        store.save(data);
        plugin.message(sender, "&aMaterial &f" + key + " &auntuk &f" + displayName(target) + " &asekarang &f" + data.evolutionMaterial(key) + "&a.");
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("/petadmin debug <player>");
        OfflinePlayer target = findPlayer(args[1]);
        PlayerPetData data = store.get(target.getUniqueId());
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
        sender.sendMessage(Colors.color("&b&lCDRPETS DEBUG &8• &f" + displayName(target)));
        sender.sendMessage(Colors.color("&7UUID: &f" + target.getUniqueId()));
        sender.sendMessage(Colors.color("&7Selected: &f" + data.selectedPet() + " &8• &7Mode: &f" + data.mode()));
        sender.sendMessage(Colors.color("&7Active persisted: &f" + data.active() + " &8• &7Runtime entity: &f" + (manager.getActivePet(target.getUniqueId()) != null)));
        sender.sendMessage(Colors.color("&7Unlocked: &f" + data.unlocked().size() + " &8• &7Essence: &d" + data.essence()));
        PetProgress p = data.progress(data.selectedPet());
        sender.sendMessage(Colors.color("&7Selected progress: &fLv." + p.level() + " EXP " + p.exp() + " Evo " + p.evolution() + " M" + p.masteryLevel()));
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
    }


    private void combatDebug(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("/petadmin combatdebug <player>");
        OfflinePlayer target = findPlayer(args[1]);
        Player online = target.getPlayer();
        PlayerPetData data = store.get(target.getUniqueId());
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
        sender.sendMessage(Colors.color("&b&lCDRPETS COMBAT DEBUG &8• &f" + displayName(target)));
        sender.sendMessage(Colors.color("&7Pet: &f" + data.selectedPet() + " &8• &7Energy: &e" + data.progress(data.selectedPet()).energy()));
        sender.sendMessage(Colors.color("&7Cooldowns: &f" + skills.cooldownSummary(target.getUniqueId(), data.selectedPet())));
        var entity = manager.getActivePet(target.getUniqueId());
        sender.sendMessage(Colors.color("&7Runtime pet: &f" + (entity == null ? "none" : entity.getUniqueId())));
        var combatTarget = manager.getCombatTarget(target.getUniqueId());
        sender.sendMessage(Colors.color("&7Target: &f" + (combatTarget == null ? "none" : combatTarget.getType() + "/" + combatTarget.getUniqueId())));
        if (combatTarget != null) sender.sendMessage(Colors.color("&7Target statuses: &f" + statuses.describe(combatTarget)));
        if (online != null) sender.sendMessage(Colors.color("&7World: &f" + online.getWorld().getName()));
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void clearCooldowns(CommandSender sender, String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("/petadmin clearcooldowns <player>");
        OfflinePlayer target = findPlayer(args[1]);
        skills.clearCooldowns(target.getUniqueId());
        plugin.message(sender, "&aCooldown skill dibersihkan untuk &f" + displayName(target) + "&a.");
    }


    private void spawnWild(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("/petadmin spawnwild harus dijalankan player agar punya lokasi spawn.");
        if (args.length < 2) throw new IllegalArgumentException("/petadmin spawnwild <pet> [level] [alpha]");
        String petId = registry.normalize(args[1]);
        if (petId == null || !WildPetManager.RELEASE_SPECIES.contains(petId)) throw new IllegalArgumentException("Pet bukan species PETOPIA release.");
        int level = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
        boolean alpha = args.length >= 4 && args[3].equalsIgnoreCase("alpha");
        var location = player.getTargetBlockExact(12) == null ? player.getLocation().add(player.getLocation().getDirection().multiply(3)) : player.getTargetBlockExact(12).getLocation().add(0.5, 1, 0.5);
        var entity = wilds.spawn(petId, level, location, alpha, "ADMIN:" + sender.getName());
        plugin.message(sender, entity == null ? "&cGagal spawn Wild Pet." : "&aWild Pet spawned: &f" + petId + " &8• &7Lv.&f" + wilds.level(entity) + (wilds.isAlpha(entity) ? " &5✦ALPHA" : ""));
    }

    private void cleanupWild(CommandSender sender) {
        int count = wilds.activeEntities().size();
        for (var entity : new ArrayList<>(wilds.activeEntities())) wilds.remove(entity, true);
        plugin.message(sender, "&aPETOPIA cleanup: &f" + count + " &aWild Pet dihapus.");
    }

    private void giveOrb(CommandSender sender, String[] args) {
        if (args.length < 4) throw new IllegalArgumentException("/petadmin giveorb <player> <basic|great|master> <amount>");
        OfflinePlayer target = findPlayer(args[1]);
        Player online = target.getPlayer();
        if (online == null) throw new IllegalArgumentException("Player harus online untuk menerima Orb.");
        OrbType type = OrbType.parse(args[2]);
        if (type == null) throw new IllegalArgumentException("Orb harus basic/great/master.");
        int amount = Math.max(1, Integer.parseInt(args[3]));
        capture.giveOrb(online, type, amount, true);
        plugin.message(sender, "&aDiberikan &f" + amount + "x " + type.key() + " orb &ake &f" + online.getName() + "&a.");
    }

    private void importSkript(CommandSender sender, String[] args) throws Exception {
        String configured = plugin.getConfig().getString("migration.default-skript-variables-path", "plugins/Skript/variables.csv");
        File file = new File(args.length >= 2 ? join(args, 1) : configured);
        plugin.message(sender, "&eMemulai import data Skript dari &f" + file.getPath() + "&e...");
        SkriptVariablesImporter.ImportResult result = importer.importFile(file);
        plugin.message(sender, "&aImport selesai: &f" + result.imported() + "&a key diimport dari &f" + result.seen() + "&a key MPET, &f" + result.players() + "&a player tersentuh.");
        plugin.message(sender, "&7Unmapped: &f" + result.unmapped() + " &8• &7Malformed: &f" + result.malformed());
        if (result.backupDirectory().exists()) plugin.message(sender, "&7Backup sebelum import: &f" + result.backupDirectory().getPath());
    }

    private OfflinePlayer findPlayer(String raw) {
        try {
            UUID uuid = UUID.fromString(raw);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (IllegalArgumentException ignored) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(raw);
            if (!player.hasPlayedBefore() && !player.isOnline()) throw new IllegalArgumentException("Player tidak ditemukan: " + raw);
            return player;
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
        sender.sendMessage(Colors.color("&b&lCDRPETS ADMIN"));
        sender.sendMessage(Colors.color("&f/petadmin unlock <player> <pet>"));
        sender.sendMessage(Colors.color("&f/petadmin lock <player> <pet>"));
        sender.sendMessage(Colors.color("&f/petadmin setlevel <player> <pet> <level>"));
        sender.sendMessage(Colors.color("&f/petadmin setevolution <player> <pet> <stage>"));
        sender.sendMessage(Colors.color("&f/petadmin giveessence <player> <amount>"));
        sender.sendMessage(Colors.color("&f/petadmin givematerial <player> <key> <amount>"));
        sender.sendMessage(Colors.color("&f/petadmin importskript [variables.csv path]"));
        sender.sendMessage(Colors.color("&f/petadmin cleanup &8- &7hapus orphan pet CdrPets"));
        sender.sendMessage(Colors.color("&f/petadmin reload &8- &7reload config + pets.yml"));
        sender.sendMessage(Colors.color("&f/petadmin debug <player>"));
        sender.sendMessage(Colors.color("&f/petadmin combatdebug <player>"));
        sender.sendMessage(Colors.color("&f/petadmin clearcooldowns <player>"));
        sender.sendMessage(Colors.color("&f/petadmin spawnwild <pet> [level] [alpha]"));
        sender.sendMessage(Colors.color("&f/petadmin cleanupwild"));
        sender.sendMessage(Colors.color("&f/petadmin giveorb <player> <basic|great|master> <amount>"));
        sender.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private static String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cdrpets.admin")) return List.of();
        if (args.length == 1) return filter(args[0], List.of("help", "reload", "cleanup", "unlock", "lock", "setlevel", "setevolution", "giveessence", "givematerial", "importskript", "saveall", "debug", "combatdebug", "clearcooldowns", "spawnwild", "cleanupwild", "giveorb"));
        if (args.length == 2 && args[0].equalsIgnoreCase("spawnwild")) {
            return filter(args[1], WildPetManager.RELEASE_SPECIES);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("importskript") && !args[0].equalsIgnoreCase("spawnwild")) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        if (args.length == 3 && Set.of("unlock", "lock", "setlevel", "setevolution").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(args[2], registry.all().stream().map(PetDefinition::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveorb")) {
            return filter(args[2], List.of("basic", "great", "master"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("givematerial")) {
            List<String> keys = new ArrayList<>(List.of("shard", "icarus_feather", "icarus_ascendant_core"));
            keys.addAll(registry.all().stream().map(PetDefinition::id).toList());
            return filter(args[2], keys);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, Collection<String> values) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).distinct().sorted().toList();
    }
}
