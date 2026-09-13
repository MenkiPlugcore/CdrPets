package id.cadera.cdrpets.data;

public final class PetProgress {
    private int level = 1;
    private double exp = 0;
    private int energy = 100;
    private int evolution = 0;
    private int masteryLevel = 0;
    private double masteryExp = 0;
    private int bondLevel = 0;
    private double bondExp = 0;

    public int level() { return level; }
    public void level(int value) { level = Math.max(1, value); }
    public double exp() { return exp; }
    public void exp(double value) { exp = Math.max(0, value); }
    public int energy() { return energy; }
    public void energy(int value) { energy = Math.max(0, value); }
    public int evolution() { return evolution; }
    public void evolution(int value) { evolution = Math.max(0, value); }
    public int masteryLevel() { return masteryLevel; }
    public void masteryLevel(int value) { masteryLevel = Math.max(0, value); }
    public double masteryExp() { return masteryExp; }
    public void masteryExp(double value) { masteryExp = Math.max(0, value); }
    public int bondLevel() { return bondLevel; }
    public void bondLevel(int value) { bondLevel = Math.max(0, value); }
    public double bondExp() { return bondExp; }
    public void bondExp(double value) { bondExp = Math.max(0, value); }
}
