/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.config.Configuration;

import java.io.File;
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

    public static PermissionHandler permissions;

    private final NethrarPlayerListener playerListener =
        new NethrarPlayerListener();

    private final NethrarVehicleListener vehicleListener =
        new NethrarVehicleListener();

    private final NethrarWorldListener worldListener =
        new NethrarWorldListener();

    private final NethrarCommandExecutor commandExecutor =
        new NethrarCommandExecutor(this);

    private final Logger log = Logger.getLogger("Minecraft.Nethrar");

    public void onEnable() {
        log.setLevel(Level.INFO);
        Configuration c = getConfiguration();
        Configuration worldConfig = new Configuration(
            new File(getDataFolder(), "worlds.yml"));
        worldConfig.load();
        PluginManager pm = getServer().getPluginManager();

        int debugLevel = c.getInt("debugLevel", 0);

        boolean usePermissions = c.getBoolean("usePermissions", false);

        if (usePermissions) {
            log.info("[NETHRAR] Using Permissions. Set permissions nodes as " +
                "appropriate.");
            setupPermissions();
        } else {
            permissions = null;
        }

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
        keepAliveRadius = getConfiguration().getInt("forceLoadRadius", 0);

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

        PortalUtil.initialize(this, worldConfig, keepAliveRadius);

        c.setProperty("usePermissions", usePermissions);
        c.setProperty("riderlessVehicles", riderlessVehicles);
        c.setProperty("listen.respawn", listenForRespawns);
        c.setProperty("forceLoadRadius", keepAliveRadius);
        c.setProperty("debugLevel", debugLevel);

        c.save();

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

    private void setupPermissions() {
        Plugin test = getServer().getPluginManager().getPlugin("Permissions");

        if (this.permissions == null) {
            if (test != null) {
                this.permissions = ((Permissions)test).getHandler();
                log.info("[NETHRAR] Permissions enabled.");
            } else {
                log.warning("[NETHRAR] Permissions not detected.");
            }
        }
    }

    public void onDisable() {
        if (PortalUtil.savePortals()) {
            log.info("[NETHRAR] Portal saving successful.");
        } else {
            log.severe("[NETHRAR] Unable to save portals. All links will be " +
                "broken on reload.");
        }
    }

    public void onLoad() { }
}
