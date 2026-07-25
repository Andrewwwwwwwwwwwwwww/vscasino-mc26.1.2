package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.CasinoConfig;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The bet row, shared by every game so the controls sit in the same place everywhere.
 *
 * <p>Laid out across one full 9-wide row, mirrored around the action button in the middle:
 *
 * <pre>  Back | -10 | -5 | -1 | ACTION | +1 | +5 | +10 | Close  </pre>
 *
 * Callers pass the slot index of the row's left edge ({@code rowStart}) and use the offset
 * constants for everything else, so a game only has to know which row its controls live on.
 */
public final class BetControls {
    private BetControls() {}

    public static final int BACK = 0;
    public static final int DOWN_10 = 1;
    public static final int DOWN_5 = 2;
    public static final int DOWN_1 = 3;
    public static final int ACTION = 4;
    public static final int UP_1 = 5;
    public static final int UP_5 = 6;
    public static final int UP_10 = 7;
    public static final int CLOSE = 8;

    /** Draw the six bet-step buttons. The action slot is left to the calling game. */
    public static void render(SimpleContainer container, int rowStart, ServerPlayer player) {
        CasinoConfig cfg = CasinoConfig.get();
        step(container, rowStart + DOWN_10, player, -cfg.betStepLarge);
        step(container, rowStart + DOWN_5, player, -cfg.betStepMedium);
        step(container, rowStart + DOWN_1, player, -cfg.betStepSmall);
        step(container, rowStart + UP_1, player, cfg.betStepSmall);
        step(container, rowStart + UP_5, player, cfg.betStepMedium);
        step(container, rowStart + UP_10, player, cfg.betStepLarge);
    }

    private static void step(SimpleContainer container, int slot, ServerPlayer player, int delta) {
        boolean up = delta > 0;
        container.setItem(slot, Guis.button(
                up ? Items.GLOWSTONE_DUST : Items.REDSTONE,
                Lang.tr(player, up ? "vscasino.menu.bet_up" : "vscasino.menu.bet_down",
                        up ? "+%d" : "-%d", Math.abs(delta)),
                up ? ChatFormatting.GREEN : ChatFormatting.RED,
                List.of(Lang.tr(player, up ? "vscasino.menu.bet_up.desc" : "vscasino.menu.bet_down.desc",
                        up ? "Raise your bet by %d." : "Lower your bet by %d.", Math.abs(delta))),
                up ? "vscasino:bet_up" : "vscasino:bet_down"));
    }

    /**
     * Apply a click on one of the bet buttons.
     *
     * @return the new bet, already clamped to the configured limits, or -1 if {@code slotId} was
     *         not a bet button (in which case the caller should keep handling the click)
     */
    public static int handle(int slotId, int rowStart, int currentBet) {
        CasinoConfig cfg = CasinoConfig.get();
        int delta = switch (slotId - rowStart) {
            case DOWN_10 -> -cfg.betStepLarge;
            case DOWN_5 -> -cfg.betStepMedium;
            case DOWN_1 -> -cfg.betStepSmall;
            case UP_1 -> cfg.betStepSmall;
            case UP_5 -> cfg.betStepMedium;
            case UP_10 -> cfg.betStepLarge;
            default -> 0;
        };
        if (delta == 0) return -1;
        return Math.max(cfg.minBet, Math.min(cfg.maxBet, currentBet + delta));
    }
}
