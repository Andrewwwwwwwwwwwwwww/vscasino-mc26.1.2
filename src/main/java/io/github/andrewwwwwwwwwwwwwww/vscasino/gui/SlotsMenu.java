package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.CasinoConfig;
import io.github.andrewwwwwwwwwwwwwww.vscasino.Sfx;
import io.github.andrewwwwwwwwwwwwwww.vscasino.Wager;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three-reel slots.
 *
 * <p>The paytable's <i>shape</i> comes from the config (which symbols are rare, what matches pay)
 * but its <i>level</i> is solved for: {@link #payoutScale} computes the theoretical return of the
 * configured table and scales every payout to hit {@code slotsRtpPercent} exactly. Editing a weight
 * changes how wins feel, never how much the machine pays out overall.
 *
 * <p>The spin animation is purely cosmetic. The outcome is rolled and the payout is decided the
 * instant the lever is pulled; the reels then play catch-up. That ordering matters — closing the
 * screen or disconnecting mid-spin still settles correctly instead of losing the result.
 */
public class SlotsMenu extends ChestMenu {
    private static final int INFO_SLOT = 4;
    private static final int RULES_SLOT = 8;
    private static final int[] REEL_SLOTS = {11, 13, 15};
    private static final int ROW = 18;                 // control row (9x3 menu)
    private static final int SPIN_SLOT = ROW + BetControls.ACTION;

    /** A reel symbol: how often it shows up, and what matches of it are worth. */
    private record Symbol(String id, Item item, int weight, double payThree, double payTwo) {}

    /** Which item draws each symbol id. Ids in the config that aren't here are ignored. */
    private static final Map<String, Item> SYMBOL_ITEMS = Map.of(
            "coal", Items.COAL,
            "wheat", Items.WHEAT,
            "iron", Items.IRON_INGOT,
            "gold", Items.GOLD_INGOT,
            "emerald", Items.EMERALD,
            "diamond", Items.DIAMOND);

    /** Persisted per player so a chosen stake survives closing and reopening the machine. */
    private static final Map<UUID, Integer> BETS = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    /** Machines mid-spin, advanced once per server tick. */
    private static final Set<SlotsMenu> SPINNING = ConcurrentHashMap.newKeySet();

    private final ServerPlayer player;
    private final SimpleContainer container;

    private Symbol[] reels;
    private String resultLine;
    private ChatFormatting resultColour = ChatFormatting.GRAY;

    // Animation state. The outcome is already decided while these run.
    private Symbol[] pendingReels;
    private int pendingBet;
    private int pendingGross;
    private int ticksLeft;
    private int lockedReels;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new SlotsMenu(syncId, inv, (ServerPlayer) p),
                Component.literal(Lang.tr(player, "vscasino.menu.slots.title", "Slots"))));
    }

    private SlotsMenu(int syncId, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, syncId, inv, new SimpleContainer(27), 3);
        this.player = player;
        this.container = (SimpleContainer) getContainer();
        populate();
    }

    // ---- paytable ------------------------------------------------------------------------

    /** The active symbol set, read fresh from config so {@code /casino reload} takes effect at once. */
    private static List<Symbol> symbols() {
        Map<String, CasinoConfig.SymbolSettings> configured = CasinoConfig.get().slotsSymbols;
        List<Symbol> out = new ArrayList<>();
        for (Map.Entry<String, CasinoConfig.SymbolSettings> e : new LinkedHashMap<>(configured).entrySet()) {
            Item item = SYMBOL_ITEMS.get(e.getKey());
            if (item == null || e.getValue() == null || e.getValue().weight <= 0) continue;
            out.add(new Symbol(e.getKey(), item, e.getValue().weight,
                    e.getValue().payThree, e.getValue().payTwo));
        }
        return out;
    }

    private static int totalWeight(List<Symbol> symbols) {
        return symbols.stream().mapToInt(Symbol::weight).sum();
    }

    /**
     * Factor turning the configured paytable into one that returns {@code slotsRtpPercent}.
     * Derived from the exact probability of every paying outcome.
     */
    private static double payoutScale(List<Symbol> symbols) {
        int total = totalWeight(symbols);
        if (total <= 0) return 0;
        double natural = 0;
        for (Symbol s : symbols) {
            double p = (double) s.weight() / total;
            natural += Math.pow(p, 3) * s.payThree();        // all three match
            natural += 3 * p * p * (1 - p) * s.payTwo();     // exactly two match
        }
        if (natural <= 0) return 0;
        return (CasinoConfig.get().slotsRtpPercent / 100.0) / natural;
    }

    private static Symbol spinReel(List<Symbol> symbols) {
        int total = totalWeight(symbols);
        int roll = RANDOM.nextInt(Math.max(1, total));
        int acc = 0;
        for (Symbol s : symbols) {
            acc += s.weight();
            if (roll < acc) return s;
        }
        return symbols.get(symbols.size() - 1);
    }

    /** Gross return for a spin, stake included. 0 means the stake is lost. */
    private static int evaluate(List<Symbol> symbols, Symbol[] reels, int bet) {
        double scale = payoutScale(symbols);
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            return (int) Math.round(bet * reels[0].payThree() * scale);
        }
        Symbol pair = null;
        if (reels[0] == reels[1] || reels[0] == reels[2]) pair = reels[0];
        else if (reels[1] == reels[2]) pair = reels[1];
        if (pair != null) return (int) Math.round(bet * pair.payTwo() * scale);
        return 0;
    }

    private int bet() {
        CasinoConfig cfg = CasinoConfig.get();
        int bet = BETS.getOrDefault(player.getUUID(), cfg.minBet);
        return Math.max(cfg.minBet, Math.min(cfg.maxBet, bet));
    }

    private boolean spinning() {
        return ticksLeft > 0;
    }

    // ---- animation -----------------------------------------------------------------------

    /** Advance every spinning machine. Registered on the server tick event. */
    public static void tickAll() {
        for (SlotsMenu menu : SPINNING) {
            try {
                menu.tick();
            } catch (Exception e) {
                io.github.andrewwwwwwwwwwwwwww.vscasino.VsCasino.LOGGER
                        .warn("Slots animation failed; settling immediately", e);
                menu.settle();
            }
        }
    }

    private void tick() {
        // Player walked away mid-spin: pay out now rather than leaving shards in limbo.
        if (player.containerMenu != this || player.hasDisconnected()) {
            settle();
            return;
        }

        ticksLeft--;
        CasinoConfig cfg = CasinoConfig.get();
        List<Symbol> symbols = symbols();

        // Reels lock left to right, one every stagger interval.
        int shouldBeLocked = 3 - Math.min(3, (ticksLeft / Math.max(1, cfg.slotsReelStaggerTicks)) + 1);
        shouldBeLocked = Math.max(0, Math.min(3, shouldBeLocked));
        while (lockedReels < shouldBeLocked) {
            reels[lockedReels] = pendingReels[lockedReels];
            Sfx.reelStop(player, lockedReels);
            lockedReels++;
        }
        for (int i = lockedReels; i < 3; i++) {
            reels[i] = symbols.isEmpty() ? pendingReels[i] : spinReel(symbols);
        }

        if (ticksLeft <= 0) {
            settle();
            return;
        }
        populate();
        broadcastChanges();
    }

    /** End the spin: show the real reels, pay, and report. Safe to call more than once. */
    private void settle() {
        if (pendingReels == null) {
            SPINNING.remove(this);
            ticksLeft = 0;
            return;
        }
        reels = pendingReels;
        int gross = pendingGross;
        int bet = pendingBet;
        pendingReels = null;
        ticksLeft = 0;
        lockedReels = 3;
        SPINNING.remove(this);

        if (gross > 0) {
            int paid = Wager.pay(player, gross);
            int net = paid - bet;
            if (net > 0) {
                resultLine = t("vscasino.slots.win", "You won %d Quest Shards!", net);
                resultColour = ChatFormatting.GREEN;
                boolean triple = reels[0] == reels[1] && reels[1] == reels[2];
                if (triple) Sfx.jackpot(player);
                else if (paid >= CasinoConfig.get().bigWinLogThreshold) Sfx.bigWin(player);
                else Sfx.win(player);
            } else {
                resultLine = t("vscasino.slots.push", "Broke even.");
                resultColour = ChatFormatting.YELLOW;
                Sfx.win(player);
            }
        } else {
            resultLine = t("vscasino.slots.lose", "No match. Lost %d Quest Shards.", bet);
            resultColour = ChatFormatting.RED;
            Sfx.lose(player);
        }

        if (player.containerMenu == this) {
            populate();
            broadcastChanges();
        }
    }

    // ---- rendering -----------------------------------------------------------------------

    private void populate() {
        CasinoConfig cfg = CasinoConfig.get();
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

        List<String> info = new ArrayList<>();
        info.add(t("vscasino.menu.balance", "Balance: %d Quest Shards", Wager.balance(player)));
        info.add(t("vscasino.slots.paytable", "Three of a kind pays big; two pays small."));
        info.add(t("vscasino.slots.rtp", "Payout rate: %d%%", cfg.slotsRtpPercent));
        container.setItem(INFO_SLOT, Guis.button(Items.GOLD_BLOCK,
                t("vscasino.menu.slots.title", "Slots"), ChatFormatting.YELLOW, info, "vscasino:slots_icon"));

        for (int i = 0; i < REEL_SLOTS.length; i++) {
            if (reels == null || reels[i] == null) {
                container.setItem(REEL_SLOTS[i], Guis.button(Items.GRAY_STAINED_GLASS_PANE,
                        t("vscasino.slots.reel_empty", "- - -"), ChatFormatting.DARK_GRAY));
            } else {
                Symbol s = reels[i];
                boolean locked = !spinning() || i < lockedReels;
                container.setItem(REEL_SLOTS[i], Guis.button(s.item(),
                        t("vscasino.slots.symbol." + s.id(), s.id()),
                        locked ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            }
        }

        container.setItem(RULES_SLOT, RulesBook.button(player));
        if (!spinning()) BetControls.render(container, ROW, player);

        List<Component> spinLore = new ArrayList<>();
        spinLore.add(Guis.styled(t("vscasino.menu.bet_current", "Bet: %d Quest Shards", bet()),
                ChatFormatting.GOLD));
        if (resultLine != null) spinLore.add(Guis.styled(resultLine, resultColour));
        container.setItem(SPIN_SLOT, Guis.model(Guis.coloured(Items.LEVER,
                spinning() ? t("vscasino.slots.spinning", "Spinning...")
                           : t("vscasino.slots.spin", "SPIN"),
                spinning() ? ChatFormatting.GRAY : ChatFormatting.YELLOW, spinLore),
                "vscasino:spin_lever"));

        container.setItem(ROW + BetControls.BACK, Guis.button(Items.ARROW,
                t("vscasino.menu.back", "Back"), ChatFormatting.AQUA));
        container.setItem(ROW + BetControls.CLOSE, Guis.button(Items.BARRIER,
                t("vscasino.menu.close", "Close"), ChatFormatting.RED));
    }

    private String t(String key, String fallback, Object... args) {
        return Lang.tr(player, key, fallback, args);
    }

    // ---- interaction ---------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (!(clicker instanceof ServerPlayer sp)) return;

        if (slotId == ROW + BetControls.CLOSE) { sp.closeContainer(); return; }
        if (slotId == ROW + BetControls.BACK) { CasinoMenu.open(sp); return; }
        if (slotId == RULES_SLOT) { RulesBook.openSlots(sp); return; }
        if (spinning()) return;                       // no touching the machine mid-spin

        int newBet = BetControls.handle(slotId, ROW, bet());
        if (newBet >= 0) {
            if (newBet != bet()) Sfx.betChange(sp);
            BETS.put(sp.getUUID(), newBet);
            populate();
            broadcastChanges();
            return;
        }

        if (slotId == SPIN_SLOT) startSpin(sp);
    }

    private void startSpin(ServerPlayer sp) {
        CasinoConfig cfg = CasinoConfig.get();
        List<Symbol> symbols = symbols();
        if (symbols.isEmpty()) return;

        int bet = bet();
        if (!Wager.take(sp, bet)) return;             // stake debited here, atomically

        // Decide everything now; the animation is only presentation.
        pendingReels = new Symbol[]{spinReel(symbols), spinReel(symbols), spinReel(symbols)};
        pendingBet = bet;
        pendingGross = evaluate(symbols, pendingReels, bet);
        resultLine = null;
        Sfx.deal(sp);

        if (!cfg.slotsAnimationEnabled || cfg.slotsSpinTicks <= 0) {
            settle();
            return;
        }

        reels = new Symbol[]{spinReel(symbols), spinReel(symbols), spinReel(symbols)};
        lockedReels = 0;
        ticksLeft = cfg.slotsSpinTicks;
        SPINNING.add(this);
        populate();
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        if (spinning()) settle();                     // never strand a decided outcome
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
