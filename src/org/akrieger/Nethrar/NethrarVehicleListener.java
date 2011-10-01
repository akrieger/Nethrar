/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.logging.Logger;

/**
 * VehicleListener object for the Portals plugin.
 *
 * This class listens for VehicleMoveEvents to determine whether a vehicle/cart
 * hit a portal and should be teleported, and for PlayerRespawnEvents to send a
 * player back to the main/first/normal world.
 *
 * @author Andrew Krieger
 */
public class NethrarVehicleListener extends VehicleListener {
    private final Logger log = Logger.getLogger("Minecraft.Nethrar");

    public NethrarVehicleListener() { }

    @Override
    public void onVehicleMove(VehicleMoveEvent event) {
        Block b;
        Vehicle vehicle = event.getVehicle();

        if (vehicle.getPassenger() != null) {
            return;
        }

        b = vehicle.getLocation().getBlock();

        if (!b.getType().equals(Material.PORTAL)) {
            // Not a portal.
            return;
        }

        Portal portal = PortalUtil.getPortalAt(b);
        portal.teleport(vehicle, event.getTo());
    }
}
