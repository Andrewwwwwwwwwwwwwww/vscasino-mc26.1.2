package io.github.andrewwwwwwwwwwwwwww.vscasino;

import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single choke point for money moving in or out of the casino. Every game bets and pays out
 * through here so the rules below are enforced in exactly one place.
 *
 * <p>Casino play uses <b>Quest Shards only</b>. That is deliberate: Quest Shards convert to Skill
 * Shards at a fixed ratio, so keeping winnings one conversion step away from the skill tree stops
 * the casino from directly inflating progression.
 *
 * <p><b>Stakes are taken immediately.</b> {@link #take} debits the player the moment a hand starts;
 * a payout is a separate credit at the end. Nothing is ever "held" inside an open menu, so closing
 * a screen, disconnecting, or a server crash mid-hand can never duplicate or resurrect a stake.
 * The cost of that safety is that a hand interrupted before it resolves is a loss — which is why
 * games must resolve within a single interaction wherever possible.
 */
public final class Wager {
    private Wager() {}

    /** Net winnings per player since server start, used for the daily-style cap. */
    private static final Map<UUID, Integer> NET_TODAY = new ConcurrentHashMap<>();

    /**
     * Debit {@code amount} Quest Shards as a stake.
     *
     * <p>Returns false (and messages the player) if the bet is outside configured limits or they
     * cannot afford it. The underlying spend is atomic, so double-clicking a bet button can never
     * take the stake twice or drive a balance negative.
     */
    public static boolean take(ServerPlayer player, int amount) {
        CasinoConfig cfg = CasinoConfig.get();
        if (amount < cfg.minBet) {
            deny(player, Lang.tr(player, "vscasino.msg.bet_too_small",
                    "Minimum bet is %d Quest Shards.", cfg.minBet));
            return false;
        }
        if (amount > cfg.maxBet) {
            deny(player, Lang.tr(player, "vscasino.msg.bet_too_large",
                    "Maximum bet is %d Quest Shards.", cfg.maxBet));
            return false;
        }
        if (!VsCasino.questShardsSpend(player, amount)) {
            deny(player, Lang.tr(player, "vscasino.msg.insufficient",
                    "You need %d Quest Shards to bet that.", amount));
            return false;
        }
        NET_TODAY.merge(player.getUUID(), -amount, Integer::sum);
        return true;
    }

    /**
     * Credit winnings. {@code amount} is the gross return (stake included), so a push pays back
     * exactly the stake and a loss pays nothing.
     *
     * <p>Winnings are clamped by the configured net-winnings cap: once a player is up by that much,
     * further wins pay only up to the cap. This is the backstop that keeps a lucky streak from
     * printing unbounded shards into the economy.
     *
     * @return the amount actually paid, which may be less than requested if the cap bit
     */
    public static int pay(ServerPlayer player, int amount) {
        if (amount <= 0) return 0;
        CasinoConfig cfg = CasinoConfig.get();
        int paid = amount + (int) Math.round(amount * luckBonus(player));
        if (cfg.netWinCap > 0) {
            int net = NET_TODAY.getOrDefault(player.getUUID(), 0);
            if (net >= cfg.netWinCap) {
                deny(player, Lang.tr(player, "vscasino.msg.cap_reached",
                        "You've hit the winnings cap of %d Quest Shards. Come back later.", cfg.netWinCap));
                return 0;
            }
            if (net + paid > cfg.netWinCap) paid = cfg.netWinCap - net;
        }
        if (paid <= 0) return 0;
        VsCasino.questShardsAdd(player, paid);
        NET_TODAY.merge(player.getUUID(), paid, Integer::sum);
        if (paid >= cfg.bigWinLogThreshold) {
            VsCasino.LOGGER.info("[casino] {} won {} Quest Shards", player.getName().getString(), paid);
            if (cfg.broadcastBigWins && VsCasino.server != null) {
                int won = paid;
                VsCasino.server.getPlayerList().broadcastSystemMessage(
                        Component.literal(Lang.tr(player, "vscasino.msg.big_win_broadcast",
                                "%s just won %d Quest Shards at the casino!",
                                player.getName().getString(), won))
                                .withStyle(ChatFormatting.GOLD), false);
            }
        }
        return paid;
    }

    /**
     * How much this player's luck improves their return, as a fraction (0.02 = +2%).
     *
     * <p>Read from the vanilla luck attribute, which VanillaSkills' Fortune Finder lane raises, so
     * investing in that lane pays off at the tables too. Deliberately small: it is a nudge, not a
     * strategy, and the bonus is applied to winnings rather than to the odds so it can never push a
     * game past break-even on its own.
     */
    public static double luckBonus(ServerPlayer player) {
        CasinoConfig cfg = CasinoConfig.get();
        if (player == null || cfg.luckMaxBonusPercent <= 0 || cfg.luckReferenceValue <= 0) return 0;
        double luck = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        if (luck <= 0) return 0;
        double fraction = Math.min(1.0, luck / cfg.luckReferenceValue);
        return fraction * (cfg.luckMaxBonusPercent / 100.0);
    }

    /** Current balance in Quest Shards. */
    public static int balance(ServerPlayer player) {
        return VsCasino.questShards(player);
    }

    /** Net winnings for this player since server start (negative when down). */
    public static int net(ServerPlayer player) {
        return NET_TODAY.getOrDefault(player.getUUID(), 0);
    }

    /** Clear a player's running total (server stop / op reset). */
    public static void reset(UUID id) {
        NET_TODAY.remove(id);
    }

    public static void resetAll() {
        NET_TODAY.clear();
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
