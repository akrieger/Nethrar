/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockListener;

import java.util.HashSet;
import java.util.Set;

/**
 * BlockListener object for the Portals plugin.
 *
 * This listens for physics events on portals in the End, at least to allow
 * one-way teleports. Configurable option allows two-way teleports.
 *
 * @author Andrew Krieger
 */
public class NethrarBlockListener extends BlockListener {
    private static Set<Block> protectedPortalBlocks = new HashSet<Block>();

    public NethrarBlockListener() { }

    public static boolean protectPortalBlock(Block b) {
        if (!b.getType().equals(Material.PORTAL)) {
            return false;
        }
        protectedPortalBlocks.add(b);
        return true;
    }

    @Override
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (protectedPortalBlocks.contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
