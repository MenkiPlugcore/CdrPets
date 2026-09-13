package id.cadera.cdrpets.ui;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class PetMenu implements Listener {
    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final PetManager manager;
    private final NamespacedKey petIdKey;

    public PetMenu(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store, PetManager manager) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.manager = manager;
        this.petIdKey = new NamespacedKey(plugin, "menu_pet_id");
    }

    public void open(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 54, Colors.color("&8CdrPets &7• &bCollection"));
        holder.inventory = inventory;
        PlayerPetData data = store.get(player.getUniqueId());

        int slot = 0;
        for (PetDefinition pet : registry.all()) {
            if (slot >= 45) break;
            inventory.setItem(slot++, petItem(data, pet));
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName(Colors.color("&b&lCDRPETS"));
        meta.setLore(List.of(
                Colors.color("&7Pet aktif pilihan: &f" + data.selectedPet()),
                Colors.color("&7Mode: &f" + data.mode().name()),
                Colors.color("&7Essence: &d" + data.essence()),
                "",
                Colors.color("&eKlik pet yang sudah terbuka untuk memilih."),
                Colors.color("&7GUI ini memakai inventory vanilla agar aman untuk Java/Bedrock.")
        ));
        info.setItemMeta(meta);
        inventory.setItem(49, info);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String petId = meta.getPersistentDataContainer().get(petIdKey, PersistentDataType.STRING);
        if (petId == null) return;

        PlayerPetData data = store.get(player.getUniqueId());
        if (!data.isUnlocked(petId)) {
            plugin.message(player, "&cPet tersebut masih terkunci.");
            return;
        }
        data.selectedPet(petId);
        store.save(data);
        plugin.message(player, "&aPet utama dipilih: &f" + petId + "&a.");
        player.closeInventory();
        if (data.active()) manager.summon(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private ItemStack petItem(PlayerPetData data, PetDefinition pet) {
        boolean unlocked = data.isUnlocked(pet.id());
        ItemStack item = new ItemStack(unlocked ? pet.icon() : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        PetProgress progress = data.progress(pet.id());
        meta.setDisplayName(Colors.color((unlocked ? "&a✔ " : "&c✘ ") + pet.displayName(Math.min(progress.evolution(), pet.maxEvolution()))));
        List<String> lore = new ArrayList<>();
        lore.add(Colors.color("&7ID: &f" + pet.id()));
        lore.add(Colors.color("&7Rarity: " + pet.rarity()));
        lore.add(Colors.color("&7Element: &f" + pet.element().toUpperCase()));
        lore.add(Colors.color("&7Role: " + pet.role()));
        lore.add(Colors.color("&7Level: &f" + progress.level() + " &8• &7Evo: &f" + progress.evolution()));
        if (pet.adminOnly()) lore.add(Colors.color("&bADMIN-ONLY PET"));
        lore.add("");
        lore.add(Colors.color(unlocked ? "&eKlik untuk memilih pet." : "&cBelum dimiliki."));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(petIdKey, PersistentDataType.STRING, pet.id());
        item.setItemMeta(meta);
        return item;
    }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
}
