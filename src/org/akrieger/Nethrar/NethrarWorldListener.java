/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * World Listener object for the Portals plugin.
 *
 * This class listens for ChunkUnloadEvents, and selectively cancels them
 * based on a configurable radius around portals. This is to help prevent
 * chunk loading failures, which may cause players to fall and die or to
 * spawn within solid material.
 *
 * @author Andrew Krieger
 */
public class NethrarWorldListener implements Listener {
  public NethrarWorldListener() { }

  @EventHandler
  public void onChunkUnload(ChunkUnloadEvent event) {
    Chunk c = event.getChunk();
    if (PortalUtil.isChunkForcedLoaded(c)) {
      event.setCancelled(true);
    }
  }
}
