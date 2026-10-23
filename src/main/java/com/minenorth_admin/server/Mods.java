package com.minenorth_admin.server;

import net.minecraftforge.fml.ModList;

/** Mods MineNorth présents : chaque onglet du panneau n'existe que si son mod est installé. */
public final class Mods {
    private Mods() {}

    public static boolean bank() {
        return ModList.get().isLoaded("minenorth_eurobank");
    }

    public static boolean permis() {
        return ModList.get().isLoaded("minenorth_permis");
    }

    public static boolean garage() {
        return ModList.get().isLoaded("minenorth_rp_vehicles");
    }

    public static boolean police() {
        return ModList.get().isLoaded("minenorthpolice");
    }

    public static boolean etat() {
        return ModList.get().isLoaded("minenorthetat");
    }

    public static boolean entreprises() {
        return ModList.get().isLoaded("minenorthentreprises");
    }

    public static boolean grades() {
        return ModList.get().isLoaded("minenorthvip");
    }

    public static boolean secours() {
        return ModList.get().isLoaded("minenorthsecours");
    }

    public static boolean vol() {
        return ModList.get().isLoaded("minenorthvol");
    }

    /** Gestion admin des gangs : le mod Portes doit être là ET assez récent pour offrir l'API des gangs. */
    public static boolean gangs() {
        if (!ModList.get().isLoaded("minenorthportes")) return false;
        try {
            Class.forName("fr.minenorth.portes.api.GangAdminApi", false, Mods.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean portes() {
        return ModList.get().isLoaded("minenorthportes");
    }
}
