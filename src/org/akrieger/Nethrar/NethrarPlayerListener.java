/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import com.nijiko.permissions.PermissionHandler;

import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;

import java.util.logging.Logger;

/**
 * PlayerListener object for the Portals plugin.
 *
 * This class listens for PlayerMoveEvents to determine whether a player hit
 * a portal and should be teleported, and for PlayerRespawnEvents to send a
 * player back to the main/first/normal world.
 *
 * @author Andrew Krieger
 */
public class NethrarPlayerListener extends PlayerListener {
	private final Logger log = Logger.getLogger("Minecraft.Nethrar");

	public NethrarPlayerListener() { }

	@Override
	public void onPlayerMove(PlayerMoveEvent event) {
		Block b;
		Player player = event.getPlayer();
		
		if (Nethrar.permissions != null &&
			!Nethrar.permissions.has(player, "nethrar.use")) {
			return;
		}

		if (!event.getPlayer().isInsideVehicle()) {
			b = event.getTo().getBlock();
		} else {
			// Turns out the player is in / at the block *under* the minecart.
			Vehicle v = player.getVehicle();
			if (v == null) {
				// Strange race condition has occured. Or server lag.
				return;
			}
			Location l = v.getLocation();
			if (l == null) {
				// Even stranger race condition, or corruption.
				return;
			}
			b = l.getBlock();
			if (b == null) {
				// What the hell.
				return;
			}
		}

		if (!b.getType().equals(Material.PORTAL)) {
			// Not a portal.
			return;
		}
		
		Portal portal = PortalUtil.getPortalAt(b);
		Location endpoint = portal.teleportPlayer(player);
		if (endpoint != null) {
			event.setTo(endpoint);
		}
	}

	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Location rl = event.getRespawnLocation();
		if (rl == null) {
			log.warning("[NETHRAR] Player died, but respawn event has no respawn location.");
			return;
		}

		World rw = rl.getWorld();
		if (rw == null) {
			log.warning("[NETHRAR] Player died, respawn event has a location, but the location has no world.");
			return;
		}
		if (PortalUtil.getNetherWorld() == null) {
			log.severe("[NETHRAR] No Nether world configured in Nethrar.");
			return;
		}
		if (event.getRespawnLocation().getWorld().equals(
				PortalUtil.getNetherWorld())) {

			World normalWorld = PortalUtil.getNormalWorld();

			event.setRespawnLocation(normalWorld.getSpawnLocation());
		}
	}
}
