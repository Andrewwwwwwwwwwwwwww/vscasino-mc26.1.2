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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blackjack against the house, with doubling and splitting.
 *
 * <p>House rules: dealer stands on all 17s, blackjack pays the configured rate (3:2 by default),
 * double is allowed on any two-card hand including after a split, a hand may be split once, and
 * split aces receive exactly one card each. A hand made 21 by splitting is a plain 21, not a
 * blackjack — that is the standard rule and it matters, because it pays even money.
 *
 * <p>Hands live in {@link #GAMES} keyed by player rather than in the menu, so closing the screen
 * mid-hand resumes instead of forfeiting an already-debited stake.
 */
public class BlackjackMenu extends ChestMenu {
    private static final int DEALER_INFO = 0;
    private static final int[] DEALER_CARDS = {1, 2, 3, 4, 5, 6, 7};
    private static final int RULES_SLOT = 8;
    private static final int[] HAND_INFO = {18, 27};
    private static final int[][] HAND_CARDS = {
            {19, 20, 21, 22, 23, 24, 25},
            {28, 29, 30, 31, 32, 33, 34}};
    private static final int ROW = 45;                 // control row (9x6 menu)

    // Action buttons while playing a hand.
    private static final int HIT_SLOT = ROW + 2;
    private static final int STAND_SLOT = ROW + 3;
    private static final int DOUBLE_SLOT = ROW + 5;
    private static final int SPLIT_SLOT = ROW + 6;

    private enum Phase { BETTING, PLAYING, DONE }

    /** One player hand. After a split there are two, each with its own stake. */
    private static final class Hand {
        final List<Card> cards = new ArrayList<>();
        int bet;
        boolean done;
        boolean doubled;
        boolean splitAce;      // dealt from split aces: exactly one card, no further action
        String result;
        ChatFormatting resultColour = ChatFormatting.GRAY;
    }

    private static final class Game {
        final Deck deck = new Deck(new Random());
        final List<Hand> hands = new ArrayList<>();
        final List<Card> dealer = new ArrayList<>();
        int active;
        Phase phase = Phase.BETTING;
        String summary;
        ChatFormatting summaryColour = ChatFormatting.GRAY;
    }

    private static final Map<UUID, Game> GAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> BETS = new ConcurrentHashMap<>();

    private final ServerPlayer player;
    private final SimpleContainer container;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new BlackjackMenu(syncId, inv, (ServerPlayer) p),
                Component.literal(Lang.tr(player, "vscasino.menu.blackjack.title", "Blackjack"))));
    }

    private BlackjackMenu(int syncId, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, inv, new SimpleContainer(54), 6);
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

    /** Best total for a hand: aces count 11 until that would bust, then 1. */
    public static int value(List<Card> hand) {
        int total = 0;
        int aces = 0;
        for (Card c : hand) {
            total += c.rank().blackjackValue;
            if (c.rank() == Card.Rank.ACE) aces++;
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    /** A two-card 21 dealt at the start. A split hand making 21 does not count. */
    private static boolean isNatural(Hand hand, int handCount) {
        return handCount == 1 && hand.cards.size() == 2 && value(hand.cards) == 21;
    }

    private boolean canDouble(Game g, Hand h) {
        return CasinoConfig.get().blackjackAllowDouble && h.cards.size() == 2 && !h.doubled && !h.splitAce
                && Wager.balance(player) >= h.bet;
    }

    private boolean canSplit(Game g, Hand h) {
        return CasinoConfig.get().blackjackAllowSplit && g.hands.size() == 1 && h.cards.size() == 2 && !h.splitAce
                && h.cards.get(0).rank().blackjackValue == h.cards.get(1).rank().blackjackValue
                && Wager.balance(player) >= h.bet;
    }

    // ---- rendering -----------------------------------------------------------------------

    private void populate() {
        Game g = game();
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

        boolean hideHole = g.phase == Phase.PLAYING;
        int dealerShown = hideHole && g.dealer.size() > 1 ? value(g.dealer.subList(0, 1)) : value(g.dealer);

        container.setItem(DEALER_INFO, Guis.button(Items.WITHER_SKELETON_SKULL,
                t("vscasino.blackjack.dealer", "Dealer"), ChatFormatting.DARK_RED,
                g.dealer.isEmpty() ? List.of()
                        : List.of(hideHole
                                ? t("vscasino.blackjack.showing", "Showing: %d", dealerShown)
                                : t("vscasino.blackjack.total", "Total: %d", dealerShown))));

        for (int i = 0; i < g.dealer.size() && i < DEALER_CARDS.length; i++) {
            boolean hidden = hideHole && i == 1;
            container.setItem(DEALER_CARDS[i],
                    hidden ? Card.faceDown(player) : g.dealer.get(i).toStack(player));
        }

        container.setItem(RULES_SLOT, RulesBook.button(player));

        for (int h = 0; h < g.hands.size() && h < HAND_INFO.length; h++) {
            Hand hand = g.hands.get(h);
            boolean isActive = g.phase == Phase.PLAYING && h == g.active;
            List<String> lore = new ArrayList<>();
            lore.add(t("vscasino.blackjack.total", "Total: %d", value(hand.cards)));
            lore.add(t("vscasino.menu.bet_current", "Bet: %d Quest Shards", hand.bet));
            if (isActive) lore.add(t("vscasino.blackjack.active", "Playing this hand"));
            if (hand.result != null) lore.add(hand.result);

            String handTitle = g.hands.size() > 1
                    ? t("vscasino.blackjack.hand_n", "Hand %d", h + 1)
                    : t("vscasino.blackjack.you", "Your hand");
            if (hand.result != null) {
                // The hand is decided (bust, win, push or lose) — show a face-down card rather
                // than the "in progress" head/paper icon, so it's obvious at a glance the hand is over.
                container.setItem(HAND_INFO[h], Guis.button(Items.MAP, handTitle,
                        ChatFormatting.DARK_GRAY, lore, "vscasino:card_back"));
            } else {
                container.setItem(HAND_INFO[h], Guis.button(
                        isActive ? Items.PLAYER_HEAD : Items.PAPER, handTitle,
                        isActive ? ChatFormatting.YELLOW : ChatFormatting.GREEN, lore));
            }

            for (int i = 0; i < hand.cards.size() && i < HAND_CARDS[h].length; i++) {
                container.setItem(HAND_CARDS[h][i], hand.cards.get(i).toStack(player));
            }
        }

        switch (g.phase) {
            case BETTING -> {
                BetControls.render(container, ROW, player);
                List<Component> lore = new ArrayList<>();
                lore.add(Guis.styled(t("vscasino.menu.bet_current", "Bet: %d Quest Shards", bet()),
                        ChatFormatting.GOLD));
                lore.add(Guis.styled(t("vscasino.menu.balance", "Balance: %d Quest Shards",
                        Wager.balance(player)), ChatFormatting.GRAY));
                if (g.summary != null) lore.add(Guis.styled(g.summary, g.summaryColour));
                container.setItem(ROW + BetControls.ACTION, Guis.coloured(Items.EMERALD,
                        t("vscasino.blackjack.deal", "Deal"), ChatFormatting.YELLOW, lore));
            }
            case PLAYING -> {
                Hand h = g.hands.get(g.active);
                container.setItem(HIT_SLOT, Guis.button(Items.LIME_DYE,
                        t("vscasino.blackjack.hit", "Hit"), ChatFormatting.GREEN,
                        List.of(t("vscasino.blackjack.hit.desc", "Take another card.")), "vscasino:hit_button"));
                container.setItem(STAND_SLOT, Guis.button(Items.RED_DYE,
                        t("vscasino.blackjack.stand", "Stand"), ChatFormatting.RED,
                        List.of(t("vscasino.blackjack.stand.desc", "Keep this hand and move on.")), "vscasino:stand_button"));
                if (canDouble(g, h)) {
                    container.setItem(DOUBLE_SLOT, Guis.button(Items.COPPER_INGOT,
                            t("vscasino.blackjack.double", "Double Down"), ChatFormatting.GOLD,
                            List.of(t("vscasino.blackjack.double.desc",
                                    "Double your bet, take exactly one more card.")), "vscasino:double_button"));
                }
                if (canSplit(g, h)) {
                    container.setItem(SPLIT_SLOT, Guis.button(Items.SHEARS,
                            t("vscasino.blackjack.split", "Split"), ChatFormatting.AQUA,
                            List.of(t("vscasino.blackjack.split.desc",
                                    "Split into two hands, each with its own bet.")), "vscasino:split_button"));
                }
            }
            case DONE -> {
                List<Component> lore = new ArrayList<>();
                if (g.summary != null) lore.add(Guis.styled(g.summary, g.summaryColour));
                lore.add(Guis.styled(t("vscasino.menu.balance", "Balance: %d Quest Shards",
                        Wager.balance(player)), ChatFormatting.GRAY));
                container.setItem(ROW + BetControls.ACTION, Guis.coloured(Items.EMERALD,
                        t("vscasino.blackjack.again", "Play again"), ChatFormatting.YELLOW, lore));
            }
        }

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
        if (slotId == RULES_SLOT) { RulesBook.openBlackjack(sp); return; }

        if (g.phase == Phase.BETTING) {
            int newBet = BetControls.handle(slotId, ROW, bet());
            if (newBet >= 0) {
                if (newBet != bet()) Sfx.betChange(sp);
                BETS.put(sp.getUUID(), newBet);
                refresh();
                return;
            }
            if (slotId == ROW + BetControls.ACTION) startHand(sp, g);
            return;
        }

        if (g.phase == Phase.PLAYING) {
            Hand h = g.hands.get(g.active);
            if (slotId == HIT_SLOT) { hit(sp, g, h); return; }
            if (slotId == STAND_SLOT) { h.done = true; advance(sp, g); return; }
            if (slotId == DOUBLE_SLOT && canDouble(g, h)) { doubleDown(sp, g, h); return; }
            if (slotId == SPLIT_SLOT && canSplit(g, h)) { split(sp, g, h); return; }
            return;
        }

        if (g.phase == Phase.DONE && slotId == ROW + BetControls.ACTION) {
            reset(g);
            refresh();
        }
    }

    /** Clear a finished round so the table reads as fresh, including last round's result text. */
    private static void reset(Game g) {
        g.phase = Phase.BETTING;
        g.hands.clear();
        g.dealer.clear();
        g.active = 0;
        g.summary = null;
    }

    @Override
    public void removed(Player player) {
        // Leaving the table clears a finished round, so coming back doesn't show a stale result.
        // A hand still in progress is kept: the stake is already down, and resuming beats losing it.
        Game g = GAMES.get(this.player.getUUID());
        if (g != null && g.phase == Phase.DONE) reset(g);
        super.removed(player);
    }

    private void startHand(ServerPlayer sp, Game g) {
        int bet = bet();
        if (!Wager.take(sp, bet)) return;      // stake debited before a single card is dealt
        g.hands.clear();
        g.dealer.clear();
        g.active = 0;
        g.summary = null;
        g.deck.reset();

        Hand hand = new Hand();
        hand.bet = bet;
        g.hands.add(hand);

        hand.cards.add(g.deck.deal());
        g.dealer.add(g.deck.deal());
        hand.cards.add(g.deck.deal());
        g.dealer.add(g.deck.deal());
        Sfx.deal(sp);
        g.phase = Phase.PLAYING;

        if (isNatural(hand, g.hands.size())) {
            hand.done = true;
            finish(sp, g);
            return;
        }
        refresh();
    }

    private void hit(ServerPlayer sp, Game g, Hand h) {
        h.cards.add(g.deck.deal());
        Sfx.card(sp, h.cards.size());
        // Only a bust ends the hand for you. Standing on 21 is your call, not the table's — and
        // running out of card slots is the one other stopping point.
        if (value(h.cards) > 21 || h.cards.size() >= HAND_CARDS[0].length) {
            h.done = true;
            advance(sp, g);
            return;
        }
        refresh();
    }

    private void doubleDown(ServerPlayer sp, Game g, Hand h) {
        if (!Wager.take(sp, h.bet)) return;    // the extra stake, same limits and checks
        h.bet *= 2;
        h.doubled = true;
        h.cards.add(g.deck.deal());
        h.done = true;
        advance(sp, g);
    }

    private void split(ServerPlayer sp, Game g, Hand h) {
        if (!Wager.take(sp, h.bet)) return;    // second hand needs its own stake
        Hand second = new Hand();
        second.bet = h.bet;
        second.cards.add(h.cards.remove(1));
        g.hands.add(second);

        boolean aces = CasinoConfig.get().blackjackSplitAcesOneCard && h.cards.get(0).rank() == Card.Rank.ACE;
        h.cards.add(g.deck.deal());
        second.cards.add(g.deck.deal());

        if (aces) {                            // split aces get one card each and stand
            h.splitAce = true;
            second.splitAce = true;
            h.done = true;
            second.done = true;
            finish(sp, g);
            return;
        }
        refresh();
    }

    /** Move to the next unfinished hand, or let the dealer play if there are none. */
    private void advance(ServerPlayer sp, Game g) {
        while (g.active < g.hands.size() && g.hands.get(g.active).done) g.active++;
        if (g.active >= g.hands.size()) {
            finish(sp, g);
            return;
        }
        refresh();
    }

    /** Play out the dealer (if anyone is still live), settle every hand, and end the round. */
    private void finish(ServerPlayer sp, Game g) {
        boolean anyLive = g.hands.stream().anyMatch(h -> value(h.cards) <= 21);
        if (anyLive) {
            while (value(g.dealer) < CasinoConfig.get().blackjackDealerStandsOn) g.dealer.add(g.deck.deal());
        }
        int dealerTotal = value(g.dealer);
        boolean dealerNatural = g.dealer.size() == 2 && dealerTotal == 21;
        int totalPaid = 0;
        int totalStaked = 0;

        for (Hand h : g.hands) {
            totalStaked += h.bet;
            int total = value(h.cards);
            int gross;
            if (total > 21) {
                gross = 0;
                h.result = t("vscasino.blackjack.bust", "Bust");
                h.resultColour = ChatFormatting.RED;
            } else if (isNatural(h, g.hands.size()) && !dealerNatural) {
                gross = h.bet + (int) Math.round(h.bet
                        * (CasinoConfig.get().blackjackNaturalPercent / 100.0));
                h.result = t("vscasino.blackjack.natural", "Blackjack!");
                h.resultColour = ChatFormatting.GOLD;
            } else if (dealerTotal > 21 || total > dealerTotal) {
                gross = h.bet * 2;
                h.result = t("vscasino.blackjack.hand_win", "Win");
                h.resultColour = ChatFormatting.GREEN;
            } else if (total == dealerTotal) {
                gross = h.bet;
                h.result = t("vscasino.blackjack.hand_push", "Push");
                h.resultColour = ChatFormatting.YELLOW;
            } else {
                gross = 0;
                h.result = t("vscasino.blackjack.hand_lose", "Lose");
                h.resultColour = ChatFormatting.RED;
            }
            if (gross > 0) totalPaid += Wager.pay(sp, gross);
        }

        int net = totalPaid - totalStaked;
        boolean anyNatural = g.hands.stream().anyMatch(h -> isNatural(h, g.hands.size()));
        if (net > 0) {
            g.summary = t("vscasino.blackjack.round_win", "You won %d Quest Shards.", net);
            g.summaryColour = ChatFormatting.GREEN;
            if (anyNatural) Sfx.jackpot(sp);
            else if (totalPaid >= CasinoConfig.get().bigWinLogThreshold) Sfx.bigWin(sp);
            else Sfx.win(sp);
        } else if (net < 0) {
            g.summary = t("vscasino.blackjack.round_lose", "You lost %d Quest Shards.", -net);
            g.summaryColour = ChatFormatting.RED;
            Sfx.lose(sp);
        } else {
            g.summary = t("vscasino.blackjack.round_push", "You broke even.");
            g.summaryColour = ChatFormatting.YELLOW;
            Sfx.win(sp);
        }
        g.phase = Phase.DONE;
        refresh();
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
