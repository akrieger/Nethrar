/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * NethrarVehicleTeleportEvent class.
 *
 * Fires whenever a vehicle gets teleported between worlds, letting
 * plugins which modify vehicles closely (like Minecart Mania) catch
 * that and transfer data successfully.
 *
 * Also calls a NethrarMinecartTeleportEvent if the vehicle being
 * teleported is a minecart.
 */
public class NethrarVehicleTeleportEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private Vehicle oldV;
    private Vehicle newV;

    public NethrarVehicleTeleportEvent(Vehicle oldV, Vehicle newV) {
        super();
        this.oldV = oldV;
        this.newV = newV;

        if (oldV instanceof Minecart) {
            Bukkit.getServer().getPluginManager().callEvent(new NethrarMinecartTeleportEvent((Minecart)oldV, (Minecart)newV));
        }
    }

    public Vehicle getOldV() {
        return this.oldV;
    }

    public Vehicle getNewV() {
        return this.newV;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
