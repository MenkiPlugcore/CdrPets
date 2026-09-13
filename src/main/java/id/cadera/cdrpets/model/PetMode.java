package id.cadera.cdrpets.model;

public enum PetMode {
    FOLLOW,
    DEFEND,
    STAY;

    public static PetMode parse(String raw) {
        if (raw == null) return DEFEND;
        try {
            return PetMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return DEFEND;
        }
    }
}
