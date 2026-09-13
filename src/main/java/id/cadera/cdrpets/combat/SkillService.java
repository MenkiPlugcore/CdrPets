package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.model.SkillSlot;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SkillService {
    private final CdrPetsPlugin plugin;
    private final PetRegistry registry;
    private final PlayerDataStore store;
    private final PetManager manager;
    private final CombatStatusService statuses;
    private final Map<CooldownKey, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> temporaryDamageBuffActions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> temporaryDamagePenaltyUntil = new ConcurrentHashMap<>();
    private BukkitTask energyTask;
    private long tick;

    public SkillService(CdrPetsPlugin plugin, PetRegistry registry, PlayerDataStore store, PetManager manager, CombatStatusService statuses) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.manager = manager;
        this.statuses = statuses;
    }

    public void start() {
        int interval = Math.max(20, plugin.getConfig().getInt("combat.energy-regen-interval-ticks", 40));
        energyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += interval;
            regenEnergy();
            cleanupCooldowns();
        }, interval, interval);
    }

    public void stop() {
        if (energyTask != null) energyTask.cancel();
        cooldowns.clear();
        temporaryDamageBuffActions.clear();
        temporaryDamagePenaltyUntil.clear();
    }

    public boolean use(Player player, SkillSlot slot) {
        if (plugin.getConfig().getStringList("combat.disabled-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
            plugin.message(player, "&cCombat pet dinonaktifkan di world ini.");
            return false;
        }
        PlayerPetData data = store.get(player.getUniqueId());
        PetDefinition pet = registry.get(data.selectedPet());
        LivingEntity entity = manager.getActivePet(player.getUniqueId());
        if (pet == null || entity == null) {
            plugin.message(player, "&cPanggil pet terlebih dahulu.");
            return false;
        }
        if (!SkillCatalog.supports(pet.id(), slot)) {
            plugin.message(player, "&c" + pet.id() + " belum mempunyai " + slot.key() + ".");
            return false;
        }

        SkillDefinition skill = SkillCatalog.definition(pet.id(), slot);
        PetProgress progress = data.progress(pet.id());
        if (progress.level() < skill.requiredLevel()) {
            plugin.message(player, "&cSkill tersebut terbuka pada level &f" + skill.requiredLevel() + "&c.");
            return false;
        }

        long remaining = cooldownRemaining(player.getUniqueId(), pet.id(), slot);
        if (remaining > 0) {
            plugin.message(player, "&cSkill masih cooldown &f" + String.format(Locale.US, "%.1f", remaining / 20.0) + "s&c.");
            return false;
        }
        if (progress.energy() < skill.energyCost()) {
            plugin.message(player, "&cEnergy tidak cukup. Dibutuhkan &f" + skill.energyCost() + "&c, tersedia &f" + progress.energy() + "&c.");
            return false;
        }

        LivingEntity target = manager.getCombatTarget(player.getUniqueId());
        if (skill.requiresTarget()) {
            if (target == null || target.isDead() || !target.isValid()) {
                plugin.message(player, "&cPet belum mempunyai target. Serang mob lebih dulu atau biarkan mode DEFEND mengunci target.");
                return false;
            }
            if (!target.getWorld().equals(entity.getWorld()) || entity.getLocation().distanceSquared(target.getLocation()) > 144.0) {
                plugin.message(player, "&cTarget terlalu jauh untuk skill.");
                return false;
            }
            if (target instanceof Player && !plugin.getConfig().getBoolean("runtime.allow-open-world-pvp", false)) {
                plugin.message(player, "&cSkill pet tidak boleh digunakan ke player di mode PvP saat ini.");
                return false;
            }
        }

        // Atomic state mutation: cost + cooldown are committed before effects.
        progress.energy(Math.max(0, progress.energy() - skill.energyCost()));
        cooldowns.put(new CooldownKey(player.getUniqueId(), pet.id(), slot), currentTick() + skill.cooldownTicks());
        store.save(data);

        double evo = evolutionSkillMultiplier(progress.evolution());
        double damageHearts = (skill.baseDamageHearts() + progress.level() * skill.damagePerLevelHearts()) * evo;
        boolean deal = skill.baseDamageHearts() > 0;

        if (pet.id().equals("icarus")) {
            if (slot == SkillSlot.SKILL_1) damageHearts = (5.4 + progress.level() * 0.14) * evo;
            if (slot == SkillSlot.SKILL_3) { damageHearts = (7.5 + progress.level() * 0.17) * evo; deal = true; }
            if (slot == SkillSlot.ULTIMATE) damageHearts = (10.0 + progress.level() * 0.22) * evo;
        }
        if (pet.id().equals("voxaur")) {
            if (slot == SkillSlot.SKILL_1) damageHearts = (3.45 + progress.level() * 0.11) * evo;
            if (slot == SkillSlot.SKILL_2) damageHearts = (5.0 + progress.level() * 0.14) * evo;
            if (slot == SkillSlot.ULTIMATE) damageHearts = (7.5 + progress.level() * 0.20) * evo;
            if (deal) damageHearts *= 1.05;
        }
        if (pet.id().equals("thewarden")) {
            if (slot == SkillSlot.SKILL_1) damageHearts = (3.25 + progress.level() * 0.10) * evo;
            if (slot == SkillSlot.ULTIMATE) damageHearts = (7.2 + progress.level() * 0.18) * evo;
        }
        if (pet.id().equals("vengeanceminion")) {
            if (slot == SkillSlot.SKILL_1) damageHearts = 13 * evo;
            if (slot == SkillSlot.SKILL_2) { deal = false; damageHearts = 0; }
            if (slot == SkillSlot.ULTIMATE) damageHearts = 30 * evo;
        }

        if (temporaryDamagePenaltyUntil.getOrDefault(player.getUniqueId(), 0L) > currentTick() && deal) damageHearts *= 0.95;
        Integer buffActions = temporaryDamageBuffActions.get(player.getUniqueId());
        if (buffActions != null && buffActions > 0 && deal) {
            damageHearts *= 1.25;
            if (buffActions <= 1) temporaryDamageBuffActions.remove(player.getUniqueId());
            else temporaryDamageBuffActions.put(player.getUniqueId(), buffActions - 1);
        }

        applyElementSideEffect(player, entity, target, pet, slot);
        deal = applySpecific(player, entity, target, pet, progress, slot, damageHearts, deal, evo);

        if (deal && target != null && target.isValid() && !target.isDead()) {
            damage(target, entity, damageHearts);
            if (pet.id().equals("icarus")) heal(entity, damageHearts * 0.10);
            if (pet.id().equals("vengeanceminion")) heal(entity, Math.min(damageHearts * 0.10, 8.0));
        }

        entity.getWorld().spawnParticle(Particle.ENCHANT, entity.getLocation().add(0, entity.getHeight() * 0.7, 0), 12, 0.3, 0.3, 0.3, 0.05);
        if (target != null && target != entity) target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() * 0.55, 0), 10, 0.25, 0.25, 0.25, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.25f);
        plugin.message(player, "&aPet menggunakan " + skill.name() + "&a. &7Energy: &e" + progress.energy() + "&7/" + energyMax());
        return true;
    }

    public List<SkillDefinition> skillsFor(String petId) {
        List<SkillDefinition> out = new ArrayList<>();
        for (SkillSlot slot : SkillSlot.values()) if (SkillCatalog.supports(petId, slot)) out.add(SkillCatalog.definition(petId, slot));
        return out;
    }

    public long cooldownRemaining(UUID owner, String petId, SkillSlot slot) {
        return Math.max(0L, cooldowns.getOrDefault(new CooldownKey(owner, petId, slot), 0L) - currentTick());
    }


    public String cooldownSummary(UUID owner, String petId) {
        List<String> parts = new ArrayList<>();
        for (SkillSlot slot : SkillSlot.values()) {
            if (!SkillCatalog.supports(petId, slot)) continue;
            long left = cooldownRemaining(owner, petId, slot);
            if (left > 0) parts.add(slot.key() + "=" + String.format(Locale.US, "%.1fs", left / 20.0));
        }
        return parts.isEmpty() ? "ready" : String.join(",", parts);
    }

    public void clearCooldowns(UUID owner) {
        cooldowns.keySet().removeIf(k -> k.owner.equals(owner));
    }

    public double basicAttackMultiplier(UUID owner) {
        if (temporaryDamagePenaltyUntil.getOrDefault(owner, 0L) > currentTick()) return 0.95;
        Integer buff = temporaryDamageBuffActions.get(owner);
        if (buff != null && buff > 0) {
            if (buff <= 1) temporaryDamageBuffActions.remove(owner);
            else temporaryDamageBuffActions.put(owner, buff - 1);
            return 1.25;
        }
        return 1.0;
    }

    private boolean applySpecific(Player player, LivingEntity petEntity, LivingEntity target, PetDefinition pet, PetProgress progress,
                                  SkillSlot slot, double damageHearts, boolean deal, double evo) {
        UUID owner = player.getUniqueId();
        String id = pet.id();
        switch (id) {
            case "flamefox" -> {
                if (target != null && (slot == SkillSlot.SKILL_1 || slot == SkillSlot.ULTIMATE)) statuses.apply(target, StatusType.BURN, slot == SkillSlot.ULTIMATE ? 7 : 4, slot == SkillSlot.ULTIMATE ? 2 : 1, 0.45, owner);
            }
            case "cindermite" -> {
                if (slot == SkillSlot.SKILL_2) {
                    resistance(petEntity, 8, 1);
                    heal(petEntity, 2.0);
                    deal = false;
                } else if (target != null) statuses.apply(target, StatusType.BURN, 4, 1, 0.35, owner);
            }
            case "ashpup" -> {
                if (slot == SkillSlot.SKILL_1) heal(petEntity, Math.max(1.5, damageHearts * 0.20));
                if (slot == SkillSlot.SKILL_2) { petEntity.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1)); temporaryDamageBuffActions.put(owner, 3); deal = false; }
                if (slot == SkillSlot.ULTIMATE && target != null) {
                    damage(target, petEntity, damageHearts * 0.45);
                    damage(target, petEntity, damageHearts * 0.45);
                }
            }
            case "solchick" -> {
                if (slot == SkillSlot.SKILL_1) addEnergy(player, pet.id(), 20);
                if (target != null && slot == SkillSlot.SKILL_2) { statuses.apply(target, StatusType.BURN, 6, 1, 0.45, owner); statuses.apply(target, StatusType.WEAKNESS, 6, 1, 0, owner); }
            }
            case "frostwolf" -> {
                if (target != null && slot == SkillSlot.SKILL_1 && Math.random() < 0.35) statuses.apply(target, StatusType.FREEZE, 3, 1, 0, owner);
                if (target != null && slot == SkillSlot.SKILL_2) { statuses.apply(target, StatusType.ROOT, 5, 1, 0, owner); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.FREEZE, 6, 1, 0, owner);
            }
            case "hailhorn" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.WEAKNESS, 5, 1, 0, owner);
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 8, 1); heal(petEntity, 2.5); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE && Math.random() < 0.55) statuses.apply(target, StatusType.STUN, 3, 1, 0, owner);
            }
            case "snowisp" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.SLOW, 5, 1, 0, owner);
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 6, 2); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) { statuses.apply(target, StatusType.FREEZE, 7, 1, 0, owner); statuses.apply(target, StatusType.WEAKNESS, 7, 1, 0, owner); }
            }
            case "guardian" -> {
                if (slot == SkillSlot.SKILL_1) resistance(petEntity, 4, 0);
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 10, 2); heal(petEntity, 2.0); deal = false; }
                if (slot == SkillSlot.ULTIMATE) heal(petEntity, 3.0);
            }
            case "dustrat" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.SLOW, 4, 1, 0, owner);
                if (slot == SkillSlot.SKILL_2) petEntity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.ROOT, 4, 1, 0, owner);
            }
            case "craglet" -> {
                if (target != null && slot == SkillSlot.SKILL_2) { statuses.apply(target, StatusType.WEAKNESS, 8, 1, 0, owner); deal = false; }
                if (slot == SkillSlot.ULTIMATE) resistance(petEntity, 5, 1);
            }
            case "voltlet" -> {
                if (slot == SkillSlot.SKILL_1) addEnergy(player, pet.id(), 10);
                if (target != null && slot == SkillSlot.SKILL_2) statuses.apply(target, StatusType.ROOT, 5, 1, 0, owner);
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.STUN, 3, 1, 0, owner);
            }
            case "zapbug" -> {
                if (slot == SkillSlot.SKILL_1) addEnergy(player, pet.id(), 10);
                if (slot == SkillSlot.SKILL_2) { addEnergy(player, pet.id(), 45); deal = false; }
            }
            case "sparkit" -> {
                if (slot == SkillSlot.SKILL_2) { petEntity.setHealth(Math.max(1.0, petEntity.getHealth() - 4.0)); addEnergy(player, pet.id(), 45); temporaryDamageBuffActions.put(owner, 2); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.STUN, 4, 1, 0, owner);
            }
            case "axibble" -> {
                if (slot == SkillSlot.SKILL_2) { statuses.clearNegative(petEntity); heal(petEntity, 5); regeneration(petEntity, 6, 1); deal = false; }
                if (slot == SkillSlot.ULTIMATE) heal(petEntity, 5);
            }
            case "bubfin" -> {
                if (slot == SkillSlot.SKILL_2) { regeneration(petEntity, 10, 1); deal = false; }
                if (slot == SkillSlot.ULTIMATE) regeneration(petEntity, 8, 2);
            }
            case "shellop" -> {
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 10, 2); deal = false; }
                if (slot == SkillSlot.ULTIMATE) { resistance(petEntity, 12, 2); heal(petEntity, 5); }
            }
            case "sprigawn" -> {
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 8, 1); regeneration(petEntity, 8, 1); deal = false; }
                if (slot == SkillSlot.ULTIMATE) { heal(petEntity, 7); addEnergy(player, pet.id(), 25); regeneration(petEntity, 10, 2); }
            }
            case "budbun" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.SLOW, 4, 1, 0, owner);
                if (slot == SkillSlot.SKILL_2) { heal(petEntity, 3); regeneration(petEntity, 8, 1); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.ROOT, 3, 1, 0, owner);
            }
            case "sporeling" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.POISON, 6, 1, 0.35, owner);
                if (slot == SkillSlot.SKILL_2) { regeneration(petEntity, 8, 1); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.POISON, 10, 3, 0.40, owner);
            }
            case "thorncub" -> {
                if (target != null && slot == SkillSlot.SKILL_1) statuses.apply(target, StatusType.BLEED, 6, 1, 0.45, owner);
                if (target != null && slot == SkillSlot.SKILL_2) { statuses.apply(target, StatusType.ROOT, 6, 1, 0, owner); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) { statuses.apply(target, StatusType.BLEED, 8, 2, 0.50, owner); statuses.apply(target, StatusType.ROOT, 4, 1, 0, owner); }
            }
            case "menkibun" -> {
                if (slot == SkillSlot.SKILL_2) { resistance(petEntity, 6, 1); deal = false; }
                if (slot == SkillSlot.ULTIMATE) heal(petEntity, 3);
            }
            case "icarus" -> {
                if (target != null && slot == SkillSlot.SKILL_1) { statuses.apply(target, StatusType.STUN, 3, 1, 0, owner); statuses.apply(target, StatusType.WEAKNESS, 3, 1, 0, owner); }
                if (slot == SkillSlot.SKILL_2) { statuses.clearNegative(petEntity); resistance(petEntity, 6, 1); regeneration(petEntity, 6, 1); deal = false; }
                if (target != null && slot == SkillSlot.SKILL_3) statuses.apply(target, StatusType.WEAKNESS, 6, 1, 0, owner);
                if (target != null && slot == SkillSlot.ULTIMATE) { statuses.apply(target, StatusType.SILENCE, 6, 1, 0, owner); statuses.apply(target, StatusType.SLOW, 6, 1, 0, owner); temporaryDamageBuffActions.put(owner, 3); }
            }
            case "voxaur" -> {
                if (slot == SkillSlot.SKILL_2 && target != null) {
                    target.getNearbyEntities(4, 4, 4).stream().filter(e -> e instanceof LivingEntity && e != petEntity && !manager.isPet(e)).map(e -> (LivingEntity) e).forEach(e -> {
                        e.setVelocity(e.getLocation().toVector().subtract(petEntity.getLocation().toVector()).normalize().multiply(0.7).setY(0.25));
                        damage(e, petEntity, 1.575);
                    });
                }
                if (slot == SkillSlot.SKILL_3) {
                    petEntity.getNearbyEntities(5, 5, 5).stream().filter(e -> e instanceof LivingEntity && e != petEntity && !manager.isPet(e)).map(e -> (LivingEntity) e).forEach(e -> {
                        statuses.apply(e, StatusType.ROOT, 9, 1, 0, owner);
                        statuses.apply(e, StatusType.WEAKNESS, 9, 1, 0, owner);
                    });
                    deal = false;
                }
                if (slot == SkillSlot.SKILL_4) {
                    statuses.clearNegative(petEntity);
                    healToFull(petEntity);
                    addEnergy(player, pet.id(), 35);
                    temporaryDamagePenaltyUntil.put(owner, currentTick() + 1400);
                    deal = false;
                }
                if (target != null && slot == SkillSlot.ULTIMATE) {
                    target.removePotionEffect(PotionEffectType.REGENERATION);
                    target.removePotionEffect(PotionEffectType.ABSORPTION);
                    target.removePotionEffect(PotionEffectType.STRENGTH);
                    target.removePotionEffect(PotionEffectType.SPEED);
                    target.removePotionEffect(PotionEffectType.RESISTANCE);
                    statuses.apply(target, StatusType.SILENCE, 5, 1, 0, owner);
                    statuses.apply(target, StatusType.WEAKNESS, 5, 1, 0, owner);
                    statuses.apply(target, StatusType.SLOW, 5, 1, 0, owner);
                }
            }
            case "thewarden" -> {
                if (target != null && slot == SkillSlot.SKILL_1) { statuses.apply(target, StatusType.WEAKNESS, 4, 1, 0, owner); target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0)); }
                if (slot == SkillSlot.SKILL_2) { heal(petEntity, 3.5); resistance(petEntity, 6, 1); deal = false; }
                if (target != null && slot == SkillSlot.ULTIMATE) { statuses.apply(target, StatusType.SILENCE, 6, 1, 0, owner); statuses.apply(target, StatusType.WEAKNESS, 6, 1, 0, owner); target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0)); }
            }
            case "vengeanceminion" -> {
                if (slot == SkillSlot.SKILL_2 && target != null) {
                    damageAndSteal(petEntity, target, 3 * evo);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (target.isValid() && !target.isDead()) damageAndSteal(petEntity, target, 9 * evo); }, 2L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (target.isValid() && !target.isDead()) damageAndSteal(petEntity, target, 15 * evo); }, 4L);
                    deal = false;
                }
                if (target != null && slot == SkillSlot.ULTIMATE) statuses.apply(target, StatusType.WEAKNESS, 9, 1, 0, owner);
            }
            default -> { }
        }
        return deal;
    }

    private void applyElementSideEffect(Player player, LivingEntity petEntity, LivingEntity target, PetDefinition pet, SkillSlot slot) {
        if (target == null) return;
        UUID owner = player.getUniqueId();
        switch (pet.element()) {
            case "fire" -> statuses.apply(target, StatusType.BURN, 4, 1, 0.20, owner);
            case "ice" -> statuses.apply(target, StatusType.SLOW, 4, 1, 0, owner);
            case "lightning" -> addEnergy(player, pet.id(), 10);
            case "water" -> heal(petEntity, 2);
            case "nature" -> heal(petEntity, 1.5);
            case "earth" -> heal(petEntity, 1);
            default -> { }
        }
    }

    private void regenEnergy() {
        int amount = Math.max(0, plugin.getConfig().getInt("combat.energy-regen-amount", 5));
        if (amount <= 0) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerPetData data = store.get(player.getUniqueId());
            if (!data.active()) continue;
            PetProgress progress = data.progress(data.selectedPet());
            if (progress.energy() < energyMax()) {
                progress.energy(Math.min(energyMax(), progress.energy() + amount));
                store.save(data);
            }
        }
    }

    private void addEnergy(Player player, String petId, int amount) {
        PlayerPetData data = store.get(player.getUniqueId());
        PetProgress progress = data.progress(petId);
        progress.energy(Math.min(energyMax(), Math.max(0, progress.energy() + amount)));
        store.save(data);
    }

    private int energyMax() { return plugin.getConfig().getInt("progression.energy-max", 100); }

    private void cleanupCooldowns() {
        long now = currentTick();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        temporaryDamagePenaltyUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }

    private long currentTick() {
        return Math.max(tick, Bukkit.getCurrentTick());
    }

    private static double evolutionSkillMultiplier(int stage) {
        if (stage >= 2) return 1.25;
        if (stage >= 1) return 1.10;
        return 1.0;
    }

    private static void damage(LivingEntity target, LivingEntity damager, double hearts) {
        if (target == null || target.isDead() || hearts <= 0) return;
        target.damage(Math.max(0.1, hearts * 2.0), damager);
    }

    private void damageAndSteal(LivingEntity pet, LivingEntity target, double hearts) {
        double before = target.getHealth();
        damage(target, pet, hearts);
        double actualPoints = Math.max(0, before - (target.isDead() ? 0 : target.getHealth()));
        heal(pet, (actualPoints / 2.0) * 0.10);
    }

    private static void heal(LivingEntity entity, double hearts) {
        if (entity == null || entity.isDead() || hearts <= 0) return;
        double max = Objects.requireNonNull(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).getValue();
        entity.setHealth(Math.min(max, entity.getHealth() + hearts * 2.0));
    }

    private static void healToFull(LivingEntity entity) {
        if (entity == null || entity.isDead()) return;
        double max = Objects.requireNonNull(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).getValue();
        entity.setHealth(max);
    }

    private static void resistance(LivingEntity entity, int seconds, int amplifier) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, amplifier, false, true, true));
    }

    private static void regeneration(LivingEntity entity, int seconds, int amplifier) {
        entity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds * 20, amplifier, false, true, true));
    }

    private record CooldownKey(UUID owner, String petId, SkillSlot slot) { }
}
