/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import com.nijiko.permissions.PermissionHandler;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

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

        //log.severe("Got PlayerMoveEvent.");
        //log.severe("From: " + event.getFrom());
        //log.severe("To  : " + event.getTo());

        if (Nethrar.permissions != null &&
            !Nethrar.permissions.has(player, "nethrar.use")) {
            return;
        }

        if (!event.getPlayer().isInsideVehicle()) {
            b = event.getTo().getBlock();
        } else {
            b = event.getTo().getBlock().getFace(BlockFace.UP);
        }

        if (!b.getType().equals(Material.PORTAL)) {
            // Not a portal.
            return;
        }

        Portal portal = PortalUtil.getPortalAt(b);
        if (portal != null) {
            Location endpoint = portal.teleport(player);
            if (endpoint != null) {
                event.setTo(endpoint);
            }
        }
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location pl = event.getPlayer().getLocation();
        if (pl == null) {
            log.warning("[NETHRAR] Player died, but respawn event has no " +
                "respawn location.");
            return;
        }

        World pw = pl.getWorld();
        if (pw == null) {
            log.warning("[NETHRAR] Player died, respawn event has a " +
                "location, but the location has no world.");
            return;
        }

        World respawnWorld = PortalUtil.getRespawnWorldFor(pw);
        if (respawnWorld != null) {
            event.setRespawnLocation(respawnWorld.getSpawnLocation());
        }
    }
}
