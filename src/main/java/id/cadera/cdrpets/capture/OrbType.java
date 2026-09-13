package id.cadera.cdrpets.capture;

import org.bukkit.Material;

import java.util.Locale;

public enum OrbType {
    BASIC("basic", "&d&lPET ORB", Material.AMETHYST_SHARD, 30),
    GREAT("great", "&5&lGREAT PET ORB", Material.ECHO_SHARD, 55),
    MASTER("master", "&6&lMASTER PET ORB", Material.HEART_OF_THE_SEA, 100);

    private final String key;
    private final String displayName;
    private final Material material;
    private final int baseChance;

    OrbType(String key, String displayName, Material material, int baseChance) {
        this.key = key;
        this.displayName = displayName;
        this.material = material;
        this.baseChance = baseChance;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public Material material() { return material; }
    public int baseChance() { return baseChance; }

    public static OrbType parse(String raw) {
        if (raw == null) return null;
        String key = raw.toLowerCase(Locale.ROOT).trim();
        for (OrbType type : values()) if (type.key.equals(key)) return type;
        return null;
    }
}
