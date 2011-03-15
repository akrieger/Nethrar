/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Portals;

import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Minecart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for interacting with and managing Portals in the world.
 * This class represents the 'meta-logic' of portals, eg. the way portals are
 * linked, created, etc. Whereas the Portal class manages teleporting players
 * and the various conditions for doing so, this class manages the connections
 * between portals that define where a Portal will teleport a player to. This
 * class also manages creating and removing Portal objects, to properly
 * maintain links. Currently, Portals decide where their counterparts 'should'
 * be, whereas this class is in charge of creating them.
 *
 * @author Andrew Krieger
 */
public class PortalUtil {

	private static Map<Location, Portal> portals;
	private static World normalWorld, netherWorld;
	private static int normalScale, netherScale;

	/**
	 * Initializes the utility class with the given worlds and relative spatial
	 * scales.
	 *
	 * This resets the Portals configuration to link between the two given
	 * worlds. One world should be made with Environment.NORMAL, the other with
	 * Environment.NETHER, otherwise things will break. Also sets the relative
	 * spatial scale, so that newNormalScale blocks distance in the first world
	 * correspond to newNetherScale blocks distance in the second world.
	 *
	 * @param newNormalWorld The normal world for nether portals to link to.
	 * @param newNetherWorld The nether world for normal portals to link to.
	 * @param newNormalScale The relative scale of the normal world to the
	 *     nether world's scale.
	 * @param newNetherScale The relative scale of the nether world to the
	 *     normal world's scale.
	 * @return true
	 */
	public static boolean initialize(World newNormalWorld,
			World newNetherWorld, int newNormalScale, int newNetherScale) {

		portals = new HashMap<Location, Portal>();
		normalWorld = newNormalWorld;
		netherWorld = newNetherWorld;
		normalScale = newNormalScale;
		netherScale = newNetherScale;

		return true;
	}

	/**
	 * "Registers" the Portal with the mod, and performs relevant global
	 * initialization based on the new Portal.
	 */
	public static boolean addPortal(Portal p) {
		return portals.put(p.getKeyBlock().getLocation(), p) != null;
	}

	/**
	 * Removes the given Portal, and updates mappings accordingly.
	 */
	public static boolean removePortal(Portal p) {
		return removePortalAt(p.getKeyBlock());
	}

	/** Attempts to remove a Portal whose keyBlock is at the given Block. */
	public static boolean removePortalAt(Block b) {
		return portals.remove(b.getLocation()) != null;
	}

	/** 
	 * Gets the Portal at the given Location, or null if there is none.
	 * The Location need not be the keyBlock location, but it must be one of
	 * Material.PORTAL blocks in the portal.
	 */
	public static Portal getPortalAt(Location loc) {
		return getPortalAt(loc.getBlock());
	}

	/** Returns the normal world from the most recent initialization. */
	public static World getNormalWorld() {
		return normalWorld;
	}

	/** Returns the nether world from the most recent initialization. */
	public static World getNetherWorld() {
		return netherWorld;
	}

	/**
	 * Returns the scale for the normal world from the most recent
	 * initialization.
	 */
	public static int getNormalScale() {
		return normalScale;
	}

	/**
	 * Returns the scale for the nether world from the most recent
	 * initialization.
	 */
	public static int getNetherScale() {
		return netherScale;
	}

	/**
	 * Returns the Portal object corresponding to the given Portal block. One
	 * will be created if there is not already a Portal object for the given
	 * portal. Returns null if the block is not a Portal block.
	 */
	public static Portal getPortalAt(Block b) {
		int keyX = b.getX();
		int keyY = b.getY();
		int keyZ = b.getZ();
		World bWorld = b.getWorld();

		if (!b.getType().equals(Material.PORTAL)) {
			return null;
		}

		// Get keyBlock.
		// Look in -x direction
		while (bWorld.getBlockAt(--keyX, keyY, keyZ)
					 .getType()
					 .equals(Material.PORTAL));

		keyX++;

		// Look in -z direction
		while (bWorld.getBlockAt(keyX, keyY, --keyZ)
					 .getType()
					 .equals(Material.PORTAL));

		keyZ++;

		// Look in -y direction
		while (bWorld.getBlockAt(keyX, --keyY, keyZ)
					 .getType()
					 .equals(Material.PORTAL));

		keyY++;

		Location portalLoc = new Location(b.getWorld(), keyX, keyY, keyZ);

		Portal newPortal = portals.get(portalLoc);

		if (newPortal == null) {
			// Newly entered portal.
			newPortal = new Portal(b.getWorld()
										  .getBlockAt(keyX, keyY, keyZ));
		}
		return newPortal;
	}

	/**
	 * Finds or creates a portal at the destination, following Notchian-esque
	 * portal creation semantics.
	 *
	 * If there exist portal blocks immediately adjacent to where the new
	 * portal would be, choose one and link to that portal instead.
	 */
	public static Portal getOrCreatePortalAt(Portal sourcePortal, Block dest) {

		World world = dest.getWorld();
		int destX = dest.getX(), destY = dest.getY(), destZ = dest.getZ();
		
		// Get list of frameBlocks in a rectangle around the portal.

		Set<Block> airBlocks = new HashSet<Block>();
		if (sourcePortal.isFacingNorth()) {
			for (int x = destX - 1; x <= destX + 1; x++) {
				for (int z = destZ; z <= destZ + 1; z++) {
					airBlocks.add(world.getBlockAt(x, destY + 0, z));
					airBlocks.add(world.getBlockAt(x, destY + 1, z));
					airBlocks.add(world.getBlockAt(x, destY + 2, z));
				}
			}
		} else {
			for (int x = destX; x <= destX + 1; x++) {
				for (int z = destZ - 1; z <= destZ + 1; z++) {
					airBlocks.add(world.getBlockAt(x, destY + 0, z));
					airBlocks.add(world.getBlockAt(x, destY + 1, z));
					airBlocks.add(world.getBlockAt(x, destY + 2, z));
				}
			}
		}

		// For each column try to find a portal keyBlock
		for (Block col : airBlocks) {
			for (int y = 0; y <= 2; y++) {
				Block b = world.getBlockAt(col.getX(), col.getY() + y, col.getZ());
				if (b.getType().equals(Material.PORTAL)) {
					// Found an existing, lit, portal, overlapping where this
					// one would go.
					return getPortalAt(b);
				}
			}
		}


		// No existing portal - try to place one.
		// Determine if there is obsidian already occupying the same space -
		// this can indicate a destroyed portal. Don't place one in that case.
		// We don't want to accidentally nuke an existing portal.
		// This also means you can anti-portal an area by cleverly placing
		// obsidian. This also means that portals won't necessarily be
		// autogenerated in configurations which might be otherwise valid.
		// Which I think is fine.

		Set<Block> frameBlocks = new HashSet<Block>();

		if (sourcePortal.isFacingNorth()) {
			// Top and bottom
			for (int z = destZ - 1; z <= destZ + 2; z++) {
				frameBlocks.add(world.getBlockAt(destX, destY - 1, z));
				frameBlocks.add(world.getBlockAt(destX, destY + 3, z));
			}
			// Sides
			for (int dy = 0; dy <= 2; dy++) {
				frameBlocks.add(world.getBlockAt(destX, destY + dy, destZ - 1));
				frameBlocks.add(world.getBlockAt(destX, destY + dy, destZ + 2));
			}
		} else {
			// Top and bottom
			for (int x = destX - 1; x <= destX + 2; x++) {
				frameBlocks.add(world.getBlockAt(x, destY - 1, destZ));
				frameBlocks.add(world.getBlockAt(x, destY + 3, destZ));
			}
			// Sides
			for (int dy = 0; dy <= 2; dy++) {
				frameBlocks.add(world.getBlockAt(destX - 1, destY + dy, destZ));
				frameBlocks.add(world.getBlockAt(destX + 2, destY + dy, destZ));
			}
		}

		Set<Block> platformBlocks = new HashSet<Block>();
		if (sourcePortal.isFacingNorth()) {
			platformBlocks.add(world.getBlockAt(destX + 1, destY - 1, destZ));
			platformBlocks.add(world.getBlockAt(destX + 1, destY - 1, destZ + 1));
			platformBlocks.add(world.getBlockAt(destX - 1, destY - 1, destZ));
			platformBlocks.add(world.getBlockAt(destX - 1, destY - 1, destZ + 1));
		} else {
			platformBlocks.add(world.getBlockAt(destX, destY - 1, destZ - 1));
			platformBlocks.add(world.getBlockAt(destX + 1, destY - 1, destZ - 1));
			platformBlocks.add(world.getBlockAt(destX, destY - 1, destZ + 1));
			platformBlocks.add(world.getBlockAt(destX + 1, destY - 1, destZ + 1));
		}

		for (Block newAirBlock : airBlocks) {
			if (newAirBlock.getType().equals(Material.OBSIDIAN)) {
				return null;
			}
		}

		for (Block newPlatformBlock : platformBlocks) {
			if (newPlatformBlock.getType().equals(Material.OBSIDIAN)) {
				return null;
			}
		}

		// Build and light the portal.

		for (Block newFrameBlock : frameBlocks) {
			newFrameBlock.setType(Material.OBSIDIAN);
		}

		for (Block newAirBlock : airBlocks) {
			newAirBlock.setType(Material.AIR);
		}

		for (Block newPlatformBlock : platformBlocks) {
			newPlatformBlock.setType(Material.STONE);
		}

		dest.setType(Material.FIRE);

		if (dest.getType().equals(Material.PORTAL)) {
			// Successful portal ignition.
			return getPortalAt(dest);
		}

		// Nope!
		return null;
	}

	/** Links two portals together, without overriding existing linkages. */
	public static void linkPortals(Portal a, Portal b) {
		if (a == null || b == null) {
			return;
		}

		if (a.getCounterpart() == null) {
			a.setCounterpart(b);
		}

		if (b.getCounterpart() == null) {
			b.setCounterpart(a);
		}
	}
}
