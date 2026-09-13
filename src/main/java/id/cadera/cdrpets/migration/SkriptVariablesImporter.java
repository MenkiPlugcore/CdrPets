package id.cadera.cdrpets.migration;

import id.cadera.cdrpets.CdrPetsPlugin;
import id.cadera.cdrpets.data.PetProgress;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.data.PlayerPetData;
import id.cadera.cdrpets.model.PetMode;
import id.cadera.cdrpets.registry.PetRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class SkriptVariablesImporter {
    private final CdrPetsPlugin plugin;
    private final PlayerDataStore store;
    private final PetRegistry registry;

    public SkriptVariablesImporter(CdrPetsPlugin plugin, PlayerDataStore store, PetRegistry registry) {
        this.plugin = plugin;
        this.store = store;
        this.registry = registry;
    }

    public ImportResult importFile(File source) throws IOException {
        if (!source.isFile()) throw new FileNotFoundException(source.getAbsolutePath());
        File backup = backupPlayerData();
        int seen = 0;
        int imported = 0;
        int unmapped = 0;
        int malformed = 0;
        Set<UUID> touched = new LinkedHashSet<>();
        List<String> unmappedLines = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(source.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                List<String> columns = parseCsv(line);
                if (columns.size() < 2) {
                    malformed++;
                    continue;
                }
                String key = cleanKey(columns.get(0));
                if (!key.startsWith("mpet.") && !key.startsWith("mpetopia.") && !key.startsWith("mpcapture.")) continue;
                seen++;
                String value = columns.get(1).trim();
                ApplyResult result = apply(key, value);
                if (result.uuid != null) touched.add(result.uuid);
                if (result.applied) imported++;
                else {
                    unmapped++;
                    if (plugin.getConfig().getBoolean("migration.keep-unmapped-legacy-keys", true)) unmappedLines.add(line);
                }
            }
        }

        for (UUID uuid : touched) store.save(uuid);
        if (!unmappedLines.isEmpty()) {
            File out = new File(plugin.getDataFolder(), "legacy-unmapped.csv");
            Files.write(out.toPath(), unmappedLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        return new ImportResult(seen, imported, unmapped, malformed, touched.size(), backup);
    }

    private ApplyResult apply(String key, String value) {
        String[] parts = key.split("::");
        if (parts.length < 2) return ApplyResult.no();
        String family = parts[0].toLowerCase(Locale.ROOT);
        UUID uuid = parseUuid(parts[1]);
        if (uuid == null) return ApplyResult.no();
        PlayerPetData data = store.get(uuid);

        try {
            if (family.equals("mpetopia.starter.receipt")) {
                data.petopiaStarterClaimed(parseBoolean(value));
                return ApplyResult.yes(uuid);
            }
            if (family.equals("mpetopia.alpha.total")) {
                data.petopiaAlphaCaptures((long) parseDouble(value));
                return ApplyResult.yes(uuid);
            }
            if (family.equals("mpcapture.player")) {
                if (parts.length >= 3 && parts[2].equalsIgnoreCase("captures")) {
                    data.petopiaTotalCaptures((long) parseDouble(value));
                    return ApplyResult.yes(uuid);
                }
            }
            if (family.equals("mpetopia.dex.seen") || family.equals("mpetopia.dex.caught") || family.equals("mpetopia.dex.capturecount") || family.equals("mpetopia.dex.alpha")) {
                if (parts.length < 3) return ApplyResult.no(uuid);
                String pet = registry.normalize(parts[2]);
                if (pet == null) return ApplyResult.no(uuid);
                switch (family) {
                    case "mpetopia.dex.seen" -> { if (parseBoolean(value)) data.petopiaSeen().add(pet); }
                    case "mpetopia.dex.caught" -> { if (parseBoolean(value)) data.petopiaCaught().add(pet); }
                    case "mpetopia.dex.alpha" -> { if (parseBoolean(value)) data.petopiaAlphaCaught().add(pet); }
                    case "mpetopia.dex.capturecount" -> data.petopiaCaptureCounts().put(pet, Math.max(0, (int) parseDouble(value)));
                }
                return ApplyResult.yes(uuid);
            }
            switch (family) {
                case "mpet.type" -> {
                    String pet = registry.normalize(value);
                    if (pet == null) return ApplyResult.no(uuid);
                    data.selectedPet(pet);
                    return ApplyResult.yes(uuid);
                }
                case "mpet.mode" -> {
                    data.mode(PetMode.parse(value));
                    return ApplyResult.yes(uuid);
                }
                case "mpet.essence" -> {
                    data.essence((long) parseDouble(value));
                    return ApplyResult.yes(uuid);
                }
                case "mpet.evomaterial" -> {
                    if (parts.length < 3) return ApplyResult.no(uuid);
                    data.evolutionMaterial(parts[2], (long) parseDouble(value));
                    return ApplyResult.yes(uuid);
                }
                case "mpet.unlocked", "mpet.favorite", "mpet.level", "mpet.exp", "mpet.energy", "mpet.evolution",
                     "mpet.mastery.level", "mpet.mastery.exp", "mpet.bond.level", "mpet.bond.exp" -> {
                    if (parts.length < 3) return ApplyResult.no(uuid);
                    String pet = registry.normalize(parts[2]);
                    if (pet == null) return ApplyResult.no(uuid);
                    if (family.equals("mpet.unlocked")) {
                        if (parseBoolean(value)) data.unlock(pet); else data.lock(pet);
                    } else if (family.equals("mpet.favorite")) {
                        if (parseBoolean(value)) data.favorites().add(pet); else data.favorites().remove(pet);
                    } else {
                        PetProgress progress = data.progress(pet);
                        switch (family) {
                            case "mpet.level" -> progress.level((int) parseDouble(value));
                            case "mpet.exp" -> progress.exp(parseDouble(value));
                            case "mpet.energy" -> progress.energy((int) parseDouble(value));
                            case "mpet.evolution" -> progress.evolution((int) parseDouble(value));
                            case "mpet.mastery.level" -> progress.masteryLevel((int) parseDouble(value));
                            case "mpet.mastery.exp" -> progress.masteryExp(parseDouble(value));
                            case "mpet.bond.level" -> progress.bondLevel((int) parseDouble(value));
                            case "mpet.bond.exp" -> progress.bondExp(parseDouble(value));
                        }
                    }
                    return ApplyResult.yes(uuid);
                }
                default -> {
                    return ApplyResult.no(uuid);
                }
            }
        } catch (RuntimeException ex) {
            return ApplyResult.no(uuid);
        }
    }

    private File backupPlayerData() throws IOException {
        File players = new File(plugin.getDataFolder(), "players");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File backup = new File(plugin.getDataFolder(), "backups/pre-skript-import-" + stamp);
        if (!players.exists()) return backup;
        Path source = players.toPath();
        Path destination = backup.toPath();
        Files.walk(source).forEach(path -> {
            try {
                Path relative = source.relativize(path);
                Path target = destination.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
        return backup;
    }

    private static String cleanKey(String raw) {
        String key = raw.trim();
        if (key.startsWith("{") && key.endsWith("}")) key = key.substring(1, key.length() - 1);
        return key;
    }

    private static UUID parseUuid(String raw) {
        try { return UUID.fromString(raw.trim()); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static boolean parseBoolean(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("on");
    }

    private static double parseDouble(String raw) {
        String value = raw.trim().replace(',', '.');
        return Double.parseDouble(value);
    }

    private static List<String> parseCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private record ApplyResult(boolean applied, UUID uuid) {
        static ApplyResult yes(UUID uuid) { return new ApplyResult(true, uuid); }
        static ApplyResult no(UUID uuid) { return new ApplyResult(false, uuid); }
        static ApplyResult no() { return new ApplyResult(false, null); }
    }

    public record ImportResult(int seen, int imported, int unmapped, int malformed, int players, File backupDirectory) {}
}
