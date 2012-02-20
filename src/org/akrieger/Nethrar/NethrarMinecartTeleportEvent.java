/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.entity.Minecart;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * NethrarMinecartTeleportEvent class.
 *
 * Fires whenever a minecart gets teleported between worlds, letting plugins
 * which modify minecarts closely (like Minecart Mania) catch that and transfer
 * data successfully.
 */
public class NethrarMinecartTeleportEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private Minecart oldCart;
    private Minecart newCart;

    public NethrarMinecartTeleportEvent(Minecart oldCart, Minecart newCart) {
        super();
        this.oldCart = oldCart;
        this.newCart = newCart;
    }

    public Minecart getOldCart() {
        return this.oldCart;
    }

    public Minecart getNewCart() {
        return this.newCart;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
