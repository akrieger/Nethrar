/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldListener;

/**
 * WorldListener object for the Portals plugin.
 *
 * This class listens for ChunkUnloadEvents, and selectively cancels them
 * based on a configurable radius around portals. This is to help prevent
 * chunk loading failures, which may cause players to fall and die or to
 * spawn within solid material.
 *
 * @author Andrew Krieger
 */
public class NethrarWorldListener extends WorldListener {
    public NethrarWorldListener() { }

    @Override
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk c = event.getChunk();
        if (PortalUtil.isChunkForcedLoaded(c)) {
            event.setCancelled(true);
        }
    }
}
