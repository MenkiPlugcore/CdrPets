package id.cadera.cdrpets.capture;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.petopia.WildPetManager;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class CaptureService {
    private final CdrPetsPlugin plugin;
    private final PlayerDataStore store;
    private final PetRegistry registry;
    private final WildPetManager wilds;
    private final NamespacedKey orbKey;
    private final Map<UUID, CaptureLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong transactionCounter = new AtomicLong();

    public CaptureService(CdrPetsPlugin plugin, PlayerDataStore store, PetRegistry registry, WildPetManager wilds) {
        this.plugin = plugin;
        this.store = store;
        this.registry = registry;
        this.wilds = wilds;
        this.orbKey = new NamespacedKey(plugin, "capture_orb");
    }

    public ItemStack createOrb(OrbType type, int amount) {
        ItemStack item = new ItemStack(type.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Colors.color(type.displayName()));
        List<String> lore = new ArrayList<>();
        lore.add(Colors.color("&8MPET_ORB_" + type.name() + "_CDRPETS_v031"));
        if (type == OrbType.MASTER) {
            lore.add(Colors.color("&7Menangkap Wild Pet secara pasti."));
            lore.add(Colors.color("&7Tidak membutuhkan batas HP."));
        } else {
            lore.add(Colors.color(type == OrbType.GREAT ? "&7Peluang tangkap lebih tinggi." : "&7Orb standar PETOPIA."));
            lore.add(Colors.color("&7Wild Pet normal harus ≤50% HP."));
            lore.add(Colors.color("&7Alpha harus ≤25% HP dan minimal Great Orb."));
        }
        lore.add("");
        lore.add(Colors.color("&eKlik kanan langsung pada Wild Pet."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(orbKey, PersistentDataType.STRING, type.key());
        item.setItemMeta(meta);
        return item;
    }

    public OrbType orbType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String pdc = meta.getPersistentDataContainer().get(orbKey, PersistentDataType.STRING);
        OrbType direct = OrbType.parse(pdc);
        if (direct != null) return direct;
        // Compatibility fallback for old Skript orbs that only retain lore/name/material.
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                String plain = Colors.plain(line).toUpperCase(Locale.ROOT);
                if (plain.contains("MPET_ORB_BASIC")) return OrbType.BASIC;
                if (plain.contains("MPET_ORB_GREAT")) return OrbType.GREAT;
                if (plain.contains("MPET_ORB_MASTER")) return OrbType.MASTER;
            }
        }
        String name = meta.hasDisplayName() ? Colors.plain(meta.getDisplayName()) : "";
        if (item.getType() == OrbType.BASIC.material() && name.equalsIgnoreCase("PET ORB")) return OrbType.BASIC;
        if (item.getType() == OrbType.GREAT.material() && name.equalsIgnoreCase("GREAT PET ORB")) return OrbType.GREAT;
        if (item.getType() == OrbType.MASTER.material() && name.equalsIgnoreCase("MASTER PET ORB")) return OrbType.MASTER;
        return null;
    }

    public void giveOrb(Player player, OrbType type, int amount, boolean notify) {
        if (player == null || type == null || amount < 1) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createOrb(type, amount));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        if (notify) plugin.message(player, "&dPETOPIA &8• &aKamu menerima &f" + amount + "x " + type.displayName() + "&a.");
    }

    public boolean claimStarter(Player player) {
        PlayerPetData data = store.get(player.getUniqueId());
        if (data.petopiaStarterClaimed()) {
            plugin.message(player, "&dPETOPIA &8• &eStarter Pack sudah pernah diklaim.");
            return false;
        }
        data.petopiaStarterClaimed(true); // receipt first: anti double-claim
        store.save(data);
        giveOrb(player, OrbType.BASIC, 3, false);
        plugin.message(player, "&dPETOPIA &8• &aStarter aktif: 3 Pet Orb diterima. Cari Wild Pet, lemahkan sampai 50% HP, lalu klik kanan.");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.1f);
        return true;
    }

    public int chance(LivingEntity wild, OrbType orb) {
        if (orb == OrbType.MASTER) return 100;
        double hpPercent = wild.getHealth() * 100.0 / maxHealth(wild);
        int chance = orb.baseChance() + (hpPercent <= 25.0 ? 20 : 10);
        String species = wilds.species(wild);
        chance -= wilds.rarityPenalty(species == null ? "" : species);
        if (wilds.isAlpha(wild)) chance -= plugin.getConfig().getInt("petopia.alpha-capture-penalty", 25);
        chance = Math.max(5, chance);
        return Math.min(wilds.isAlpha(wild) ? 85 : 95, chance);
    }

    public boolean attempt(Player player, LivingEntity wild, ItemStack held) {
        if (player == null || wild == null || !wilds.isWild(wild) || !wild.isValid() || wild.isDead()) return false;
        if (!player.getWorld().equals(wild.getWorld()) || player.getLocation().distanceSquared(wild.getLocation()) > 49.0) {
            plugin.message(player, "&dPETOPIA &8• &cWild Pet terlalu jauh. Maksimal 7 blok.");
            return false;
        }
        OrbType orb = orbType(held);
        if (orb == null) {
            plugin.message(player, "&dPETOPIA &8• &ePegang Pet Orb, Great Pet Orb, atau Master Pet Orb.");
            return false;
        }

        boolean alpha = wilds.isAlpha(wild);
        double hpPercent = wild.getHealth() * 100.0 / maxHealth(wild);
        if (alpha && orb == OrbType.BASIC) {
            plugin.message(player, "&dPETOPIA &8• &cPet Orb biasa tidak cukup untuk Alpha. Gunakan Great atau Master Pet Orb.");
            return false;
        }
        if (orb != OrbType.MASTER && hpPercent > (alpha ? 25.0 : 50.0)) {
            plugin.message(player, "&dPETOPIA &8• &eLemahkan " + (alpha ? "Alpha" : "Wild Pet") + " sampai maksimal &c" + (alpha ? 25 : 50) + "% HP&e. Saat ini: &f" + String.format(Locale.US, "%.1f", hpPercent) + "%&e.");
            return false;
        }

        long now = System.currentTimeMillis();
        long lockMs = plugin.getConfig().getLong("petopia.capture-lock-seconds", 30) * 1000L;
        CaptureLock existing = locks.get(wild.getUniqueId());
        if (existing != null && existing.expiresAt > now && !existing.owner.equals(player.getUniqueId())) {
            plugin.message(player, "&dPETOPIA &8• &eWild Pet sedang menjadi encounter pemain lain.");
            return false;
        }
        locks.put(wild.getUniqueId(), new CaptureLock(player.getUniqueId(), now + lockMs));

        consumeOne(held);
        String species = wilds.species(wild);
        if (species == null || registry.get(species) == null || !WildPetManager.RELEASE_SPECIES.contains(species)) {
            giveOrb(player, orb, 1, false);
            locks.remove(wild.getUniqueId());
            plugin.message(player, "&dPETOPIA &8• &cRegistry Wild Pet tidak valid. Orb dikembalikan.");
            return false;
        }

        int chance = chance(wild, orb);
        int roll = ThreadLocalRandom.current().nextInt(1, 101);
        plugin.message(player, "&dPETOPIA &8• &7Target &f" + species + " &8• &7HP &c" + String.format(Locale.US, "%.1f", hpPercent) + "% &8• &7Chance &a" + chance + "% &8• &7Roll &f" + roll);
        if (roll > chance) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
            plugin.message(player, "&dPETOPIA &8• &cCapture gagal. Wild Pet masih bisa dicoba lagi.");
            return false;
        }

        String receipt = "CAPTURE-" + transactionCounter.incrementAndGet() + "-" + wild.getUniqueId() + "-" + player.getUniqueId();
        PlayerPetData data = store.get(player.getUniqueId());
        boolean newPet = !data.isUnlocked(species);
        int duplicateEssence = 0;
        if (newPet) {
            data.unlock(species);
            PetProgress progress = data.progress(species);
            progress.level(1);
            progress.exp(0);
            progress.energy(plugin.getConfig().getInt("progression.energy-max", 100));
            progress.evolution(0);
        } else {
            duplicateEssence = alpha ? alphaDuplicateEssence(species) : duplicateEssence(species);
            data.essence(data.essence() + duplicateEssence);
        }
        if (alpha && newPet) data.essence(data.essence() + 25);

        boolean newDex = data.petopiaCaught().add(species);
        data.petopiaSeen().add(species);
        int speciesCaptures = data.incrementPetopiaCaptureCount(species);
        long totalCaptures = data.incrementPetopiaTotalCaptures();
        if (alpha) {
            data.petopiaAlphaCaught().add(species);
            data.incrementPetopiaAlphaCaptures();
        }
        processMilestones(player, data, species, speciesCaptures, newDex, alpha);
        store.save(data);

        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.sendMessage(Colors.color(alpha ? "&5&l✦ ALPHA PET CAPTURED!" : "&d&lWILD PET CAPTURED!"));
        PetDefinition def = registry.get(species);
        player.sendMessage(Colors.color("&7Pet: " + def.displayName(0) + " &8• " + def.rarity()));
        player.sendMessage(Colors.color("&7Encounter Level: &f" + wilds.level(wild)));
        if (newPet) {
            player.sendMessage(Colors.color("&aSpesies baru dibuka! &7Pet masuk koleksi pada Level 1."));
            if (alpha) player.sendMessage(Colors.color("&5Alpha Capture Bonus: &d+25 Essence&5."));
        } else {
            player.sendMessage(Colors.color("&eDuplikat dikonversi menjadi &d" + duplicateEssence + " Essence&e."));
        }
        player.sendMessage(Colors.color("&7Total capture: &f" + totalCaptures + " &8• &7Receipt: &8" + receipt));
        player.sendMessage(Colors.color("&8&m--------------------------------"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
        locks.remove(wild.getUniqueId());
        wilds.remove(wild, true);
        return true;
    }

    private void processMilestones(Player player, PlayerPetData data, String species, int speciesCaptures, boolean newDex, boolean alpha) {
        if (newDex) {
            int caught = data.petopiaCaught().size();
            if (caught == 3) { giveOrb(player, OrbType.BASIC, 2, true); }
            if (caught == 5) { giveOrb(player, OrbType.GREAT, 1, true); }
            if (caught == WildPetManager.RELEASE_SPECIES.size()) {
                data.essence(data.essence() + 100);
                plugin.message(player, "&dPETOPIA &8• &6&lPET HUNTER! &aRoster awal lengkap. &d+100 Essence&a.");
            }
        }
        if (speciesCaptures == 3) { data.essence(data.essence() + 10); plugin.message(player, "&dPETOPIA &8• &7" + species + " x3: &d+10 Essence&7."); }
        if (speciesCaptures == 5) giveOrb(player, OrbType.BASIC, 1, true);
        if (speciesCaptures == 10) giveOrb(player, OrbType.GREAT, 1, true);
        if (speciesCaptures == 25) { data.essence(data.essence() + 50); plugin.message(player, "&dPETOPIA &8• &7" + species + " x25: &d+50 Essence&7."); }
        if (alpha) {
            long count = data.petopiaAlphaCaptures();
            if (count == 1) giveOrb(player, OrbType.GREAT, 1, true);
            if (count == 3) { data.essence(data.essence() + 100); plugin.message(player, "&dPETOPIA &8• &5Alpha Dex 3! &d+100 Essence."); }
            if (count == 5) plugin.message(player, "&dPETOPIA &8• &5&lACHIEVEMENT: ALPHA HUNTER &7terbuka.");
        }
    }

    private int duplicateEssence(String species) {
        return switch (wilds.rarityKey(species)) {
            case "rare" -> 10;
            case "epic" -> 20;
            case "legendary" -> 40;
            case "mythic" -> 75;
            default -> 5;
        };
    }

    private int alphaDuplicateEssence(String species) {
        return switch (wilds.rarityKey(species)) {
            case "rare" -> 40;
            case "epic" -> 75;
            case "legendary" -> 150;
            case "mythic" -> 250;
            default -> 20;
        };
    }

    private static void consumeOne(ItemStack item) {
        if (item.getAmount() <= 1) item.setAmount(0);
        else item.setAmount(item.getAmount() - 1);
    }

    private static double maxHealth(LivingEntity entity) {
        var attr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr == null ? Math.max(1, entity.getHealth()) : Math.max(1, attr.getValue());
    }

    private record CaptureLock(UUID owner, long expiresAt) { }
}
