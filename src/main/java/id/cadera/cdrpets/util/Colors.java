package id.cadera.cdrpets.util;

import org.bukkit.ChatColor;

public final class Colors {
    private Colors() {}

    public static String color(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String plain(String input) {
        return ChatColor.stripColor(color(input));
    }
}
