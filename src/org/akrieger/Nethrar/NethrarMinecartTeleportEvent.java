/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;

/**
 * NethrarMinecartTeleportEvent class.
 *
 * Fires whenever a minecart gets teleported between worlds, letting plugins
 * which modify minecarts closely (like Minecart Mania) catch that and transfer
 * data successfully.
 */
public class NethrarMinecartTeleportEvent extends Event {

    private Minecart oldCart;
    private Minecart newCart;

    public NethrarMinecartTeleportEvent(Minecart oldCart, Minecart newCart) {
        super("NethrarMinecartTeleportEvent");
        this.oldCart = oldCart;
        this.newCart = newCart;
    }

    public Minecart getOldCart() {
        return this.oldCart;
    }

    public Minecart getNewCart() {
        return this.newCart;
    }
}
