package id.cadera.cdrpets;

import id.cadera.cdrpets.command.PetAdminCommand;
import id.cadera.cdrpets.command.PetCommand;
import id.cadera.cdrpets.capture.CaptureListener;
import id.cadera.cdrpets.capture.CaptureService;
import id.cadera.cdrpets.combat.CombatListener;
import id.cadera.cdrpets.combat.CombatStatusService;
import id.cadera.cdrpets.combat.CombatSafetyService;
import id.cadera.cdrpets.combat.SkillService;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.listener.PetListener;
import id.cadera.cdrpets.petopia.PetopiaCommand;
import id.cadera.cdrpets.petopia.PetopiaListener;
import id.cadera.cdrpets.petopia.WildPetManager;
import id.cadera.cdrpets.migration.SkriptVariablesImporter;
import id.cadera.cdrpets.registry.PetRegistry;
import id.cadera.cdrpets.service.PetManager;
import id.cadera.cdrpets.ui.PetMenu;
import id.cadera.cdrpets.util.Colors;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class CdrPetsPlugin extends JavaPlugin {
    private PetRegistry registry;
    private PlayerDataStore store;
    private PetManager manager;
    private CombatStatusService statusService;
    private SkillService skillService;
    private CombatSafetyService safetyService;
    private WildPetManager wildPetManager;
    private CaptureService captureService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }

        registry = new PetRegistry(this);
        registry.load();
        store = new PlayerDataStore(this);
        manager = new PetManager(this, registry, store);
        statusService = new CombatStatusService(this, manager);
        skillService = new SkillService(this, registry, store, manager, statusService);
        safetyService = new CombatSafetyService(this, manager);
        wildPetManager = new WildPetManager(this, registry, store, manager);
        captureService = new CaptureService(this, store, registry, wildPetManager);
        PetMenu menu = new PetMenu(this, registry, store, manager);
        SkriptVariablesImporter importer = new SkriptVariablesImporter(this, store, registry);

        PetCommand petCommand = new PetCommand(this, registry, store, manager, menu, skillService);
        PetAdminCommand adminCommand = new PetAdminCommand(this, registry, store, manager, importer, statusService, skillService, wildPetManager, captureService);
        PetopiaCommand petopiaCommand = new PetopiaCommand(this, store, registry, wildPetManager, captureService);
        Objects.requireNonNull(getCommand("pet"), "pet command missing from plugin.yml").setExecutor(petCommand);
        Objects.requireNonNull(getCommand("pet"), "pet command missing from plugin.yml").setTabCompleter(petCommand);
        Objects.requireNonNull(getCommand("petadmin"), "petadmin command missing from plugin.yml").setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("petadmin"), "petadmin command missing from plugin.yml").setTabCompleter(adminCommand);
        Objects.requireNonNull(getCommand("petopia"), "petopia command missing from plugin.yml").setExecutor(petopiaCommand);
        Objects.requireNonNull(getCommand("petopia"), "petopia command missing from plugin.yml").setTabCompleter(petopiaCommand);

        getServer().getPluginManager().registerEvents(new PetListener(this, manager, store), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, manager, store, statusService, skillService), this);
        getServer().getPluginManager().registerEvents(safetyService, this);
        getServer().getPluginManager().registerEvents(new CaptureListener(wildPetManager, captureService), this);
        getServer().getPluginManager().registerEvents(new PetopiaListener(wildPetManager), this);
        getServer().getPluginManager().registerEvents(menu, this);

        if (getConfig().getBoolean("runtime.remove-orphan-pets-on-enable", true)) {
            int removed = manager.cleanupOrphans();
            if (removed > 0) getLogger().warning("Removed " + removed + " orphan CdrPets entities during startup.");
        }
        manager.start();
        statusService.start();
        skillService.start();
        wildPetManager.start();
        getLogger().info("CdrPets v" + getDescription().getVersion() + " enabled with " + registry.size() + " pets.");
    }

    @Override
    public void onDisable() {
        if (wildPetManager != null) wildPetManager.stop();
        if (skillService != null) skillService.stop();
        if (statusService != null) statusService.stop();
        if (manager != null) manager.stop();
        if (store != null) store.saveAll();
        getLogger().info("CdrPets disabled cleanly.");
    }

    public void message(CommandSender target, String body) {
        String prefix = getConfig().getString("prefix", "&8[&b&lPET&8] &7");
        target.sendMessage(Colors.color(prefix + body));
    }

    public PetRegistry registry() { return registry; }
    public PlayerDataStore store() { return store; }
    public PetManager manager() { return manager; }
    public CombatStatusService statusService() { return statusService; }
    public SkillService skillService() { return skillService; }
    public CombatSafetyService safetyService() { return safetyService; }
    public WildPetManager wildPetManager() { return wildPetManager; }
    public CaptureService captureService() { return captureService; }
}
