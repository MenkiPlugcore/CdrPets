package id.cadera.cdrpets.data;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.model.PetMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataStore {
    private final CdrPetsPlugin plugin;
    private final File directory;
    private final Map<UUID, PlayerPetData> cache = new ConcurrentHashMap<>();

    public PlayerDataStore(CdrPetsPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "players");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create player data directory: " + directory);
        }
    }

    public PlayerPetData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void unload(UUID uuid, boolean save) {
        PlayerPetData data = cache.remove(uuid);
        if (save && data != null) save(data);
    }

    public void save(UUID uuid) {
        PlayerPetData data = cache.get(uuid);
        if (data != null) save(data);
    }

    public synchronized void save(PlayerPetData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("uuid", data.uuid().toString());
        yaml.set("last-known-name", data.lastKnownName());
        yaml.set("selected-pet", data.selectedPet());
        yaml.set("mode", data.mode().name());
        yaml.set("active", data.active());
        yaml.set("essence", data.essence());
        yaml.set("unlocked", new ArrayList<>(data.unlocked()));
        yaml.set("favorites", new ArrayList<>(data.favorites()));
        yaml.set("petopia.seen", new ArrayList<>(data.petopiaSeen()));
        yaml.set("petopia.caught", new ArrayList<>(data.petopiaCaught()));
        yaml.set("petopia.alpha-caught", new ArrayList<>(data.petopiaAlphaCaught()));
        yaml.set("petopia.starter-claimed", data.petopiaStarterClaimed());
        yaml.set("petopia.total-captures", data.petopiaTotalCaptures());
        yaml.set("petopia.alpha-captures", data.petopiaAlphaCaptures());
        for (Map.Entry<String, Integer> entry : data.petopiaCaptureCounts().entrySet()) {
            yaml.set("petopia.capture-counts." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : data.evolutionMaterials().entrySet()) {
            yaml.set("evolution-materials." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, PetProgress> entry : data.progressMap().entrySet()) {
            String base = "progress." + entry.getKey();
            PetProgress p = entry.getValue();
            yaml.set(base + ".level", p.level());
            yaml.set(base + ".exp", p.exp());
            yaml.set(base + ".energy", p.energy());
            yaml.set(base + ".evolution", p.evolution());
            yaml.set(base + ".mastery-level", p.masteryLevel());
            yaml.set(base + ".mastery-exp", p.masteryExp());
            yaml.set(base + ".bond-level", p.bondLevel());
            yaml.set(base + ".bond-exp", p.bondExp());
        }

        File target = file(data.uuid());
        File temp = new File(directory, data.uuid() + ".yml.tmp");
        try {
            yaml.save(temp);
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            try {
                yaml.save(target);
                Files.deleteIfExists(temp.toPath());
            } catch (IOException fallbackFailure) {
                plugin.getLogger().severe("Failed saving data for " + data.uuid() + ": " + fallbackFailure.getMessage());
            }
        }
    }

    public void saveAll() {
        for (PlayerPetData data : cache.values()) save(data);
    }

    public void clearCache() {
        cache.clear();
    }

    private PlayerPetData load(UUID uuid) {
        PlayerPetData data = new PlayerPetData(uuid);
        File file = file(uuid);
        if (!file.exists()) return data;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        data.lastKnownName(yaml.getString("last-known-name", "Unknown"));
        data.selectedPet(yaml.getString("selected-pet", "flamefox"));
        data.mode(PetMode.parse(yaml.getString("mode", "DEFEND")));
        data.active(yaml.getBoolean("active", false));
        data.essence(yaml.getLong("essence", 0L));

        data.unlocked().clear();
        data.unlocked().addAll(yaml.getStringList("unlocked"));
        data.unlocked().add("flamefox");
        data.favorites().addAll(yaml.getStringList("favorites"));
        data.petopiaSeen().addAll(yaml.getStringList("petopia.seen"));
        data.petopiaCaught().addAll(yaml.getStringList("petopia.caught"));
        data.petopiaAlphaCaught().addAll(yaml.getStringList("petopia.alpha-caught"));
        data.petopiaStarterClaimed(yaml.getBoolean("petopia.starter-claimed", false));
        data.petopiaTotalCaptures(yaml.getLong("petopia.total-captures", 0L));
        data.petopiaAlphaCaptures(yaml.getLong("petopia.alpha-captures", 0L));
        ConfigurationSection captureCounts = yaml.getConfigurationSection("petopia.capture-counts");
        if (captureCounts != null) {
            for (String key : captureCounts.getKeys(false)) data.petopiaCaptureCounts().put(key, captureCounts.getInt(key, 0));
        }
        ConfigurationSection materials = yaml.getConfigurationSection("evolution-materials");
        if (materials != null) {
            for (String key : materials.getKeys(false)) data.evolutionMaterial(key, materials.getLong(key, 0L));
        }

        ConfigurationSection section = yaml.getConfigurationSection("progress");
        if (section != null) {
            for (String petId : section.getKeys(false)) {
                PetProgress p = data.progress(petId);
                String base = "progress." + petId;
                p.level(yaml.getInt(base + ".level", 1));
                p.exp(yaml.getDouble(base + ".exp", 0));
                p.energy(yaml.getInt(base + ".energy", 100));
                p.evolution(yaml.getInt(base + ".evolution", 0));
                p.masteryLevel(yaml.getInt(base + ".mastery-level", 0));
                p.masteryExp(yaml.getDouble(base + ".mastery-exp", 0));
                p.bondLevel(yaml.getInt(base + ".bond-level", 0));
                p.bondExp(yaml.getDouble(base + ".bond-exp", 0));
            }
        }
        return data;
    }

    private File file(UUID uuid) {
        return new File(directory, uuid + ".yml");
    }
}
