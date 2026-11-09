package com.thelastascension.ascendentAdmin;

import com.thelastascension.ascendentAdmin.command.StaffCommand;
import com.thelastascension.ascendentAdmin.gui.AdminPanelGui;
import com.thelastascension.ascendentClasses.AscendentClasses;
import com.thelastascension.ascendentCombat.AscendentCombat;
import com.thelastascension.ascendentCore.AscendentCore;
import com.thelastascension.ascendentCore.module.AscendentModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * AscendentAdmin v1.0.0 — Panel de Staff y Administración Total de The Last Ascension (Ascendant).
 * Permite gestionar jugadores, estadísticas globales, clases, habilidades, talismanes,
 * modelos 3D (ModelEngine/MythicMobs), arsenal elemental y ajustes del servidor desde un menú unificado.
 */
public final class AscendentAdmin extends JavaPlugin implements AscendentModule {

    private AscendentCore core;
    private AdminPanelGui adminPanelGui;

    @Override
    public void onEnable() {
        this.core = AscendentCore.getInstance();
        if (this.core == null) {
            getLogger().severe("No se encontró AscendentCore activo. Desactivando AscendentAdmin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        this.core.getLang().registerPluginLanguageFiles(this, "lang/es_es.yml", "lang/en_us.yml");

        this.adminPanelGui = new AdminPanelGui(this);
        getServer().getPluginManager().registerEvents(this.adminPanelGui, this);

        PluginCommand staffCmd = getCommand("staff");
        if (staffCmd != null) {
            StaffCommand executor = new StaffCommand(this);
            staffCmd.setExecutor(executor);
            staffCmd.setTabCompleter(executor);
        }

        this.core.getModuleRegistry().registerModule(this);
        getLogger().info("AscendentAdmin v" + getVersion() + " activado (Panel de Staff /staff y /admin listo).");
    }

    @Override
    public void onDisable() {
        if (this.core != null && this.core.getModuleRegistry() != null) {
            this.core.getModuleRegistry().unregisterModule(getModuleId());
        }
    }

    /**
     * Comprueba si el usuario tiene acceso al Panel de Staff (OP o permisos configurados).
     */
    public boolean hasStaffAccess(CommandSender sender) {
        if (sender.isOp() || sender.hasPermission("ascendent.admin") || sender.hasPermission("ascendent.staff")) {
            return true;
        }
        List<String> perms = getConfig().getStringList("security.required-permissions");
        for (String perm : perms) {
            if (perm != null && !perm.isBlank() && sender.hasPermission(perm)) {
                return true;
            }
        }
        return false;
    }

    public AscendentCore getCore() {
        return core;
    }

    public AscendentCombat getCombatPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AscendentCombat");
        return (p instanceof AscendentCombat combat && p.isEnabled()) ? combat : null;
    }

    public AscendentClasses getClassesPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("AscendentClasses");
        return (p instanceof AscendentClasses classes && p.isEnabled()) ? classes : null;
    }

    public AdminPanelGui getAdminPanelGui() {
        return adminPanelGui;
    }

    @Override
    public String getModuleId() {
        return "AscendentAdmin";
    }

    @Override
    public Plugin getPlugin() {
        return this;
    }

    @Override
    public String getVersion() {
        return getPluginMeta().getVersion();
    }

    @Override
    public void onReload() {
        reloadConfig();
        if (this.core != null) {
            this.core.getLang().registerPluginLanguageFiles(this, "lang/es_es.yml", "lang/en_us.yml");
        }
    }
}
