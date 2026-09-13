package id.cadera.cdrpets.data;

import id.cadera.cdrpets.model.PetMode;

import java.util.*;

public final class PlayerPetData {
    private final UUID uuid;
    private String lastKnownName = "Unknown";
    private String selectedPet = "flamefox";
    private PetMode mode = PetMode.DEFEND;
    private boolean active;
    private long essence;
    private final Set<String> unlocked = new LinkedHashSet<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private final Map<String, PetProgress> progress = new LinkedHashMap<>();
    private final Map<String, Long> evolutionMaterials = new LinkedHashMap<>();
    private final Set<String> petopiaSeen = new LinkedHashSet<>();
    private final Set<String> petopiaCaught = new LinkedHashSet<>();
    private final Set<String> petopiaAlphaCaught = new LinkedHashSet<>();
    private final Map<String, Integer> petopiaCaptureCounts = new LinkedHashMap<>();
    private boolean petopiaStarterClaimed;
    private long petopiaTotalCaptures;
    private long petopiaAlphaCaptures;

    public PlayerPetData(UUID uuid) {
        this.uuid = uuid;
        unlocked.add("flamefox");
    }

    public UUID uuid() { return uuid; }
    public String lastKnownName() { return lastKnownName; }
    public void lastKnownName(String value) { if (value != null && !value.isBlank()) lastKnownName = value; }
    public String selectedPet() { return selectedPet; }
    public void selectedPet(String value) { if (value != null && !value.isBlank()) selectedPet = value.toLowerCase(Locale.ROOT); }
    public PetMode mode() { return mode; }
    public void mode(PetMode value) { mode = value == null ? PetMode.DEFEND : value; }
    public boolean active() { return active; }
    public void active(boolean value) { active = value; }
    public long essence() { return essence; }
    public void essence(long value) { essence = Math.max(0, value); }
    public Set<String> unlocked() { return unlocked; }
    public Set<String> favorites() { return favorites; }
    public Map<String, PetProgress> progressMap() { return progress; }
    public Map<String, Long> evolutionMaterials() { return evolutionMaterials; }
    public long evolutionMaterial(String key) { return evolutionMaterials.getOrDefault(key.toLowerCase(Locale.ROOT), 0L); }
    public void evolutionMaterial(String key, long amount) { evolutionMaterials.put(key.toLowerCase(Locale.ROOT), Math.max(0L, amount)); }
    public void addEvolutionMaterial(String key, long amount) { evolutionMaterial(key, evolutionMaterial(key) + amount); }
    public boolean isUnlocked(String petId) { return unlocked.contains(petId.toLowerCase(Locale.ROOT)); }
    public void unlock(String petId) { unlocked.add(petId.toLowerCase(Locale.ROOT)); }
    public void lock(String petId) { if (!"flamefox".equalsIgnoreCase(petId)) unlocked.remove(petId.toLowerCase(Locale.ROOT)); }
    public PetProgress progress(String petId) { return progress.computeIfAbsent(petId.toLowerCase(Locale.ROOT), ignored -> new PetProgress()); }
    public Set<String> petopiaSeen() { return petopiaSeen; }
    public Set<String> petopiaCaught() { return petopiaCaught; }
    public Set<String> petopiaAlphaCaught() { return petopiaAlphaCaught; }
    public Map<String, Integer> petopiaCaptureCounts() { return petopiaCaptureCounts; }
    public int petopiaCaptureCount(String petId) { return petopiaCaptureCounts.getOrDefault(petId.toLowerCase(Locale.ROOT), 0); }
    public int incrementPetopiaCaptureCount(String petId) { String id = petId.toLowerCase(Locale.ROOT); int next = petopiaCaptureCount(id) + 1; petopiaCaptureCounts.put(id, next); return next; }
    public boolean petopiaStarterClaimed() { return petopiaStarterClaimed; }
    public void petopiaStarterClaimed(boolean value) { petopiaStarterClaimed = value; }
    public long petopiaTotalCaptures() { return petopiaTotalCaptures; }
    public void petopiaTotalCaptures(long value) { petopiaTotalCaptures = Math.max(0, value); }
    public long incrementPetopiaTotalCaptures() { return ++petopiaTotalCaptures; }
    public long petopiaAlphaCaptures() { return petopiaAlphaCaptures; }
    public void petopiaAlphaCaptures(long value) { petopiaAlphaCaptures = Math.max(0, value); }
    public long incrementPetopiaAlphaCaptures() { return ++petopiaAlphaCaptures; }
}
