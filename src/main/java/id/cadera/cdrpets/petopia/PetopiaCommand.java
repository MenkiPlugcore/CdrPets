package id.cadera.cdrpets.petopia;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.capture.CaptureService;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.command.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;

public final class PetopiaCommand implements CommandExecutor, TabCompleter {
    private final CdrPetsPlugin plugin;
    private final PlayerDataStore store;
    private final PetRegistry registry;
    private final WildPetManager wilds;
    private final CaptureService capture;

    public PetopiaCommand(CdrPetsPlugin plugin, PlayerDataStore store, PetRegistry registry, WildPetManager wilds, CaptureService capture) {
        this.plugin = plugin;
        this.store = store;
        this.registry = registry;
        this.wilds = wilds;
        this.capture = capture;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("PETOPIA commands are player-only.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status", "info" -> status(player);
            case "dex" -> dex(player);
            case "starter" -> capture.claimStarter(player);
            case "near", "nearby" -> nearby(player);
            case "help" -> help(player);
            default -> plugin.message(player, "&dPETOPIA &8• &cGunakan /petopia help.");
        }
        return true;
    }

    private void status(Player player) {
        PlayerPetData data = store.get(player.getUniqueId());
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&d&lPETOPIA &7— Wild Pet System"));
        player.sendMessage(Colors.color("&7Seen: &f" + data.petopiaSeen().size() + "&7/" + WildPetManager.RELEASE_SPECIES.size()));
        player.sendMessage(Colors.color("&7Caught: &f" + data.petopiaCaught().size() + "&7/" + WildPetManager.RELEASE_SPECIES.size()));
        player.sendMessage(Colors.color("&7Total Capture: &f" + data.petopiaTotalCaptures() + " &8• &7Alpha: &5" + data.petopiaAlphaCaptures()));
        player.sendMessage(Colors.color("&7Wild aktif server: &f" + wilds.activeEntities().size()));
        player.sendMessage(Colors.color("&7Starter Pack: " + (data.petopiaStarterClaimed() ? "&aCLAIMED" : "&e/petopia starter")));
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void dex(Player player) {
        PlayerPetData data = store.get(player.getUniqueId());
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&d&lPETOPIA DEX"));
        for (String id : WildPetManager.RELEASE_SPECIES) {
            boolean seen = data.petopiaSeen().contains(id);
            boolean caught = data.petopiaCaught().contains(id);
            String name = seen ? registry.get(id).displayName(0) : "&8???";
            String state = caught ? "&aCAUGHT" : seen ? "&eSEEN" : "&8UNKNOWN";
            String alpha = data.petopiaAlphaCaught().contains(id) ? " &5✦ALPHA" : "";
            player.sendMessage(Colors.color("&7• " + name + " &8— " + state + alpha + " &8• &7x" + data.petopiaCaptureCount(id)));
        }
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    private void nearby(Player player) {
        LivingEntity wild = wilds.nearest(player, 64);
        if (wild == null) {
            plugin.message(player, "&dPETOPIA &8• &7Tidak ada Wild Pet dalam radius 64 blok.");
            return;
        }
        double distance = Math.sqrt(wild.getLocation().distanceSquared(player.getLocation()));
        plugin.message(player, "&dPETOPIA &8• &7Terdekat: " + (wilds.isAlpha(wild) ? "&5✦ ALPHA " : "&dWILD ") + "&f" + wilds.species(wild) + " &8• &7Lv.&f" + wilds.level(wild) + " &8• &7" + String.format(Locale.US, "%.1f", distance) + "m");
    }

    private void help(Player player) {
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color("&d&lPETOPIA COMMANDS"));
        player.sendMessage(Colors.color("&f/petopia &8- &7Status PETOPIA"));
        player.sendMessage(Colors.color("&f/petopia dex &8- &7Capture Dex"));
        player.sendMessage(Colors.color("&f/petopia starter &8- &7Klaim 3 Pet Orb satu kali"));
        player.sendMessage(Colors.color("&f/petopia near &8- &7Cari Wild Pet terdekat"));
        player.sendMessage(Colors.color("&8&m--------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "dex", "starter", "near", "help").stream().filter(v -> v.startsWith(prefix)).toList();
    }
}
