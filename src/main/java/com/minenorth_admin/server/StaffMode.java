package com.minenorth_admin.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Mode staff : invisible (sans particules) + créatif. L'effet d'invisibilité seul laisse voir l'armure et un contour translucide pour certains
 * joueurs ; en plus, le joueur est retiré de l'écran des autres joueurs (hors mode staff) : ils ne reçoivent plus son entité du tout.
 * Ancienne description : L'état est stocké dans les données persistantes du joueur
 * (survit à la déconnexion, à la mort et au milk). Quitter le mode restaure le mode de jeu d'avant.
 */
public final class StaffMode {
    private static final String ON = "mnadmin_staffmode", PREV = "mnadmin_prevgm";

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static boolean is(ServerPlayer p) {
        return data(p).getBoolean(ON);
    }

    private static void applyVanish(ServerPlayer p) {
        // ambiant=false, particules=false, icône=false : aucune bulle autour du joueur
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    /** @return true si le mode est maintenant actif. */
    public static boolean toggle(ServerPlayer p) {
        CompoundTag d = data(p);
        if (d.getBoolean(ON)) {
            d.putBoolean(ON, false);
            p.removeEffect(MobEffects.INVISIBILITY);
            GameType prev = GameType.byId(d.contains(PREV) ? d.getInt(PREV) : GameType.SURVIVAL.getId());
            p.setGameMode(prev == GameType.SPECTATOR ? GameType.SURVIVAL : prev);
            d.remove(PREV);
            retrack(p);
            return false;
        }
        GameType cur = p.gameMode.getGameModeForPlayer();
        d.putInt(PREV, cur == GameType.CREATIVE ? GameType.SURVIVAL.getId() : cur.getId());
        d.putBoolean(ON, true);
        p.setGameMode(GameType.CREATIVE);
        applyVanish(p);
        retrack(p);
        return true;
    }

    /** Refait le suivi du joueur par les autres : ils le perdent puis le revoient (ou non, selon le mode), et l'événement ci-dessous décide qui le voit. */
    private static void retrack(ServerPlayer p) {
        var cache = p.serverLevel().getChunkSource();
        cache.removeEntity(p);
        cache.addEntity(p);
    }

    /** Un joueur qui commence à suivre un membre du staff en mode staff le perd aussitôt de vue (sauf s'il est lui-même en mode staff). */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer staff && e.getEntity() instanceof ServerPlayer viewer && staff != viewer
                && is(staff) && !is(viewer)) {
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(staff.getId()));
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        if (is(p) && !p.hasEffect(MobEffects.INVISIBILITY)) applyVanish(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && is(p)) {
            applyVanish(p);
            p.setGameMode(GameType.CREATIVE);
        }
    }
}
