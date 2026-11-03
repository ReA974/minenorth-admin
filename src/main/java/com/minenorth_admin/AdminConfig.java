package com.minenorth_admin;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuration serveur : world/serverconfig/minenorth_admin-server.toml */
public final class AdminConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue OWNER_OP_LEVEL;
    public static final ForgeConfigSpec.IntValue MAX_LOGS;
    public static final ForgeConfigSpec.BooleanValue LOG_FILE;
    public static final ForgeConfigSpec.BooleanValue NOTIFY_TARGET;
    public static final ForgeConfigSpec.BooleanValue TAG_ON;
    public static final ForgeConfigSpec.ConfigValue<String> TAG_FORMAT;
    public static final ForgeConfigSpec.ConfigValue<String> TAG_COLOR;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> TAG_ROLES;
    public static final ForgeConfigSpec.BooleanValue TAG_KEEP_VIP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("acces");
        OWNER_OP_LEVEL = b.comment("Niveau d'op qui donne TOUS les droits du panneau (propriétaire), sans rôle.",
                        "Les autres joueurs n'ont accès qu'avec un rôle attribué dans l'onglet Staff.")
                .defineInRange("niveauOpProprietaire", 4, 1, 4);
        b.pop();
        b.push("journal");
        MAX_LOGS = b.comment("Nombre d'actions gardées dans le journal du panneau.")
                .defineInRange("entreesMax", 2000, 100, 20000);
        LOG_FILE = b.comment("Écrire aussi chaque action dans logs/minenorth_admin.log (dossier du serveur).")
                .define("fichier", true);
        b.pop();
        b.push("joueurs");
        NOTIFY_TARGET = b.comment("Prévenir le joueur concerné (message discret) quand le staff modifie sa banque ou ses permis.")
                .define("prevenirJoueur", true);
        b.pop();
        b.push("modeStaff");
        TAG_ON = b.comment("En mode staff, afficher le rôle devant le nom dans le chat et la liste des joueurs (TAB). Hors mode staff : nom RP normal.")
                .define("afficherRole", true);
        TAG_FORMAT = b.comment("Forme de l'étiquette ; %s = libellé du rôle.")
                .define("format", "[%s]");
        TAG_COLOR = b.comment("Couleur des rôles absents de la liste ci-dessous : #RRGGBB ou nom Minecraft (red, gold, aqua...).")
                .define("couleurParDefaut", "#FF5555");
        TAG_ROLES = b.comment("Un rôle par ligne : \"Nom du rôle dans l'onglet Staff;Libellé affiché;Couleur\" (libellé vide = nom du rôle).",
                        "\"Propriétaire\" = op sans rôle attribué ; \"Staff\" = membre dont le rôle a été supprimé.")
                .defineListAllowEmpty(java.util.List.of("roles"), () -> java.util.List.of(
                        "Propriétaire;Fondateur;#AA0000",
                        "Gérant;Gérant;#AA00AA",
                        "Admin;Admin;#FF5555",
                        "Modérateur;Modo;#55FFFF",
                        "Helper;Helper;#55FF55"), o -> o instanceof String);
        TAG_KEEP_VIP = b.comment("Garder le préfixe VIP / VIP+ après l'étiquette du rôle (faux : le rôle remplace le préfixe VIP).")
                .define("garderPrefixeVip", false);
        b.pop();
        SPEC = b.build();
    }

    private AdminConfig() {}
}
