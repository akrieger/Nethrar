/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Location;
import org.bukkit.entity.CreatureType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityListener;

/**
 * EntityListener object for the Portals plugin.
 *
 * This class listens for CreatureSpawnEvents, and cancels the event if it
 * occurs in the Nether and is a Ghast/Zombie Pigman.
 * @author Andrew Krieger
 */
public class NethrarEntityListener extends EntityListener {
	public NethrarEntityListener() { }

	@Override
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		Location l = event.getLocation();
		if (l.getWorld().equals(PortalUtil.getNetherWorld())) {
			CreatureType t = event.getCreatureType();
			if (t.equals(CreatureType.GHAST) ||
				t.equals(CreatureType.PIG_ZOMBIE)) {

				event.setCancelled(true);
			}
		}
	}
}
