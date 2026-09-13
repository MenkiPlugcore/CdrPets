package id.cadera.cdrpets.capture;

import id.cadera.cdrpets.petopia.WildPetManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CaptureListener implements Listener {
    private final WildPetManager wilds;
    private final CaptureService capture;

    public CaptureListener(WildPetManager wilds, CaptureService capture) {
        this.wilds = wilds;
        this.capture = capture;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof LivingEntity target) || !wilds.isWild(target)) return;
        event.setCancelled(true);
        capture.attempt(event.getPlayer(), target, event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler
    public void onWildDeath(EntityDeathEvent event) {
        if (!wilds.isWild(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        wilds.remove(event.getEntity(), false);
    }
}
