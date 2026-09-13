package id.cadera.cdrpets.registry;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.model.PetDefinition;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

public final class PetRegistry {
    private final CdrPetsPlugin plugin;
    private final Map<String, PetDefinition> pets = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public PetRegistry(CdrPetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "pets.yml");
        if (!file.exists()) plugin.saveResource("pets.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("pets");
        if (root == null) throw new IllegalStateException("pets.yml does not contain 'pets:' section");

        pets.clear();
        aliases.clear();

        for (String idRaw : root.getKeys(false)) {
            String id = idRaw.toLowerCase(Locale.ROOT);
            ConfigurationSection section = root.getConfigurationSection(idRaw);
            if (section == null) continue;
            try {
                String name = section.getString("name", id);
                List<String> forms = section.getStringList("forms");
                if (forms.isEmpty()) forms = List.of(name);
                String rarity = section.getString("rarity", "&7UNKNOWN");
                String role = section.getString("role", "&7Unknown");
                String element = section.getString("element", "neutral").toLowerCase(Locale.ROOT);
                EntityType type = EntityType.valueOf(section.getString("entity", "RABBIT").toUpperCase(Locale.ROOT));
                Material icon = Material.matchMaterial(section.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;

                PetDefinition definition = new PetDefinition(
                        id,
                        name,
                        List.copyOf(forms),
                        rarity,
                        role,
                        element,
                        type,
                        icon,
                        section.getDouble("base-health", 16.0),
                        section.getDouble("base-damage", 2.0),
                        Math.max(1L, section.getLong("attack-delay-ticks", 30L)),
                        Math.max(0, section.getInt("max-evolution", 2)),
                        section.getBoolean("custom", false),
                        section.getBoolean("admin-only", false)
                );

                pets.put(id, definition);
                aliases.put(id, id);
                aliases.put(normalizeAlias(Colors.plain(name)), id);
                for (String alias : section.getStringList("aliases")) {
                    aliases.put(normalizeAlias(alias), id);
                }
                for (String form : forms) {
                    aliases.put(normalizeAlias(Colors.plain(form)), id);
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed loading pet '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + pets.size() + " pet definitions.");
    }

    public PetDefinition get(String raw) {
        String id = normalize(raw);
        return id == null ? null : pets.get(id);
    }

    public String normalize(String raw) {
        if (raw == null) return null;
        String key = normalizeAlias(raw);
        String id = aliases.get(key);
        if (id != null) return id;
        return pets.containsKey(key) ? key : null;
    }

    public Collection<PetDefinition> all() {
        return Collections.unmodifiableCollection(pets.values());
    }

    public List<PetDefinition> publicPets() {
        return pets.values().stream().filter(p -> !p.adminOnly()).toList();
    }

    public List<PetDefinition> adminOnlyPets() {
        return pets.values().stream().filter(PetDefinition::adminOnly).toList();
    }

    public int size() {
        return pets.size();
    }

    private static String normalizeAlias(String input) {
        if (input == null) return "";
        return input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
