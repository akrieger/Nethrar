/*
 * Copyright (C) 2011-present Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class NethrarCommandExecutor implements CommandExecutor {

    private Nethrar plugin;

    public NethrarCommandExecutor(Nethrar pl) {
        this.plugin = pl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
            String commandLabel, String[] args) {

        if (!(sender instanceof Player)) {
            return false;
        }

        Player player = ((Player)sender);

        if (args.length == 0) {
            return false;
        }

        if (args[0].equals("tp")) {
            if (this.plugin.shouldUsePermissions()) {
                if (!player.hasPermission("nethrar.tp")) {
                    sender.sendMessage(
                        ChatColor.RED + "You do not have permission to " +
                        "teleport to other worlds."
                    );
                    return true;
                }
            } else {
              if (!player.isOp()) {
                    sender.sendMessage(
                      ChatColor.RED + "You must be an op to teleport to " +
                      "other worlds."
                  );
                  return true;
              }
            }

            if (args.length < 2) {
                return false;
            }

            World destWorld = plugin.getServer().getWorld(args[1]);
            if (destWorld == null) {
                sender.sendMessage(ChatColor.RED + "The world you wanted to " +
                    "teleport to (" + args[1] + ") does not exist.");
                return true;
            }

            int destScale = PortalUtil.getScaleFor(destWorld);
            if (destScale == 0) {
                sender.sendMessage(ChatColor.RED + "The world you wanted to " +
                    "teleport to (" + args[1] + ") exists, but Nethrar does " +
                    "not know about it.");
                sender.sendMessage(ChatColor.RED + "Add an entry for the " +
                    "world in Nethrar's worlds.yml file.");
                return true;
            }

            World sourceWorld = player.getWorld();
            int sourceScale = PortalUtil.getScaleFor(sourceWorld);
            if (sourceScale == 0) {
                sender.sendMessage(ChatColor.RED + "The world you are in (" +
                    sourceWorld.getName() + ") exists, but Nethrar does not " +
                    "know about it.");
                sender.sendMessage(ChatColor.RED + "Add an entry for the " +
                    "world in Nethrar's worlds.yml file.");
                return true;
            }

            Location sourceLoc = player.getLocation();

            double scale = destScale / (double)sourceScale;

            int destX = (int)Math.floor(sourceLoc.getX() * scale);
            int destY = (int)Math.floor(sourceLoc.getY());
            int destZ = (int)Math.floor(sourceLoc.getZ() * scale);

            Location destLoc = new Location(
                destWorld, destX + .5, destY, destZ + .5);

            destWorld.getBlockAt(destX, destY, destZ).setType(Material.AIR);
            destWorld.getBlockAt(destX, destY + 1, destZ).setType(Material.AIR);
            destWorld.getBlockAt(destX, destY - 1, destZ)
                .setType(Material.GLASS);

            player.teleport(destLoc);
        }

        return true;
    }
}
