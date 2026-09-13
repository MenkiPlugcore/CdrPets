package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.service.PetManager;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Locale;

public final class CombatSafetyService implements Listener {
    private final CdrPetsPlugin plugin;
    private final PetManager manager;

    public CombatSafetyService(CdrPetsPlugin plugin, PetManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public boolean worldAllowed(World world) {
        if (world == null) return false;
        List<String> disabled = plugin.getConfig().getStringList("combat.disabled-worlds");
        String name = world.getName().toLowerCase(Locale.ROOT);
        return disabled.stream().map(v -> v.toLowerCase(Locale.ROOT)).noneMatch(name::equals);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectPets(EntityDamageEvent event) {
        if (manager.isPet(event.getEntity())) {
            // Active pets are virtual companions. Their HP is mutated only by CdrPets mechanics.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void validatePetDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!manager.isPet(damager)) return;
        if (!worldAllowed(damager.getWorld())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        if (manager.isPet(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player && !plugin.getConfig().getBoolean("runtime.allow-open-world-pvp", false)) {
            event.setCancelled(true);
            return;
        }
        double capHearts = Math.max(1.0, plugin.getConfig().getDouble("combat.max-direct-skill-damage-hearts", 40.0));
        event.setDamage(Math.min(event.getDamage(), capHearts * 2.0));
    }
}
