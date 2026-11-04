package com.minenorth_admin;

import com.minenorth_admin.net.Net;
import com.minenorth_admin.server.AdminService;
import com.minenorth_admin.staff.Access;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Panneau d'administration MineNorthRP : /mnadmin.
 * La commande n'existe (et n'est autocomplétée) que pour les membres du staff : les autres joueurs ne voient rien.
 */
@Mod(MineNorthAdmin.MODID)
public class MineNorthAdmin {
    public static final String MODID = "minenorth_admin";
    public static final Logger LOG = LogUtils.getLogger();

    public MineNorthAdmin() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        // COMMON (dossier config/) et non SERVER (<monde>/serverconfig/, remis à zéro à chaque redémarrage) ; serveur dédié seulement : le client ne crée aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AdminConfig.SPEC, "minenorth_admin-server.toml");
        }
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new com.minenorth_admin.server.StaffMode());
        fr.minenorth.api.MineNorth.provide(fr.minenorth.api.StaffService.class, new fr.minenorth.api.StaffService() {
            @Override public net.minecraft.network.chat.Component dutyTag(net.minecraft.server.level.ServerPlayer p) { return com.minenorth_admin.server.StaffMode.dutyTag(p); }
            @Override public boolean keepGradePrefix() { return com.minenorth_admin.server.StaffMode.keepVipPrefix(); }
            @Override public boolean isStaff(net.minecraft.server.MinecraftServer s, java.util.UUID player) {
                com.minenorth_admin.staff.StaffData d = com.minenorth_admin.staff.StaffData.get(s);
                com.minenorth_admin.staff.StaffData.Member m = d.member(player);
                return m != null && d.role(m.role) != null;
            }
        });
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Net::register);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("mnadmin").requires(Access::canOpen)
                .executes(c -> {
                    AdminService.handle(c.getSource().getPlayerOrException(), Net.Req.of("open"));
                    return 1;
                }));
    }
}
