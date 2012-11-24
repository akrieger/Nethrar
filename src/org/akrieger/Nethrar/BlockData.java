/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Material;

public class BlockData implements Comparable {

    public Material m;
    public byte data;

    public BlockData(Material m, byte data) {
        this.m = m; this.data = data;
    }

    public int compareTo(Object o) {
        BlockData bd = (BlockData)o;
        int diff = this.m.ordinal() - bd.m.ordinal();
        if (diff != 0) {
            return diff;
        }
        diff = this.data - bd.data;
        return diff;
    }

    public boolean equals(Object o) {
        if (!(o instanceof BlockData)) {
            return false;
        }
        BlockData other = (BlockData)o;
        return this.m.equals(other.m) && this.data == other.data;
    }

    public int hashCode() {
        return Integer.parseInt(this.m.ordinal() + "00" + this.data);
    }
}
