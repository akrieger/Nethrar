/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.lang.IllegalArgumentException;
import java.util.Arrays;
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

    private static Plugin plugin;
    private static Map<Location, Portal> portals;
    private static Map<World, World> worldLinks;
    private static Map<World, World> respawnRedirects;
    private static Map<World, Integer> worldScales;
    private static Map<Material, World> blocksToWorlds;
    private static Map<World, Material> worldsToBlocks;
    private static Map<Entity, Long> entityLastTeleportedTime;
    // Map of chunks, encoded in a Location object, and a list of portals
    // keeping that chunk loaded.
    private static Map<Location, List<Portal>> forceLoadedChunks;
    private static int keepAliveRadius;

    private static final Logger log = Logger.getLogger("Minecraft.Nethrar");

    private static final long TELEPORT_TIMEOUT_NANOS = 500000000;

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
    public static boolean initialize(Plugin pl, Configuration worldsConfig,
            int newKeepAliveRadius) throws IOException {

        plugin = pl;
        keepAliveRadius = newKeepAliveRadius;
        portals = new HashMap<Location, Portal>();
        worldLinks = new HashMap<World, World>();
        worldScales = new HashMap<World, Integer>();
        respawnRedirects = new HashMap<World, World>();
        blocksToWorlds = new HashMap<Material, World>();
        worldsToBlocks = new HashMap<World, Material>();
        entityLastTeleportedTime = new HashMap<Entity, Long>();
        forceLoadedChunks = new HashMap<Location, List<Portal>>();

        initializeWorlds(worldsConfig);

        File portalsFile = new File(plugin.getDataFolder(), "portals.yml");
        YamlConfiguration portalsConfig =
            YamlConfiguration.loadConfiguration(portalsFile);
        initializePortals(portalsConfig);
        portalsConfig.save(portalsFile);

        return true;
    }

    private static void initializeWorlds(Configuration worldsConfig) {
        Map<World, String> tempWorldLinks = new HashMap<World, String>();

        Set<String> worldNames = worldsConfig.getKeys(false);

        if (worldNames == null || worldNames.size() == 0) {
            // Generate defaults.
            List<World> worlds = plugin.getServer().getWorlds();
            World normalWorld = null;
            for (World w : worlds) {
                if (w.getEnvironment().equals(Environment.NORMAL)) {
                    normalWorld = w;
                    break;
                }
            }

            if (normalWorld == null) {
                // Ok, not getting more defensive than this. If they made
                // 'world' be a non-normal world, sucks to be them.
                WorldCreator wc = new WorldCreator("world");
                wc.environment(Environment.NORMAL);
                normalWorld = wc.createWorld();
            }

            worlds = plugin.getServer().getWorlds();
            World netherWorld = null;

            for (World w : worlds) {
                if (w.getEnvironment().equals(Environment.NETHER)) {
                    netherWorld = w;
                    break;
                }
            }

            if (netherWorld == null) {
                // Ok, not getting more defensive than this. If they made
                // $WORLD_nether' be a non-nether world, sucks to be them.
                WorldCreator wc = new WorldCreator(
                    normalWorld.getName() + "_nether");
                wc.environment(Environment.NETHER);
                netherWorld = wc.createWorld();
            }

            worldLinks.put(normalWorld, netherWorld);
            worldLinks.put(netherWorld, normalWorld);
            respawnRedirects.put(netherWorld, normalWorld);
            worldScales.put(normalWorld, 8);
            worldScales.put(netherWorld, 1);

            String normalName = normalWorld.getName();
            String netherName = netherWorld.getName();

            worldsConfig.set(normalName + ".environment", "normal");
            worldsConfig.set(normalName + ".destination", netherName);
            worldsConfig.set(normalName + ".scale", 8);

            worldsConfig.set(netherName + ".environment", "nether");
            worldsConfig.set(netherName + ".destination", normalName);
            worldsConfig.set(netherName + ".scale", 1);
            worldsConfig.set(netherName + ".peaceful", false);
            worldsConfig.set(netherName + ".respawnTo", normalName);
            return;
        }

        // Validate and generate worlds.
        for (String worldName : worldNames) {
            ConfigurationSection worldConfig =
                worldsConfig.getConfigurationSection(worldName);
            String envtype = worldConfig.getString("environment", "");
            World world;
            Environment env;
            String cgPluginName;
            String cgArgs;
            Plugin cgPlugin;
            ChunkGenerator cg;

            if (envtype.equals("")) {
                world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    log.severe("[NETHRAR] World \"" + worldName + "\" " +
                        "does not exist, and does not have an environment " +
                        "set. Please set \"" + worldName + ".environment\" " +
                        "in worlds.yml.");
                    throw new IllegalArgumentException("Need to set an " +
                        "environment for world " + worldName + ", or create " +
                        "the world through some means.");
                }
            } else {
                try {
                    env = Environment.valueOf(envtype.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.severe("[NETHRAR] Illegal environment " + envtype +
                        " specified for world " + worldName + ".");
                    throw new IllegalArgumentException("Need to set a valid " +
                        "environment for world " + worldName);
                }

                world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    WorldCreator wc = new WorldCreator(worldName);
                    wc.environment(env);
                    cgPluginName = worldConfig.getString(
                        "worldGenerator.name", "");
                    if (!cgPluginName.equals("")) {
                        cgPlugin = plugin.getServer().getPluginManager()
                            .getPlugin(cgPluginName);
                        if (cgPlugin == null) {
                            log.severe("[NETHRAR] World generator " +
                                cgPluginName + " does not exist. Cannot " +
                                "create world " + worldName + ".");
                            continue;
                        }
                        cgArgs =
                            worldConfig.getString("worldGenerator.args", "");
                        cg = cgPlugin.getDefaultWorldGenerator(
                            worldName, cgArgs);
                        wc.generator(cg);
                    }
                    world = wc.createWorld();
                } else if(!world.getEnvironment().equals(env)) {
                    log.warning("[NETHRAR] World \"" + worldName + "\" " +
                        "already exists, but is the wrong enviroment. Either " +
                        "remove the world's directory, or change the " +
                        "environment in the configuration file.");
                }
            }

            boolean peaceful = worldConfig.getBoolean("peaceful", false);
            world.setSpawnFlags(!peaceful, true);

            int scale = worldConfig.getInt("scale", 1);
            worldScales.put(world, scale);

            String destWorldName = worldConfig.getString("destination", "");
            tempWorldLinks.put(world, destWorldName);

            int blockId = worldConfig.getInt("worldBlock", -1);
            if (blockId != -1) {
                blocksToWorlds.put(Material.getMaterial(blockId), world);
                worldsToBlocks.put(world, Material.getMaterial(blockId));
                log.info("[NETHRAR] World \"" + worldName + "\", environment " +
                    envtype + ", scale " + scale + ", world block ID " + blockId
                    + ".");
            } else {
                log.info("[NETHRAR] World \"" + worldName + "\", environment " +
                    envtype + ", scale " + scale + ".");
            }
        }

        // Link worlds and set up metadata.
        log.info("[NETHRAR] World graph:");
        for (String worldName : worldNames) {
            World world = plugin.getServer().getWorld(worldName);
            World destWorld = plugin.getServer().getWorld(
                tempWorldLinks.get(world));
            if (destWorld == null) {
                log.severe("World " + world.getName() + " does not have a " +
                    "valid destination set. Expected destination: " +
                    tempWorldLinks.get(world) + ".");
                continue;
            }
            worldLinks.put(world, destWorld);
            log.info(world.getName() + " --> " + destWorld.getName());
            String respawnToName = worldsConfig.getString(
                worldName + ".respawnTo", "");

            if (!respawnToName.equals("")) {
                World respawnTo = plugin.getServer().getWorld(respawnToName);
                if (respawnTo != null) {
                    respawnRedirects.put(world, respawnTo);
                }
            }
        }

        log.info("[NETHRAR] World respawn redirects:");
        for (Map.Entry<World, World> mapent : respawnRedirects.entrySet()) {
            log.info(mapent.getKey().getName() + " respawns redirect to " +
                mapent.getValue().getName());
        }
    }

    private static void initializePortals(Configuration portalConfig) {
        Map<String, Portal> namesToPortals = new HashMap<String, Portal>();
        Set<String> portalKeys = portalConfig.getKeys(false);

        if (portals == null) {
            return;
        }

        for (String portalKey : portalKeys) {
            String worldName =
                portalKey.substring(0, portalKey.lastIndexOf(";"));

            ConfigurationSection config =
                portalConfig.getConfigurationSection(portalKey);

            List<Integer> coords = config.getIntegerList("keyblock");
            if (coords == null) {
                continue;
            }

            World portalWorld = plugin.getServer().getWorld(worldName);
            if (portalWorld == null) {
                continue;
            }

            Block keyBlock = portalWorld.getBlockAt(
                coords.get(0), coords.get(1), coords.get(2));
            Portal p = getPortalAt(keyBlock);
            if (p == null) {
                continue;
            }

            addPortal(p);
            namesToPortals.put(portalKey, p);
        }
        for (String portalKey : portalKeys) {
            ConfigurationSection config =
                portalConfig.getConfigurationSection(portalKey);
            String destKey = config.getString("destination");
            boolean prot = config.getBoolean("protected");

            Portal source = namesToPortals.get(portalKey);
            if (source == null) {
                continue;
            }
            source.setCounterpart(namesToPortals.get(destKey));
            Block keyBlock = source.getKeyBlock();
            World world = keyBlock.getWorld();
            int destX = keyBlock.getX(),
                destY = keyBlock.getY(),
                destZ = keyBlock.getZ();
            if (prot) {
                // Build portal block set.
                Set<Block> pBlocks = new HashSet<Block>();
                if (source.isFacingNorth()) {
                    for (int z = destZ; z <= destZ + 1; z++) {
                        pBlocks.add(world.getBlockAt(destX, destY + 0, z));
                        pBlocks.add(world.getBlockAt(destX, destY + 1, z));
                        pBlocks.add(world.getBlockAt(destX, destY + 2, z));
                    }
                } else {
                    for (int x = destX; x <= destX + 1; x++) {
                        pBlocks.add(world.getBlockAt(x, destY + 0, destZ));
                        pBlocks.add(world.getBlockAt(x, destY + 1, destZ));
                        pBlocks.add(world.getBlockAt(x, destY + 2, destZ));
                    }
                }
                for (Block b : pBlocks) {
                    NethrarDefaultListener.protectPortalBlock(b);
                }
            }
        }
    }

    public static boolean savePortals() throws IOException {
        File portalsFile = new File(plugin.getDataFolder(), "portals.yml");
        YamlConfiguration portalConfig = new YamlConfiguration();

        savePortals(portalConfig);

        portalConfig.save(portalsFile);
        return true;
    }

    private static void savePortals(Configuration portalConfig) {
        int nonce = 0;
        Map<Portal, String> portalKeyMap = new HashMap<Portal, String>();

        for (Map.Entry<Location, Portal> ent : portals.entrySet()) {
            Portal p = ent.getValue();
            World w = p.getKeyBlock().getWorld();
            portalKeyMap.put(p, w.getName() + ";" + (nonce++));
        }

        for (Map.Entry<Location, Portal> ent : portals.entrySet()) {
            Portal p = ent.getValue();
            Location l = p.getKeyBlock().getLocation();

            String portalKey = portalKeyMap.get(p);
            String destKey = portalKeyMap.get(p.getCounterpart());

            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            List<Integer> locCoords = Arrays.asList(x, y, z);

            portalConfig.set(portalKey + ".keyblock", locCoords);
            portalConfig.set(portalKey + ".destination", destKey);
            portalConfig.set(portalKey + ".protected",
                p.getKeyBlock().getWorld().getEnvironment().equals(
                    Environment.THE_END));
        }
    }

    public static Plugin getPlugin() {
        return plugin;
    }

    /**
     * "Registers" the Portal with the mod, and performs relevant global
     * initialization based on the new Portal.
     *
     * Note that this does not add chunks to the keep-alive set.
     */
    public static boolean addPortal(Portal p) {
        if (keepAliveRadius > 0) {
            World w = p.getKeyBlock().getWorld();
            int chunkX = p.getKeyBlock().getChunk().getX();
            int chunkZ = p.getKeyBlock().getChunk().getZ();
            for (int x = chunkX - keepAliveRadius + 1,
                endx = chunkX + keepAliveRadius - 1; x <= endx; x++) {

                for (int z = chunkZ - keepAliveRadius + 1,
                    endz = chunkZ + keepAliveRadius - 1; z <= endz; z++) {

                    Location tempLoc = new Location(w, x, 0, z);
                    List<Portal> tempList = forceLoadedChunks.get(tempLoc);
                    if (tempList == null) {
                        tempList = new LinkedList<Portal>();
                        tempList.add(p);
                        forceLoadedChunks.put(tempLoc, tempList);
                        w.loadChunk(x, z);
                    } else {
                        tempList.add(p);
                    }
                }
            }
        }
        Portal oldPortal = portals.get(p.getKeyBlock().getLocation());
        if (oldPortal != null) {
            removePortal(oldPortal);
        }
        return portals.put(p.getKeyBlock().getLocation(), p) != null;
    }

    /**
     * Removes the given Portal, and updates mappings accordingly.
     *
     * This will remove any necessary chunks from the keep-alive list.
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
                            bWorld.unloadChunkRequest(x, z);
                        }
                    } else {
                        log.warning("Chunk location " + tempLoc + " was not " +
                            "kept loaded when it should have been.");
                    }
                }
            }
        }
        if (p.getCounterpart() != null) {
            p.getCounterpart().setCounterpart(null);
        }
        return portals.remove(b.getLocation()) != null;
    }

    /** Attempts to remove a Portal whose keyBlock is at the given Block. */
    public static boolean removePortalAt(Block b) {
        Portal temp = getPortalAt(b);
        return removePortal(temp);
    }

    public static int getScaleFor(World w) {
        Integer scale = worldScales.get(w);
        if (scale != null) {
            return scale;
        }
        return 0;
    }

    public static World getRespawnWorldFor(World sourceWorld) {
        return respawnRedirects.get(sourceWorld);
    }

    public static World getDestWorldFor(Portal p) {
        Material mat = p.getWorldBlockType();
        if (blocksToWorlds.get(mat) != null) {
            return blocksToWorlds.get(mat);
        }

        return getDestWorldFor(p.getKeyBlock().getWorld());
    }

    public static World getDestWorldFor(World sourceWorld) {
        return worldLinks.get(sourceWorld);
    }

    /**
     * Gets the Portal at the given Location, or null if there is none.
     * The Location need not be the keyBlock location, but it must be one of
     * Material.PORTAL blocks in the portal.
     */
    public static Portal getPortalAt(Location loc) {
        return getPortalAt(loc.getBlock());
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
            // This is the only place Portals are "new'd".
            newPortal = new Portal(b.getWorld()
                                          .getBlockAt(keyX, keyY, keyZ));
            if (newPortal.isValid()) {
                addPortal(newPortal);
            } else {
                newPortal = null;
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
            platformBlocks.add(world.getBlockAt(
                destX + 1, destY - 1, destZ));
            platformBlocks.add(world.getBlockAt(
                destX + 1, destY - 1, destZ + 1));
            platformBlocks.add(world.getBlockAt(
                destX - 1, destY - 1, destZ));
            platformBlocks.add(world.getBlockAt(
                destX - 1, destY - 1, destZ + 1));
        } else {
            platformBlocks.add(world.getBlockAt(
                destX, destY - 1, destZ - 1));
            platformBlocks.add(world.getBlockAt(
                destX + 1, destY - 1, destZ - 1));
            platformBlocks.add(world.getBlockAt(
                destX, destY - 1, destZ + 1));
            platformBlocks.add(world.getBlockAt(
                destX + 1, destY - 1, destZ + 1));
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

        if (dest.getWorld().getEnvironment().equals(
                Environment.THE_END)) {
            // Manually set portal blocks - but if they go out, you're screwed!
            // ... if the server restarts ...
            for (Block newPortalBlock : innerAirBlocks) {
                newPortalBlock.setType(Material.PORTAL);
                NethrarDefaultListener.protectPortalBlock(newPortalBlock);
            }
            return getPortalAt(dest);
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
        return getCounterpartPortalFor(source, getDestWorldFor(source));
    }

    public static Portal getCounterpartPortalFor(
            Portal source, World destWorld) {

        if (destWorld == null) {
            // No outbound edge defined.
            return null;
        }

        // Calculate the counterpart portal's keyblock location.
        double destX, destY, destZ;
        Block sourceKeyBlock = source.getKeyBlock();
        Block destBlock;

        double scale = PortalUtil.getScaleFor(destWorld) /
            (double)PortalUtil.getScaleFor(sourceKeyBlock.getWorld());

        destX = Math.floor(sourceKeyBlock.getX() * scale);
        destY = sourceKeyBlock.getY();
        destZ = Math.floor(sourceKeyBlock.getZ() * scale);

        // If the destination world is 'larger', then we need to 'look around'
        // more to find potential portals to link to, based on collision
        // detection rules. Heuristic is, essentially, "If I were to step into
        // the other portal, and it would link to the one I am entering here,
        // then link to that other portal."
        // Generalize.
        if (scale > 1) {

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
             * oxxx  ominx: -1, maxx: 0; ominz: +2, omaxz: +3;
             *                  minx/maxx same; minz: -1, maxz: +2
             * xxpxs minx: 0, maxx: +1; minz: -1, maxz: +3;
             *                sminx/smaxx same; sminz: -2, smaxz: -1
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
             * 2: sminx: -2, smaxx: -1; sminz: 0, smaxz: +1;
             *    minx: -1, maxx: +3; minz/maxz same
             * 3: minx: -1, maxx: +2; minz: -1, maxz: 0;
             *    ominx: +2, omaxx: +3; ominz/omaxz same
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
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    ndeltas, scale, sourceKeyBlock, destWorld));
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    nsdeltas, scale, sourceKeyBlock, destWorld));
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    nodeltas, scale, sourceKeyBlock, destWorld));
            } else {
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    wdeltas, scale, sourceKeyBlock, destWorld));
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    wsdeltas, scale, sourceKeyBlock, destWorld));
                potentialPortals.addAll(findPortalsInDeltaRegions(
                    wodeltas, scale, sourceKeyBlock, destWorld));
            }

            Vector destVector = new Vector(destX, destY, destZ);
            double minDistSquared = Double.MAX_VALUE;
            Portal candidatePortal = null;

            for (Portal pp : potentialPortals) {
                Vector tempVec = new Vector(pp.getKeyBlock().getX(),
                    pp.getKeyBlock().getY(), pp.getKeyBlock().getZ());
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
        // Alternatively, in the Nether, layer 119 is the highest safe point
        // for a portal keyblock to exist without accidentally nuking bedrock.
        if (destY < 6) {
            destY = 6;
        }

        if (destWorld.getEnvironment().equals(Environment.NETHER) &&
            destY > 119) {

            destY = 119;
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
                    if (w.getBlockAt(x, y, z).getType()
                                             .equals(Material.PORTAL)) {

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

    /** Links two portals together, without overriding existing linkages.
     *
     * Note: Assumes this was initiated by a player entering a, going to b.
     */
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

    public static void markTeleported(Entity e) {
        entityLastTeleportedTime.put(e, System.nanoTime());
    }

    public static boolean canTeleport(Entity e) {
        Long last = entityLastTeleportedTime.get(e);
        if (last == null) {
            return true;
        }
        long delta = System.nanoTime() - last;
        return delta > TELEPORT_TIMEOUT_NANOS;
    }
}
