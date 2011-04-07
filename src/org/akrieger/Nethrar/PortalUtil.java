/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;
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
	// Map of chunks, encoded in a Location object, and a list of portals
	// keeping that chunk loaded.
	private static Map<Location, List<Portal>> forceLoadedChunks;
	private static World normalWorld, netherWorld;
	private static int normalScale, netherScale, keepAliveRadius;

	private static final Logger log = Logger.getLogger("Minecraft.Nethrar");

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
			World newNetherWorld, int newNormalScale, int newNetherScale,
			int newKeepAliveRadius) {

		portals = new HashMap<Location, Portal>();
		forceLoadedChunks = new HashMap<Location, List<Portal>>();
		normalWorld = newNormalWorld;
		netherWorld = newNetherWorld;
		normalScale = newNormalScale;
		netherScale = newNetherScale;
		keepAliveRadius = newKeepAliveRadius;

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
		Block b = p.getKeyBlock();
		if (keepAliveRadius > 0) {
			int chunkX = b.getChunk().getX();
			int chunkZ = b.getChunk().getZ();
			World bWorld = b.getWorld();
			for (int x = chunkX - keepAliveRadius + 1,
				endx = chunkX + keepAliveRadius - 1; x <= endx; x++) {

				for (int z = chunkZ - keepAliveRadius + 1,
					endz = chunkZ + keepAliveRadius - 1; z <= endz; z++) {

					Location tempLoc = new Location(bWorld, x, 0, z);
					List<Portal> tempList = forceLoadedChunks.get(tempLoc);
					if (tempList != null) {
						if (!tempList.remove(p)) {
							log.warning("Chunk location " + tempLoc + " did " +
								"not have the portal at " + b + " linked to " +
								"it.");
						}
						if (tempList.isEmpty()) {
							forceLoadedChunks.remove(tempLoc);
							bWorld.unloadChunk(x, z);
						}
					} else {
						log.warning("Chunk location " + tempLoc + " was not " +
							"kept loaded when it should have been.");
					}
				}
			}
		}
		return portals.remove(b.getLocation()) != null;
	}

	/** Attempts to remove a Portal whose keyBlock is at the given Block. */
	public static boolean removePortalAt(Block b) {
		Portal temp = getPortalAt(b);
		return removePortal(temp);
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
			// This is the only place Portals are "new'd".
			if (keepAliveRadius > 0) {
				int chunkX = newPortal.getKeyBlock().getChunk().getX();
				int chunkZ = newPortal.getKeyBlock().getChunk().getZ();
				for (int x = chunkX - keepAliveRadius + 1,
					endx = chunkX + keepAliveRadius - 1; x <= endx; x++) {

					for (int z = chunkZ - keepAliveRadius + 1,
						endz = chunkZ + keepAliveRadius - 1; z <= endz; z++) {

						Location tempLoc = new Location(bWorld, x, 0, z);
						List<Portal> tempList = forceLoadedChunks.get(tempLoc);
						if (tempList == null) {
							tempList = new LinkedList<Portal>();
							tempList.add(newPortal);
							forceLoadedChunks.put(tempLoc, tempList);
							bWorld.loadChunk(x, z);
						} else {
							tempList.add(newPortal);
						}
					}
				}
			}
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
		
		Set<Block> outerAirBlocks = new HashSet<Block>();
		Set<Block> innerAirBlocks = new HashSet<Block>();
		if (sourcePortal.isFacingNorth()) {
			for (int z = destZ; z <= destZ + 1; z++) {
				innerAirBlocks.add(world.getBlockAt(destX, destY + 0, z));
				innerAirBlocks.add(world.getBlockAt(destX, destY + 1, z));
				innerAirBlocks.add(world.getBlockAt(destX, destY + 2, z));

				outerAirBlocks.add(world.getBlockAt(destX - 1, destY + 0, z));
				outerAirBlocks.add(world.getBlockAt(destX - 1, destY + 1, z));
				outerAirBlocks.add(world.getBlockAt(destX - 1, destY + 2, z));

				outerAirBlocks.add(world.getBlockAt(destX + 1, destY + 0, z));
				outerAirBlocks.add(world.getBlockAt(destX + 1, destY + 1, z));
				outerAirBlocks.add(world.getBlockAt(destX + 1, destY + 2, z));
			}
		} else {
			for (int x = destX; x <= destX + 1; x++) {
				innerAirBlocks.add(world.getBlockAt(x, destY + 0, destZ));
				innerAirBlocks.add(world.getBlockAt(x, destY + 1, destZ));
				innerAirBlocks.add(world.getBlockAt(x, destY + 2, destZ));

				outerAirBlocks.add(world.getBlockAt(x, destY + 0, destZ - 1));
				outerAirBlocks.add(world.getBlockAt(x, destY + 1, destZ - 1));
				outerAirBlocks.add(world.getBlockAt(x, destY + 2, destZ - 1));

				outerAirBlocks.add(world.getBlockAt(x, destY + 0, destZ + 1));
				outerAirBlocks.add(world.getBlockAt(x, destY + 1, destZ + 1));
				outerAirBlocks.add(world.getBlockAt(x, destY + 2, destZ + 1));
			}
		}

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

		// Look for existing portals that interest where this portal would go.
		for (Block newAirBlock : outerAirBlocks) {
			if (newAirBlock.getType().equals(Material.PORTAL)) {
				return getPortalAt(newAirBlock);
			}
		}

		for (Block newAirBlock : innerAirBlocks) {
			if (newAirBlock.getType().equals(Material.PORTAL)) {
				return getPortalAt(newAirBlock);
			}
		}

		for (Block newFrameBlock : frameBlocks) {
			if (newFrameBlock.getType().equals(Material.PORTAL)) {
				return getPortalAt(newFrameBlock);
			}
		}

		// No existing portal - try to place one.
		// Do our darnest not to accidentally delete an existing lit portal.
		// We don't need to test our air blocks, because if there was obsidian
		// in the air blocks attacked to a portal, we would detect that portal.
		// We just won't delete obsidian if it exists.

		// Build and light the portal.

		for (Block newFrameBlock : frameBlocks) {
			newFrameBlock.setType(Material.OBSIDIAN);
		}

		for (Block newAirBlock : innerAirBlocks) {
			newAirBlock.setType(Material.AIR);
		}

		for (Block newAirBlock : outerAirBlocks) {
			if (!newAirBlock.getType().equals(Material.OBSIDIAN)) {
				newAirBlock.setType(Material.AIR);
			}
		}

		for (Block newPlatformBlock : platformBlocks) {
			if (!newPlatformBlock.getType().equals(Material.OBSIDIAN) &&
				!newPlatformBlock.getType().equals(Material.PORTAL)) {
				newPlatformBlock.setType(Material.STONE);
			}
		}

		dest.setType(Material.FIRE);

		if (dest.getType().equals(Material.PORTAL)) {
			// Successful portal ignition.
			return getPortalAt(dest);
		}

		// Nope!
		return null;
	}

	public static Portal getCounterpartPortalFor(Portal source) {
		World destWorld;

		if (source.isInNether()) {
			destWorld = getNormalWorld();
		} else {
			destWorld = getNetherWorld();
		}

		// Calculate the counterpart portal's keyblock location.
		double destX, destY, destZ;
		Block sourceKeyBlock = source.getKeyBlock();
		Block destBlock;

		double scale = PortalUtil.getNormalScale() /
			(double)PortalUtil.getNetherScale();

		if (source.isInNether()) {
			destX = Math.floor(sourceKeyBlock.getX() * scale);
			destY = sourceKeyBlock.getY();
			destZ = Math.floor(sourceKeyBlock.getZ() * scale);
		} else {
			destX = Math.floor(sourceKeyBlock.getX() / scale);
			destY = sourceKeyBlock.getY();
			destZ = Math.floor(sourceKeyBlock.getZ() / scale);
		}

		// If the destination world is 'larger', then we need to 'look around'
		// more to find potential portals to link to, based on collision
		// detection rules. Heuristic is, essentially, "If I were to step into
		// the other portal, and it would link to the one I am entering here,
		// then link to that other portal."
		if ((scale > 1 && source.isInNether()) ||
		    (scale < 1 && !source.isInNether())) {

			if (scale < 1) {
				scale = PortalUtil.getNetherScale() /
					(double)PortalUtil.getNormalScale();
			}

			/*
			 * The following diagrams define the 'macrogrid' area to search for
			 * a portal to link to. A 'macrogrid' square is a scale x scale area
			 * on the ground, where any portal in that square would have it's
			 * keyblock map to the same location in the other, smaller, world.
			 * The diagrams are the result of exhaustive manual generation and
			 * collision detection, done with pencils, paper, and fingers.
			 *
			 * o refers to 'opposite' zones where only portals that are the
			 * opposite orientation would collide with the source portal. s is
			 * 'same', wherein only portals of the same orientation would
			 * collide with the source portal.
			 *
			 * For portals facing north, this is the detection macrogrid.
			 * Regions are defined by minimum, inclusive, and maximum,
			 * exclusive, deltas from the portal's macrogrid square.
			 *  oo   ominx: -2, omaxx: -1; ominz: 0, omaxz: +2
			 * oxxx  ominx: -1, maxx: 0; ominz: +2, omaxz: +3; minx/maxx same; minz: -1, maxz: +2
			 * xxpxs minx: 0, maxx: +1; minz: -1, maxz: +3; sminx/smaxx same; sminz: -2, smaxz: -1
			 *  xxx  minx: +1, maxx: +2; minz: -1; maxz: +2
			 *
			 * West-facing portals have regions defined column-wise.
			 *  s
			 * xxx
			 * xpxo            p
			 * xxxo for portal p
			 *  xo
			 * 1234
			 *
			 * 1: minx: -1, maxx: +2; minz: +1, maxz: +2;
			 * 2: sminx: -2, smaxx: -1; sminz: 0, smaxz: +1; minx: -1, maxx: +3; minz/maxz same
			 * 3: minx: -1, maxx: +2; minz: -1, maxz: 0; ominx: +2, omaxx: +3; ominz/omaxz same
			 * 4: ominx: 0, omaxx: +2; ominz: -2, omaxz: -1;
			 */

			// minx,maxx,minz,maxz, repeating
			int[] ndeltas = {-1,0,-1,2, 0,1,-1,3, 1,2,-1,2};
			int[] nsdeltas = {0,1,-2,-1};
			int[] nodeltas = {-2,-1,0,2, -1,0,2,3};

			int[] wdeltas = {-1,2,1,2, -1,3,0,1, -1,2,-1,0};
			int[] wsdeltas = {-2,-1,0,1};
			int[] wodeltas = {2,3,-1,0, 0,2,-2,-1};

			int minX, minY, minZ, maxX, maxY, maxZ, sourceX, sourceY, sourceZ;

			sourceX = sourceKeyBlock.getX();
			sourceY = sourceKeyBlock.getY();
			sourceZ = sourceKeyBlock.getZ();

			minY = sourceY - 1;
			maxY = sourceY + 3;

			Set<Portal> potentialPortals = new HashSet<Portal>();

			if (source.isFacingNorth()) {
				potentialPortals.addAll(findPortalsInDeltaRegions(ndeltas, scale, sourceKeyBlock, destWorld));
				potentialPortals.addAll(findPortalsInDeltaRegions(nsdeltas, scale, sourceKeyBlock, destWorld));
				potentialPortals.addAll(findPortalsInDeltaRegions(nodeltas, scale, sourceKeyBlock, destWorld));
			} else {
				potentialPortals.addAll(findPortalsInDeltaRegions(wdeltas, scale, sourceKeyBlock, destWorld));
				potentialPortals.addAll(findPortalsInDeltaRegions(wsdeltas, scale, sourceKeyBlock, destWorld));
				potentialPortals.addAll(findPortalsInDeltaRegions(wodeltas, scale, sourceKeyBlock, destWorld));
			}

			Vector destVector = new Vector(destX, destY, destZ);
			double minDistSquared = Double.MAX_VALUE;
			Portal candidatePortal = null;

			for (Portal pp : potentialPortals) {
				Vector tempVec = new Vector(pp.getKeyBlock().getX(), pp.getKeyBlock().getY(), pp.getKeyBlock().getZ());
				double tempDist = tempVec.distanceSquared(destVector);
				// Need to do add'l checks here.
				if (tempDist < minDistSquared) {
					minDistSquared = tempDist;
					candidatePortal = pp;
				}
			}
			if (candidatePortal != null) {
				linkPortals(source, candidatePortal);
				return candidatePortal;
			}
		}

		// Don't let the portal go into bedrock.
		// Layer 6 for the portal, layer 5 for the obsidian
		// Bedrock at layer 4 and below.
		if (destY < 6) {
			destY = 6;
		}

		destBlock = destWorld.getBlockAt((int)destX, (int)destY, (int)destZ);

		Portal dest = getOrCreatePortalAt(source, destBlock);

		linkPortals(source, dest);

		return dest;
	}

	/**
	 * Finds portals in the given delta regions, as defined by the code in
	 * getCounterpartPortalFor(Portal).
	 *
	 * @param deltas An array specifying the closed/open range to search for
	 *     for portals. The first coordinate is an inclusive delta x, the next
	 *     is an exclusive x delta, then an inclusive/exclusive z delta pair.
	 * @param scale The scale to multiply the transformed coordinates to get
	 *     real-world coordinates to search in.
	 * @param sourceBlock The source block whose coordinates the deltas will be
	 *     applied to.
	 * @param destWorld The world to search for portals.
	 * @return All portals in the delta region in the destination world.
	 */
	private static Set<Portal> findPortalsInDeltaRegions(int[] deltas,
			double scale, Block sourceBlock, World destWorld) {

		int minX, minY, minZ, maxX, maxY, maxZ, sourceX, sourceY, sourceZ;

		sourceX = sourceBlock.getX();
		sourceY = sourceBlock.getY();
		sourceZ = sourceBlock.getZ();

		minY = sourceY - 1;
		maxY = sourceY + 3;

		Set<Portal> portals = new HashSet<Portal>();

		for (int i = 0; i < deltas.length; i += 4) {
			minX = (int)Math.ceil((sourceX + deltas[i + 0]) * scale);
			maxX = (int)Math.ceil((sourceX + deltas[i + 1]) * scale - 1);
			minZ = (int)Math.ceil((sourceZ + deltas[i + 2]) * scale);
			maxZ = (int)Math.ceil((sourceZ + deltas[i + 3]) * scale - 1);
			portals.addAll(findPortalsInRegion(
				minX, minY, minZ, maxX, maxY, maxZ, destWorld));
		}

		return portals;
	}

	/**
	 * Returns all portals in the cuboid region defined by the parameters.
	 *
	 * @param minX The minimum X coordinate to search for portals.
	 * @param minY The minimum Y coordinate to search for portals.
	 * @param minZ The minimum Z coordinate to search for portals.
	 * @param maxX The minimum X coordinate to search for portals.
	 * @param maxY The minimum Y coordinate to search for portals.
	 * @param maxZ The minimum Z coordinate to search for portals.
	 * @param w The world to find portals in.
	 * @return All Portals whose keyblocks fall in the XZ region, and which any
	 *     part of the portal is in the actual region.
	 */
	private static Set<Portal> findPortalsInRegion(int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, World w) {

		minY = (minY < 0 ? 0 : minY);
		maxY = (maxY > 127 ? 127 : maxY);

		Set<Portal> portals = new HashSet<Portal>();

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (w.getBlockAt(x, y, z).getType().equals(Material.PORTAL)) {
						Portal tempPortal = getPortalAt(w.getBlockAt(x, y, z));
						Block tempKeyBlock = tempPortal.getKeyBlock();
						if (tempKeyBlock.getX() <= maxX &&
						    tempKeyBlock.getX() >= minX &&
							tempKeyBlock.getZ() <= maxZ &&
							tempKeyBlock.getZ() >= minZ) {

							portals.add(tempPortal);
						}
					}
				}
			}
		}

		return portals;
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

	public static boolean isChunkForcedLoaded(Chunk c) {
		Location chunkLoc = new Location(c.getWorld(), c.getX(), 0, c.getZ());
		return forceLoadedChunks.keySet().contains(chunkLoc);
	}
}
