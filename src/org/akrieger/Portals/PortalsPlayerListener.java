/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Portals;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;

/**
 * PlayerListener object for the Portals plugin.
 *
 * This class listens for PlayerMoveEvents to determine whether a player hit
 * a portal and should be teleported, and for PlayerRespawnEvents to send a
 * player back to the main/first/normal world.
 *
 * @author Andrew Krieger
 */
public class PortalsPlayerListener extends PlayerListener {
	public PortalsPlayerListener() { }

	@Override
	public void onPlayerMove(PlayerMoveEvent event) {
		Block b;
		Player player = event.getPlayer();
		
		if (!event.getPlayer().isInsideVehicle()) {
			b = event.getTo().getBlock();
		} else {
			// Turns out the player is in / at the block *under* the minecart.
			b = player.getVehicle().getLocation().getBlock();
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
		if (event.getRespawnLocation().getWorld().equals(
				PortalUtil.getNetherWorld())) {

			World normalWorld = PortalUtil.getNormalWorld();

			event.setRespawnLocation(normalWorld.getSpawnLocation());
		}
	}
}
