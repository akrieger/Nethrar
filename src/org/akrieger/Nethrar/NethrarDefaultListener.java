/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Default Listener object for the Portals plugin.
 *
 * This class listens for PlayerMoveEvents to determine whether a player hit
 * a portal and should be teleported. It also listens for BlockPhysics events
 * for protecting Portals in case of teleporting to the End, since we want to
 * keep portals alive.
 *
 * @author Andrew Krieger
 */
public class NethrarDefaultListener implements Listener {
    private final Logger log = Logger.getLogger("Minecraft.Nethrar");

    private static Set<Block> protectedPortalBlocks = new HashSet<Block>();

    private Nethrar plugin;

    public NethrarDefaultListener(Nethrar nethrar) {
        this.plugin = nethrar;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Block b;
        Player player = event.getPlayer();

        if (this.plugin.shouldUsePermissions() &&
            !player.hasPermission("nethrar.use")) {

            return;
        }

        if (!event.getPlayer().isInsideVehicle()) {
            b = event.getTo().getBlock();
        } else {
            b = event.getTo().getBlock().getRelative(BlockFace.UP);
        }

        if (!b.getType().equals(Material.PORTAL)) {
            // Not a portal.
            return;
        }

        if (!PortalUtil.canTeleport(player)) {
            // Teleported recently.
            return;
        }

        Portal portal = PortalUtil.getPortalAt(b);
        if (portal != null) {
            Location endpoint = portal.teleport(player, event.getTo());
            if (endpoint != null) {
                event.setTo(endpoint);
            }
        }
    }

    public static boolean protectPortalBlock(Block b) {
        if (!b.getType().equals(Material.PORTAL)) {
            return false;
        }
        protectedPortalBlocks.add(b);
        return true;
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (protectedPortalBlocks.contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }

}
