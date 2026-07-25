package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.CasinoConfig;
import io.github.andrewwwwwwwwwwwwwww.vscasino.Sfx;
import io.github.andrewwwwwwwwwwwwwww.vscasino.Wager;
import io.github.andrewwwwwwwwwwwwwww.vscasino.card.Card;
import io.github.andrewwwwwwwwwwwwwww.vscasino.card.Deck;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Five-card draw video poker: deal five, hold any, draw replacements, get paid on the final hand.
 *
 * <p>Payouts are multiples of the stake taken straight from the table below, so unlike the slots
 * this game's return is set by the paytable itself rather than solved for. The table is the classic
 * conservative one (jacks-or-better pays even money), which lands a little under break-even.
 */
public class VideoPokerMenu extends ChestMenu {
    private static final int INFO_SLOT = 4;
    private static final int RULES_SLOT = 8;
    private static final int[] CARD_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] HOLD_SLOTS = {20, 21, 22, 23, 24};
    private static final int ROW = 27;                 // control row (9x4 menu)
    private static final int ACTION_SLOT = ROW + BetControls.ACTION;

    private enum Phase { BETTING, DRAW, DONE }

    /** Ranked hand categories, best first. What each pays comes from the config. */
    private enum HandRank {
        ROYAL_FLUSH("royal_flush", "Royal Flush"),
        STRAIGHT_FLUSH("straight_flush", "Straight Flush"),
        FOUR_KIND("four_kind", "Four of a Kind"),
        FULL_HOUSE("full_house", "Full House"),
        FLUSH("flush", "Flush"),
        STRAIGHT("straight", "Straight"),
        THREE_KIND("three_kind", "Three of a Kind"),
        TWO_PAIR("two_pair", "Two Pair"),
        JACKS_OR_BETTER("jacks_or_better", "Jacks or Better"),
        NOTHING("nothing", "No win");

        final String id;
        final String english;

        HandRank(String id, String english) {
            this.id = id;
            this.english = english;
        }
    }

    /** Gross return per unit staked for a hand, read live so {@code /casino reload} applies at once. */
    private static int payout(HandRank rank) {
        CasinoConfig cfg = CasinoConfig.get();
        return switch (rank) {
            case ROYAL_FLUSH -> cfg.pokerPayRoyalFlush;
            case STRAIGHT_FLUSH -> cfg.pokerPayStraightFlush;
            case FOUR_KIND -> cfg.pokerPayFourKind;
            case FULL_HOUSE -> cfg.pokerPayFullHouse;
            case FLUSH -> cfg.pokerPayFlush;
            case STRAIGHT -> cfg.pokerPayStraight;
            case THREE_KIND -> cfg.pokerPayThreeKind;
            case TWO_PAIR -> cfg.pokerPayTwoPair;
            case JACKS_OR_BETTER -> cfg.pokerPayJacksOrBetter;
            case NOTHING -> 0;
        };
    }

    private static final class Game {
        final Deck deck = new Deck(new Random());
        final List<Card> hand = new ArrayList<>();
        final boolean[] held = new boolean[5];
        int bet;
        Phase phase = Phase.BETTING;
        String result;
        ChatFormatting resultColour = ChatFormatting.GRAY;
    }

    private static final Map<UUID, Game> GAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BETS = new ConcurrentHashMap<>();

    private final ServerPlayer player;
    private final SimpleContainer container;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new VideoPokerMenu(syncId, inv, (ServerPlayer) p),
                Component.literal(Lang.tr(player, "vscasino.menu.poker.title", "Video Poker"))));
    }

    private VideoPokerMenu(int syncId, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x4, syncId, inv, new SimpleContainer(36), 4);
        this.player = player;
        this.container = (SimpleContainer) getContainer();
        populate();
    }

    private Game game() {
        return GAMES.computeIfAbsent(player.getUUID(), id -> new Game());
    }

    private int bet() {
        CasinoConfig cfg = CasinoConfig.get();
        int bet = BETS.getOrDefault(player.getUUID(), cfg.minBet);
        return Math.max(cfg.minBet, Math.min(cfg.maxBet, bet));
    }

    // ---- hand evaluation -----------------------------------------------------------------

    static HandRank evaluate(List<Card> hand) {
        int[] rankCounts = new int[15];
        int[] suitCounts = new int[4];
        for (Card c : hand) {
            rankCounts[c.rank().pokerRank()]++;
            suitCounts[c.suit().ordinal()]++;
        }

        boolean flush = Arrays.stream(suitCounts).anyMatch(n -> n == 5);

        int[] present = new int[5];
        int n = 0;
        for (int r = 2; r <= 14 && n < 5; r++) if (rankCounts[r] > 0) present[n++] = r;
        boolean straight = n == 5 && present[4] - present[0] == 4;
        // Wheel: A-2-3-4-5, where the ace plays low.
        boolean wheel = n == 5 && present[0] == 2 && present[1] == 3
                && present[2] == 4 && present[3] == 5 && present[4] == 14;
        boolean anyStraight = straight || wheel;
        boolean royal = straight && present[0] == 10;

        int trips = 0, pairs = 0, quads = 0;
        int highPair = 0;
        for (int r = 2; r <= 14; r++) {
            if (rankCounts[r] == 4) quads++;
            else if (rankCounts[r] == 3) trips++;
            else if (rankCounts[r] == 2) { pairs++; highPair = Math.max(highPair, r); }
        }

        if (royal && flush) return HandRank.ROYAL_FLUSH;
        if (anyStraight && flush) return HandRank.STRAIGHT_FLUSH;
        if (quads > 0) return HandRank.FOUR_KIND;
        if (trips > 0 && pairs > 0) return HandRank.FULL_HOUSE;
        if (flush) return HandRank.FLUSH;
        if (anyStraight) return HandRank.STRAIGHT;
        if (trips > 0) return HandRank.THREE_KIND;
        if (pairs >= 2) return HandRank.TWO_PAIR;
        if (pairs == 1 && highPair >= 11) return HandRank.JACKS_OR_BETTER;
        return HandRank.NOTHING;
    }

    // ---- rendering -----------------------------------------------------------------------

    private void populate() {
        Game g = game();
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

        List<String> info = new ArrayList<>();
        info.add(t("vscasino.menu.balance", "Balance: %d Quest Shards", Wager.balance(player)));
        info.add(t("vscasino.poker.paytable", "Jacks or better pays. Royal flush pays %dx.",
                payout(HandRank.ROYAL_FLUSH)));
        container.setItem(INFO_SLOT, Guis.button(Items.BRICK,
                t("vscasino.menu.poker.title", "Video Poker"), ChatFormatting.AQUA, info, "vscasino:poker_icon"));

        for (int i = 0; i < 5; i++) {
            if (g.hand.size() > i) {
                container.setItem(CARD_SLOTS[i], g.hand.get(i).toStack(player,
                        g.phase == Phase.DRAW
                                ? List.of(t("vscasino.poker.toggle_hold", "Click to hold or release."))
                                : null));
                if (g.phase == Phase.DRAW) {
                    container.setItem(HOLD_SLOTS[i], g.held[i]
                            ? Guis.button(Items.LIME_DYE, t("vscasino.poker.held", "HELD"), ChatFormatting.GREEN,
                                    null, "vscasino:held_marker")
                            : Guis.button(Items.GRAY_DYE, t("vscasino.poker.not_held", "Discard"), ChatFormatting.DARK_GRAY, null, "vscasino:discard_marker"));
                }
            } else {
                container.setItem(CARD_SLOTS[i], Guis.button(Items.GRAY_STAINED_GLASS_PANE,
                        t("vscasino.poker.empty", "-"), ChatFormatting.DARK_GRAY));
            }
        }

        switch (g.phase) {
            case BETTING -> {
                BetControls.render(container, ROW, player);
                List<Component> lore = new ArrayList<>();
                lore.add(Guis.styled(t("vscasino.menu.bet_current", "Bet: %d Quest Shards", bet()),
                        ChatFormatting.GOLD));
                if (g.result != null) lore.add(Guis.styled(g.result, g.resultColour));
                container.setItem(ACTION_SLOT, Guis.coloured(Items.EMERALD,
                        t("vscasino.poker.deal", "Deal"), ChatFormatting.YELLOW, lore));
            }
            case DRAW -> container.setItem(ACTION_SLOT, Guis.button(Items.EMERALD,
                    t("vscasino.poker.draw", "Draw"), ChatFormatting.YELLOW,
                    List.of(t("vscasino.poker.draw.desc", "Replace every card you did not hold."))));
            case DONE -> {
                List<Component> lore = new ArrayList<>();
                if (g.result != null) lore.add(Guis.styled(g.result, g.resultColour));
                container.setItem(ACTION_SLOT, Guis.coloured(Items.EMERALD,
                        t("vscasino.poker.again", "Play again"), ChatFormatting.YELLOW, lore));
            }
        }

        container.setItem(RULES_SLOT, RulesBook.button(player));
        container.setItem(ROW + BetControls.BACK, Guis.button(Items.ARROW,
                t("vscasino.menu.back", "Back"), ChatFormatting.AQUA));
        container.setItem(ROW + BetControls.CLOSE, Guis.button(Items.BARRIER,
                t("vscasino.menu.close", "Close"), ChatFormatting.RED));
    }

    private String t(String key, String fallback, Object... args) {
        return Lang.tr(player, key, fallback, args);
    }

    private void refresh() {
        populate();
        broadcastChanges();
    }

    // ---- interaction ---------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;
        Game g = game();

        if (slotId == ROW + BetControls.CLOSE) { sp.closeContainer(); return; }
        if (slotId == ROW + BetControls.BACK) { CasinoMenu.open(sp); return; }
        if (slotId == RULES_SLOT) { RulesBook.openPoker(sp); return; }

        if (g.phase == Phase.BETTING) {
            int newBet = BetControls.handle(slotId, ROW, bet());
            if (newBet >= 0) {
                BETS.put(sp.getUUID(), newBet);
                refresh();
                return;
            }
        }

        if (g.phase == Phase.DRAW) {
            for (int i = 0; i < 5; i++) {
                if (slotId == CARD_SLOTS[i] || slotId == HOLD_SLOTS[i]) {
                    g.held[i] = !g.held[i];
                    Sfx.betChange(sp);          // small click so holding feels responsive
                    refresh();
                    return;
                }
            }
        }

        if (slotId == ACTION_SLOT) {
            switch (g.phase) {
                case BETTING -> deal(sp, g);
                case DRAW -> draw(sp, g);
                case DONE -> {
                    reset(g);
                    refresh();
                }
            }
        }
    }

    private void deal(ServerPlayer sp, Game g) {
        int bet = bet();
        if (!Wager.take(sp, bet)) return;
        g.bet = bet;
        g.result = null;
        g.hand.clear();
        Arrays.fill(g.held, false);
        g.deck.reset();
        for (int i = 0; i < 5; i++) g.hand.add(g.deck.deal());
        g.phase = Phase.DRAW;
        Sfx.deal(sp);
        refresh();
    }

    private void draw(ServerPlayer sp, Game g) {
        for (int i = 0; i < 5; i++) {
            if (!g.held[i]) g.hand.set(i, g.deck.deal());
        }
        Sfx.deal(sp);
        HandRank rank = evaluate(g.hand);
        int gross = g.bet * payout(rank);
        if (gross > 0) {
            int paid = Wager.pay(sp, gross);
            int net = paid - g.bet;
            g.result = net > 0
                    ? t("vscasino.poker.win", "%s! You won %d.",
                        t("vscasino.poker.hand." + rank.id, rank.english), net)
                    : t("vscasino.poker.push", "%s - stake returned.",
                        t("vscasino.poker.hand." + rank.id, rank.english));
            g.resultColour = net > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            // Anything above a flush is rare enough to deserve the big cue.
            if (rank.ordinal() <= HandRank.FLUSH.ordinal()) Sfx.jackpot(sp);
            else if (net > 0) Sfx.win(sp);
            else Sfx.win(sp);
        } else {
            g.result = t("vscasino.poker.lose", "No win. Lost %d.", g.bet);
            g.resultColour = ChatFormatting.RED;
            Sfx.lose(sp);
        }
        g.phase = Phase.DONE;
        refresh();
    }

    /** Clear a finished round so the machine reads as fresh, including last round's result text. */
    private static void reset(Game g) {
        g.phase = Phase.BETTING;
        g.hand.clear();
        Arrays.fill(g.held, false);
        g.result = null;
    }

    @Override
    public void removed(Player player) {
        // Leaving clears a finished round; a hand mid-draw is kept so the stake isn't wasted.
        Game g = GAMES.get(this.player.getUUID());
        if (g != null && g.phase == Phase.DONE) reset(g);
        super.removed(player);
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
