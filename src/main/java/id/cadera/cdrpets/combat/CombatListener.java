package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.service.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

public final class CombatListener implements Listener {
    private final CdrPetsPlugin plugin;
    private final PetManager manager;
    private final PlayerDataStore store;
    private final CombatStatusService statuses;
    private final SkillService skills;

    public CombatListener(CdrPetsPlugin plugin, PetManager manager, PlayerDataStore store, CombatStatusService statuses, SkillService skills) {
        this.plugin = plugin;
        this.manager = manager;
        this.store = store;
        this.statuses = statuses;
        this.skills = skills;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetBasicAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity pet) || !manager.isPet(pet)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        UUID ownerId = manager.ownerOf(pet);
        if (ownerId == null) return;
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null) return;
        String petId = manager.typeOf(pet);
        if (petId == null) return;
        PlayerPetData data = store.get(ownerId);

        double multiplier = skills.basicAttackMultiplier(ownerId);
        if (multiplier != 1.0) event.setDamage(event.getDamage() * multiplier);

        switch (petId) {
            case "flamefox" -> statuses.apply(target, StatusType.BURN, 4, 1, 0.25, ownerId);
            case "frostwolf" -> { if (Math.random() < 0.15) statuses.apply(target, StatusType.FREEZE, 2, 1, 0, ownerId); }
            case "sporeling" -> { if (Math.random() < 0.25) statuses.apply(target, StatusType.POISON, 5, 1, 0.25, ownerId); }
            case "thorncub" -> { if (Math.random() < 0.25) statuses.apply(target, StatusType.BLEED, 5, 1, 0.30, ownerId); }
            case "icarus" -> {
                statuses.apply(target, StatusType.BLEED, 6, 1, 0.30, ownerId);
                var progress = data.progress(petId);
                progress.energy(Math.min(plugin.getConfig().getInt("progression.energy-max", 100), progress.energy() + 20));
                heal(pet, event.getFinalDamage() * 0.02);
                store.save(data);
            }
            case "vengeanceminion" -> {
                heal(pet, event.getFinalDamage() * 0.10);
                if (Math.random() < 0.60) statuses.apply(target, StatusType.BLEED, 6, 1, 0.75, ownerId);
            }
            default -> { }
        }
    }

    private static void heal(LivingEntity entity, double healthPoints) {
        if (healthPoints <= 0 || entity.isDead()) return;
        var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attribute == null) return;
        entity.setHealth(Math.min(attribute.getValue(), entity.getHealth() + healthPoints));
    }
}
