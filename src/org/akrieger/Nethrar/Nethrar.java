/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.config.Configuration;
import org.bukkit.World;
import org.bukkit.World.Environment;

import org.bukkit.plugin.*;

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

	private final Logger log = Logger.getLogger("Minecraft.Nethrar");

	private int forceNetherNightTid = -1;

	public void onEnable() {
		Configuration c = getConfiguration();
		PluginManager pm = getServer().getPluginManager();

		int debugLevel = c.getInt("debugLevel", 0);

		boolean usePermissions = c.getBoolean("usePermissions", false);

		if (usePermissions) {
			log.info("[NETHRAR] Using Permissions. Set permission node "
				+ "\"nethrar.use\" as appropriate.");
			setupPermissions();
		} else {
			permissions = null;
		}

		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener,
			Priority.Normal, this);

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

		final String normalWorldName = getConfiguration().getString(
		    "worlds.normalWorld", "world");
		World normalWorld = getServer().getWorld(normalWorldName);

		log.info("[NETHRAR] Normal world name: " + normalWorldName);

		if (normalWorld == null) {
			normalWorld = getServer().createWorld(
			    normalWorldName, Environment.NORMAL);
		}

		final String netherWorldName = getConfiguration().getString(
		    "worlds.netherWorld", "netherWorld");
		World netherWorld = getServer().getWorld(netherWorldName);

		log.info("[NETHRAR] Nether world name: " + netherWorldName);

		if (netherWorld == null) {
			netherWorld = getServer().createWorld(
			    netherWorldName, Environment.NETHER);
		}

		int normalScale, netherScale, keepAliveRadius;
		normalScale = getConfiguration().getInt("scale.normal", 8);
		netherScale = getConfiguration().getInt("scale.nether", 1);
		keepAliveRadius = getConfiguration().getInt("forceLoadRadius", 0);

		log.info("[NETHRAR] Normal : Nether scale: "
			+ normalScale + ":" + netherScale);

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

		PortalUtil.initialize(normalWorld, netherWorld,
			normalScale, netherScale, keepAliveRadius);

		boolean forcePeacefulNether =
			c.getBoolean("forcePeacefulNether", false);

		if (forcePeacefulNether) {
			((CraftWorld)netherWorld).getHandle().spawnMonsters = 0;

			clearNetherCreatures(netherWorld);

			log.info("[NETHRAR] Forcing 'peaceful' Nether.");
		} else {
			((CraftWorld)netherWorld).getHandle().spawnMonsters = 1;
		}

		boolean forceNetherNight = c.getBoolean("forceNetherNight", true);

		if (forceNetherNight) {
			if (forceNetherNightTid == -1) {
				forceNetherNightTid = getServer().getScheduler()
												 .scheduleAsyncRepeatingTask(
					this,
					new Runnable() {
						public void run() {
							World nw = getServer().getWorld(netherWorldName);
							if (nw != null) {
								nw.setTime(14000);
							}
						}
					}, 20L, 6000L);
			}
			log.info("[NETHRAR] Forcing night in Nether.");
		} else {
			if (forceNetherNightTid != -1) {
				getServer().getScheduler().cancelTask(forceNetherNightTid);
				forceNetherNightTid = -1;
			}
		}

		c.setProperty("usePermissions", usePermissions);
		c.setProperty("riderlessVehicles", riderlessVehicles);
		c.setProperty("worlds.normalWorld", normalWorldName);
		c.setProperty("worlds.netherWorld", netherWorldName);
		c.setProperty("scale.normal", normalScale);
		c.setProperty("scale.nether", netherScale);
		c.setProperty("listen.respawn", listenForRespawns);
		c.setProperty("forceLoadRadius", keepAliveRadius);
		c.setProperty("forcePeacefulNether", forcePeacefulNether);
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
				log.info("[NETHRAR] Permissions not detected.");
			}
		}
	}

	private void clearNetherCreatures(World w) {
		for (LivingEntity le : w.getLivingEntities()) {
			if (le instanceof Ghast ||
				le instanceof PigZombie) {

				le.remove();
			}
		}
	}

	public void onDisable() { }

	public void onLoad() { }
}
