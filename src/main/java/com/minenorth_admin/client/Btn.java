package com.minenorth_admin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Bouton plat, même style que les écrans MineNorth (banque, boutiques). */
@OnlyIn(Dist.CLIENT)
public class Btn extends AbstractButton {
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF4A3CB4;
    public static final int PINK = 0xFFC83CF0;
    public static final int GREEN = 0xFF22A86B;
    public static final int RED = 0xFFD8434F;
    public static final int GHOST = 0;

    private final Runnable action;
    private final int color;
    private boolean selected;

    public Btn(int x, int y, int w, int h, String label, int color, Runnable action) {
        super(x, y, w, h, Component.literal(label));
        this.color = color;
        this.action = action;
    }

    public Btn enabled(boolean on) {
        this.active = on;
        return this;
    }

    /** Onglet actif : reste allumé. */
    public Btn selected(boolean on) {
        this.selected = on;
        return this;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    /** Rectangle aux coins arrondis (rayon r en pixels GUI), comme la charte MineNorth. */
    static void rfill(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int c) {
        r = Math.max(0, Math.min(r, Math.min(x2 - x1, y2 - y1) / 2));
        g.fill(x1, y1 + r, x2, y2 - r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - dy * dy));
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, c);
            g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, c);
        }
    }

    /** Libellé tronqué (trop long pour le bouton même réduit) : l'écran affiche alors le texte complet en infobulle. */
    public boolean truncated() {
        Font font = Minecraft.getInstance().font;
        return font.width(getMessage()) * MIN_SCALE > width - 6;
    }

    private static final float MIN_SCALE = 0.75f;

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        boolean hov = isHovered() && active;
        Component label = getMessage();
        int avail = width - 6;
        int tw = font.width(label);
        float sc = 1f;
        if (tw > avail) {
            sc = Math.max(MIN_SCALE, avail / (float) tw);
            if (tw * sc > avail) {
                label = Component.literal(font.plainSubstrByWidth(label.getString(), (int) (avail / sc) - 6) + "…");
                tw = font.width(label);
            }
        }
        int textH = Math.round(8 * sc);
        int ty = y + (height - textH) / 2;
        int tc;
        if (color == GHOST) {
            tc = !active ? 0xFF6F7FB0 : selected || hov ? 0xFFFFFFFF : 0xFFCFE3FF;
            if (selected || hov) g.fill(x + 3, y + height - 1, x + width - 3, y + height, selected ? CYAN : tc);
        } else {
            int bg = !active ? 0xFF2A2468 : selected ? lighten(color) : hov ? lighten(color) : color;
            int r = height >= 16 ? 4 : 3;
            rfill(g, x, y, x + width, y + height, r, bg);
            if (selected) g.fill(x + r, y + height - 2, x + width - r, y + height - 1, 0xFFFFFFFF);
            tc = active ? 0xFFFFFFFF : 0xFF8FA8E0;
        }
        int cx = x + width / 2;
        if (sc == 1f) {
            g.drawCenteredString(font, label, cx, ty, tc);
        } else {
            g.pose().pushPose();
            g.pose().scale(sc, sc, 1f);
            g.drawString(font, label, Math.round((cx - tw * sc / 2f) / sc), Math.round(ty / sc), tc, false);
            g.pose().popPose();
        }
    }
}
