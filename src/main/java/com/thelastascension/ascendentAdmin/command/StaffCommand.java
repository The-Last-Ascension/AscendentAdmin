package com.thelastascension.ascendentAdmin.command;

import com.thelastascension.ascendentAdmin.AscendentAdmin;
import com.thelastascension.ascendentAdmin.gui.AdminPanelGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comando /staff (/admin, /panel, /ascadmin) — restringido a OP o permisos ascendent.admin / ascendent.staff.
 */
public class StaffCommand implements CommandExecutor, TabCompleter {

    private final AscendentAdmin plugin;

    public StaffCommand(AscendentAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("Este comando solo puede ejecutarse dentro del juego.");
            return true;
        }

        if (!plugin.hasStaffAccess(staff)) {
            plugin.getCore().getLang().send(staff, "admin.no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.getCore().getLang().send(staff, "admin.panel-opened");
            plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.PLAYERS);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "players", "jugadores" -> plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.PLAYERS);
            case "entities", "entidades", "models", "modelos" -> plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.ENTITIES);
            case "items", "arsenal" -> plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.ITEMS);
            case "server", "servidor", "mundo" -> plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.SERVER);
            case "player", "jugador", "edit" -> {
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target != null) {
                        plugin.getAdminPanelGui().openPlayerEditor(staff, target.getUniqueId());
                        return true;
                    }
                }
                plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.PLAYERS);
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target != null) {
                    plugin.getAdminPanelGui().openPlayerEditor(staff, target.getUniqueId());
                } else {
                    plugin.getAdminPanelGui().openTab(staff, AdminPanelGui.Tab.PLAYERS);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!plugin.hasStaffAccess(sender)) return List.of();

        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("jugadores", "entidades", "items", "servidor", "jugador"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                opts.add(p.getName());
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return opts.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("jugador") || args[0].equalsIgnoreCase("edit"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
