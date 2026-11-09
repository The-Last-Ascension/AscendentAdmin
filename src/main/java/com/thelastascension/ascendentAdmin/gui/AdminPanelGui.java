package com.thelastascension.ascendentAdmin.gui;

import com.thelastascension.ascendentAdmin.AscendentAdmin;
import com.thelastascension.ascendentClasses.AscendentClasses;
import com.thelastascension.ascendentClasses.model.ClassSkill;
import com.thelastascension.ascendentClasses.model.PlayerClassProgress;
import com.thelastascension.ascendentClasses.model.RpgClassDefinition;
import com.thelastascension.ascendentClasses.model.TalismanDefinition;
import com.thelastascension.ascendentCombat.AscendentCombat;
import com.thelastascension.ascendentCore.i18n.LangManager;
import com.thelastascension.ascendentCore.model.ElementType;
import com.thelastascension.ascendentCore.model.PlayerProfile;
import com.thelastascension.ascendentCore.model.StatType;
import com.thelastascension.ascendentCore.module.AscendentModule;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menú Interactivo Multi-Pestaña de Administración Total (/staff, /admin, /panel).
 * Pestaña 1: Jugadores Conectados -> Ficha de Edición Total por Jugador.
 * Pestaña 2: Spawner 3D (ModelEngine + MythicMobs + Muñecos Parry/Imparable).
 * Pestaña 3: Arsenal RPG (Armas 8 Elementos, Armaduras de Clase, Talismanes e Ítems SamusDev).
 * Pestaña 4: Control del Servidor, Mundo, Modo Vanish, Fly Speed y Recarga Global.
 */
public class AdminPanelGui implements Listener {

    public enum Tab {
        PLAYERS,
        PLAYER_EDITOR,
        ENTITIES,
        ITEMS,
        SERVER
    }

    public static final class AdminGuiHolder implements InventoryHolder {
        private final Tab tab;
        private final UUID targetPlayerUuid;

        public AdminGuiHolder(Tab tab, UUID targetPlayerUuid) {
            this.tab = tab;
            this.targetPlayerUuid = targetPlayerUuid;
        }

        public Tab getTab() {
            return tab;
        }

        public UUID getTargetPlayerUuid() {
            return targetPlayerUuid;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(this, 54);
        }
    }

    private final AscendentAdmin plugin;
    private final NamespacedKey keyAction;
    private final NamespacedKey keyPayload;

    // Jugadores congelados por el Staff
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    // Miembros del Staff en modo Vanish (invisibles para jugadores normales)
    private final Set<UUID> vanishedStaff = ConcurrentHashMap.newKeySet();

    public AdminPanelGui(AscendentAdmin plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "admin_action");
        this.keyPayload = new NamespacedKey(plugin, "admin_payload");
    }

    public void openTab(Player staff, Tab tab) {
        openGui(staff, tab, staff.getUniqueId());
    }

    public void openPlayerEditor(Player staff, UUID targetUuid) {
        openGui(staff, Tab.PLAYER_EDITOR, targetUuid);
    }

    private void openGui(Player staff, Tab tab, UUID targetUuid) {
        if (!plugin.hasStaffAccess(staff)) {
            plugin.getCore().getLang().send(staff, "admin.no-permission");
            return;
        }

        String title = switch (tab) {
            case PLAYERS -> "&8[&c&lSTAFF&8] &6Gestión de Jugadores";
            case PLAYER_EDITOR -> {
                Player t = Bukkit.getPlayer(targetUuid);
                yield "&8[&c&lSTAFF&8] &eEditando: &f" + (t != null ? t.getName() : "Jugador");
            }
            case ENTITIES -> "&8[&c&lSTAFF&8] &bSpawner 3D y Entidades";
            case ITEMS -> "&8[&c&lSTAFF&8] &dArsenal RPG e Ítems Custom";
            case SERVER -> "&8[&c&lSTAFF&8] &aControl de Servidor y Mundo";
        };

        Inventory inv = Bukkit.createInventory(new AdminGuiHolder(tab, targetUuid), 54, LangManager.colorize(title));
        ItemStack glass = createIcon(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null, null);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // Barra Superior de Pestañas (Slots 0..8)
        inv.setItem(1, createIcon(
                tab == Tab.PLAYERS || tab == Tab.PLAYER_EDITOR ? Material.RECOVERY_COMPASS : Material.COMPASS,
                "&6&l[1] Jugadores y Perfiles",
                List.of(
                        "&7Inspecciona y edita cualquier atributo,",
                        "&7estadística, clase, moneda o inventario",
                        "&7de todos los jugadores conectados.",
                        "",
                        "&e▶ Clic para abrir lista de jugadores"
                ),
                "switch_tab", Tab.PLAYERS.name()
        ));
        inv.setItem(3, createIcon(
                Material.SPAWNER,
                "&b&l[2] Spawner 3D y Entidades",
                List.of(
                        "&7Invoca al instante los modelos 3D de",
                        "&7&fModelEngine &7y &fMythicMobs &7(NPCs, Nodos,",
                        "&7Portales, Runas y Muñecos de Entrenamiento).",
                        "",
                        "&e▶ Clic para abrir el Spawner 3D"
                ),
                "switch_tab", Tab.ENTITIES.name()
        ));
        inv.setItem(5, createIcon(
                Material.NETHERITE_SWORD,
                "&d&l[3] Arsenal RPG e Ítems Custom",
                List.of(
                        "&7Obtén Armas con Parry de los 8 Elementos,",
                        "&7Armaduras Inteligentes de Clase, Talismanes",
                        "&7estilo Elden Ring e Ítems de SamusDev.",
                        "",
                        "&e▶ Clic para abrir el Arsenal"
                ),
                "switch_tab", Tab.ITEMS.name()
        ));
        inv.setItem(7, createIcon(
                Material.COMMAND_BLOCK,
                "&a&l[4] Servidor, Mundo y Staff",
                List.of(
                        "&7Controla Hora, Clima, Modo Vanish,",
                        "&7Velocidad de Vuelo, Mantenimiento y",
                        "&7Recarga en caliente de todos los módulos.",
                        "",
                        "&e▶ Clic para abrir Control del Servidor"
                ),
                "switch_tab", Tab.SERVER.name()
        ));

        switch (tab) {
            case PLAYERS -> renderPlayersTab(inv, staff);
            case PLAYER_EDITOR -> renderPlayerEditorTab(inv, staff, targetUuid);
            case ENTITIES -> renderEntitiesTab(inv);
            case ITEMS -> renderItemsTab(inv);
            case SERVER -> renderServerTab(inv, staff);
        }

        staff.openInventory(inv);
    }

    // =========================================================================
    // PESTAÑA 1: LISTA DE JUGADORES CONECTADOS
    // =========================================================================
    private void renderPlayersTab(Inventory inv, Player staff) {
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };
        int idx = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (idx >= slots.length) break;
            PlayerProfile prof = plugin.getCore().getProfileManager().getCachedProfile(online.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                boolean frozen = frozenPlayers.contains(online.getUniqueId());
                meta.setDisplayName(LangManager.colorize("&e&l" + online.getName() + (online.isOp() ? " &c[OP]" : "") + (frozen ? " &b[CONGELADO]" : "")));

                String classId = prof != null ? prof.getActiveClassId() : "none";
                int globalLvl = prof != null ? prof.getGlobalLevel() : 1;
                int freePts = prof != null ? prof.getFreeStatPoints() : 0;
                long coins = prof != null ? prof.getCoins() : 0;
                int talSlots = prof != null ? prof.getUnlockedTalismanSlots() : 1;

                List<String> lore = new ArrayList<>();
                lore.add(LangManager.colorize("&8UUID: " + online.getUniqueId()));
                lore.add("");
                lore.add(LangManager.colorize("&7Nivel Global: &a" + globalLvl + " &8| &7Pts Estadística: &e" + freePts));
                lore.add(LangManager.colorize("&7Monedas: &6" + coins + " ⛃ &8| &7Ranuras Talismán: &d" + talSlots));
                lore.add(LangManager.colorize("&7Clase Activa: &b" + classId));
                lore.add(LangManager.colorize("&7Vida MC: &c" + String.format("%.1f/%.1f ❤", online.getHealth(), getMaxHealth(online))));
                lore.add(LangManager.colorize("&7Modo de Juego: &f" + online.getGameMode().name()));
                lore.add(LangManager.colorize("&7Ubicación: &f" + online.getWorld().getName() + " (" + online.getLocation().getBlockX() + ", " + online.getLocation().getBlockY() + ", " + online.getLocation().getBlockZ() + ")"));
                lore.add("");
                lore.add(LangManager.colorize("&a▶ Clic Izquierdo: &fAbrir Editor Total del Jugador"));
                lore.add(LangManager.colorize("&b▶ Clic Derecho: &fTeletransportarse a " + online.getName()));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "select_player");
                meta.getPersistentDataContainer().set(keyPayload, PersistentDataType.STRING, online.getUniqueId().toString());
                head.setItemMeta(meta);
            }
            inv.setItem(slots[idx++], head);
        }

        // Botón rápido para editarse a uno mismo directamente
        inv.setItem(49, createIcon(
                Material.GOLD_BLOCK,
                "&6&lEditar Mi Propio Perfil (" + staff.getName() + ")",
                List.of(
                        "&7Acceso directo para modificar tus propios",
                        "&7niveles, monedas, clase, habilidades y stats.",
                        "",
                        "&e▶ Clic para abrir tu Ficha de Edición"
                ),
                "select_player", staff.getUniqueId().toString()
        ));
    }

    // =========================================================================
    // SUB-MENÚ: EDITOR INTEGRAL DE UN JUGADOR (TODOS SUS DATOS Y STATS)
    // =========================================================================
    private void renderPlayerEditorTab(Inventory inv, Player staff, UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            inv.setItem(22, createIcon(Material.BARRIER, "&cJugador Desconectado", List.of("&7Vuelve a la pestaña de jugadores."), "switch_tab", Tab.PLAYERS.name()));
            return;
        }

        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (profile == null) return;

        AscendentClasses classesPlugin = plugin.getClassesPlugin();
        RpgClassDefinition activeClass = (classesPlugin != null)
                ? classesPlugin.getRegistry().getClass(profile.getActiveClassId())
                : null;
        PlayerClassProgress classProg = (classesPlugin != null && activeClass != null)
                ? classesPlugin.getClassManager().getOrCreateClassProgress(targetUuid, activeClass.id())
                : null;

        // Fila 1 (Slots 10..16): Progresión Global, Economía, Ranuras, Clase y Estado
        inv.setItem(10, createIcon(
                Material.EXPERIENCE_BOTTLE,
                "&a&lNivel Global: &f" + profile.getGlobalLevel(),
                List.of(
                        "&7Modifica el Nivel Global del jugador.",
                        "",
                        "&a▶ Clic Izq: &f+1 Nivel Global",
                        "&a▶ Shift + Clic Izq: &f+10 Niveles Globales",
                        "&c▶ Clic Der: &f-1 Nivel Global",
                        "&c▶ Shift + Clic Der: &fResetear a Nivel 1"
                ),
                "edit_global_level", targetUuid.toString()
        ));

        inv.setItem(11, createIcon(
                Material.NETHER_STAR,
                "&e&lPuntos Globales Libres: &f" + profile.getFreeStatPoints(),
                List.of(
                        "&7Puntos de Estadística Globales que persisten",
                        "&7al cambiar de clase (estilo Trove).",
                        "",
                        "&a▶ Clic Izq: &f+5 Puntos Libres",
                        "&a▶ Shift + Clic Izq: &f+50 Puntos Libres",
                        "&c▶ Clic Der: &f-5 Puntos Libres",
                        "&6▶ Shift + Clic Der: &fReembolsar todos los invertidos"
                ),
                "edit_free_points", targetUuid.toString()
        ));

        inv.setItem(12, createIcon(
                Material.GOLD_INGOT,
                "&6&lMonedas (Economía): &f" + profile.getCoins() + " ⛃",
                List.of(
                        "&7Modifica el saldo de monedas de Ascendant.",
                        "",
                        "&a▶ Clic Izq: &f+500 Monedas",
                        "&a▶ Shift + Clic Izq: &f+10,000 Monedas",
                        "&c▶ Clic Der: &f-500 Monedas",
                        "&c▶ Shift + Clic Der: &fPoner Monedas a 0"
                ),
                "edit_coins", targetUuid.toString()
        ));

        inv.setItem(13, createIcon(
                Material.AMETHYST_SHARD,
                "&d&lRanuras de Talismán: &f" + profile.getUnlockedTalismanSlots() + " / 4",
                List.of(
                        "&7Huecos desbloqueados para equipar",
                        "&7Talismanes estilo Elden Ring.",
                        "",
                        "&a▶ Clic Izq: &f+1 Ranura de Talismán",
                        "&a▶ Shift + Clic Izq: &fDesbloquear las 4 Ranuras",
                        "&c▶ Clic Der: &f-1 Ranura de Talismán"
                ),
                "edit_talisman_slots", targetUuid.toString()
        ));

        String className = activeClass != null ? activeClass.nameEs() : "Sin Clase (none)";
        String classType = activeClass != null ? (activeClass.isStarter() ? "Clase Inicial" : "Ascensión Evolucionada") : "Ninguna";
        inv.setItem(14, createIcon(
                activeClass != null ? activeClass.icon() : Material.BOOK,
                "&b&lClase Activa: &f" + className,
                List.of(
                        "&7Tipo: &e" + classType,
                        "&7ID: &8" + profile.getActiveClassId(),
                        "&7Cambia la clase del jugador al instante",
                        "&7(incluyendo Ascensiones sin bloqueo de nivel).",
                        "",
                        "&a▶ Clic Izq: &fSiguiente Clase (12 disponibles)",
                        "&e▶ Clic Der: &fClase Anterior",
                        "&c▶ Shift + Clic: &fQuitar Clase (none)"
                ),
                "cycle_player_class", targetUuid.toString()
        ));

        int cLvl = classProg != null ? classProg.getClassLevel() : 1;
        int cPts = classProg != null ? classProg.getFreeSkillPoints() : 0;
        inv.setItem(15, createIcon(
                Material.ENCHANTED_BOOK,
                "&3&lNivel de Clase: &f" + cLvl + " &8(&b" + cPts + " Pts Hab.&8)",
                List.of(
                        "&7Gestiona el progreso de la clase actual",
                        "&7y el nivel de sus habilidades evolutivas.",
                        "",
                        "&a▶ Clic Izq: &f+1 Nivel de Clase y +2 Pts Habilidad",
                        "&a▶ Shift + Clic Izq: &f+10 Niveles y +20 Pts Habilidad",
                        "&c▶ Clic Der: &f-1 Nivel de Clase",
                        "&d▶ Shift + Clic Der: &fSubir TODAS sus Habilidades al Nv. 5"
                ),
                "edit_class_progress", targetUuid.toString()
        ));

        inv.setItem(16, createIcon(
                Material.GOLDEN_APPLE,
                "&c&lSalud, Stun y Estado Abatido",
                List.of(
                        "&7Vida actual: &c" + String.format("%.1f / %.1f ❤", target.getHealth(), getMaxHealth(target)),
                        "&7Barra de Stun: &e" + String.format("%.1f / %.1f", profile.getCurrentStun(), profile.getStat(StatType.MAX_STUN)),
                        "&7Estado Abatido (Downed): " + (profile.isDowned() ? "&cSÍ (Esperando Revivir)" : "&aNO (En pie)"),
                        "",
                        "&a▶ Clic Izq: &fCurar 100%, saciar y limpiar Stun",
                        "&e▶ Clic Der: &fAlternar Abatir / Revivir al instante"
                ),
                "heal_or_revive_player", targetUuid.toString()
        ));

        // Fila Central (Slots 19..25 y 28..30): Editor directo de las 10 Estadísticas (StatType)
        int[] statSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30};
        StatType[] allStats = StatType.values();
        for (int i = 0; i < allStats.length && i < statSlots.length; i++) {
            StatType st = allStats[i];
            int invested = profile.getInvestedPoints(st);
            double totalVal = profile.getStat(st);
            inv.setItem(statSlots[i], createIcon(
                    getStatIcon(st),
                    "&6&lStat: &e" + st.getDisplayNameEs(),
                    List.of(
                            "&7Valor Total Actual: &a" + String.format("%.1f", totalVal),
                            "&7Puntos Globales Invertidos: &f" + invested,
                            "",
                            "&a▶ Clic Izq: &f+1 Punto en " + st.getDisplayNameEs(),
                            "&a▶ Shift + Clic Izq: &f+5 Puntos en " + st.getDisplayNameEs(),
                            "&c▶ Clic Der: &f-1 Punto en " + st.getDisplayNameEs(),
                            "&c▶ Shift + Clic Der: &fResetear a 0 puntos"
                    ),
                    "edit_single_stat", targetUuid + ":" + st.name()
            ));
        }

        // Slots 32..34: Entrega directa de Equipamiento a este jugador
        inv.setItem(32, createIcon(
                Material.NETHERITE_CHESTPLATE,
                "&6&lEntregar Kit de su Clase Activa",
                List.of(
                        "&7Le entrega en su inventario la &eCoraza Ascendente",
                        "&7de su clase actual + una &eEspada con Parry",
                        "&7imbuida en el elemento primario de su clase.",
                        "",
                        "&e▶ Clic para entregar Kit a " + target.getName()
                ),
                "give_player_class_kit", targetUuid.toString()
        ));

        inv.setItem(33, createIcon(
                Material.TOTEM_OF_UNDYING,
                "&d&lDesbloquear y Equipar Talismanes",
                List.of(
                        "&7Desbloquea las 4 ranuras y le equipa los",
                        "&74 mejores Talismanes estilo Elden Ring.",
                        "",
                        "&e▶ Clic para aplicar a " + target.getName()
                ),
                "give_player_max_talismans", targetUuid.toString()
        ));

        // Fila Inferior (Slots 37..43): Moderación, Inventario en Vivo, EnderChest, TP, Gamemode, Freeze, Kick
        inv.setItem(37, createIcon(
                Material.CHEST,
                "&e&lAbrir Inventario / EnderChest en Vivo",
                List.of(
                        "&7Mira y modifica en tiempo real los objetos",
                        "&7que lleva &f" + target.getName() + "&7.",
                        "",
                        "&a▶ Clic Izq: &fVer/Editar su Inventario",
                        "&d▶ Clic Der: &fVer/Editar su EnderChest"
                ),
                "inspect_inventory", targetUuid.toString()
        ));

        inv.setItem(38, createIcon(
                Material.ENDER_PEARL,
                "&b&lTeletransporte Rápido",
                List.of(
                        "&7Ubicación: &f" + target.getLocation().getBlockX() + ", " + target.getLocation().getBlockY() + ", " + target.getLocation().getBlockZ(),
                        "",
                        "&a▶ Clic Izq: &fIr hacia " + target.getName(),
                        "&e▶ Clic Der: &fTraer a " + target.getName() + " hacia ti"
                ),
                "teleport_player", targetUuid.toString()
        ));

        inv.setItem(39, createIcon(
                Material.COMMAND_BLOCK_MINECART,
                "&a&lModo de Juego: &f" + target.getGameMode().name(),
                List.of(
                        "&7Cambia el Gamemode de &f" + target.getName() + "&7.",
                        "",
                        "&e▶ Clic para rotar (Survival / Creative / Adventure / Spectator)"
                ),
                "cycle_player_gamemode", targetUuid.toString()
        ));

        boolean isFrozen = frozenPlayers.contains(targetUuid);
        inv.setItem(40, createIcon(
                isFrozen ? Material.BLUE_ICE : Material.ICE,
                "&b&lCongelar Jugador (Freeze): " + (isFrozen ? "&cACTIVO" : "&aINACTIVO"),
                List.of(
                        "&7Inmoviliza por completo al jugador para",
                        "&7revisiones o soporte sin que pueda moverse.",
                        "",
                        "&e▶ Clic para Conmutar Congelación"
                ),
                "toggle_freeze_player", targetUuid.toString()
        ));

        inv.setItem(43, createIcon(
                Material.BARRIER,
                "&c&lExpulsar del Servidor (Kick)",
                List.of(
                        "&7Expulsa inmediatamente a &f" + target.getName() + "&7.",
                        "",
                        "&c▶ Shift + Clic para confirmar expulsión"
                ),
                "kick_player", targetUuid.toString()
        ));

        inv.setItem(49, createIcon(
                Material.ARROW,
                "&e&l⬅ Volver a la Lista de Jugadores",
                List.of("&7Regresa al listado general."),
                "switch_tab", Tab.PLAYERS.name()
        ));
    }

    // =========================================================================
    // PESTAÑA 2: SPAWNER 3D (MODELENGINE + MYTHICMOBS + DUMMIES DE COMBATE)
    // =========================================================================
    private void renderEntitiesTab(Inventory inv) {
        // Muñecos nativos de AscendentCombat (Parryable e Imparable)
        inv.setItem(10, createIcon(
                Material.ARMOR_STAND,
                "&a&lMuñeco de Combate (Parryable)",
                List.of(
                        "&7Entidad de prueba de &fAscendentCombat&7.",
                        "&7Permite hacerle &aParry Perfecto &7con Clic Derecho,",
                        "&7subir su Barra de Stun y probar Reacciones Elementales.",
                        "",
                        "&e▶ Clic para invocar en tu posición"
                ),
                "spawn_combat_dummy", "parryable"
        ));

        inv.setItem(11, createIcon(
                Material.NETHERITE_AXE,
                "&c&lMuñeco de Combate (Ataque Imparable)",
                List.of(
                        "&7Entidad de prueba con golpes &cNO parreables&7.",
                        "&7Al intentar hacer parry solo bloqueas el golpe,",
                        "&7subiendo tu propio Stun estilo Grounded 2.",
                        "",
                        "&e▶ Clic para invocar en tu posición"
                ),
                "spawn_combat_dummy", "unparryable"
        ));

        // Los 11 Modelos 3D de SamusDev cargados en ModelEngine + MythicMobs
        String[][] models3d = {
                {"Training_Dummy", "&6&lMuñeco de Entrenamiento 3D", "CARVED_PUMPKIN", "Modelo 3D animado (dummy.bbmodel) al recibir golpes."},
                {"NPC_Quest_Master", "&e&lNPC: Maestro de Misiones 3D", "WRITABLE_BOOK", "Modelo 3D (npc_quest_master.bbmodel) con animaciones."},
                {"NPC_Merchant_Weapon", "&c&lNPC: Herrero de Armas 3D", "IRON_SWORD", "Modelo 3D (npc_merchant_weapon.bbmodel)."},
                {"NPC_Merchant_Armor", "&9&lNPC: Herrero de Armaduras 3D", "IRON_CHESTPLATE", "Modelo 3D (npc_merchant_armor.bbmodel)."},
                {"NPC_Merchant_Potion", "&d&lNPC: Alquimista / Pociones 3D", "BREWING_STAND", "Modelo 3D (npc_merchant_potion.bbmodel)."},
                {"NPC_Banker", "&6&lNPC: Banquero 3D", "ENDER_CHEST", "Modelo 3D (npc_banker.bbmodel) con menú de monedas."},
                {"NPC_Healing_Fairy", "&a&lNPC: Hada Curativa 3D", "ALLAY_SPAWN_EGG", "Modelo 3D (npc_healing_fairy.bbmodel) que cura jugadores."},
                {"OBJ_Portal_Dungeon", "&5&lObjeto 3D: Portal de Mazmorra", "CRYING_OBSIDIAN", "Modelo 3D (obj_portal_dungeon.bbmodel)."},
                {"OBJ_Waypoint_Gate", "&b&lObjeto 3D: Puerta de Viaje Rápido", "LODESTONE", "Modelo 3D (obj_waypoint_gate.bbmodel)."},
                {"Ore_Dragonite", "&d&lNodo 3D: Mena de Dragonita", "AMETHYST_CLUSTER", "Nodo recolectable 3D (ore_dragonite.bbmodel)."},
                {"Runestone_Heal", "&a&lObjeto 3D: Piedra Rúnica de Curación", "EMERALD_BLOCK", "Piedra rúnica interactiva 3D (runestone_heal.bbmodel)."}
        };

        int[] modelSlots = {13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < models3d.length && i < modelSlots.length; i++) {
            String mobId = models3d[i][0];
            String title = models3d[i][1];
            Material mat = Material.matchMaterial(models3d[i][2]);
            String desc = models3d[i][3];
            inv.setItem(modelSlots[i], createIcon(
                    mat != null ? mat : Material.SPAWNER,
                    title,
                    List.of(
                            "&7ID MythicMobs: &f" + mobId,
                            "&7" + desc,
                            "",
                            "&a▶ Clic para invocar modelo 3D aquí"
                    ),
                    "spawn_mythic_mob", mobId
            ));
        }

        // Limpiador de entidades/modelos cercanos
        inv.setItem(40, createIcon(
                Material.TNT,
                "&c&lLimpiar Entidades / Modelos Cercanos",
                List.of(
                        "&7Elimina todos los Mobs, NPCs y Modelos 3D",
                        "&7alrededor tuyo (sin afectar a jugadores).",
                        "",
                        "&e▶ Clic Izq: &fLimpiar en radio de 15 bloques",
                        "&c▶ Shift + Clic Izq: &fLimpiar en radio de 50 bloques"
                ),
                "clear_nearby_entities", "15"
        ));
    }

    // =========================================================================
    // PESTAÑA 3: ARSENAL RPG (ARMAS 8 ELEMENTOS, ARMADURAS, TALISMANES E ÍTEMS SAMUSDEV)
    // =========================================================================
    private void renderItemsTab(Inventory inv) {
        // Fila 1 (Slots 10..17): Armas con Parry de los 8 Elementos
        int[] elemSlots = {10, 11, 12, 13, 14, 15, 16, 19};
        ElementType[] elements = ElementType.values();
        for (int i = 0; i < elements.length && i < elemSlots.length; i++) {
            ElementType el = elements[i];
            inv.setItem(elemSlots[i], createIcon(
                    Material.NETHERITE_SWORD,
                    "&6&lHoja Ascendente: &e" + el.name(),
                    List.of(
                            "&7Arma configurada con &aParry Perfecto&7,",
                            "&7Bloqueo (65% mitigación) e imbuida en",
                            "&7el elemento &f" + el.name() + "&7.",
                            "",
                            "&a▶ Clic para recibir en tu inventario"
                    ),
                    "give_elemental_weapon", el.name()
            ));
        }

        // Slots 20..25: Corazas Inteligentes de las Clases y Talismanes
        String[][] classArmors = {
                {"vanguardia", "&6&lCoraza de Vanguardia (Terra)"},
                {"filo_sombra", "&5&lCoraza de Filo de Sombra (Umbra)"},
                {"arcanista", "&c&lCoraza de Arcanista (Ígneo)"},
                {"custodio_sylva", "&a&lCoraza de Custodio Sylva (Sylva)"}
        };
        int[] armorSlots = {21, 22, 23, 24};
        for (int i = 0; i < classArmors.length; i++) {
            inv.setItem(armorSlots[i], createIcon(
                    Material.NETHERITE_CHESTPLATE,
                    classArmors[i][1],
                    List.of(
                            "&7Armadura con Stats Base Universales",
                            "&7+ Pasiva exclusiva de la rama &f" + classArmors[i][0] + "&7.",
                            "",
                            "&a▶ Clic para recibir en tu inventario"
                    ),
                    "give_class_armor", classArmors[i][0]
            ));
        }

        // Fila 3 y 4 (Slots 28..34 y 37..43): Ítems Custom de SamusDev (MythicMobs)
        String[][] samusItems = {
                {"Coin", "&e&lMoneda de Bronce (SamusDev)", "GOLD_NUGGET"},
                {"Stacked_Coin", "&6&lPila de Monedas (SamusDev)", "RAW_GOLD"},
                {"Coin_Bundle", "&6&lSaco de Monedas (SamusDev)", "GOLD_BLOCK"},
                {"Bar_Copper", "&6&lLingote de Cobre 3D", "COPPER_INGOT"},
                {"Bar_Gold", "&e&lLingote de Oro RPG", "GOLD_INGOT"},
                {"Bar_Ruby", "&c&lLingote de Rubí", "REDSTONE"},
                {"Bar_Dragonite", "&d&lLingote de Dragonita", "AMETHYST_SHARD"},
                {"Key_Golden", "&e&lLlave Dorada de Cofre", "TRIPWIRE_HOOK"},
                {"Key_Shadow", "&5&lLlave Sombría de Cofre", "ECHO_SHARD"},
                {"Rune_Fire", "&c&lRuna de Fuego", "FIRE_CHARGE"},
                {"Rune_Shadow", "&5&lRuna de Sombra", "NETHER_STAR"},
                {"Potion_Eternal", "&d&lPoción Eterna", "DRAGON_BREATH"},
                {"Deathbloom", "&4&lFlor de la Muerte (Alquimia)", "WITHER_ROSE"},
                {"Scroll_Teleport", "&b&lPergamino de Teletransporte", "PAPER"}
        };
        int[] samusSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < samusItems.length && i < samusSlots.length; i++) {
            Material m = Material.matchMaterial(samusItems[i][2]);
            inv.setItem(samusSlots[i], createIcon(
                    m != null ? m : Material.FEATHER,
                    samusItems[i][1],
                    List.of(
                            "&7Ítem MythicMobs: &f" + samusItems[i][0],
                            "&7Con textura custom en AscendentResourcePack.",
                            "",
                            "&a▶ Clic Izq: &fRecibir x1",
                            "&e▶ Shift + Clic Izq: &fRecibir x16"
                    ),
                    "give_mythic_item", samusItems[i][0]
            ));
        }
    }

    // =========================================================================
    // PESTAÑA 4: CONTROL DEL SERVIDOR, MUNDO Y UTILIDADES DE STAFF
    // =========================================================================
    private void renderServerTab(Inventory inv, Player staff) {
        World world = staff.getWorld();
        boolean isVanished = vanishedStaff.contains(staff.getUniqueId());

        // Hora del Mundo
        inv.setItem(10, createIcon(Material.SUNFLOWER, "&e&lHora: Amanecer (06:00)", List.of("&7Fija la hora del mundo en 0 ticks."), "world_time", "0"));
        inv.setItem(11, createIcon(Material.GLOWSTONE, "&6&lHora: Mediodía (12:00)", List.of("&7Fija la hora del mundo en 6000 ticks."), "world_time", "6000"));
        inv.setItem(12, createIcon(Material.ORANGE_DYE, "&c&lHora: Atardecer (18:00)", List.of("&7Fija la hora del mundo en 12000 ticks."), "world_time", "12000"));
        inv.setItem(13, createIcon(Material.ENDER_EYE, "&9&lHora: Medianoche (00:00)", List.of("&7Fija la hora del mundo en 18000 ticks."), "world_time", "18000"));

        // Clima del Mundo
        inv.setItem(15, createIcon(Material.BUCKET, "&b&lClima: Despejado", List.of("&7Quita la lluvia y tormentas."), "world_weather", "clear"));
        inv.setItem(16, createIcon(Material.WATER_BUCKET, "&9&lClima: Lluvia / Tormenta", List.of("&a▶ Clic Izq: &fLluvia", "&e▶ Clic Der: &fTormenta Eléctrica"), "world_weather", "rain"));

        // Utilidades personales de Staff
        inv.setItem(19, createIcon(
                Material.GRASS_BLOCK,
                "&a&lTu Modo de Juego: &f" + staff.getGameMode().name(),
                List.of(
                        "&7Cambia rápidamente tu propio Gamemode.",
                        "",
                        "&a▶ Clic Izq: &fAlternar Creativo / Supervivencia",
                        "&e▶ Clic Der: &fModo Espectador"
                ),
                "self_gamemode", "toggle"
        ));

        inv.setItem(20, createIcon(
                isVanished ? Material.LIME_DYE : Material.GRAY_DYE,
                "&d&lModo Invisible (Staff Vanish): " + (isVanished ? "&aACTIVO" : "&cINACTIVO"),
                List.of(
                        "&7Te oculta completamente de los jugadores",
                        "&7que no sean Staff y te da Visión Nocturna.",
                        "",
                        "&e▶ Clic para Conmutar Vanish"
                ),
                "toggle_vanish", "self"
        ));

        float flySpeed = staff.getFlySpeed();
        inv.setItem(21, createIcon(
                Material.FEATHER,
                "&b&lVelocidad de Vuelo: &f" + String.format("x%.1f", flySpeed * 10.0f),
                List.of(
                        "&7Cambia tu velocidad de vuelo e incluye",
                        "&7activación de vuelo incluso en Survival.",
                        "",
                        "&e▶ Clic para rotar (x1.0 -> x2.0 -> x4.0 -> x8.0)"
                ),
                "cycle_fly_speed", "self"
        ));

        inv.setItem(22, createIcon(
                Material.BEACON,
                "&6&lFijar Punto de Spawn del Mundo Aquí",
                List.of(
                        "&7Establece el Spawn de &f" + world.getName(),
                        "&7en tu ubicación actual.",
                        "",
                        "&e▶ Clic para fijar World Spawn"
                ),
                "set_world_spawn", "here"
        ));

        inv.setItem(23, createIcon(
                Bukkit.hasWhitelist() ? Material.REDSTONE_TORCH : Material.LEVER,
                "&c&lModo Mantenimiento (Whitelist): " + (Bukkit.hasWhitelist() ? "&cACTIVO" : "&aABIERTO"),
                List.of(
                        "&7Activa o desactiva la Whitelist del servidor.",
                        "",
                        "&e▶ Clic para Conmutar Whitelist"
                ),
                "toggle_whitelist", "server"
        ));

        // Recarga de Módulos Ascendent + MythicMobs + ModelEngine
        inv.setItem(25, createIcon(
                Material.NETHER_STAR,
                "&a&lRecarga Global de Módulos (Hot Reload)",
                List.of(
                        "&7Recarga la configuración y traducciones de:",
                        "&8- &6AscendentCore",
                        "&8- &cAscendentCombat",
                        "&8- &bAscendentClasses",
                        "&8- &dAscendentAdmin",
                        "",
                        "&a▶ Clic Izq: &fRecargar todos los módulos Ascendent",
                        "&e▶ Shift + Clic Izq: &fRecargar también MythicMobs y ModelEngine"
                ),
                "reload_all_modules", "ascendent"
        ));
    }

    // =========================================================================
    // MANEJADOR DE CLICS EN EL PANEL DE STAFF
    // =========================================================================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player staff)) return;

        if (!plugin.hasStaffAccess(staff)) {
            staff.closeInventory();
            plugin.getCore().getLang().send(staff, "admin.no-permission");
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        String payload = meta.getPersistentDataContainer().get(keyPayload, PersistentDataType.STRING);
        if (action == null || payload == null) return;

        ClickType click = event.getClick();

        switch (action) {
            case "switch_tab" -> {
                Tab nextTab = Tab.valueOf(payload);
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                openTab(staff, nextTab);
            }
            case "select_player" -> {
                UUID targetUuid = UUID.fromString(payload);
                Player target = Bukkit.getPlayer(targetUuid);
                if (target == null) {
                    plugin.getCore().getLang().send(staff, "admin.player-not-found");
                    openTab(staff, Tab.PLAYERS);
                    return;
                }
                if (click.isRightClick()) {
                    staff.closeInventory();
                    staff.teleport(target.getLocation());
                    staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.1f);
                } else {
                    staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    openPlayerEditor(staff, targetUuid);
                }
            }
            case "edit_global_level" -> handleEditGlobalLevel(staff, UUID.fromString(payload), click);
            case "edit_free_points" -> handleEditFreePoints(staff, UUID.fromString(payload), click);
            case "edit_coins" -> handleEditCoins(staff, UUID.fromString(payload), click);
            case "edit_talisman_slots" -> handleEditTalismanSlots(staff, UUID.fromString(payload), click);
            case "cycle_player_class" -> handleCyclePlayerClass(staff, UUID.fromString(payload), click);
            case "edit_class_progress" -> handleEditClassProgress(staff, UUID.fromString(payload), click);
            case "heal_or_revive_player" -> handleHealOrRevive(staff, UUID.fromString(payload), click);
            case "edit_single_stat" -> handleEditSingleStat(staff, payload, click);
            case "give_player_class_kit" -> handleGivePlayerClassKit(staff, UUID.fromString(payload));
            case "give_player_max_talismans" -> handleGivePlayerMaxTalismans(staff, UUID.fromString(payload));
            case "inspect_inventory" -> {
                Player target = Bukkit.getPlayer(UUID.fromString(payload));
                if (target != null) {
                    staff.playSound(staff.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
                    if (click.isRightClick()) {
                        staff.openInventory(target.getEnderChest());
                    } else {
                        staff.openInventory(target.getInventory());
                    }
                }
            }
            case "teleport_player" -> {
                Player target = Bukkit.getPlayer(UUID.fromString(payload));
                if (target != null) {
                    if (click.isRightClick()) {
                        target.teleport(staff.getLocation());
                        staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    } else {
                        staff.closeInventory();
                        staff.teleport(target.getLocation());
                        staff.playSound(staff.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    }
                }
            }
            case "cycle_player_gamemode" -> {
                Player target = Bukkit.getPlayer(UUID.fromString(payload));
                if (target != null) {
                    GameMode next = switch (target.getGameMode()) {
                        case SURVIVAL -> GameMode.CREATIVE;
                        case CREATIVE -> GameMode.ADVENTURE;
                        case ADVENTURE -> GameMode.SPECTATOR;
                        case SPECTATOR -> GameMode.SURVIVAL;
                    };
                    target.setGameMode(next);
                    staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
                    openPlayerEditor(staff, target.getUniqueId());
                }
            }
            case "toggle_freeze_player" -> {
                UUID targetUuid = UUID.fromString(payload);
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null) {
                    if (frozenPlayers.contains(targetUuid)) {
                        frozenPlayers.remove(targetUuid);
                        plugin.getCore().getLang().send(target, "admin.unfrozen-alert");
                        plugin.getCore().getLang().send(staff, "admin.player-frozen", LangManager.placeholders("player", target.getName(), "state", "&aDESCONGELADO"));
                    } else {
                        frozenPlayers.add(targetUuid);
                        plugin.getCore().getLang().send(target, "admin.frozen-alert");
                        plugin.getCore().getLang().send(staff, "admin.player-frozen", LangManager.placeholders("player", target.getName(), "state", "&cCONGELADO"));
                    }
                    staff.playSound(staff.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.9f, 1.1f);
                    openPlayerEditor(staff, targetUuid);
                }
            }
            case "kick_player" -> {
                if (click.isShiftClick()) {
                    Player target = Bukkit.getPlayer(UUID.fromString(payload));
                    if (target != null && !target.equals(staff)) {
                        target.kick(net.kyori.adventure.text.Component.text(LangManager.colorize("&cExpulsado por un Administrador de The Last Ascension.")));
                        openTab(staff, Tab.PLAYERS);
                    }
                }
            }
            case "spawn_combat_dummy" -> {
                boolean unparryable = "unparryable".equalsIgnoreCase(payload);
                spawnCombatDummy(staff, unparryable);
            }
            case "spawn_mythic_mob" -> {
                staff.closeInventory();
                staff.performCommand("mm m spawn " + payload);
                plugin.getCore().getLang().send(staff, "admin.entity-spawned", LangManager.placeholders("entity", payload));
            }
            case "clear_nearby_entities" -> {
                int radius = click.isShiftClick() ? 50 : 15;
                int removed = 0;
                for (Entity ent : staff.getNearbyEntities(radius, radius, radius)) {
                    if (!(ent instanceof Player)) {
                        ent.remove();
                        removed++;
                    }
                }
                staff.playSound(staff.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
                plugin.getCore().getLang().send(staff, "admin.entities-cleared", LangManager.placeholders("count", String.valueOf(removed), "radius", String.valueOf(radius)));
            }
            case "give_elemental_weapon" -> {
                ElementType elem = ElementType.valueOf(payload);
                AscendentCombat combat = plugin.getCombatPlugin();
                if (combat != null) {
                    ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
                    ItemMeta sm = sword.getItemMeta();
                    if (sm != null) {
                        sm.setDisplayName(LangManager.colorize("&6&lHoja Ascendente de " + elem.name()));
                        sword.setItemMeta(sm);
                    }
                    combat.getWeaponTags().setCanBlock(sword, true);
                    combat.getWeaponTags().setCanParry(sword, true);
                    combat.getWeaponTags().setWeaponElement(sword, elem);
                    staff.getInventory().addItem(sword);
                    staff.playSound(staff.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                    plugin.getCore().getLang().send(staff, "admin.item-given", LangManager.placeholders("item", "Hoja Ascendente (" + elem.name() + ")"));
                }
            }
            case "give_class_armor" -> {
                AscendentClasses classes = plugin.getClassesPlugin();
                if (classes != null) {
                    RpgClassDefinition def = classes.getRegistry().getClass(payload);
                    if (def != null) {
                        ItemStack armor = classes.getClassManager().createClassArmorPiece(def);
                        staff.getInventory().addItem(armor);
                        staff.playSound(staff.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.1f);
                        plugin.getCore().getLang().send(staff, "admin.item-given", LangManager.placeholders("item", "Coraza de " + def.nameEs()));
                    }
                }
            }
            case "give_mythic_item" -> {
                int amount = click.isShiftClick() ? 16 : 1;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm i give " + staff.getName() + " " + payload + " " + amount);
                staff.playSound(staff.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                plugin.getCore().getLang().send(staff, "admin.item-given", LangManager.placeholders("item", payload + " x" + amount));
            }
            case "world_time" -> {
                long ticks = Long.parseLong(payload);
                staff.getWorld().setTime(ticks);
                staff.playSound(staff.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.3f);
                plugin.getCore().getLang().send(staff, "admin.world-updated", LangManager.placeholders("detail", "Hora fijada en " + ticks + " ticks"));
            }
            case "world_weather" -> {
                World w = staff.getWorld();
                if ("clear".equalsIgnoreCase(payload)) {
                    w.setStorm(false);
                    w.setThundering(false);
                    plugin.getCore().getLang().send(staff, "admin.world-updated", LangManager.placeholders("detail", "Clima Despejado"));
                } else {
                    w.setStorm(true);
                    w.setThundering(click.isRightClick());
                    plugin.getCore().getLang().send(staff, "admin.world-updated", LangManager.placeholders("detail", click.isRightClick() ? "Tormenta Eléctrica" : "Lluvia"));
                }
                staff.playSound(staff.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.2f);
            }
            case "self_gamemode" -> {
                if (click.isRightClick()) {
                    staff.setGameMode(GameMode.SPECTATOR);
                } else {
                    staff.setGameMode(staff.getGameMode() == GameMode.CREATIVE ? GameMode.SURVIVAL : GameMode.CREATIVE);
                }
                staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
                openTab(staff, Tab.SERVER);
            }
            case "toggle_vanish" -> {
                toggleVanish(staff);
                openTab(staff, Tab.SERVER);
            }
            case "cycle_fly_speed" -> {
                staff.setAllowFlight(true);
                float cur = staff.getFlySpeed();
                float next = cur < 0.15f ? 0.2f : (cur < 0.35f ? 0.4f : (cur < 0.7f ? 0.8f : 0.1f));
                staff.setFlySpeed(next);
                staff.playSound(staff.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.9f, 1.3f);
                openTab(staff, Tab.SERVER);
            }
            case "set_world_spawn" -> {
                staff.getWorld().setSpawnLocation(staff.getLocation());
                staff.playSound(staff.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.2f);
                plugin.getCore().getLang().send(staff, "admin.world-updated", LangManager.placeholders("detail", "Spawn del mundo actualizado"));
            }
            case "toggle_whitelist" -> {
                Bukkit.setWhitelist(!Bukkit.hasWhitelist());
                staff.playSound(staff.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.9f, 1.1f);
                openTab(staff, Tab.SERVER);
            }
            case "reload_all_modules" -> {
                plugin.getCore().reloadConfig();
                plugin.getCore().getModuleRegistry().reloadAllModules();
                if (click.isShiftClick()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mm reload");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "meg reload");
                }
                staff.playSound(staff.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
                plugin.getCore().getLang().send(staff, "admin.modules-reloaded");
            }
        }
    }

    // =========================================================================
    // ACCIONES DE EDICIÓN PROFUNDA DE JUGADOR
    // =========================================================================
    private void handleEditGlobalLevel(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        if (click == ClickType.SHIFT_RIGHT) {
            profile.setGlobalLevel(1);
        } else if (click.isRightClick()) {
            profile.setGlobalLevel(Math.max(1, profile.getGlobalLevel() - 1));
        } else if (click == ClickType.SHIFT_LEFT) {
            profile.setGlobalLevel(profile.getGlobalLevel() + 10);
            profile.setFreeStatPoints(profile.getFreeStatPoints() + 30);
        } else {
            profile.setGlobalLevel(profile.getGlobalLevel() + 1);
            profile.setFreeStatPoints(profile.getFreeStatPoints() + 3);
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleEditFreePoints(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        if (click == ClickType.SHIFT_RIGHT) {
            int refunded = 0;
            for (StatType st : StatType.values()) {
                refunded += profile.getInvestedPoints(st);
                profile.setInvestedPoints(st, 0);
            }
            profile.setFreeStatPoints(profile.getFreeStatPoints() + refunded);
        } else if (click.isRightClick()) {
            profile.setFreeStatPoints(Math.max(0, profile.getFreeStatPoints() - 5));
        } else if (click == ClickType.SHIFT_LEFT) {
            profile.setFreeStatPoints(profile.getFreeStatPoints() + 50);
        } else {
            profile.setFreeStatPoints(profile.getFreeStatPoints() + 5);
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleEditCoins(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        if (click == ClickType.SHIFT_RIGHT) {
            profile.setCoins(0L);
        } else if (click.isRightClick()) {
            profile.addCoins(-500L);
        } else if (click == ClickType.SHIFT_LEFT) {
            profile.addCoins(10000L);
        } else {
            profile.addCoins(500L);
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleEditTalismanSlots(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        if (click == ClickType.SHIFT_LEFT) {
            profile.setUnlockedTalismanSlots(4);
        } else if (click.isRightClick()) {
            profile.setUnlockedTalismanSlots(Math.max(1, profile.getUnlockedTalismanSlots() - 1));
        } else {
            profile.setUnlockedTalismanSlots(Math.min(4, profile.getUnlockedTalismanSlots() + 1));
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleCyclePlayerClass(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        AscendentClasses classes = plugin.getClassesPlugin();
        if (target == null || profile == null || classes == null) return;

        if (click.isShiftClick()) {
            profile.setActiveClassId("none");
        } else {
            List<RpgClassDefinition> all = new ArrayList<>(classes.getRegistry().getAllClasses());
            if (all.isEmpty()) return;
            int idx = -1;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equalsIgnoreCase(profile.getActiveClassId())) {
                    idx = i;
                    break;
                }
            }
            int nextIdx = click.isRightClick()
                    ? (idx <= 0 ? all.size() - 1 : idx - 1)
                    : (idx + 1) % all.size();
            profile.setActiveClassId(all.get(nextIdx).id());
            classes.getClassManager().getOrCreateClassProgress(targetUuid, all.get(nextIdx).id());
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleEditClassProgress(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        AscendentClasses classes = plugin.getClassesPlugin();
        if (target == null || profile == null || classes == null) return;

        RpgClassDefinition active = classes.getRegistry().getClass(profile.getActiveClassId());
        if (active == null) return;
        PlayerClassProgress prog = classes.getClassManager().getOrCreateClassProgress(targetUuid, active.id());

        if (click == ClickType.SHIFT_RIGHT) {
            for (ClassSkill sk : active.skills()) {
                prog.setSkillLevel(sk.id(), 5);
            }
        } else if (click.isRightClick()) {
            prog.setClassLevel(Math.max(1, prog.getClassLevel() - 1));
        } else if (click == ClickType.SHIFT_LEFT) {
            prog.setClassLevel(prog.getClassLevel() + 10);
            prog.setFreeSkillPoints(prog.getFreeSkillPoints() + 20);
        } else {
            prog.setClassLevel(prog.getClassLevel() + 1);
            prog.setFreeSkillPoints(prog.getFreeSkillPoints() + 2);
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.9f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleHealOrRevive(Player staff, UUID targetUuid, ClickType click) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        AscendentCombat combat = plugin.getCombatPlugin();
        if (click.isRightClick() && combat != null) {
            if (profile.isDowned()) {
                combat.getDownedReviveManager().revivePlayer(target, profile, staff);
            } else {
                combat.getDownedReviveManager().enterDownedState(target, profile);
            }
        } else {
            if (profile.isDowned() && combat != null) {
                combat.getDownedReviveManager().revivePlayer(target, profile, staff);
            }
            profile.setCurrentStun(0.0);
            profile.setStunned(false);
            target.setHealth(getMaxHealth(target));
            target.setFoodLevel(20);
            target.setSaturation(20f);
            target.setFireTicks(0);
            plugin.getCore().getLang().send(staff, "admin.player-healed", LangManager.placeholders("player", target.getName()));
        }
        staff.playSound(staff.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleEditSingleStat(Player staff, String payload, ClickType click) {
        String[] parts = payload.split(":");
        if (parts.length != 2) return;
        UUID targetUuid = UUID.fromString(parts[0]);
        StatType stat = StatType.valueOf(parts[1]);

        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        if (target == null || profile == null) return;

        int cur = profile.getInvestedPoints(stat);
        if (click == ClickType.SHIFT_RIGHT) {
            profile.setInvestedPoints(stat, 0);
        } else if (click.isRightClick()) {
            profile.setInvestedPoints(stat, Math.max(0, cur - 1));
        } else if (click == ClickType.SHIFT_LEFT) {
            profile.setInvestedPoints(stat, cur + 5);
        } else {
            profile.setInvestedPoints(stat, cur + 1);
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        openPlayerEditor(staff, targetUuid);
    }

    private void handleGivePlayerClassKit(Player staff, UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        AscendentClasses classes = plugin.getClassesPlugin();
        AscendentCombat combat = plugin.getCombatPlugin();
        if (target == null || profile == null || classes == null) return;

        RpgClassDefinition def = classes.getRegistry().getClass(profile.getActiveClassId());
        if (def == null) {
            def = classes.getRegistry().getClass("vanguardia");
        }
        if (def != null) {
            target.getInventory().addItem(classes.getClassManager().createClassArmorPiece(def));
            if (combat != null) {
                ItemStack weapon = new ItemStack(Material.NETHERITE_SWORD);
                ItemMeta wm = weapon.getItemMeta();
                if (wm != null) {
                    wm.setDisplayName(LangManager.colorize("&6&lArma Ascendente de " + def.nameEs()));
                    weapon.setItemMeta(wm);
                }
                combat.getWeaponTags().setCanBlock(weapon, true);
                combat.getWeaponTags().setCanParry(weapon, true);
                combat.getWeaponTags().setWeaponElement(weapon, def.primaryElement());
                target.getInventory().addItem(weapon);
            }
            staff.playSound(staff.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
            plugin.getCore().getLang().send(staff, "admin.player-updated", LangManager.placeholders("player", target.getName(), "detail", "Kit de " + def.nameEs() + " entregado"));
        }
    }

    private void handleGivePlayerMaxTalismans(Player staff, UUID targetUuid) {
        Player target = Bukkit.getPlayer(targetUuid);
        PlayerProfile profile = plugin.getCore().getProfileManager().getCachedProfile(targetUuid);
        AscendentClasses classes = plugin.getClassesPlugin();
        if (target == null || profile == null || classes == null) return;

        profile.setUnlockedTalismanSlots(4);
        List<String> eq = classes.getClassManager().getEquippedTalismans(targetUuid);
        eq.clear();
        for (TalismanDefinition tal : classes.getRegistry().getAllTalismans()) {
            if (eq.size() < 4) {
                eq.add(tal.id());
            }
        }
        saveAndRecalculate(target, profile);
        staff.playSound(staff.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1.0f, 1.3f);
        openPlayerEditor(staff, targetUuid);
    }

    private void saveAndRecalculate(Player target, PlayerProfile profile) {
        AscendentClasses classes = plugin.getClassesPlugin();
        if (classes != null) {
            classes.getClassManager().recalculateAndApplyStats(target);
            plugin.getCore().getDatabaseManager().runAsync(() -> classes.getClassManager().savePlayerDataSync(target.getUniqueId()));
        }
        plugin.getCore().getDatabaseManager().runAsync(() -> plugin.getCore().getProfileManager().saveToDatabaseSync(profile));
    }

    private void spawnCombatDummy(Player staff, boolean unparryable) {
        Location loc = staff.getLocation().add(staff.getLocation().getDirection().normalize().multiply(2.5));
        loc.setY(staff.getLocation().getY());
        Zombie dummy = staff.getWorld().spawn(loc, Zombie.class, z -> {
            z.setAdult();
            z.setShouldBurnInDay(false);
            z.setCustomNameVisible(true);
            z.setCustomName(LangManager.colorize(unparryable
                    ? "&c&l[Dummy Imparable] &7(Solo Bloqueo)"
                    : "&a&l[Dummy de Parry] &7(Parry con Clic Der.)"));
            AttributeInstance maxHp = z.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(500.0);
            z.setHealth(500.0);
            AscendentCombat combat = plugin.getCombatPlugin();
            if (combat != null) {
                combat.getWeaponTags().setAttackParryable(z, !unparryable);
            }
        });
        staff.playSound(staff.getLocation(), Sound.ENTITY_ARMOR_STAND_PLACE, 1.0f, 1.1f);
        plugin.getCore().getLang().send(staff, "admin.entity-spawned", LangManager.placeholders("entity", dummy.getName()));
    }

    private void toggleVanish(Player staff) {
        UUID id = staff.getUniqueId();
        if (vanishedStaff.contains(id)) {
            vanishedStaff.remove(id);
            staff.removePotionEffect(PotionEffectType.NIGHT_VISION);
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, staff);
            }
            plugin.getCore().getLang().send(staff, "admin.vanish-toggled", LangManager.placeholders("state", "&cDESACTIVADO (Visible)"));
        } else {
            vanishedStaff.add(id);
            staff.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, true));
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!plugin.hasStaffAccess(other)) {
                    other.hidePlayer(plugin, staff);
                }
            }
            plugin.getCore().getLang().send(staff, "admin.vanish-toggled", LangManager.placeholders("state", "&aACTIVADO (Invisible)"));
        }
        staff.playSound(staff.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.9f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!frozenPlayers.contains(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            Location lock = event.getFrom().clone();
            lock.setYaw(event.getTo().getYaw());
            lock.setPitch(event.getTo().getPitch());
            event.setTo(lock);
        }
    }

    @EventHandler
    public void onPlayerJoinHideVanished(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (plugin.hasStaffAccess(joined)) return;
        for (UUID vId : vanishedStaff) {
            Player staff = Bukkit.getPlayer(vId);
            if (staff != null) {
                joined.hidePlayer(plugin, staff);
            }
        }
    }

    private Material getStatIcon(StatType stat) {
        return switch (stat) {
            case MAX_HEALTH -> Material.APPLE;
            case DEFENSE -> Material.SHIELD;
            case PHYSICAL_DAMAGE -> Material.IRON_SWORD;
            case MAGIC_DAMAGE -> Material.BLAZE_POWDER;
            case CRIT_CHANCE -> Material.ARROW;
            case CRIT_DAMAGE -> Material.SPECTRAL_ARROW;
            case ATTACK_SPEED -> Material.SUGAR;
            case MOVE_SPEED -> Material.LEATHER_BOOTS;
            case STUN_DAMAGE -> Material.MACE;
            case MAX_STUN -> Material.ANVIL;
        };
    }

    private double getMaxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    private ItemStack createIcon(Material material, String name, List<String> loreLines, String action, String payload) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LangManager.colorize(name));
            if (!loreLines.isEmpty()) {
                meta.setLore(loreLines.stream().map(LangManager::colorize).toList());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (action != null && payload != null) {
                meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
                meta.getPersistentDataContainer().set(keyPayload, PersistentDataType.STRING, payload);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
