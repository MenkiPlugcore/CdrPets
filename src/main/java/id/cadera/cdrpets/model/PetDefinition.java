package id.cadera.cdrpets.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public record PetDefinition(
        String id,
        String baseName,
        List<String> forms,
        String rarity,
        String role,
        String element,
        EntityType entityType,
        Material icon,
        double baseHealth,
        double baseDamage,
        long attackDelayTicks,
        int maxEvolution,
        boolean custom,
        boolean adminOnly
) {
    public String displayName(int stage) {
        if (forms == null || forms.isEmpty()) return baseName;
        if (stage <= 0) return forms.getFirst();
        int index = Math.min(stage, forms.size() - 1);
        return forms.get(index);
    }
}
