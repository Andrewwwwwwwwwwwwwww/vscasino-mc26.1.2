package io.github.andrewwwwwwwwwwwwwww.vscasino.card;

import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.Guis;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * One playing card.
 *
 * <p>Cards are rendered as named paper items rather than custom textures. That keeps the whole mod
 * vanilla-client safe with no resource pack: the rank and suit live in the item's display name, and
 * both are pulled from the language file, so a translator (or a server owner who dislikes the suit
 * symbols) can change how every card reads without touching code.
 *
 * <p>The base item is {@code paper} specifically because VanillaSkills does not override
 * {@code assets/minecraft/items/paper.json} — pack files replace rather than merge, so building on
 * one of its 57 gear overrides would wipe that gear's textures if art is ever added here.
 */
public record Card(Rank rank, Suit suit) {

    public enum Suit {
        SPADES("spades", "♠", false),
        HEARTS("hearts", "♥", true),
        DIAMONDS("diamonds", "♦", true),
        CLUBS("clubs", "♣", false);

        public final String id;
        public final String symbol;
        public final boolean red;

        Suit(String id, String symbol, boolean red) {
            this.id = id;
            this.symbol = symbol;
            this.red = red;
        }
    }

    public enum Rank {
        ACE("ace", "A", 11), TWO("2", "2", 2), THREE("3", "3", 3), FOUR("4", "4", 4),
        FIVE("5", "5", 5), SIX("6", "6", 6), SEVEN("7", "7", 7), EIGHT("8", "8", 8),
        NINE("9", "9", 9), TEN("10", "10", 10), JACK("jack", "J", 10),
        QUEEN("queen", "Q", 10), KING("king", "K", 10);

        public final String id;
        public final String shortLabel;
        /** Blackjack value; aces count 11 here and are demoted to 1 by the hand evaluator. */
        public final int blackjackValue;

        Rank(String id, String shortLabel, int blackjackValue) {
            this.id = id;
            this.shortLabel = shortLabel;
            this.blackjackValue = blackjackValue;
        }

        /** Poker ordering, aces high (2 = 2 ... A = 14). */
        public int pokerRank() {
            return this == ACE ? 14 : blackjackValue == 10 && this != TEN
                    ? (this == JACK ? 11 : this == QUEEN ? 12 : 13)
                    : blackjackValue;
        }
    }

    /** Localized display label, e.g. "A(spade)" — both halves come from the language file. */
    public String label(ServerPlayer player) {
        String r = Lang.tr(player, "vscasino.card.rank." + rank.id, rank.shortLabel);
        String s = Lang.tr(player, "vscasino.card.suit." + suit.id, suit.symbol);
        return Lang.tr(player, "vscasino.card.format", "%s%s", r, s);
    }

    /** The model id this card asks for, e.g. {@code vscasino:card_2_clubs}. */
    public String modelId() {
        return "vscasino:card_" + rank.id + "_" + suit.id;
    }

    /** The face-up card as a menu item. */
    public ItemStack toStack(ServerPlayer player, List<String> extraLore) {
        ItemStack stack = new ItemStack(Items.PAPER);
        Guis.hideStats(stack);
        // Ask for this card's own model. Cards with no texture yet fall through to plain paper,
        // so art can be added one card at a time without anything else breaking.
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                List.of(), List.of(), List.of(modelId()), List.of()));
        stack.set(DataComponents.CUSTOM_NAME,
                Guis.styled(label(player), suit.red ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        if (extraLore != null && !extraLore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(
                    extraLore.stream().map(l -> (Component) Guis.styled(l, ChatFormatting.GRAY)).toList()));
        }
        return stack;
    }

    public ItemStack toStack(ServerPlayer player) {
        return toStack(player, null);
    }

    /** A face-down card (the dealer's hole card). */
    public static ItemStack faceDown(ServerPlayer player) {
        return Guis.button(Items.MAP,
                Lang.tr(player, "vscasino.card.face_down", "Face-down card"), ChatFormatting.DARK_PURPLE, null, "vscasino:card_back");
    }
}
