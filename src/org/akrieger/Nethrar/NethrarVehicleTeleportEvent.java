/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;

/**
 * NethrarVehicleTeleportEvent class.
 *
 * Fires whenever a vehicle gets teleported between worlds, letting
 * plugins which modify vehicles closely (like Minecart Mania) catch
 * that and transfer data successfully.
 */
public class NethrarVehicleTeleportEvent extends Event {

	private Vehicle oldV;
	private Vehicle newV;

	public NethrarVehicleTeleportEvent(Vehicle oldV, Vehicle newV) {
		super("NethrarVehicleTeleportEvent");
		this.oldV = oldV;
		this.newV = newV;
	}

	public Vehicle getOldV() {
		return this.oldV;
	}

	public Vehicle getNewV() {
		return this.newV;
	}
}
