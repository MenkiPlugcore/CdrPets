package id.cadera.cdrpets.combat;

import id.cadera.cdrpets.model.SkillSlot;

public record SkillDefinition(
        SkillSlot slot,
        String name,
        String description,
        int requiredLevel,
        int energyCost,
        long cooldownTicks,
        double baseDamageHearts,
        double damagePerLevelHearts,
        boolean requiresTarget
) {}
