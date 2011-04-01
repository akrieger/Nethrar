/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.World;
import org.bukkit.World.Environment;

import org.bukkit.plugin.*;

import java.util.logging.Level;
import java.util.logging.Logger;

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
public class NethrarMain extends JavaPlugin {
	private final NethrarPlayerListener playerListener =
		new NethrarPlayerListener();
	private final Logger log = Logger.getLogger("Minecraft.Nethrar");

	public void onEnable() {
		PluginManager pm = getServer().getPluginManager();

		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener,
			Priority.Normal, this);

		if (getConfiguration().getBoolean("listen.respawn", true)) {
			pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener,
				Priority.Normal, this);
			log.log(Level.INFO, "[NETHRAR] Listening for player respawns.");
		} else {
			log.log(Level.INFO, "[NETHRAR] Not listening for player respawns.");
		}

		String normalWorldName = getConfiguration().getString(
		    "worlds.normalWorld", "world");
		World normalWorld = getServer().getWorld(normalWorldName);

		log.log(Level.INFO, "[NETHRAR] Normal world name: " + normalWorldName);

		if (normalWorld == null) {
			normalWorld = getServer().createWorld(
			    normalWorldName, Environment.NORMAL);
		}

		String netherWorldName = getConfiguration().getString(
		    "worlds.netherWorld", "netherWorld");
		World netherWorld = getServer().getWorld(netherWorldName);

		if (netherWorld == null) {
			netherWorld = getServer().createWorld(
			    netherWorldName, Environment.NETHER);
		}

		log.log(Level.INFO, "[NETHRAR] Nether world name: " + netherWorldName);

		int normalScale, netherScale;
		normalScale = getConfiguration().getInt("scale.normal", 8);
		netherScale = getConfiguration().getInt("scale.nether", 1);

		log.log(Level.INFO, "[NETHRAR] Normal : Nether scale: " + normalScale +
			":" + netherScale);

		PortalUtil.initialize(normalWorld, netherWorld, normalScale, netherScale);

		PluginDescriptionFile pdfFile = this.getDescription();
		log.log(Level.INFO, "[NETHRAR] " + pdfFile.getName() + " v" +
			pdfFile.getVersion() + " enabled.");
	}

	public void onDisable() { }

	public void onLoad() { }
}
