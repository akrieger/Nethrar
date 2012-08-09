/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Andrew Krieger
 */
public class NethrarTeleporter implements Runnable {

    private Entity e;
    private Vehicle v, oldv;
    private Location destination;
    private Vector velocity;

    private NethrarTeleporter() { }

    public NethrarTeleporter(Entity e, Location d) {
        this.e = e;
        this.destination = d;
        this.v = this.oldv = null;
        this.velocity = null;
    }

    public NethrarTeleporter(
        Entity e, Location d, Vehicle v, Vector velocity, Vehicle oldv) {

        this.e = e;
        this.destination = d;
        this.v = v;
        this.velocity = velocity;
        this.oldv = oldv;
    }

    public void run() {
        // Preload chunks.
        Chunk dChunk = destination.getBlock().getChunk();
        World dWorld = e.getLocation().getWorld();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                dWorld.loadChunk(
                    dChunk.getX() + dx, dChunk.getZ() + dz);
            }
        }

        if (e != null) {
            if (e.teleport(destination)) {
                int x = dChunk.getX(), z = dChunk.getZ();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        dWorld.refreshChunk(x + dx, z + dz);
                    }
                }
            } else {
                if (v != null) {
                    v.remove();
                    if (e != null) {
                        oldv.setPassenger(e);
                        Vector reverse = oldv.getVelocity();
                        reverse.setX(reverse.getX() * -1);
                        reverse.setY(reverse.getY() * -1);
                        reverse.setZ(reverse.getZ() * -1);
                        oldv.setVelocity(reverse);
                    }
                }
                // Teleport failed for whatever reason. Abort.
                return;
            }
        }
        if (v != null) {
            if (e != null) {
                v.setPassenger(e);
            }
            v.setVelocity(velocity);
        }
        if (v != null && oldv != null) {
            Bukkit.getServer().getPluginManager().callEvent(
                new NethrarVehicleTeleportEvent(oldv, v));
        }
        if (oldv != null) {
            oldv.remove();
        }
    }
}
