package id.cadera.cdrpets.service;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.model.PetMode;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PetManager {
    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final NamespacedKey markerKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey typeKey;
    private final Map<UUID, UUID> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> petOwners = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> combatTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextAttackTick = new ConcurrentHashMap<>();
    private BukkitTask task;
    private long tick;

    public PetManager(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.markerKey = new NamespacedKey(plugin, "pet");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.typeKey = new NamespacedKey(plugin, "type");
    }

    public void start() {
        int interval = Math.max(1, plugin.getConfig().getInt("runtime.tick-interval", 5));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::runtimeTick, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID ownerId : new ArrayList<>(activePets.keySet())) {
            Player player = Bukkit.getPlayer(ownerId);
            if (player != null) dismiss(player, false, true);
            else removeEntityOnly(ownerId);
        }
        activePets.clear();
        petOwners.clear();
        combatTargets.clear();
        nextAttackTick.clear();
    }

    public boolean summon(Player player) {
        PlayerPetData data = store.get(player.getUniqueId());
        String id = registry.normalize(data.selectedPet());
        PetDefinition definition = id == null ? null : registry.get(id);
        if (definition == null) {
            data.selectedPet("flamefox");
            definition = registry.get("flamefox");
        }
        if (definition == null) return false;
        if (!data.isUnlocked(definition.id())) {
            message(player, "&cPet tersebut masih terkunci.");
            return false;
        }

        removeEntityOnly(player.getUniqueId());
        LivingEntity pet;
        try {
            Entity spawned = player.getWorld().spawnEntity(player.getLocation(), definition.entityType());
            if (!(spawned instanceof LivingEntity living)) {
                spawned.remove();
                message(player, "&cEntity pet gagal dibuat.");
                return false;
            }
            pet = living;
        } catch (Exception ex) {
            plugin.getLogger().warning("Spawn failed for " + player.getName() + "/" + definition.id() + ": " + ex.getMessage());
            message(player, "&cPet gagal dibuat. Coba summon ulang.");
            return false;
        }

        PetProgress progress = data.progress(definition.id());
        int stage = Math.min(progress.evolution(), definition.maxEvolution());
        double maxHealth = totalHealth(definition, progress.level(), stage);
        AttributeInstance healthAttribute = pet.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttribute != null) healthAttribute.setBaseValue(Math.max(1.0, maxHealth));
        pet.setHealth(Math.min(maxHealth, healthAttribute == null ? pet.getHealth() : healthAttribute.getValue()));
        pet.setAI(false);
        pet.setCanPickupItems(false);
        pet.setSilent(true);
        pet.setPersistent(true);
        pet.setRemoveWhenFarAway(false);
        pet.setCustomName(Colors.color(definition.displayName(stage) + " &8• &f" + player.getName()));
        pet.setCustomNameVisible(true);

        PersistentDataContainer pdc = pet.getPersistentDataContainer();
        pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(typeKey, PersistentDataType.STRING, definition.id());

        activePets.put(player.getUniqueId(), pet.getUniqueId());
        petOwners.put(pet.getUniqueId(), player.getUniqueId());
        data.active(true);
        data.lastKnownName(player.getName());
        store.save(data);
        message(player, "&a" + definition.displayName(stage) + " &7berhasil dipanggil.");
        return true;
    }

    public void dismiss(Player player, boolean notify, boolean preserveActiveState) {
        removeEntityOnly(player.getUniqueId());
        PlayerPetData data = store.get(player.getUniqueId());
        if (!preserveActiveState) data.active(false);
        combatTargets.remove(player.getUniqueId());
        nextAttackTick.remove(player.getUniqueId());
        store.save(data);
        if (notify) message(player, "&ePet disimpan kembali.");
    }

    public void recall(Player player) {
        LivingEntity pet = getActivePet(player.getUniqueId());
        if (pet == null) {
            message(player, "&cTidak ada pet aktif.");
            return;
        }
        pet.teleport(safeFollowLocation(player));
        combatTargets.remove(player.getUniqueId());
        message(player, "&aPet berhasil dipanggil kembali.");
    }

    public void setMode(Player player, PetMode mode) {
        PlayerPetData data = store.get(player.getUniqueId());
        data.mode(mode);
        if (mode != PetMode.DEFEND) combatTargets.remove(player.getUniqueId());
        store.save(data);
        message(player, "&aMode diubah ke &f" + mode.name() + "&a.");
    }

    public void setCombatTarget(Player owner, LivingEntity target) {
        if (owner == null || target == null || target.isDead()) return;
        if (target instanceof Player && !plugin.getConfig().getBoolean("runtime.allow-open-world-pvp", false)) return;
        PlayerPetData data = store.get(owner.getUniqueId());
        if (data.mode() != PetMode.DEFEND || isPet(target)) return;
        combatTargets.put(owner.getUniqueId(), target.getUniqueId());
    }

    public LivingEntity getActivePet(UUID owner) {
        UUID entityId = activePets.get(owner);
        if (entityId == null) return null;
        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) return living;
        activePets.remove(owner);
        petOwners.remove(entityId);
        return null;
    }

    public boolean isPet(Entity entity) {
        if (entity == null) return false;
        Byte marker = entity.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public UUID ownerOf(Entity entity) {
        if (entity == null) return null;
        UUID cached = petOwners.get(entity.getUniqueId());
        if (cached != null) return cached;
        String raw = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    public String typeOf(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    public int cleanupOrphans() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isPet(entity) && !isTracked(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
    }

    public int cleanupOrphans(Chunk chunk) {
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (isPet(entity) && !isTracked(entity)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public void restoreIfNeeded(Player player) {
        PlayerPetData data = store.get(player.getUniqueId());
        data.lastKnownName(player.getName());
        if (data.active() && plugin.getConfig().getBoolean("runtime.restore-active-on-join", true)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) summon(player);
            }, 20L);
        }
    }

    public double totalHealth(PetDefinition def, int level, int stage) {
        double value = def.baseHealth() + (Math.max(1, level) - 1) * plugin.getConfig().getDouble("progression.health-per-level", 0.8);
        if (stage >= 2) value *= plugin.getConfig().getDouble("progression.evolution-stage-2-health-multiplier", 1.25);
        else if (stage >= 1) value *= plugin.getConfig().getDouble("progression.evolution-stage-1-health-multiplier", 1.10);
        if ("craglet".equals(def.id())) value *= 1.10;
        return value;
    }

    public double totalDamage(PetDefinition def, int level, int stage) {
        double value = def.baseDamage() + (Math.max(1, level) - 1) * plugin.getConfig().getDouble("progression.damage-per-level", 0.08);
        if (stage >= 2) value *= plugin.getConfig().getDouble("progression.evolution-stage-2-damage-multiplier", 1.18);
        else if (stage >= 1) value *= plugin.getConfig().getDouble("progression.evolution-stage-1-damage-multiplier", 1.08);
        return value;
    }

    public void addXp(Player player, String petId, double amount) {
        PlayerPetData data = store.get(player.getUniqueId());
        PetProgress p = data.progress(petId);
        int cap = plugin.getConfig().getInt("progression.level-cap", 50);
        double xp = p.exp() + Math.max(0, amount);
        int level = p.level();
        while (level < cap) {
            double required = level * plugin.getConfig().getDouble("progression.xp-per-level-multiplier", 100.0);
            if (xp < required) break;
            xp -= required;
            level++;
            message(player, "&aPet &f" + petId + " &anaik ke level &f" + level + "&a!");
        }
        p.level(level);
        p.exp(level >= cap ? 0 : xp);
        store.save(data);
    }

    private void runtimeTick() {
        int interval = Math.max(1, plugin.getConfig().getInt("runtime.tick-interval", 5));
        tick += interval;
        double targetRange = plugin.getConfig().getDouble("runtime.target-range", 18.0);
        for (UUID ownerId : new ArrayList<>(activePets.keySet())) {
            Player owner = Bukkit.getPlayer(ownerId);
            LivingEntity pet = getActivePet(ownerId);
            if (owner == null || !owner.isOnline() || pet == null) continue;
            if (!pet.getWorld().equals(owner.getWorld())) {
                pet.teleport(safeFollowLocation(owner));
                continue;
            }
            PlayerPetData data = store.get(ownerId);
            LivingEntity target = resolveTarget(ownerId);
            if (data.mode() == PetMode.DEFEND && target != null && target.getWorld().equals(owner.getWorld()) && target.getLocation().distanceSquared(owner.getLocation()) <= targetRange * targetRange) {
                moveTowards(pet, target.getLocation(), 2.2);
                attackIfReady(owner, pet, target);
            } else {
                if (target != null) combatTargets.remove(ownerId);
                if (data.mode() != PetMode.STAY) follow(owner, pet);
            }
        }
    }

    private void attackIfReady(Player owner, LivingEntity pet, LivingEntity target) {
        if (target.isDead() || !target.isValid()) {
            combatTargets.remove(owner.getUniqueId());
            return;
        }
        if (pet.getLocation().distanceSquared(target.getLocation()) > 10.24) return;
        PetDefinition def = registry.get(typeOf(pet));
        if (def == null) return;
        long next = nextAttackTick.getOrDefault(owner.getUniqueId(), 0L);
        if (tick < next) return;
        PlayerPetData data = store.get(owner.getUniqueId());
        PetProgress progress = data.progress(def.id());
        double damage = totalDamage(def, progress.level(), Math.min(progress.evolution(), def.maxEvolution()));
        nextAttackTick.put(owner.getUniqueId(), tick + def.attackDelayTicks());
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.6, 0), 6, 0.2, 0.2, 0.2, 0.05);
        target.damage(Math.max(0.1, damage * 2.0), pet);
        if (target.isDead() || target.getHealth() <= 0.01) {
            combatTargets.remove(owner.getUniqueId());
            addXp(owner, def.id(), 25);
        }
    }

    private void follow(Player owner, LivingEntity pet) {
        double start = plugin.getConfig().getDouble("runtime.follow-start-distance", 3.0);
        double teleport = plugin.getConfig().getDouble("runtime.teleport-distance", 12.0);
        double distanceSquared = pet.getLocation().distanceSquared(owner.getLocation());
        if (distanceSquared > teleport * teleport) {
            pet.teleport(safeFollowLocation(owner));
            return;
        }
        if (distanceSquared > start * start) moveTowards(pet, safeFollowLocation(owner), 1.15);
    }

    private void moveTowards(LivingEntity pet, Location destination, double step) {
        Location from = pet.getLocation();
        if (!Objects.equals(from.getWorld(), destination.getWorld())) {
            pet.teleport(destination);
            return;
        }
        Vector delta = destination.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.01) return;
        if (length > step) delta.multiply(step / length);
        Location next = from.clone().add(delta);
        next.setYaw(directionYaw(delta));
        pet.teleport(next);
    }

    private float directionYaw(Vector vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.getX(), vector.getZ()));
    }

    private Location safeFollowLocation(Player player) {
        Location base = player.getLocation().clone();
        Vector flat = base.getDirection().setY(0);
        if (flat.lengthSquared() > 0.0001) flat.normalize();
        return base.add(flat.multiply(-1.7)).add(0, 0.15, 0);
    }

    private LivingEntity resolveTarget(UUID owner) {
        UUID targetId = combatTargets.get(owner);
        if (targetId == null) return null;
        Entity entity = Bukkit.getEntity(targetId);
        if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) return living;
        combatTargets.remove(owner);
        return null;
    }

    private boolean isTracked(Entity entity) {
        UUID owner = ownerOf(entity);
        if (owner == null) return false;
        return entity.getUniqueId().equals(activePets.get(owner));
    }

    private void removeEntityOnly(UUID ownerId) {
        UUID oldId = activePets.remove(ownerId);
        if (oldId != null) {
            petOwners.remove(oldId);
            Entity old = Bukkit.getEntity(oldId);
            if (old != null) old.remove();
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!isPet(entity)) continue;
                UUID owner = ownerOf(entity);
                if (ownerId.equals(owner)) {
                    petOwners.remove(entity.getUniqueId());
                    entity.remove();
                }
            }
        }
    }

    private void message(Player player, String message) {
        plugin.message(player, message);
    }
}
