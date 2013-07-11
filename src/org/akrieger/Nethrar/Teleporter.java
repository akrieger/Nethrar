/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.Vector;

public interface Teleporter extends Runnable {
  public Teleporter setSourceEntity(Entity e);
  public Teleporter setDestinationLocation(Location l);

  public Teleporter setSourceVehicle(Vehicle v);
  public Teleporter setSourceVector(Vector v);
  public Teleporter setDestinationVelocity(Vector v);
}
