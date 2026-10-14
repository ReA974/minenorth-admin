package com.minenorth_admin.server;

import com.minenorth_admin.Players;
import fr.minenorth.api.GradeService;
import fr.minenorth.api.MineNorth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/** Onglet Grade (mod MineNorth VIP, via MineNorth API) : donner un grade (durée en jours, 0 = permanent), le changer, le retirer. */
final class GradeModule {
    private GradeModule() {}

    private static String until(Long expiry) {
        return expiry == null ? "" : expiry <= 0 ? "permanent" : "jusqu'au " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(expiry));
    }

    /** Fiche : grades disponibles (celui du joueur est marqué) et état actuel. */
    static CompoundTag view(MinecraftServer s, UUID id) {
        GradeService svc = MineNorth.grades();
        String current = svc.grade(s, id);
        Long exp = svc.expiry(s, id);
        CompoundTag t = new CompoundTag();
        t.putString("Current", current == null ? "" : current);
        t.putString("Until", until(exp));
        ListTag list = new ListTag();
        for (GradeService.Info g : svc.available()) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", g.id());
            c.putString("Name", g.name());
            boolean has = g.id().equalsIgnoreCase(current);
            c.putBoolean("Has", has);
            if (has) c.putString("Until", until(exp));
            list.add(c);
        }
        t.put("Grades", list);
        return t;
    }

    /** grade.give (a = identifiant du grade, n = jours, 0 = permanent), grade.remove. */
    static Result act(ServerPlayer actor, UUID id, String action, String gradeId, long n) {
        MinecraftServer s = actor.server;
        GradeService svc = MineNorth.grades();
        String name = Players.display(s, id);
        switch (action) {
            case "grade.give" -> {
                if (n < 0 || n > 3650) return Result.fail("Durée invalide (0 = permanent, 3650 jours maximum).");
                String label = svc.available().stream().filter(g -> g.id().equalsIgnoreCase(gradeId)).map(GradeService.Info::name).findFirst().orElse(null);
                if (label == null) return Result.fail("Grade inconnu.");
                if (!svc.give(s, id, gradeId, (int) n)) return Result.fail("Impossible de donner ce grade.");
                String d = n == 0 ? "permanent" : n + " jour(s)";
                return Result.ok(name + " a maintenant le grade " + label + " (" + d + ").", "donne le grade " + label + " (" + d + ")");
            }
            case "grade.remove" -> {
                if (!svc.remove(s, id)) return Result.fail(name + " n'a pas de grade.");
                return Result.ok("Grade retiré à " + name + ".", "retire le grade");
            }
            default -> {
                return Result.fail("Action inconnue.");
            }
        }
    }

    /** Suppression d'un joueur : on lui retire son grade. */
    static boolean wipe(MinecraftServer s, UUID id) {
        return MineNorth.grades().remove(s, id);
    }
}
