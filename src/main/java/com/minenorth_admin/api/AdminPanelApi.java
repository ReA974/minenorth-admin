package com.minenorth_admin.api;

import com.minenorth_admin.server.AdminService;
import net.minecraft.server.level.ServerPlayer;

/**
 * API pour les autres mods MineNorth (appelée par réflexion : ne pas changer les noms ni les paramètres).
 * Ouvre le panneau admin principal sur un onglet (« vol » ou « sec ») ; les droits du staff sont vérifiés ici.
 */
public final class AdminPanelApi {
    private AdminPanelApi() {}

    /** Renvoie toujours vrai : le panneau a pris en charge la demande (ouverture ou message de refus). */
    public static boolean open(ServerPlayer player, String tab, int page) {
        return AdminService.openTab(player, tab, page);
    }
}
