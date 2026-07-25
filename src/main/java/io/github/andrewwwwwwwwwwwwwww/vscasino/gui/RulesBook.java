package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.CasinoConfig;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game rules books.
 *
 * <p>Each game's "How to play" button opens a written book whose <b>first page is a clickable
 * contents list</b> — every entry jumps straight to its section. One section is exactly one page,
 * so the contents can never drift out of sync with the content.
 *
 * <p>Pages are kept deliberately short. A book page fits roughly 14 lines of about 19 characters,
 * and anything past that is silently cut off by the client rather than flowing onto the next page.
 *
 * <p>Vanilla's own Done button cannot be intercepted by the server, so every page carries a
 * clickable link back to the game (and back to the contents), which runs {@code /casino game <id>}
 * and reopens the menu the player came from.
 */
public final class RulesBook {
    private RulesBook() {}

    /** One page. {@code id} keys both its contents title and its body text. */
    private record Section(String id, String title, String body) {}

    public static ItemStack button(ServerPlayer player) {
        return Guis.button(Items.BOOK,
                Lang.tr(player, "vscasino.rules.button", "How to play"), ChatFormatting.WHITE,
                List.of(Lang.tr(player, "vscasino.rules.button.desc", "Read the rules for this game.")));
    }

    public static void openBlackjack(ServerPlayer player) {
        CasinoConfig cfg = CasinoConfig.get();
        List<Section> sections = List.of(
                section(player, "blackjack", "goal", "The Goal",
                        "Beat the dealer by getting closer to 21 than they do, without going over.\n\nGo over and you bust: you lose at once, even if the dealer busts later."),
                section(player, "blackjack", "values", "Card Values",
                        "Number cards are worth their number.\n\nJ, Q and K are 10.\n\nAn Ace is 11, or 1 if that would bust you."),
                section(player, "blackjack", "playing", "Playing",
                        "Set a bet, press Deal.\n\nYou get two cards, dealer shows one.\n\nHit takes a card.\nStand ends your turn."),
                section(player, "blackjack", "double", "Double Down",
                        "On your first two cards you may Double Down.\n\nIt doubles your bet, gives you exactly one more card, then ends the hand."),
                section(player, "blackjack", "split", "Split",
                        "Two cards of the same value can be Split into two hands.\n\nThe new hand costs a second bet. You play each one in turn."),
                section(player, "blackjack", "split2", "Split (cont.)",
                        "Split Aces get one card each and stop there.\n\nA 21 made after splitting counts as a normal 21, not a blackjack."),
                section(player, "blackjack", "dealer", "The Dealer",
                        "Once you are done the dealer reveals their card and draws until they reach %d, then stops.\n\nIf they bust, you win.",
                        cfg.blackjackDealerStandsOn),
                section(player, "blackjack", "payouts", "Payouts",
                        "Win: double your bet back.\n\nPush (a tie): bet returned.\n\nBlackjack on the first two cards pays an extra %d%%.",
                        cfg.blackjackNaturalPercent));
        open(player, "blackjack",
                Lang.tr(player, "vscasino.rules.blackjack.title", "Blackjack Rules"), sections);
    }

    public static void openSlots(ServerPlayer player) {
        CasinoConfig cfg = CasinoConfig.get();
        List<Section> sections = List.of(
                section(player, "slots", "goal", "The Goal",
                        "Set a bet and pull the lever.\n\nThree reels spin and stop on a symbol each. Matching symbols pay."),
                section(player, "slots", "matches", "What Pays",
                        "Three matching symbols: the big payout.\n\nTwo matching: a small payout.\n\nNo match: you lose the bet."),
                section(player, "slots", "symbols", "The Symbols",
                        "Most common first:\n\nCoal\nWheat\nIron\nGold\nEmerald\nDiamond"),
                section(player, "slots", "symbols2", "Symbol Values",
                        "The rarer a symbol, the more a match of it pays.\n\nThree Diamonds is the jackpot."),
                section(player, "slots", "odds", "The Odds",
                        "This machine pays back about %d%% of all it takes, over time.\n\nEvery spin is independent. A loss does not make a win due.",
                        cfg.slotsRtpPercent));
        open(player, "slots", Lang.tr(player, "vscasino.rules.slots.title", "Slots Rules"), sections);
    }

    public static void openPoker(ServerPlayer player) {
        CasinoConfig cfg = CasinoConfig.get();
        List<Section> sections = List.of(
                section(player, "poker", "goal", "The Goal",
                        "You play alone, not against anyone.\n\nYou build one five card hand, and get paid if it is good enough."),
                section(player, "poker", "round", "A Round",
                        "1. Set a bet, press Deal.\n2. You get five cards.\n3. Click cards to keep.\n4. Press Draw.\n5. The final hand pays."),
                section(player, "poker", "holding", "Holding Cards",
                        "Clicking a card switches it between HELD and Discard.\n\nHELD cards stay. The rest are replaced when you press Draw."),
                section(player, "poker", "pay1", "Payouts",
                        "You need a pair of Jacks or better. A low pair pays nothing.\n\nJacks+: %dx\nTwo Pair: %dx\nThree of a Kind: %dx",
                        cfg.pokerPayJacksOrBetter, cfg.pokerPayTwoPair, cfg.pokerPayThreeKind),
                section(player, "poker", "pay2", "Payouts (cont.)",
                        "Straight: %dx\nFlush: %dx\nFull House: %dx\nFour of a Kind: %dx\nStraight Flush: %dx\nRoyal Flush: %dx",
                        cfg.pokerPayStraight, cfg.pokerPayFlush, cfg.pokerPayFullHouse,
                        cfg.pokerPayFourKind, cfg.pokerPayStraightFlush, cfg.pokerPayRoyalFlush),
                section(player, "poker", "hands1", "Hand Types",
                        "Pair: two of the same rank.\n\nTwo Pair: two different pairs.\n\nThree or Four of a Kind: three or four of a rank."),
                section(player, "poker", "hands2", "Hand Types",
                        "Full House: three of a kind plus a pair.\n\nStraight: five ranks in a row, any suits. A-2-3-4-5 counts."),
                section(player, "poker", "hands3", "Hand Types",
                        "Flush: five cards of one suit.\n\nStraight Flush: both at once.\n\nRoyal Flush: 10-J-Q-K-A in one suit."),
                section(player, "poker", "tip", "A Tip",
                        "Four cards to a flush or straight is usually worth chasing.\n\nWith nothing at all, discarding all five is normal."));
        open(player, "poker", Lang.tr(player, "vscasino.rules.poker.title", "Video Poker Rules"), sections);
    }

    /**
     * Resolve one section's title and body from the language file.
     *
     * <p>{@code args} are substituted into the body <i>after</i> translation, so a translated page
     * keeps its own placeholders rather than receiving pre-formatted English.
     */
    private static Section section(ServerPlayer player, String game, String id,
                                  String englishTitle, String englishBody, Object... args) {
        return new Section(id,
                Lang.tr(player, "vscasino.rules." + game + "." + id + ".title", englishTitle),
                Lang.tr(player, "vscasino.rules." + game + "." + id + ".text", englishBody, args));
    }

    /** Build the contents page plus one page per section, and show it. */
    private static void open(ServerPlayer player, String game, String title, List<Section> sections) {
        if (sections.isEmpty()) return;
        List<Filterable<Component>> pages = new ArrayList<>();

        // Page 1: contents. Book pages are 1-indexed, so section i lives on page i + 2.
        MutableComponent toc = Component.literal(
                Lang.tr(player, "vscasino.rules.contents", "CONTENTS") + "\n\n");
        for (int i = 0; i < sections.size(); i++) {
            int page = i + 2;
            toc.append(Component.literal(page - 1 + ". " + sections.get(i).title() + "\n")
                    .withStyle(s -> s.withColor(ChatFormatting.DARK_BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.ChangePage(page))));
        }
        toc.append(Component.literal("\n"));
        toc.append(backLink(player, game));
        pages.add(Filterable.passThrough(toc));

        // One page per section, each ending with links back to the contents and to the game.
        for (Section s : sections) {
            MutableComponent page = Component.literal(s.title() + "\n\n" + s.body() + "\n\n");
            page.append(Component.literal(Lang.tr(player, "vscasino.rules.back_contents", "[Contents]"))
                    .withStyle(st -> st.withColor(ChatFormatting.DARK_BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.ChangePage(1))));
            pages.add(Filterable.passThrough(page));
        }

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title), "Casino", 0, pages, false);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        // Swap the book into the held slot just long enough for the client to open it.
        int slot = 36 + player.getInventory().getSelectedSlot();
        int containerId = player.inventoryMenu.containerId;
        player.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, player.inventoryMenu.incrementStateId(), slot, book));
        player.connection.send(new ClientboundOpenBookPacket(InteractionHand.MAIN_HAND));
        player.connection.send(new ClientboundContainerSetSlotPacket(
                containerId, player.inventoryMenu.incrementStateId(), slot, player.getMainHandItem()));
    }

    /** A clickable line that closes the book and reopens the game the reader came from. */
    private static MutableComponent backLink(ServerPlayer player, String game) {
        return Component.literal(Lang.tr(player, "vscasino.rules.back_game", "[Back to the game]"))
                .withStyle(s -> s.withColor(ChatFormatting.DARK_GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/casino game " + game)));
    }
}
