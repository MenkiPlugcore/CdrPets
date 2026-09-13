package id.cadera.cdrpets.listener;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.service.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class PetListener implements Listener {
    private final CdrPetsPlugin plugin;
    private final PetManager manager;
    private final PlayerDataStore store;

    public PetListener(CdrPetsPlugin plugin, PetManager manager, PlayerDataStore store) {
        this.plugin = plugin;
        this.manager = manager;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetDamaged(EntityDamageEvent event) {
        if (manager.isPet(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (manager.isPet(event.getDamager())) {
            if (event.getEntity() instanceof Player && !plugin.getConfig().getBoolean("runtime.allow-open-world-pvp", false)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Player attackingPlayer = resolvePlayer(event.getDamager());
        if (attackingPlayer != null && !manager.isPet(victim)) {
            manager.setCombatTarget(attackingPlayer, victim);
        }

        if (victim instanceof Player player) {
            LivingEntity threat = resolveLivingThreat(event.getDamager());
            if (threat != null && !manager.isPet(threat)) manager.setCombatTarget(player, threat);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && manager.isPet(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerPetData data = store.get(event.getPlayer().getUniqueId());
        data.lastKnownName(event.getPlayer().getName());
        store.save(data);
        manager.restoreIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerPetData data = store.get(event.getPlayer().getUniqueId());
        boolean wasActive = data.active();
        manager.dismiss(event.getPlayer(), false, wasActive);
        store.unload(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerPetData data = store.get(event.getPlayer().getUniqueId());
        if (!data.active()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) manager.summon(event.getPlayer());
        }, 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        PlayerPetData data = store.get(event.getEntity().getUniqueId());
        if (data.active()) manager.dismiss(event.getEntity(), false, true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerPetData data = store.get(event.getPlayer().getUniqueId());
        if (!data.active()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.summon(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.getConfig().getBoolean("runtime.remove-orphan-pets-on-chunk-load", true)) {
            manager.cleanupOrphans(event.getChunk());
        }
    }

    private Player resolvePlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    private LivingEntity resolveLivingThreat(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof LivingEntity living) return living;
        }
        return null;
    }
}
