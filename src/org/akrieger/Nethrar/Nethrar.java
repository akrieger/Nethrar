/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Plugin main class.
 *
 * Registers listeners based on configuration settings, and initializes the
 * Portals with the relevant worlds.
 *
 * Currently supports custom names for the normal world and nether world, and
 * whether or not to capture respawn events.
 *
 * @author akrieger
 */
public class Nethrar extends JavaPlugin {

    private final NethrarPlayerListener playerListener =
        new NethrarPlayerListener(this);

    private final NethrarVehicleListener vehicleListener =
        new NethrarVehicleListener();

    private final NethrarBlockListener blockListener =
        new NethrarBlockListener();

    private final NethrarWorldListener worldListener =
        new NethrarWorldListener();

    private final NethrarCommandExecutor commandExecutor =
        new NethrarCommandExecutor(this);

    private final Logger log = Logger.getLogger("Minecraft.Nethrar");

    private boolean usePermissions = false;

    public void onEnable() {
        log.setLevel(Level.INFO);
        Configuration c = getConfig();
        File worldsFile = new File(getDataFolder(), "worlds.yml");
        Configuration worldConfig =
            YamlConfiguration.loadConfiguration(worldsFile);
        PluginManager pm = getServer().getPluginManager();

        int debugLevel = c.getInt("debugLevel", 0);

        this.usePermissions = c.getBoolean("usePermissions", false);

        if (this.usePermissions) {
            log.info("[NETHRAR] Using Permissions. Set permissions nodes as " +
                "appropriate.");
        }

        pm.registerEvent(Event.Type.BLOCK_PHYSICS, blockListener,
            Priority.Normal, this);

        pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener,
            Priority.Normal, this);

        getCommand("nethrar").setExecutor(commandExecutor);

        boolean riderlessVehicles = c.getBoolean("riderlessVehicles", true);

        if (riderlessVehicles) {
            pm.registerEvent(Event.Type.VEHICLE_MOVE, vehicleListener,
                Priority.Normal, this);
        } else {
            log.info("[NETHRAR] Not allowing riderless vehicles to teleport.");
        }

        boolean listenForRespawns = c.getBoolean("listen.respawn", true);

        if (listenForRespawns) {
            pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener,
                Priority.Normal, this);
        } else {
            log.info("[NETHRAR] Not listening for player respawns.");
        }

        int keepAliveRadius;
        keepAliveRadius = c.getInt("forceLoadRadius", 0);

        if (keepAliveRadius > 0) {
            pm.registerEvent(Event.Type.CHUNK_UNLOAD, worldListener,
                Priority.Normal, this);

            log.info("[NETHRAR] Forcing chunks to stay loaded in a radius of " +
                keepAliveRadius + " around portals.");
        } else if (riderlessVehicles) {
            log.warning("[NETHRAR] Riderless vehicles are enabled, but no " +
                "forceLoadRadius was defined.");
            log.warning("[NETHRAR] For best results, you should " +
                "set at least a radius of 2.");
        }

        try {
            PortalUtil.initialize(this, worldConfig, keepAliveRadius);
        } catch (IOException e) {
            System.out.println("Error initializing Nethrar:");
            e.printStackTrace();
            throw new IllegalArgumentException(
                "Nethrar failed, input files were malformed."
            );
        }

        c.set("usePermissions", this.usePermissions);
        c.set("riderlessVehicles", riderlessVehicles);
        c.set("listen.respawn", listenForRespawns);
        c.set("forceLoadRadius", keepAliveRadius);
        c.set("debugLevel", debugLevel);

        saveConfig();

        PluginDescriptionFile pdfFile = this.getDescription();
        log.info("[NETHRAR] " + pdfFile.getName() + " v" +
            pdfFile.getVersion() + " enabled.");

        switch (debugLevel) {
            case 2:
                log.setLevel(Level.INFO);
                break;
            case 1:
                log.setLevel(Level.WARNING);
                break;
            default:
                log.setLevel(Level.SEVERE);
        }
    }

    public void onDisable() {
        try {
            if (!PortalUtil.savePortals()) {
                throw new IllegalArgumentException("");
            }
            log.info("[NETHRAR] Portal saving successful.");
        } catch (Exception e) {
            log.severe("[NETHRAR] Unable to save portals. All links will be " +
                "broken on reload.");
        }
    }

    public void onLoad() { }

    public boolean shouldUsePermissions() {
      return this.usePermissions;
    }
}
