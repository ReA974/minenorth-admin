package com.minenorth_admin.server;

import com.minenorth_admin.AuditLog;
import com.minenorth_admin.Players;
import com.minenorth_admin.net.Net;
import com.minenorth_admin.net.Net.Req;
import com.minenorth_admin.staff.Access;
import com.minenorth_admin.staff.Perm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Onglet Gangs (mod MineNorth Portes, API {@code GangAdminApi} appelée par réflexion : le panneau compile sans le mod Portes et l'onglet
 * n'apparaît que s'il est installé). Les droits du staff sont vérifiés ici (GANGS_VIEW pour voir, GANGS_EDIT pour agir) ; les valeurs, les
 * limites et l'état sont vérifiés par le mod Portes. Le client n'envoie que des intentions : jamais un objet, toujours un numéro de case de l'inventaire
 * RÉEL de l'admin que le mod relit lui-même.
 * Requêtes : gangs.list, gangs.open (a = id du gang) et les actions « gangs.<action> » (voir GangAdmin du mod Portes).
 */
final class GangsModule {
    private GangsModule() {}

    private static final String API = "fr.minenorth.portes.api.GangAdminApi";

    /** Gang ouvert dans le panneau de chaque admin (mémoire seulement). */
    private static final Map<UUID, String> SELECTED = new HashMap<>();

    private static Object call(String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = Class.forName(API).getMethod(name, types);
        return m.invoke(null, args);
    }

    @SuppressWarnings("unchecked")
    private static List<String> lines(String name, Class<?>[] types, Object... args) {
        try {
            Object r = call(name, types, args);
            return r instanceof List<?> l ? (List<String>) l : List.of();
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static final Class<?>[] SRV = {MinecraftServer.class};

    private static String[] f(String line) { return line.split("\t", -1); }

    private static long num(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static boolean isUuid(String s) {
        try { UUID.fromString(s); return true; } catch (IllegalArgumentException e) { return false; }
    }

    // ------------------------------------------------------------------ vue

    private static void sendView(ServerPlayer actor, String msg, boolean ok) {
        MinecraftServer s = actor.server;
        List<String> gangs = lines("gangs", SRV, s);
        if (gangs == null) {
            Net.send(actor, new Net.View("message", new CompoundTag(), "Mets à jour le mod Portes (API des gangs absente).", false));
            return;
        }
        CompoundTag t = new CompoundTag();
        ListTag gl = new ListTag();
        for (String line : gangs) {
            String[] p = f(line);
            if (p.length < 10) continue;
            CompoundTag c = new CompoundTag();
            c.putString("Id", p[0]); c.putString("Name", p[1]); c.putString("Tag", p[2]); c.putInt("Color", (int) num(p[3]));
            c.putInt("Members", (int) num(p[4])); c.putInt("Online", (int) num(p[5])); c.putLong("Balance", num(p[6]));
            c.putInt("Zones", (int) num(p[7])); c.putInt("Factories", (int) num(p[8])); c.putInt("Pct", (int) num(p[9]));
            gl.add(c);
        }
        t.put("Gangs", gl);

        String sel = SELECTED.getOrDefault(actor.getUUID(), "");
        List<String> det = sel.isEmpty() ? List.of() : lines("detail", new Class<?>[]{MinecraftServer.class, String.class}, s, sel);
        if (det != null && !det.isEmpty()) {
            CompoundTag d = new CompoundTag();
            ListTag members = new ListTag(), zones = new ListTag(), tx = new ListTag(), fac = new ListTag();
            for (String line : det) {
                String[] p = f(line);
                switch (p[0]) {
                    case "G" -> {
                        if (p.length < 10) break;
                        d.putString("Id", p[1]); d.putString("Name", p[2]); d.putString("Tag", p[3]); d.putInt("Color", (int) num(p[4]));
                        d.putLong("Balance", num(p[5])); d.putInt("Pct", (int) num(p[6])); d.putInt("Spend", (int) num(p[7])); d.putBoolean("Base", "1".equals(p[9]));
                    }
                    case "M" -> {
                        if (p.length < 5) break;
                        CompoundTag c = new CompoundTag();
                        c.putString("Id", p[1]); c.putString("Name", p[2]); c.putInt("Rank", (int) num(p[3])); c.putBoolean("On", "1".equals(p[4]));
                        members.add(c);
                    }
                    case "T" -> {
                        if (p.length < 4) break;
                        CompoundTag c = new CompoundTag();
                        c.putString("Name", p[1]); c.putString("Status", p[2]); c.putLong("Income", num(p[3]));
                        zones.add(c);
                    }
                    case "X" -> {
                        if (p.length < 5) break;
                        CompoundTag c = new CompoundTag();
                        c.putString("When", p[1]); c.putString("Who", p[2]); c.putLong("Delta", num(p[3])); c.putString("Label", p[4]);
                        tx.add(c);
                    }
                    case "F" -> {
                        if (p.length < 9) break;
                        CompoundTag c = new CompoundTag();
                        c.putString("Dim", p[1]); c.putInt("X", (int) num(p[2])); c.putInt("Y", (int) num(p[3])); c.putInt("Z", (int) num(p[4]));
                        c.putString("Zone", p[5]); c.putInt("Stock", (int) num(p[6])); c.putInt("Queue", (int) num(p[7])); c.putInt("Mats", (int) num(p[8]));
                        fac.add(c);
                    }
                    default -> { }
                }
            }
            d.put("Members", members); d.put("Zones", zones); d.put("Tx", tx); d.put("Factories", fac);
            if (d.contains("Id")) t.put("Sel", d);
            else SELECTED.remove(actor.getUUID());
        } else if (!sel.isEmpty()) {
            SELECTED.remove(actor.getUUID());
        }

        ListTag terr = new ListTag();
        List<String> tl = lines("territories", SRV, s);
        if (tl != null) {
            for (String line : tl) {
                String[] p = f(line);
                if (p.length < 11) continue;
                CompoundTag c = new CompoundTag();
                c.putString("Name", p[0]); c.putString("Dim", p[1]); c.putInt("X", (int) num(p[2])); c.putInt("Y", (int) num(p[3])); c.putInt("Z", (int) num(p[4]));
                c.putString("HolderId", p[5]); c.putString("HolderTag", p[6]); c.putString("HolderName", p[7]); c.putLong("Income", num(p[8]));
                c.putBoolean("Custom", "1".equals(p[9])); c.putString("Status", p[10]);
                terr.add(c);
            }
        }
        t.put("Terr", terr);

        ListTag cat = new ListTag();
        List<String> cl = lines("catalog", SRV, s);
        if (cl != null) {
            for (String line : cl) {
                String[] p = f(line);
                if (p.length < 9) continue;
                CompoundTag c = new CompoundTag();
                c.putInt("I", (int) num(p[0])); c.putString("Label", p[1]); c.putString("Spec", p[2]); c.putInt("Qty", (int) num(p[3])); c.putInt("Sec", (int) num(p[4]));
                c.putLong("Cents", num(p[5])); c.putString("Needs", p[6]); c.putBoolean("On", "1".equals(p[7])); c.putInt("Daily", (int) num(p[8]));
                cat.add(c);
            }
        }
        t.put("Cat", cat);

        ListTag mats = new ListTag();
        List<String> ml = lines("materials", SRV, s);
        if (ml != null) {
            for (String line : ml) {
                String[] p = f(line);
                if (p.length < 4) continue;
                CompoundTag c = new CompoundTag();
                c.putString("Label", p[0]); c.putString("Id", p[1]); c.putInt("Pack", (int) num(p[2])); c.putLong("Cents", num(p[3]));
                mats.add(c);
            }
        }
        t.put("Mats", mats);
        Net.send(actor, new Net.View("gangs", t, msg, ok));
    }

    // ------------------------------------------------------------------ entrée

    static void handle(ServerPlayer actor, Access acc, Req m) {
        String action = m.action();
        if (action.equals("gangs") || action.equals("gangs.list")) {
            sendView(actor, "", true);
            return;
        }
        if (action.equals("gangs.open")) {
            if (isUuid(m.a())) SELECTED.put(actor.getUUID(), m.a()); else SELECTED.remove(actor.getUUID());
            sendView(actor, "", true);
            return;
        }
        if (!acc.has(Perm.GANGS_EDIT)) {
            sendView(actor, "Tu n'as pas la permission : " + Perm.GANGS_EDIT.label + ".", false);
            return;
        }
        String sub = action.substring("gangs.".length());
        String a = m.a(), b = m.b();
        long n = m.n();
        String logTarget = "";
        // les actions qui visent un joueur transmettent son UUID (cible de la requête) ; le mod Portes revérifie tout
        switch (sub) {
            case "gang.create" -> {
                UUID chef = m.target();
                if (chef == null || chef.equals(Net.NIL)) { sendView(actor, "Choisis le chef dans la liste des joueurs.", false); return; }
                b = b + "|" + chef;
                logTarget = a;
            }
            case "gang.addMember", "gang.setChef", "gang.removeMember" -> {
                UUID t = m.target();
                if (t == null || t.equals(Net.NIL)) { sendView(actor, "Choisis un joueur.", false); return; }
                b = t.toString();
                logTarget = Players.display(actor.server, t);
            }
            case "gang.setRank" -> {
                UUID t = m.target();
                if (t == null || t.equals(Net.NIL)) { sendView(actor, "Choisis un joueur.", false); return; }
                b = t + "|" + b;
                logTarget = Players.display(actor.server, t);
            }
            case "gang.dissolve", "gang.credit", "gang.debit", "gang.incomePct", "factory.clear", "terr.create", "terr.delete", "terr.free", "terr.assign", "terr.income",
                    "cat.addSlot", "cat.set", "cat.remove", "cat.move" -> { }
            default -> { sendView(actor, "Action inconnue.", false); return; }
        }
        if (sub.startsWith("gang.") || sub.equals("factory.clear")) {
            if (isUuid(a)) SELECTED.put(actor.getUUID(), a);
        }
        String res;
        try {
            Object r = call("act", new Class<?>[]{MinecraftServer.class, ServerPlayer.class, String.class, String.class, String.class, long.class},
                    actor.server, actor, sub, a, b, n);
            res = r instanceof String str ? str : "";
        } catch (ReflectiveOperationException | LinkageError e) {
            sendView(actor, "Mets à jour le mod Portes (API des gangs absente ou trop ancienne).", false);
            return;
        }
        int tab = res.indexOf('\t');
        if (tab < 0) { sendView(actor, "Réponse inattendue du mod Portes.", false); return; }
        String msg = res.substring(tab + 1);
        boolean ok = res.startsWith("OK");
        if (ok) {
            if (sub.equals("gang.dissolve")) SELECTED.remove(actor.getUUID());
            AuditLog.log(actor, logTarget, "gangs", sub + " : " + msg);
        }
        sendView(actor, msg, ok);
    }
}
