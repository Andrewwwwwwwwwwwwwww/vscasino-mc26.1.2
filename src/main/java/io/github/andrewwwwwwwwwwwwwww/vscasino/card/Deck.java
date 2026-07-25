package io.github.andrewwwwwwwwwwwwwww.vscasino.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A shuffled 52-card shoe.
 *
 * <p>Dealing removes from the deck, so a card can never be dealt twice within a hand — the shoe is
 * the single source of truth rather than each game rolling random cards independently.
 */
public final class Deck {
    private final List<Card> cards = new ArrayList<>(52);
    private final Random random;

    public Deck(Random random) {
        this.random = random;
        reset();
    }

    /** Rebuild and shuffle a full 52-card deck. */
    public void reset() {
        cards.clear();
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards, random);
    }

    /** Deal one card, reshuffling automatically if the shoe runs dry. */
    public Card deal() {
        if (cards.isEmpty()) reset();
        return cards.remove(cards.size() - 1);
    }

    public int remaining() {
        return cards.size();
    }
}
