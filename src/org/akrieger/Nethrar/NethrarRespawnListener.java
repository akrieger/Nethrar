/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.logging.Logger;

/**
 * Respawn Listener object for the Portals plugin.
 *
 * This class listens for PlayerRespawnEvents, and redirects the respawn
 * location as appropriate, as defined by the world config.
 *
 * @author Andrew Krieger
 */
public class NethrarRespawnListener implements Listener {
    private final Logger log = Logger.getLogger("Minecraft.Nethrar");

    @EventHandler
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
