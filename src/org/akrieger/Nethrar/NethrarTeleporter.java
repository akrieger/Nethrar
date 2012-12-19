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
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
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
        World dWorld = destination.getWorld();

        if (e != null) {
            boolean teleportSuccess = false;
            if (e instanceof Item) {
                Item i = dWorld.dropItem(destination, ((Item)e).getItemStack());
                if (i != null) {
                    e.remove();
                    teleportSuccess = true;
                }
            } else if (e instanceof Arrow) {
                Arrow a = dWorld.spawnArrow(destination, e.getVelocity(), (float)e.getVelocity().length(), 0f);
                if (a != null) {
                  a.setShooter(((Arrow)e).getShooter());
                  e.remove();
                  teleportSuccess = true;
                }
            } else if (isSpawnTeleportableEntity(e)) {
                LivingEntity eNew = (LivingEntity)dWorld.spawn(destination, e.getClass());
                if (eNew != null) {
                    int health = ((LivingEntity)e).getHealth();
                    if (health < 0) {
                        // Entity is dead, don't bother.
                        return;
                    }
                    eNew.setHealth(((LivingEntity)e).getHealth());
                    PortalUtil.markTeleported(eNew, 5 * 1000000000l); // 5 second delay for animals
                    teleportSuccess = true;
                    e.remove();
                }
            } else if (e instanceof Player) {
                if (e.teleport(destination)) {
                    teleportSuccess = true;
                }
            } else if (v != null) {
                teleportSuccess = true;
            }
            if (!teleportSuccess) {
                if (v != null) {
                    v.remove();
                    if (e != null) {
                        oldv.setPassenger(e);
                    }
                    Vector reverse = oldv.getVelocity();
                    reverse.setX(reverse.getX() * -1);
                    reverse.setY(reverse.getY() * -1);
                    reverse.setZ(reverse.getZ() * -1);
                    oldv.setVelocity(reverse);
                }
                // Teleport failed for whatever reason. Abort.
                return;
            }
        }
        if (v != null) {
            if (e != null) {
                final Vehicle fv = v;
                final Entity fe = e;
                final Vector fvv = velocity;
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(
                    PortalUtil.getPlugin(),
                    new Runnable() {
                        public void run() {
                            fv.setPassenger(fe);
                            fv.setVelocity(fvv);
                        }
                    },
                    1
                );
            }
        }
        if (v != null && oldv != null) {
            Bukkit.getServer().getPluginManager().callEvent(
                new NethrarVehicleTeleportEvent(oldv, v));
        }
        if (oldv != null) {
            oldv.remove();
        }
    }

    private static boolean isSpawnTeleportableEntity(Entity e) {
        return (e instanceof Animals) ||
               (e instanceof Monster);
    }
}
