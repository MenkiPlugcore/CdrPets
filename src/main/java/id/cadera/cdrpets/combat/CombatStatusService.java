package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.service.PetManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatStatusService {
    private final CdrPetsPlugin plugin;
    private final PetManager manager;
    private final Map<UUID, EnumMap<StatusType, ActiveStatus>> statuses = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tick;

    public CombatStatusService(CdrPetsPlugin plugin, PetManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        statuses.clear();
    }

    public void apply(LivingEntity target, StatusType type, int seconds, int stacks, double damageHeartsPerSecond, UUID sourceOwner) {
        if (target == null || target.isDead() || seconds <= 0) return;
        if (target instanceof Player && !plugin.getConfig().getBoolean("runtime.allow-open-world-pvp", false)) return;
        int cappedStacks = Math.max(1, Math.min(stacks, type == StatusType.POISON ? 6 : 5));
        long expires = tick + seconds;
        EnumMap<StatusType, ActiveStatus> map = statuses.computeIfAbsent(target.getUniqueId(), ignored -> new EnumMap<>(StatusType.class));
        ActiveStatus old = map.get(type);
        if (old != null) {
            cappedStacks = Math.min(type == StatusType.POISON ? 6 : 5, Math.max(cappedStacks, old.stacks + (type == StatusType.POISON ? 1 : 0)));
            expires = Math.max(expires, old.expiresAtSecond);
            damageHeartsPerSecond = Math.max(damageHeartsPerSecond, old.damageHeartsPerSecond);
            if (sourceOwner == null) sourceOwner = old.sourceOwner;
        }
        map.put(type, new ActiveStatus(expires, cappedStacks, damageHeartsPerSecond, sourceOwner));
        applyVanillaControl(target, type, seconds);
    }

    public void clearNegative(LivingEntity target) {
        if (target == null) return;
        statuses.remove(target.getUniqueId());
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.WEAKNESS);
        target.removePotionEffect(PotionEffectType.DARKNESS);
        target.setFireTicks(0);
    }

    public boolean has(LivingEntity target, StatusType type) {
        EnumMap<StatusType, ActiveStatus> map = statuses.get(target.getUniqueId());
        return map != null && map.containsKey(type);
    }

    public String describe(LivingEntity target) {
        EnumMap<StatusType, ActiveStatus> map = statuses.get(target.getUniqueId());
        if (map == null || map.isEmpty()) return "none";
        return map.entrySet().stream().map(e -> e.getKey().name() + "x" + e.getValue().stacks).sorted().reduce((a, b) -> a + "," + b).orElse("none");
    }

    private void tick() {
        tick++;
        Iterator<Map.Entry<UUID, EnumMap<StatusType, ActiveStatus>>> outer = statuses.entrySet().iterator();
        while (outer.hasNext()) {
            Map.Entry<UUID, EnumMap<StatusType, ActiveStatus>> entry = outer.next();
            Entity raw = Bukkit.getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity target) || !target.isValid() || target.isDead()) {
                outer.remove();
                continue;
            }
            Iterator<Map.Entry<StatusType, ActiveStatus>> iterator = entry.getValue().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<StatusType, ActiveStatus> statusEntry = iterator.next();
                ActiveStatus status = statusEntry.getValue();
                if (tick > status.expiresAtSecond) {
                    iterator.remove();
                    continue;
                }
                if (status.damageHeartsPerSecond > 0 && isDot(statusEntry.getKey())) {
                    double hearts = status.damageHeartsPerSecond * status.stacks;
                    LivingEntity sourcePet = status.sourceOwner == null ? null : manager.getActivePet(status.sourceOwner);
                    if (sourcePet != null && sourcePet.isValid()) target.damage(Math.min(hearts * 2.0, 20.0), sourcePet);
                    else target.damage(Math.min(hearts * 2.0, 20.0));
                    target.getWorld().spawnParticle(particle(statusEntry.getKey()), target.getLocation().add(0, target.getHeight() * 0.5, 0), 5, 0.2, 0.2, 0.2, 0.02);
                }
            }
            if (entry.getValue().isEmpty()) outer.remove();
        }
    }

    private static boolean isDot(StatusType type) {
        return type == StatusType.BURN || type == StatusType.POISON || type == StatusType.BLEED;
    }

    private static Particle particle(StatusType type) {
        return switch (type) {
            case BURN -> Particle.FLAME;
            case POISON -> Particle.SPORE_BLOSSOM_AIR;
            case BLEED -> Particle.DAMAGE_INDICATOR;
            default -> Particle.CRIT;
        };
    }

    private static void applyVanillaControl(LivingEntity target, StatusType type, int seconds) {
        int ticks = Math.max(20, seconds * 20);
        switch (type) {
            case BURN -> target.setFireTicks(Math.max(target.getFireTicks(), ticks));
            case FREEZE, STUN, ROOT -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, false, true, true));
            case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 2, false, true, true));
            case WEAKNESS, SILENCE -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 3, false, true, true));
            default -> { }
        }
    }

    private static final class ActiveStatus {
        private final long expiresAtSecond;
        private final int stacks;
        private final double damageHeartsPerSecond;
        private final UUID sourceOwner;

        private ActiveStatus(long expiresAtSecond, int stacks, double damageHeartsPerSecond, UUID sourceOwner) {
            this.expiresAtSecond = expiresAtSecond;
            this.stacks = stacks;
            this.damageHeartsPerSecond = damageHeartsPerSecond;
            this.sourceOwner = sourceOwner;
        }
    }
}
