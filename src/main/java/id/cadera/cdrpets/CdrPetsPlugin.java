package id.cadera.cdrpets;

import id.cadera.cdrpets.command.PetAdminCommand;
import id.cadera.cdrpets.command.PetCommand;
import id.cadera.cdrpets.data.PlayerDataStore;
import id.cadera.cdrpets.listener.PetListener;
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
        PetMenu menu = new PetMenu(this, registry, store, manager);
        SkriptVariablesImporter importer = new SkriptVariablesImporter(this, store, registry);

        PetCommand petCommand = new PetCommand(this, registry, store, manager, menu);
        PetAdminCommand adminCommand = new PetAdminCommand(this, registry, store, manager, importer);
        Objects.requireNonNull(getCommand("pet"), "pet command missing from plugin.yml").setExecutor(petCommand);
        Objects.requireNonNull(getCommand("pet"), "pet command missing from plugin.yml").setTabCompleter(petCommand);
        Objects.requireNonNull(getCommand("petadmin"), "petadmin command missing from plugin.yml").setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("petadmin"), "petadmin command missing from plugin.yml").setTabCompleter(adminCommand);

        getServer().getPluginManager().registerEvents(new PetListener(this, manager, store), this);
        getServer().getPluginManager().registerEvents(menu, this);

        if (getConfig().getBoolean("runtime.remove-orphan-pets-on-enable", true)) {
            int removed = manager.cleanupOrphans();
            if (removed > 0) getLogger().warning("Removed " + removed + " orphan CdrPets entities during startup.");
        }
        manager.start();
        getLogger().info("CdrPets v" + getDescription().getVersion() + " enabled with " + registry.size() + " pets.");
    }

    @Override
    public void onDisable() {
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
}
