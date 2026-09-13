package id.cadera.cdrpets.petopia;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class PetopiaListener implements Listener {
    private final WildPetManager wilds;

    public PetopiaListener(WildPetManager wilds) {
        this.wilds = wilds;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (wilds.isWild(entity) && !wilds.isTracked(entity)) entity.remove();
        }
    }

    @EventHandler
    public void onPortal(EntityPortalEvent event) {
        if (!wilds.isWild(event.getEntity())) return;
        event.setCancelled(true);
        wilds.remove(event.getEntity(), true);
    }
}
