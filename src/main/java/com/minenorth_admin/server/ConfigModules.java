package com.minenorth_admin.server;

import com.minenorth_admin.AuditLog;
import com.minenorth_admin.net.Net;
import com.minenorth_admin.net.Net.Req;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Onglets « Vol » et « Secours » : réglages en direct et gestion des données propres à ces mods.
 * Passe par fr.minenorth.vol.VolAdminApi et fr.minenorth.secours.api.SecoursAdminApi en réflexion : le panneau compile et démarre
 * sans ces mods. Le droit (VOL_ADMIN / SECOURS_ADMIN) est vérifié par AdminService ; les valeurs sont bornées par les mods eux-mêmes.
 */
final class ConfigModules {
    private ConfigModules() {}

    private static final String VOL = "fr.minenorth.vol.VolAdminApi";
    private static final String SEC = "fr.minenorth.secours.api.SecoursAdminApi";
    private static final Pattern STORE_NAME = Pattern.compile("[\\p{L}0-9 _'-]{1,24}");
    private static final Pattern SITE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,24}");

    private static Object call(String cls, String method, Class<?>[] types, Object... args) {
        try {
            Method m = Class.forName(cls).getMethod(method, types);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("API indisponible (" + cls + "." + method + ") : mets à jour le mod concerné.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> lines(String cls, String method, Class<?>[] types, Object... args) {
        Object r = call(cls, method, types, args);
        return r instanceof List<?> l ? (List<String>) l : new ArrayList<>();
    }

    private static final Class<?>[] NONE = new Class<?>[0];
    private static final Class<?>[] SRV = {MinecraftServer.class};
    private static final Class<?>[] SRV_STR = {MinecraftServer.class, String.class};

    private static double number(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** « clé \t groupe \t libellé \t type \t valeur \t min \t max \t pas » -> NBT. */
    private static ListTag settings(List<String> ls) {
        ListTag out = new ListTag();
        for (String l : ls) {
            String[] p = l.split("\t");
            if (p.length < 8) continue;
            CompoundTag t = new CompoundTag();
            t.putString("K", p[0]);
            t.putString("G", p[1]);
            t.putString("L", p[2]);
            t.putString("T", p[3]);
            t.putDouble("V", number(p[4]));
            t.putDouble("Mn", number(p[5]));
            t.putDouble("Mx", number(p[6]));
            t.putDouble("S", number(p[7]));
            out.add(t);
        }
        return out;
    }

    private static ListTag rows(List<String> ls, String... names) {
        ListTag out = new ListTag();
        for (String l : ls) {
            String[] p = l.split("\t", -1);
            CompoundTag t = new CompoundTag();
            for (int i = 0; i < names.length && i < p.length; i++) t.putString(names[i], p[i]);
            out.add(t);
        }
        return out;
    }

    // ================================================================== vol

    static void vol(ServerPlayer actor, Req m) {
        MinecraftServer s = actor.server;
        String msg = "";
        boolean ok = true;
        String name = actor.getGameProfile().getName();
        switch (m.action()) {
            case "vol.set" -> {
                msg = (String) call(VOL, "set", new Class<?>[]{String.class, String.class, double.class}, name, m.a(), number(m.b()));
                AuditLog.log(actor, "", "vol", "règle : " + msg);
            }
            case "vol.close" -> {
                try {
                    msg = (String) call(VOL, "closeCase", new Class<?>[]{MinecraftServer.class, String.class, UUID.class}, s, name, UUID.fromString(m.a()));
                    AuditLog.log(actor, "", "vol", "clôt un dossier de vol : " + msg);
                } catch (IllegalArgumentException e) {
                    msg = "Dossier introuvable.";
                    ok = false;
                }
            }
            case "vol.store.add" -> {
                String n = m.a().trim();
                if (!STORE_NAME.matcher(n).matches()) {
                    msg = "Nom invalide (1 à 24 lettres, chiffres, espaces).";
                    ok = false;
                } else {
                    msg = (String) call(VOL, "storeAdd", new Class<?>[]{ServerPlayer.class, String.class, boolean.class}, actor, n, m.n() == 1);
                    AuditLog.log(actor, "", "vol", "magasin braquable « " + n + " » : " + msg);
                }
            }
            case "vol.store.remove", "vol.store.unlock", "vol.store.test" -> {
                String method = m.action().equals("vol.store.remove") ? "storeRemove" : m.action().equals("vol.store.unlock") ? "storeUnlock" : "storeTest";
                msg = (String) call(VOL, method, SRV_STR, s, m.a());
                if (!method.equals("storeTest")) AuditLog.log(actor, "", "vol", msg + " (" + m.a() + ")");
            }
            case "vol.weapon" -> {
                msg = (String) call(VOL, "weapon", new Class<?>[]{ServerPlayer.class, boolean.class}, actor, m.a().equals("add"));
                AuditLog.log(actor, "", "vol", "armes de braquage : " + msg);
            }
            default -> {}
        }
        if (msg.startsWith("Impossible") || msg.contains("invalide") || msg.contains("inconnu") || msg.contains("introuvable")) ok = false;
        CompoundTag t = new CompoundTag();
        t.put("Settings", settings(lines(VOL, "settings", NONE)));
        t.put("Cases", rows(lines(VOL, "cases", SRV, s), "Id", "Model", "Plate", "Declared", "X", "Z", "Ago", "Extra"));
        t.put("Stores", rows(lines(VOL, "stores", SRV, s), "Name", "Station", "X", "Y", "Z", "Lock"));
        t.putString("Aim", (String) call(VOL, "aim", new Class<?>[]{ServerPlayer.class}, actor));
        ListTag w = new ListTag();
        for (String a : lines(VOL, "weapons", NONE)) w.add(StringTag.valueOf(a));
        t.put("Weapons", w);
        Net.send(actor, new Net.View("vol", t, msg, ok));
    }

    // ================================================================== secours

    static void secours(ServerPlayer actor, Req m) {
        MinecraftServer s = actor.server;
        String msg = "";
        boolean ok = true;
        switch (m.action()) {
            case "sec.set" -> {
                msg = (String) call(SEC, "set", new Class<?>[]{String.class, double.class}, m.a(), number(m.b()));
                AuditLog.log(actor, "", "secours", "règle : " + msg);
            }
            case "sec.site.add" -> {
                if (!SITE_NAME.matcher(m.a()).matches()) {
                    msg = "Nom invalide (lettres, chiffres, - et _ ; 24 au plus).";
                    ok = false;
                } else {
                    msg = (String) call(SEC, "siteAdd", new Class<?>[]{ServerPlayer.class, String.class}, actor, m.a());
                    AuditLog.log(actor, "", "secours", "site d'incendie « " + m.a() + " » défini à sa position");
                }
            }
            case "sec.site.del" -> {
                msg = (String) call(SEC, "siteRemove", SRV_STR, s, m.a());
                AuditLog.log(actor, "", "secours", "supprime le site d'incendie « " + m.a() + " »");
            }
            case "sec.fire.start" -> {
                msg = (String) call(SEC, "fireStart", SRV_STR, s, m.a());
                AuditLog.log(actor, "", "secours", "déclenche un incendie" + (m.a().isEmpty() ? "" : " (" + m.a() + ")"));
            }
            case "sec.fire.stop" -> {
                msg = (String) call(SEC, "fireStopAll", SRV, s);
                AuditLog.log(actor, "", "secours", "éteint les incendies en cours");
            }
            case "sec.spawn.add" -> {
                if (!m.a().isEmpty() && !SITE_NAME.matcher(m.a()).matches()) {
                    msg = "Nom invalide (lettres, chiffres, - et _ ; 24 au plus).";
                    ok = false;
                } else {
                    msg = (String) call(SEC, "spawnAdd", new Class<?>[]{ServerPlayer.class, boolean.class, String.class}, actor, m.b().equals("u"), m.a());
                    AuditLog.log(actor, "", "secours", "point de réveil " + (m.b().equals("u") ? "des urgences" : "de l'hôpital") + " : " + msg);
                }
            }
            case "sec.spawn.del" -> {
                msg = (String) call(SEC, "spawnRemove", new Class<?>[]{MinecraftServer.class, boolean.class, String.class}, s, m.b().equals("u"), m.a());
                AuditLog.log(actor, "", "secours", "supprime le point de réveil « " + m.a() + " »");
            }
            default -> {}
        }
        if (msg.startsWith("Impossible") || msg.contains("invalide") || msg.contains("inconnu") || msg.contains("Valeur")) ok = false;
        CompoundTag t = new CompoundTag();
        t.put("Settings", settings(lines(SEC, "settings", NONE)));
        t.put("Sites", rows(lines(SEC, "sites", SRV, s), "Name", "X", "Y", "Z"));
        t.putInt("Incidents", (Integer) call(SEC, "incidents", SRV, s));
        t.put("Hosp", rows(lines(SEC, "spawns", new Class<?>[]{MinecraftServer.class, boolean.class}, s, false), "Name", "X", "Y", "Z"));
        t.put("Urg", rows(lines(SEC, "spawns", new Class<?>[]{MinecraftServer.class, boolean.class}, s, true), "Name", "X", "Y", "Z"));
        Net.send(actor, new Net.View("sec", t, msg, ok));
    }
}
