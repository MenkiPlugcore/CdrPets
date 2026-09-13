package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.model.SkillSlot;

import java.util.*;

public final class SkillCatalog {
    private static final Map<String, String[]> NAMES = new HashMap<>();
    private static final Map<String, String[]> DESCRIPTIONS = new HashMap<>();

    static {
        put("flamefox", "&6Ember Claw", "&6Flame Dash", "&6Inferno Bloom", "Damage + Burn.", "Serangan cepat yang mengabaikan kontrol ringan.", "Damage meningkat dan memperpanjang Burn.");
        put("cindermite", "&cAsh Bite", "&cMolten Shell", "&cMagma Burst", "Damage + Burn/Ash pressure.", "Shield defensif + reflect-style resistance.", "Ledakan api besar pada target.");
        put("ashpup", "&6Searing Fang", "&6Triple Howl", "&6Hellrush", "Damage + Lifesteal.", "Buff damage dan agresi singkat.", "Burst tiga gelombang serangan.");
        put("solchick", "&eSolar Peck", "&eSunflare", "&eSolar Collapse", "Damage + Energy besar.", "Burn + Weakness.", "Burst besar dengan biaya Energy penuh.");
        put("frostwolf", "&bFrozen Fang", "&bIce Prison", "&bAbsolute Zero", "Damage + peluang Freeze.", "Freeze/Root kuat.", "Damage besar + Freeze.");
        put("hailhorn", "&fFrost Ram", "&fSnow Mantle", "&fAvalanche Charge", "Damage + guard break/Weakness.", "Shield dan Resistance.", "Damage besar + peluang Stun.");
        put("snowisp", "&bCrystal Ray", "&bMirror Frost", "&bAbsolute Aurora", "Damage + Slow/energy pressure.", "Mitigasi serangan berikutnya.", "Freeze + kontrol berat.");
        put("guardian", "&7Stone Punch", "&7Rune Barrier", "&7Continental Crash", "Damage + pertahanan.", "Resistance/Guard tanpa kehilangan tempo.", "Damage berdasarkan build tank.");
        put("dustrat", "&6Sand Claw", "&6Tunnel Ambush", "&6Sinkhole", "Damage + Slow.", "Burst ambush dan mobilitas.", "Damage + kontrol area.");
        put("craglet", "&8Rock Tail", "&8Stone Roar", "&8Titan Slam", "Heavy damage.", "Weakness pada target.", "Heavy burst scaling dari ketahanan.");
        put("voltlet", "&eThunder Peck", "&eStatic Cage", "&eDivine Thunderstorm", "Damage + Energy.", "Slow/Static control.", "Burst listrik + Stun.");
        put("zapbug", "&eStatic Sting", "&eCharge Battery", "&eChain Surge", "Damage cepat + Energy.", "Isi ulang Energy besar.", "Burst listrik berantai.");
        put("sparkit", "&eArc Claw", "&eOvervolt", "&eSkybreaker Bolt", "Damage kritis tinggi.", "Korbankan HP untuk Energy.", "Burst listrik besar + Stun.");
        put("axibble", "&bBubble Bite", "&bHealing Tide", "&bAbyssal Wave", "Damage + sustain.", "Heal + Cleanse.", "Heal besar + damage air.");
        put("bubfin", "&3Bubble Shot", "&3Aqua Ring", "&3Tidal Spiral", "Damage stabil.", "Regeneration.", "Burst air + sustain.");
        put("shellop", "&9Shell Bash", "&9Tidal Guard", "&9Ocean Fortress", "Damage + tank pressure.", "Shield/Resistance.", "Fortress heal + Resistance.");
        put("sprigawn", "&aVine Strike", "&aVerdant Shield", "&aWorldtree Blessing", "Damage + Nature sustain.", "Shield berdasarkan durability.", "Heal besar + Energy.");
        put("budbun", "&dVine Whip", "&dPollen Puff", "&dBloom Parade", "Fast damage + Slow.", "Heal/Regeneration.", "AoE sustain dan control.");
        put("sporeling", "&5Toxic Spore", "&5Mycelium Link", "&5Sporestorm", "Poison pada target.", "Sustain colony.", "Poison stack besar.");
        put("thorncub", "&2Thorn Rend", "&2Root Prison", "&2Worldroot Wrath", "Damage + Bleed.", "Root target.", "Burst Nature + Root/Bleed.");
        put("menkibun", "&bAzure Dash", "&3Royal Barrier", "&bOwner's Decree", "Dash damage.", "Resistance 6 detik.", "Burst + heal owner bond.");
        put("icarus", "&6Solar Impact", "&ePhoenix Guard", "&dAscendant Requiem", "Damage + control + lifesteal.", "Cleanse + Resistance + Regeneration.", "Burst + debuff + damage buff.");
        put("voxaur", "&5Sonic Dash", "&dSound Explosion", "&5&lSonic Boom", "Sonic damage.", "Damage + knock/control area.", "Dispel + Weakness + Slow.");
        put("thewarden", "&3Sculk Tremor", "&bEcho Barrier", "&3&lSonic Boom", "Damage + Weakness.", "Heal + Resistance.", "Burst + Silence/Weakness.");
        put("vengeanceminion", "&4Red Genocide", "&cVengeance Era", "&4&lDeity of Vengeance", "13 hearts + lifesteal.", "3/9/15 hearts combo.", "30 hearts + Weakness.");
    }

    private SkillCatalog() {}

    private static void put(String id, String s1, String s2, String ult, String d1, String d2, String du) {
        NAMES.put(id, new String[]{s1, s2, ult});
        DESCRIPTIONS.put(id, new String[]{d1, d2, du});
    }

    public static SkillDefinition definition(String petId, SkillSlot slot) {
        String id = petId == null ? "" : petId.toLowerCase(Locale.ROOT);
        String[] names = NAMES.getOrDefault(id, new String[]{"&fSkill 1", "&fSkill 2", "&fUltimate"});
        String[] desc = DESCRIPTIONS.getOrDefault(id, new String[]{"Damage skill.", "Utility skill.", "Ultimate skill."});
        int level = switch (slot) {
            case SKILL_1 -> 1;
            case SKILL_2 -> 10;
            case SKILL_3 -> 30;
            case SKILL_4 -> 40;
            case ULTIMATE -> 20;
        };
        int cost = switch (slot) {
            case SKILL_1 -> 25;
            case SKILL_2 -> 45;
            case SKILL_3 -> 60;
            case SKILL_4, ULTIMATE -> 100;
        };
        if (id.equals("icarus")) cost = switch (slot) {
            case SKILL_1 -> 35;
            case SKILL_2 -> 40;
            case SKILL_3 -> 45;
            case ULTIMATE -> 100;
            case SKILL_4 -> 100;
        };
        if (id.equals("voxaur")) cost = switch (slot) {
            case SKILL_2 -> 35;
            case SKILL_3 -> 50;
            case SKILL_4 -> 0;
            default -> cost;
        };
        if (id.equals("vengeanceminion")) cost = switch (slot) {
            case SKILL_1 -> 20;
            case SKILL_2 -> 50;
            case ULTIMATE -> 70;
            default -> cost;
        };

        String name;
        String description;
        double base;
        double perLevel;
        boolean target = true;
        long cooldown;
        switch (slot) {
            case SKILL_1 -> { name = names[0]; description = desc[0]; base = 3.0; perLevel = 0.12; cooldown = 40; }
            case SKILL_2 -> { name = names[1]; description = desc[1]; base = 4.2; perLevel = 0.14; cooldown = 80; }
            case SKILL_3 -> { name = specialName(id, 3); description = specialDescription(id, 3); base = 0; perLevel = 0; cooldown = 120; }
            case SKILL_4 -> { name = specialName(id, 4); description = specialDescription(id, 4); base = 0; perLevel = 0; cooldown = 1400; target = false; }
            case ULTIMATE -> { name = names[2]; description = desc[2]; base = 7.0; perLevel = 0.20; cooldown = 240; }
            default -> throw new IllegalStateException();
        }
        if ((id.equals("icarus") && slot == SkillSlot.SKILL_2) || (id.equals("thewarden") && slot == SkillSlot.SKILL_2)) target = false;
        return new SkillDefinition(slot, name, description, level, cost, cooldown, base, perLevel, target);
    }

    private static String specialName(String id, int slot) {
        if (id.equals("icarus") && slot == 3) return "&cRuinous Dive";
        if (id.equals("voxaur") && slot == 3) return "&5Void Aura";
        if (id.equals("voxaur") && slot == 4) return "&dRebirth";
        return "&fSkill " + slot;
    }

    private static String specialDescription(String id, int slot) {
        if (id.equals("icarus") && slot == 3) return "Damage berat + Weakness.";
        if (id.equals("voxaur") && slot == 3) return "Control area selama 9 detik.";
        if (id.equals("voxaur") && slot == 4) return "Cleanse, heal penuh, +35 Energy; cooldown 70 detik.";
        return "Skill belum tersedia untuk pet ini.";
    }

    public static boolean supports(String petId, SkillSlot slot) {
        if (slot == SkillSlot.SKILL_3) return "icarus".equals(petId) || "voxaur".equals(petId);
        if (slot == SkillSlot.SKILL_4) return "voxaur".equals(petId);
        return true;
    }
}
