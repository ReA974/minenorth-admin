package com.minenorth_admin.server;

import com.minenorth_admin.AuditLog;
import com.minenorth_admin.Players;
import com.minenorth_admin.net.Net;
import com.minenorth_admin.net.Net.Req;
import com.minenorth_admin.staff.Access;
import com.minenorth_admin.staff.Perm;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Onglet Propriétés (mod MineNorth Portes, API {@code PropertyAdminApi} appelée par réflexion : le panneau compile sans le mod Portes
 * et l'onglet n'apparaît que s'il est installé). Les droits du staff sont vérifiés ici, les valeurs et l'état du bien par le mod Portes.
 * Requêtes (Req.a = dimension TAB nom du bien) : props.list, props.open, props.tp et les actions de modification.
 */
final class PropertiesModule {
    private PropertiesModule() {}

    static final int PAGE = 13;
    private static final String API = "fr.minenorth.portes.api.PropertyAdminApi";
    private static final long EXPIRING_MS = 48L * 3_600_000L;

    /** Même ordre et mêmes identifiants que PropertyCategory du mod Portes. */
    private static final String[][] CATS = {
            {"HOUSE", "Maison", "MAISON"}, {"APARTMENT", "Appartement", "APPART."}, {"BUSINESS", "Local d'entreprise", "ENTREPRISE"},
            {"LOCAL", "Local", "LOCAL"}, {"SMALL_SHOP", "Petit commerce", "COMMERCE"}, {"STORAGE", "Stockage", "STOCKAGE"},
            {"GARAGE", "Garage", "GARAGE"}, {"OTHER", "Autres", "AUTRES"}};

    private static String catLabel(String id) {
        for (String[] c : CATS) if (c[0].equals(id)) return c[1];
        return id;
    }

    private static String catShort(String id) {
        for (String[] c : CATS) if (c[0].equals(id)) return c[2];
        return id;
    }

    // ------------------------------------------------------------------ appel du mod Portes

    private static Object call(String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method m = Class.forName(API).getMethod(name, types);
        return m.invoke(null, args);
    }

    @SuppressWarnings("unchecked")
    private static List<String> lines(String name, Class<?>[] types, Object... args) {
        try {
            Object r = call(name, types, args);
            return r instanceof List<?> l ? (List<String>) l : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static final String[] NO_FIELDS = new String[0];

    private static String[] f(String line) {
        String[] p = line.split("\t", -1);
        return p.length >= 20 ? p : NO_FIELDS;
    }

    private static long num(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }

    private static UUID uuid(String s) {
        try { return s.isEmpty() ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    // ------------------------------------------------------------------ une propriété

    private static boolean occupied(String[] f) {
        return !f[6].isEmpty() || num(f[15]) > 0 || "1".equals(f[18]);
    }

    private static String ownerName(MinecraftServer s, String[] f) {
        if (!f[6].isEmpty()) return display(s, f[6]);
        if (num(f[15]) > 0) return "Entreprise " + (f[16].isEmpty() ? "n°" + f[15] : f[16]);
        return "";
    }

    private static String renterName(MinecraftServer s, String[] f) {
        if (!"1".equals(f[18])) return "";
        if (num(f[8]) > 0) return "Entreprise n°" + f[8];
        return display(s, f[7]);
    }

    private static String display(MinecraftServer s, String id) {
        UUID u = uuid(id);
        return u == null ? "" : Players.display(s, u);
    }

    private static CompoundTag item(MinecraftServer s, String[] f) {
        CompoundTag t = new CompoundTag();
        t.putString("Dim", f[0]);
        t.putString("Name", f[1]);
        t.putString("CatId", f[2]);
        t.putString("Cat", catLabel(f[2]));
        t.putString("CatShort", catShort(f[2]));
        t.putString("Mode", f[3]);
        t.putLong("Price", num(f[4]));
        t.putInt("Days", (int) num(f[5]));
        t.putString("Owner", ownerName(s, f));
        t.putString("OwnerId", f[6]);
        t.putString("Renter", renterName(s, f));
        t.putString("RenterId", "1".equals(f[18]) ? f[7] : "");
        t.putLong("Expiry", num(f[9]));
        t.putInt("Doors", (int) num(f[10]));
        t.putInt("X", (int) num(f[11]));
        t.putInt("Y", (int) num(f[12]));
        t.putInt("Z", (int) num(f[13]));
        t.putString("Catalogue", f[14]);
        t.putLong("Purchase", num(f[17]));
        t.putInt("Trusted", (int) num(f[19]));
        t.putBoolean("Occupied", occupied(f));
        t.putBoolean("Rented", "1".equals(f[18]));
        return t;
    }

    // ------------------------------------------------------------------ liste

    private static boolean matchesFilter(String[] f, String filter, long now) {
        boolean occ = occupied(f);
        boolean rented = "1".equals(f[18]);
        return switch (filter) {
            case "free" -> !occ;
            case "occupied" -> occ;
            case "sale" -> f[3].equals("SALE");
            case "rent" -> f[3].equals("RENTAL") && !rented;
            case "noowner" -> f[6].isEmpty() && num(f[15]) <= 0;
            case "expiring" -> rented && num(f[9]) - now <= EXPIRING_MS;
            default -> true;
        };
    }

    private static String natural(String s) {
        return java.util.regex.Pattern.compile("\\d+").matcher(s.toLowerCase(Locale.ROOT))
                .replaceAll(m -> String.format("%09d", Long.parseLong(m.group().length() > 9 ? m.group().substring(0, 9) : m.group())));
    }

    private static void sendList(ServerPlayer actor, String search, String filter, long page, UUID player, String msg, boolean ok) {
        MinecraftServer s = actor.server;
        List<String> all = lines("list", new Class<?>[]{MinecraftServer.class}, s);
        if (all == null) {
            Net.send(actor, new Net.View("message", new CompoundTag(), "Mets à jour le mod Portes (API des propriétés absente).", false));
            return;
        }
        long now = System.currentTimeMillis();
        String q = search.toLowerCase(Locale.ROOT).trim();
        int free = 0, occ = 0, sale = 0, expiring = 0, total = 0;
        List<String[]> rows = new ArrayList<>();
        for (String line : all) {
            String[] f = f(line);
            if (f.length == 0) continue;
            if (player != null && !(player.toString().equals(f[6]) || (player.toString().equals(f[7]) && "1".equals(f[18])))) continue;
            total++;
            if (occupied(f)) occ++; else free++;
            if (f[3].equals("SALE")) sale++;
            if (matchesFilter(f, "expiring", now)) expiring++;
            if (!matchesFilter(f, filter, now)) continue;
            if (!q.isEmpty()) {
                String hay = (f[1] + " " + catLabel(f[2]) + " " + ownerName(s, f) + " " + renterName(s, f) + " " + f[0] + " " + f[14]).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            rows.add(f);
        }
        rows.sort(Comparator.comparing((String[] f) -> natural(f[1])));
        int pages = Math.max(1, (rows.size() + PAGE - 1) / PAGE);
        int p = (int) Math.max(0, Math.min(page, pages - 1));
        ListTag items = new ListTag();
        for (int i = p * PAGE; i < Math.min(rows.size(), (p + 1) * PAGE); i++) items.add(item(s, rows.get(i)));
        CompoundTag t = new CompoundTag();
        t.put("Items", items);
        t.putInt("Matches", rows.size());
        t.putInt("Page", p);
        t.putInt("Pages", pages);
        t.putString("Filter", filter);
        t.putString("Search", search);
        t.putInt("Total", total);
        t.putInt("Free", free);
        t.putInt("Occupied", occ);
        t.putInt("Sale", sale);
        t.putInt("Expiring", expiring);
        if (player != null) {
            t.putUUID("PlayerId", player);
            t.putString("Player", Players.display(s, player));
        }
        Net.send(actor, new Net.View("props", t, msg, ok));
    }

    // ------------------------------------------------------------------ fiche d'un bien

    private static void sendDetail(ServerPlayer actor, String dim, String house, String msg, boolean ok) {
        MinecraftServer s = actor.server;
        Object r;
        try {
            r = call("one", new Class<?>[]{MinecraftServer.class, String.class, String.class}, s, dim, house);
        } catch (ReflectiveOperationException | LinkageError e) {
            Net.send(actor, new Net.View("message", new CompoundTag(), "Mets à jour le mod Portes (API des propriétés absente).", false));
            return;
        }
        String[] f = r instanceof String line ? f(line) : NO_FIELDS;
        CompoundTag t = new CompoundTag();
        if (f.length == 0) {
            t.putBoolean("Gone", true);
            Net.send(actor, new Net.View("prop", t, msg.isEmpty() ? "Cette propriété n'existe plus." : msg, false));
            return;
        }
        t.put("Item", item(s, f));
        ListTag cats = new ListTag();
        for (String[] c : CATS) {
            CompoundTag ct = new CompoundTag();
            ct.putString("Id", c[0]);
            ct.putString("Label", c[1]);
            ct.putString("Short", c[2]);
            cats.add(ct);
        }
        t.put("Cats", cats);
        ListTag tr = new ListTag();
        List<String> trusted = lines("trusted", new Class<?>[]{MinecraftServer.class, String.class, String.class}, s, dim, house);
        if (trusted != null) {
            for (String id : trusted) {
                UUID u = uuid(id);
                if (u == null) continue;
                CompoundTag c = new CompoundTag();
                c.putUUID("Id", u);
                c.putString("Name", Players.display(s, u));
                tr.add(c);
            }
        }
        t.put("TrustedList", tr);
        ListTag lg = new ListTag();
        List<String> log = lines("log", new Class<?>[]{MinecraftServer.class, String.class, String.class}, s, dim, house);
        if (log != null) {
            for (String line : log) {
                String[] p = line.split("\t", 2);
                if (p.length < 2) continue;
                CompoundTag c = new CompoundTag();
                c.putString("D", AuditLog.date(num(p[0])));
                c.putString("X", p[1]);
                lg.add(c);
            }
        }
        t.put("Log", lg);
        Net.send(actor, new Net.View("prop", t, msg, ok));
    }

    /** Fiche joueur, section « Biens » : les propriétés dont il est propriétaire ou locataire. */
    static CompoundTag playerView(MinecraftServer s, UUID id) {
        CompoundTag t = new CompoundTag();
        ListTag items = new ListTag();
        List<String> all = lines("list", new Class<?>[]{MinecraftServer.class}, s);
        if (all != null) {
            List<String[]> mine = new ArrayList<>();
            for (String line : all) {
                String[] f = f(line);
                if (f.length == 0) continue;
                if (id.toString().equals(f[6]) || (id.toString().equals(f[7]) && "1".equals(f[18]))) mine.add(f);
            }
            mine.sort(Comparator.comparing((String[] f) -> natural(f[1])));
            for (String[] f : mine) {
                CompoundTag c = item(s, f);
                c.putBoolean("AsRenter", !id.toString().equals(f[6]));
                items.add(c);
            }
        }
        t.put("Items", items);
        return t;
    }

    // ------------------------------------------------------------------ entrée

    static void handle(ServerPlayer actor, Access acc, Req m) {
        String action = m.action();
        if (action.equals("props.list")) {
            UUID player = m.target() == null || m.target().equals(Net.NIL) ? null : m.target();
            sendList(actor, m.a(), m.b().isEmpty() ? "all" : m.b(), m.n(), player, "", true);
            return;
        }
        String[] where = m.a().split("\t", 2);
        if (where.length < 2 || where[0].isEmpty() || where[1].isEmpty()) {
            Net.send(actor, new Net.View("message", new CompoundTag(), "Propriété invalide.", false));
            return;
        }
        String dim = where[0], house = where[1];
        if (action.equals("props.open")) {
            sendDetail(actor, dim, house, "", true);
            return;
        }
        Result r = act(actor, acc, action, dim, house, m);
        if (r.ok() && r.log() != null) AuditLog.log(actor, house, "propriétés", r.log());
        sendDetail(actor, dim, house, r.message(), r.ok());
    }

    private static Result needEdit(Access acc) {
        return acc.has(Perm.PROPRIETES_EDIT) ? null : Result.fail("Tu n'as pas la permission : " + Perm.PROPRIETES_EDIT.label + ".");
    }

    /** Joueur désigné par le texte (UUID ou nom RP / pseudo, hors ligne aussi), ou par la cible de la requête. */
    private static Players.Known player(MinecraftServer s, Req m) {
        String text = m.b().trim();
        if (text.isEmpty() && m.target() != null && !m.target().equals(Net.NIL)) {
            return new Players.Known(m.target(), Players.name(s, m.target()), Players.online(s, m.target()) != null);
        }
        try {
            UUID u = UUID.fromString(text);
            return new Players.Known(u, Players.name(s, u), Players.online(s, u) != null);
        } catch (IllegalArgumentException ignored) {
            return Players.byName(s, text);
        }
    }

    private static Result act(ServerPlayer actor, Access acc, String action, String dim, String house, Req m) {
        MinecraftServer s = actor.server;
        Result denied = needEdit(acc);
        String sub = action.substring(action.indexOf('.') + 1);
        if (sub.equals("tp")) {
            if (!acc.has(Perm.TELEPORT)) return Result.fail("Tu n'as pas la permission : " + Perm.TELEPORT.label + ".");
            return teleport(actor, dim, house);
        }
        if (denied != null) return denied;
        String player = "", playerName = "", text = "";
        long n = m.n();
        int days = 0;
        switch (sub) {
            case "setOwner", "setRenter", "addTrusted", "removeTrusted" -> {
                Players.Known k = player(s, m);
                if (k == null) return Result.fail("Joueur introuvable : " + m.b() + " (nom RP « Prénom Nom », pseudo ou UUID ; il doit s'être connecté au moins une fois).");
                player = k.id().toString();
                playerName = Players.display(s, k.id());
                if (sub.equals("setRenter")) { days = (int) m.n(); n = 0; }
            }
            case "free", "evict" -> { }
            case "sale", "price" -> { }
            case "rent" -> {
                try { days = Integer.parseInt(m.b().trim()); } catch (NumberFormatException e) { return Result.fail("Durée de location invalide (1 à 365 jours)."); }
            }
            case "unlist" -> { }
            case "category" -> text = m.b().trim();
            default -> { return Result.fail("Action inconnue."); }
        }
        // actions destructives : le client envoie n = 1 après la double confirmation
        if ((sub.equals("free") || sub.equals("evict") || sub.equals("setOwner")) && m.n() != 1) return Result.fail("Confirmation manquante.");
        if (sub.equals("setOwner") || sub.equals("free") || sub.equals("evict")) n = 0;
        Object r;
        try {
            r = call("act", new Class<?>[]{MinecraftServer.class, String.class, String.class, String.class, String.class, String.class,
                    long.class, int.class, String.class, String.class}, s, sub, dim, house, player, playerName, n, days, text, actor.getGameProfile().getName());
        } catch (ReflectiveOperationException | LinkageError e) {
            return Result.fail("Mets à jour le mod Portes (API des propriétés absente ou trop ancienne).");
        }
        String res = r instanceof String str ? str : "";
        int tab = res.indexOf('\t');
        if (tab < 0) return Result.fail("Réponse inattendue du mod Portes.");
        String msg = res.substring(tab + 1);
        if (!res.startsWith("OK")) return Result.fail(msg);
        return Result.ok(msg, logText(sub, house, playerName, n, days, text));
    }

    private static String logText(String sub, String house, String who, long n, int days, String text) {
        return switch (sub) {
            case "setOwner" -> "attribue le bien à " + who;
            case "setRenter" -> "installe " + who + " en locataire (" + days + " j)";
            case "addTrusted" -> "ajoute " + who + " aux colocataires";
            case "removeTrusted" -> "retire " + who + " des colocataires";
            case "free" -> "LIBÈRE le bien (propriétaire, locataire, fiche, serrures)";
            case "evict" -> "EXPULSE l'occupant";
            case "sale" -> "met en vente à " + eur(n);
            case "rent" -> "met en location à " + eur(n) + " pour " + days + " j";
            case "unlist" -> "retire la fiche de vente / location";
            case "price" -> "change le prix : " + eur(n);
            case "category" -> "change la catégorie : " + catLabel(text);
            default -> sub;
        };
    }

    private static String eur(long cents) {
        return String.format(Locale.FRANCE, "%.2f €", cents / 100.0);
    }

    private static Result teleport(ServerPlayer actor, String dim, String house) {
        MinecraftServer s = actor.server;
        Object r;
        try {
            r = call("one", new Class<?>[]{MinecraftServer.class, String.class, String.class}, s, dim, house);
        } catch (ReflectiveOperationException | LinkageError e) {
            return Result.fail("Mets à jour le mod Portes (API des propriétés absente).");
        }
        String[] f = r instanceof String line ? f(line) : NO_FIELDS;
        if (f.length == 0) return Result.fail("Propriété introuvable.");
        ResourceLocation rl = ResourceLocation.tryParse(f[0]);
        ServerLevel level = rl == null ? null : s.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, rl));
        if (level == null) return Result.fail("Dimension introuvable : " + f[0] + ".");
        actor.teleportTo(level, num(f[11]) + 0.5, num(f[12]), num(f[13]) + 0.5, actor.getYRot(), actor.getXRot());
        return Result.ok("Téléporté à " + f[1] + ".", "se téléporte sur place");
    }
}
