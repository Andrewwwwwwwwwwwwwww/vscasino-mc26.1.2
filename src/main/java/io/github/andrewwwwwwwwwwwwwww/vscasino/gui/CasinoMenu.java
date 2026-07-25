package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.CasinoConfig;
import io.github.andrewwwwwwwwwwwwwww.vscasino.Wager;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The casino lobby: pick a game. */
public class CasinoMenu extends ChestMenu {
    private static final int SLOTS_SLOT = 11;
    private static final int BLACKJACK_SLOT = 13;
    private static final int POKER_SLOT = 15;
    private static final int BALANCE_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final ServerPlayer player;
    private final SimpleContainer container;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new CasinoMenu(syncId, inv, (ServerPlayer) p),
                Component.literal(Lang.tr(player, "vscasino.menu.casino.title", "Casino"))));
    }

    private CasinoMenu(int syncId, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, inv, new SimpleContainer(27), 3);
        this.player = player;
        this.container = (SimpleContainer) getContainer();
        populate();
    }

    private void populate() {
        CasinoConfig cfg = CasinoConfig.get();
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

        if (cfg.slotsEnabled) {
            container.setItem(SLOTS_SLOT, Guis.button(Items.GOLD_BLOCK,
                    t("vscasino.menu.slots.button", "Slots"), ChatFormatting.YELLOW,
                    List.of(t("vscasino.menu.slots.button.desc", "Spin three reels for a payout.")), "vscasino:slots_icon"));
        }
        if (cfg.blackjackEnabled) {
            container.setItem(BLACKJACK_SLOT, Guis.button(Items.CLAY_BALL,
                    t("vscasino.menu.blackjack.button", "Blackjack"), ChatFormatting.GREEN,
                    List.of(t("vscasino.menu.blackjack.button.desc", "Beat the dealer without going over 21.")), "vscasino:blackjack_icon"));
        }
        if (cfg.videoPokerEnabled) {
            container.setItem(POKER_SLOT, Guis.button(Items.BRICK,
                    t("vscasino.menu.poker.button", "Video Poker"), ChatFormatting.AQUA,
                    List.of(t("vscasino.menu.poker.button.desc", "Hold what you like, draw the rest.")), "vscasino:poker_icon"));
        }

        List<String> balanceLore = new java.util.ArrayList<>();
        balanceLore.add(t("vscasino.menu.bet_limits", "Bets: %d - %d Quest Shards", cfg.minBet, cfg.maxBet));
        double luck = Wager.luckBonus(player);
        if (luck > 0) {
            balanceLore.add(t("vscasino.menu.luck_bonus", "Luck bonus: +%s%% winnings",
                    String.format("%.1f", luck * 100)));
        }
        container.setItem(BALANCE_SLOT, Guis.button(Items.DIAMOND,
                t("vscasino.menu.balance", "Balance: %d Quest Shards", Wager.balance(player)),
                ChatFormatting.GOLD, balanceLore));

        container.setItem(CLOSE_SLOT, Guis.button(Items.BARRIER,
                t("vscasino.menu.close", "Close"), ChatFormatting.RED));
    }

    private String t(String key, String fallback, Object... args) {
        return Lang.tr(player, key, fallback, args);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (clicker instanceof ServerPlayer sp) {
            CasinoConfig cfg = CasinoConfig.get();
            if (slotId == CLOSE_SLOT) { sp.closeContainer(); return; }
            if (slotId == SLOTS_SLOT && cfg.slotsEnabled) { SlotsMenu.open(sp); return; }
            if (slotId == BLACKJACK_SLOT && cfg.blackjackEnabled) { BlackjackMenu.open(sp); return; }
            if (slotId == POKER_SLOT && cfg.videoPokerEnabled) { VideoPokerMenu.open(sp); return; }
        }
        // Everything else is inert: never call super, so nothing can be taken out of the menu.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
