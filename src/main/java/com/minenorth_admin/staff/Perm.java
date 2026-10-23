package com.minenorth_admin.staff;

/** Permissions du panneau. Un rôle en accorde une liste ; le propriétaire (op 4) les a toutes. */
public enum Perm {
    PANEL("Accès au panneau"),
    BANK_VIEW("Banque : voir"),
    BANK_EDIT("Banque : modifier"),
    PERMIS_VIEW("Permis : voir"),
    PERMIS_EDIT("Permis : modifier"),
    GARAGE_VIEW("Garage : voir"),
    GARAGE_EDIT("Garage : modifier"),
    INV_VIEW("Inventaire : voir"),
    INV_EDIT("Inventaire : modifier"),
    TELEPORT("Téléportation"),
    POLICE_VIEW("Police : voir"),
    POLICE_EDIT("Police : ajouter / retirer / grades"),
    SECOURS_VIEW("Pompiers : voir"),
    SECOURS_EDIT("Pompiers : nommer / grades"),
    GRADE_VIEW("Grades VIP : voir"),
    GRADE_EDIT("Grades VIP : donner / retirer"),
    ETAT_VIEW("État : voir"),
    ETAT_EDIT("État : impôt / maire / élections"),
    ENTREPRISES("Entreprises : menu admin"),
    LOGS("Journal"),
    STAFF("Gérer le staff"),
    DELETE("Supprimer un joueur"),
    MAP_PLAYERS("Carte : voir joueurs"),
    STAFF_MODE("Mode staff (vanish + créatif)"),
    MODERATE("Sanctions : kick / ban"),
    PROPRIETES_VIEW("Propriétés : voir"),
    PROPRIETES_EDIT("Propriétés : attribuer / vendre / libérer"),
    VOL_ADMIN("Vol : réglages / braquages / dossiers"),
    SECOURS_ADMIN("Secours : réglages / incendies / réveil"),
    GANGS_VIEW("Gangs : voir"),
    GANGS_EDIT("Gangs : gérer (membres, comptes, zones, catalogue)");

    public final String label;

    Perm(String label) {
        this.label = label;
    }

    public static Perm byName(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
