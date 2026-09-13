package id.cadera.cdrpets.petopia;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class WildPetManager {
    public static final List<String> RELEASE_SPECIES = List.of(
            "flamefox", "ashpup", "solchick", "dustrat", "hailhorn",
            "voltlet", "axibble", "budbun", "cindermite", "zapbug"
    );

    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final PetManager petManager;
    private final NamespacedKey wildKey;
    private final NamespacedKey speciesKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey alphaKey;
    private final NamespacedKey spawnedAtKey;
    private final Map<UUID, WildRecord> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttack = new ConcurrentHashMap<>();
    private final Set<String> alphaWorlds = ConcurrentHashMap.newKeySet();
    private BukkitTask spawnTask;
    private BukkitTask runtimeTask;

    public WildPetManager(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store, PetManager petManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.petManager = petManager;
        this.wildKey = new NamespacedKey(plugin, "wild_pet");
        this.speciesKey = new NamespacedKey(plugin, "wild_species");
        this.levelKey = new NamespacedKey(plugin, "wild_level");
        this.alphaKey = new NamespacedKey(plugin, "wild_alpha");
        this.spawnedAtKey = new NamespacedKey(plugin, "wild_spawned_at");
    }

    public void start() {
        cleanupPersistedWilds();
        long spawnInterval = Math.max(200L, plugin.getConfig().getLong("petopia.spawn-check-interval-ticks", 600L));
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnCycle, spawnInterval, spawnInterval);
        runtimeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::runtimeCycle, 20L, 20L);
    }

    public void stop() {
        if (spawnTask != null) spawnTask.cancel();
        if (runtimeTask != null) runtimeTask.cancel();
        for (UUID id : new ArrayList<>(active.keySet())) remove(id, true);
        active.clear();
        alphaWorlds.clear();
        lastAttack.clear();
    }

    public boolean isWild(Entity entity) {
        if (entity == null) return false;
        Byte marker = entity.getPersistentDataContainer().get(wildKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isAlpha(Entity entity) {
        if (!isWild(entity)) return false;
        Byte marker = entity.getPersistentDataContainer().get(alphaKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public String species(Entity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING);
    }

    public int level(Entity entity) {
        if (entity == null) return 1;
        Integer value = entity.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, value);
    }

    public boolean isTracked(Entity entity) {
        return entity != null && active.containsKey(entity.getUniqueId());
    }

    public Collection<LivingEntity> activeEntities() {
        List<LivingEntity> result = new ArrayList<>();
        for (UUID id : active.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) result.add(living);
        }
        return result;
    }

    public LivingEntity spawn(String rawType, int requestedLevel, Location location, boolean forceAlpha, String source) {
        String type = registry.normalize(rawType);
        if (type == null || !RELEASE_SPECIES.contains(type) || location == null || location.getWorld() == null) return null;
        if (!worldAllowed(location.getWorld())) return null;
        PetDefinition definition = registry.get(type);
        if (definition == null) return null;
        int globalLimit = plugin.getConfig().getInt("petopia.global-limit", 40);
        if (active.size() >= globalLimit && !source.startsWith("ADMIN")) return null;

        int level = Math.max(1, Math.min(50, requestedLevel));
        boolean alpha = forceAlpha;
        if (!alpha && !alphaWorlds.contains(location.getWorld().getName())) {
            int perThousand = Math.max(0, plugin.getConfig().getInt("petopia.alpha-chance-per-thousand", 30));
            alpha = ThreadLocalRandom.current().nextInt(1000) < perThousand;
        }
        if (alpha) level = Math.min(50, level + plugin.getConfig().getInt("petopia.alpha-level-bonus", 5));

        Entity raw;
        try {
            raw = location.getWorld().spawnEntity(location, definition.entityType());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed spawning wild " + type + ": " + ex.getMessage());
            return null;
        }
        if (!(raw instanceof LivingEntity living)) {
            raw.remove();
            return null;
        }

        double maxHealth = Math.max(6.0, Math.min(80.0, petManager.totalHealth(definition, level, 0)));
        if (alpha) maxHealth = Math.min(160.0, maxHealth * plugin.getConfig().getDouble("petopia.alpha-hp-multiplier", 1.85));
        AttributeInstance attr = living.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(maxHealth);
        living.setHealth(attr == null ? Math.min(living.getHealth(), maxHealth) : maxHealth);
        living.setPersistent(true);
        living.setRemoveWhenFarAway(false);
        living.setGlowing(true);

        PersistentDataContainer pdc = living.getPersistentDataContainer();
        pdc.set(wildKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(speciesKey, PersistentDataType.STRING, type);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(alphaKey, PersistentDataType.BYTE, alpha ? (byte) 1 : (byte) 0);
        pdc.set(spawnedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
        active.put(living.getUniqueId(), new WildRecord(type, level, alpha, System.currentTimeMillis(), location.getWorld().getName(), source));
        if (alpha) alphaWorlds.add(location.getWorld().getName());
        updateName(living);
        return living;
    }

    public void remove(Entity entity, boolean direct) {
        if (entity == null) return;
        remove(entity.getUniqueId(), direct);
    }

    public void remove(UUID entityId, boolean direct) {
        WildRecord record = active.remove(entityId);
        lastAttack.remove(entityId);
        if (record != null && record.alpha) alphaWorlds.remove(record.worldName);
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && (direct || isWild(entity))) entity.remove();
    }

    public LivingEntity nearest(Player player, double radius) {
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity wild : activeEntities()) {
            if (!wild.getWorld().equals(player.getWorld())) continue;
            double dist = wild.getLocation().distanceSquared(player.getLocation());
            if (dist <= bestSq) {
                bestSq = dist;
                best = wild;
            }
        }
        return best;
    }

    public int countNear(Player player, double radius) {
        int count = 0;
        double max = radius * radius;
        for (LivingEntity wild : activeEntities()) {
            if (wild.getWorld().equals(player.getWorld()) && wild.getLocation().distanceSquared(player.getLocation()) <= max) count++;
        }
        return count;
    }

    public int cleanupPersistedWilds() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isWild(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        active.clear();
        alphaWorlds.clear();
        return removed;
    }

    public String rarityKey(String type) {
        return switch (type) {
            case "flamefox", "frostwolf", "guardian", "axibble", "sprigawn" -> "rare";
            case "ashpup", "craglet", "voltlet", "shellop", "sporeling" -> "epic";
            case "solchick", "snowisp", "sparkit" -> "legendary";
            case "thorncub" -> "mythic";
            default -> "common";
        };
    }

    public int rarityPenalty(String type) {
        return switch (rarityKey(type)) {
            case "rare" -> 10;
            case "epic" -> 20;
            case "legendary" -> 35;
            case "mythic" -> 50;
            default -> 0;
        };
    }

    private void spawnCycle() {
        int globalLimit = plugin.getConfig().getInt("petopia.global-limit", 40);
        if (active.size() >= globalLimit) return;
        int nearbyLimit = plugin.getConfig().getInt("petopia.near-player-limit", 2);
        int chance = plugin.getConfig().getInt("petopia.spawn-chance-percent", 12);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (active.size() >= globalLimit) break;
            if (!player.isValid() || player.isDead() || !worldAllowed(player.getWorld())) continue;
            if (countNear(player, 48) >= nearbyLimit) continue;
            if (ThreadLocalRandom.current().nextInt(100) >= chance) continue;
            String type = pickType(player);
            Location safe = randomSafeLocation(player, type);
            if (safe == null) continue;
            int level = Math.max(1, Math.min(50, 1 + player.getLevel() / 2 + ThreadLocalRandom.current().nextInt(1, 6)));
            spawn(type, level, safe, false, "NATURAL:" + player.getUniqueId());
        }
    }

    private void runtimeCycle() {
        long now = System.currentTimeMillis();
        int normalDespawn = plugin.getConfig().getInt("petopia.despawn-seconds", 180);
        int alphaDespawn = plugin.getConfig().getInt("petopia.alpha-despawn-seconds", 600);
        for (UUID id : new ArrayList<>(active.keySet())) {
            WildRecord record = active.get(id);
            Entity raw = Bukkit.getEntity(id);
            if (!(raw instanceof LivingEntity wild) || !wild.isValid() || wild.isDead()) {
                remove(id, false);
                continue;
            }
            int limit = record.alpha ? alphaDespawn : normalDespawn;
            if (now - record.spawnedAt > limit * 1000L || !worldAllowed(wild.getWorld()) || !wild.getWorld().getName().equals(record.worldName)) {
                remove(id, true);
                continue;
            }
            updateName(wild);
            markSeen(wild);
            hostileTick(wild, record, now);
        }
    }

    private void markSeen(LivingEntity wild) {
        String type = species(wild);
        if (type == null) return;
        for (Player player : wild.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(wild.getLocation()) > 18 * 18) continue;
            PlayerPetData data = store.get(player.getUniqueId());
            if (data.petopiaSeen().add(type)) {
                store.save(data);
                plugin.message(player, "&dPETOPIA &8• &7Species ditemukan: &f" + Colors.plain(registry.get(type).displayName(0)) + "&7.");
            }
        }
    }

    private void hostileTick(LivingEntity wild, WildRecord record, long now) {
        double aggro = plugin.getConfig().getDouble("petopia.hostile-aggro-radius", 14.0);
        Player target = wild.getWorld().getPlayers().stream()
                .filter(p -> !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(wild.getLocation()) <= aggro * aggro)
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(wild.getLocation())))
                .orElse(null);
        if (target == null) return;
        double distance = target.getLocation().distance(wild.getLocation());
        double hitRadius = plugin.getConfig().getDouble("petopia.hostile-hit-radius", 2.6);
        if (distance > hitRadius) {
            Vector velocity = target.getLocation().toVector().subtract(wild.getLocation().toVector());
            if (velocity.lengthSquared() > 0.01) wild.setVelocity(velocity.normalize().multiply(0.22).setY(Math.max(0, wild.getVelocity().getY())));
            return;
        }
        long delay = plugin.getConfig().getLong("petopia.hostile-attack-delay-ms", 1000L);
        if (now - lastAttack.getOrDefault(wild.getUniqueId(), 0L) < delay) return;
        lastAttack.put(wild.getUniqueId(), now);
        double hearts = 1.0 + record.level * 0.035;
        if (record.alpha) hearts *= 1.35;
        target.damage(Math.min(8.0, hearts * 2.0), wild);
        petManager.setCombatTarget(target, wild);
        wild.getWorld().playSound(wild.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.0f);
    }

    private void updateName(LivingEntity wild) {
        String type = species(wild);
        PetDefinition def = registry.get(type);
        if (def == null) return;
        int level = level(wild);
        String prefix = isAlpha(wild) ? "&5&l✦ ALPHA &8• " : "&d&lWILD &8• ";
        double health = Math.max(0, wild.getHealth()) / 2.0;
        wild.setCustomName(Colors.color(prefix + def.displayName(0) + " &8[&fLv." + level + "&8] &c" + String.format(Locale.US, "%.1f", health) + "❤ &8• " + def.rarity()));
        wild.setCustomNameVisible(true);
    }

    private String pickType(Player player) {
        World.Environment environment = player.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER) return "cindermite";
        if (environment == World.Environment.THE_END) return "zapbug";
        Biome biome = player.getLocation().getBlock().getBiome();
        String name = biome.getKey().getKey();
        List<String> candidates = new ArrayList<>();
        if (name.contains("jungle")) candidates.add("voltlet");
        if (name.contains("desert") || name.contains("badlands")) candidates.add("dustrat");
        if (name.contains("mountain") || name.contains("peak") || name.contains("slope") || name.contains("grove")) candidates.add("hailhorn");
        if (name.contains("meadow") || name.contains("flower")) candidates.add("budbun");
        if (name.contains("lush") || name.contains("river") || name.contains("ocean") || name.contains("swamp")) candidates.add("axibble");
        if (name.contains("forest") || name.contains("taiga")) { candidates.add("flamefox"); candidates.add("ashpup"); }
        if (name.contains("plains") || name.contains("savanna")) candidates.add("solchick");
        if (candidates.isEmpty()) candidates.addAll(List.of("flamefox", "ashpup", "solchick", "dustrat", "hailhorn", "budbun"));
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private Location randomSafeLocation(Player player, String type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int min = plugin.getConfig().getInt("petopia.min-spawn-distance", 24);
        int max = plugin.getConfig().getInt("petopia.max-spawn-distance", 48);
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            int distance = random.nextInt(Math.max(1, min), Math.max(min + 1, max + 1));
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            World world = player.getWorld();
            int centerY = player.getLocation().getBlockY();
            if (world.getEnvironment() == World.Environment.NORMAL) {
                int top = world.getHighestBlockYAt(x, z) + 1;
                Location loc = new Location(world, x + 0.5, top, z + 0.5);
                if (safeGround(loc)) return loc;
            }
            for (int dy = 8; dy >= -8; dy--) {
                Location loc = new Location(world, x + 0.5, Math.max(world.getMinHeight() + 2, Math.min(world.getMaxHeight() - 2, centerY + dy)), z + 0.5);
                if (safeGround(loc)) return loc;
            }
        }
        return null;
    }

    private static boolean safeGround(Location loc) {
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);
        return !below.isPassable() && feet.isPassable() && head.isPassable() && !below.isLiquid();
    }

    private boolean worldAllowed(World world) {
        List<String> deny = plugin.getConfig().getStringList("petopia.disabled-worlds");
        return deny.stream().noneMatch(w -> w.equalsIgnoreCase(world.getName()));
    }

    private record WildRecord(String species, int level, boolean alpha, long spawnedAt, String worldName, String source) { }
}
