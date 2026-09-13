package id.cadera.cdrpets.model;

import java.util.Locale;

public enum SkillSlot {
    SKILL_1("1"), SKILL_2("2"), SKILL_3("3"), SKILL_4("4"), ULTIMATE("ultimate");

    private final String key;
    SkillSlot(String key) { this.key = key; }
    public String key() { return key; }

    public static SkillSlot parse(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "skill1", "s1" -> SKILL_1;
            case "2", "skill2", "s2" -> SKILL_2;
            case "3", "skill3", "s3" -> SKILL_3;
            case "4", "skill4", "s4" -> SKILL_4;
            case "ult", "ulti", "ultimate", "u" -> ULTIMATE;
            default -> null;
        };
    }
}
