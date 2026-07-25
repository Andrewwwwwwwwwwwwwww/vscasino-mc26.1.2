package io.github.andrewwwwwwwwwwwwwww.vscasino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-world casino settings, stored at {@code <world>/vscasino/casino.json} so each world (and each
 * server) tunes its own economy. Edit the file and run {@code /casino reload}.
 *
 * <p>The defaults are deliberately conservative. A casino paying out at or above 100% is an
 * infinite Quest Shard printer, and Quest Shards convert into skill-tree progression — so the
 * house edge here is not flavour, it is what stops the mod devaluing VanillaSkills. Anything that
 * could break that is clamped in {@link #clamp()} no matter what the file says.
 */
public class CasinoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CasinoConfig active = new CasinoConfig();

    private static final String DEFAULT_RP_URL =
            "https://github.com/Andrewwwwwwwwwwwwwww/vscasino/releases/download/v1.0.0/VSCasino-TexturePack.zip";
    private static final String DEFAULT_RP_SHA1 = "ab139a5f6c40799e2b10bfcaf252a53210fc1961";

    // ---- wagering ------------------------------------------------------------------------

    /** Smallest allowed stake, in Quest Shards. */
    public int minBet = 1;
    /** Largest allowed stake. Keeps one hand from swinging a whole economy. */
    public int maxBet = 25;
    /**
     * Cap on how far ahead a player may get (net winnings) before payouts stop. Resets on server
     * restart, or with {@code /casino resetcaps}. Set to 0 to disable the cap entirely.
     */
    public int netWinCap = 500;
    /** Wins at or above this are written to the server log. */
    public int bigWinLogThreshold = 100;
    /** Also announce those wins in chat to everyone online. */
    public boolean broadcastBigWins = false;

    /**
     * How much better a fully luck-invested player does, as a percentage added to their winnings.
     * VanillaSkills' Fortune Finder lane grants luck, so this makes that lane pay off at the tables.
     * Kept small on purpose — set to 0 to remove the effect entirely.
     */
    public double luckMaxBonusPercent = 2.0;
    /** The luck value treated as "maxed". VanillaSkills' Fortune Finder lane tops out at +5. */
    public double luckReferenceValue = 5.0;

    /** The three bet step sizes, shown either side of the action button in every game. */
    public int betStepSmall = 1;
    public int betStepMedium = 5;
    public int betStepLarge = 10;

    // ---- which games appear --------------------------------------------------------------

    public boolean slotsEnabled = true;
    public boolean blackjackEnabled = true;
    public boolean videoPokerEnabled = true;

    // ---- texture pack --------------------------------------------------------------------

    /**
     * The casino draws its buttons and cards as textured vanilla items, and a vanilla client has no
     * mod jar to read those textures from — so the server pushes this pack on join. With it off,
     * players without the mod installed see plain paper and dye instead of cards and chips.
     *
     * <p>Bump both the URL and the SHA-1 together whenever the pack changes; clients cache on the
     * hash, so a new pack at an old hash would never be re-downloaded.
     */
    public boolean pushResourcePack = true;
    public String resourcePackUrl = DEFAULT_RP_URL;
    public String resourcePackSha1 = DEFAULT_RP_SHA1;
    /** Refuse to let players join without accepting the pack, so everyone sees the same casino. */
    public boolean requireResourcePack = true;

    // ---- sound ---------------------------------------------------------------------------

    public boolean soundsEnabled = true;
    /** Playback volume for every casino sound, 0.0 to 1.0. */
    public float soundVolume = 0.7f;

    /**
     * Sound ids, resolved from the game's sound registry — any valid sound id works, so these can
     * be pointed at resource-pack sounds too. An unknown id simply plays nothing.
     */
    public String soundBetChange = "minecraft:ui.button.click";
    public String soundDeal = "minecraft:entity.item.pickup";
    public String soundCard = "minecraft:block.note_block.hat";
    public String soundReelStop = "minecraft:block.note_block.bass";
    public String soundWin = "minecraft:entity.experience_orb.pickup";
    public String soundBigWin = "minecraft:ui.toast.challenge_complete";
    public String soundJackpot = "minecraft:block.note_block.bell";
    public String soundLose = "minecraft:entity.villager.no";

    // ---- slots ---------------------------------------------------------------------------

    /**
     * Return-to-player as a percentage. The paytable below is scaled automatically to hit this, so
     * changing symbol weights or payouts alters how wins <i>feel</i> without changing the overall
     * return. Clamped below 100 — the machine can never be made to pay out more than it takes.
     */
    public int slotsRtpPercent = 95;

    public boolean slotsAnimationEnabled = true;
    /** Total spin length in ticks (20 ticks = 1 second). */
    public int slotsSpinTicks = 26;
    /** Extra ticks each reel keeps spinning after the one before it stops. */
    public int slotsReelStaggerTicks = 5;

    /** One reel symbol's odds and payouts. */
    public static class SymbolSettings {
        public int weight;
        public double payThree;
        public double payTwo;

        public SymbolSettings() {}

        public SymbolSettings(int weight, double payThree, double payTwo) {
            this.weight = weight;
            this.payThree = payThree;
            this.payTwo = payTwo;
        }
    }

    /**
     * The reel symbols, keyed by id. Weight is relative (a symbol with twice the weight shows up
     * twice as often); payThree/payTwo are multiples of the stake before RTP scaling.
     *
     * <p>Only ids the mod knows how to draw are used — unknown ids are ignored rather than
     * crashing, and a removed id simply drops that symbol from the reels.
     */
    public Map<String, SymbolSettings> slotsSymbols = defaultSymbols();

    private static Map<String, SymbolSettings> defaultSymbols() {
        Map<String, SymbolSettings> m = new LinkedHashMap<>();
        m.put("coal", new SymbolSettings(30, 3, 0.5));
        m.put("wheat", new SymbolSettings(25, 4, 0.6));
        m.put("iron", new SymbolSettings(20, 6, 1.0));
        m.put("gold", new SymbolSettings(14, 10, 1.5));
        m.put("emerald", new SymbolSettings(8, 20, 3.0));
        m.put("diamond", new SymbolSettings(3, 60, 8.0));
        return m;
    }

    // ---- blackjack -----------------------------------------------------------------------

    /** Blackjack payout as a percentage of the stake (150 = the classic 3:2). */
    public int blackjackNaturalPercent = 150;
    /** The dealer draws until reaching this total, then stands. */
    public int blackjackDealerStandsOn = 17;
    public boolean blackjackAllowDouble = true;
    public boolean blackjackAllowSplit = true;
    /** Split aces receive exactly one card each and cannot be hit (the standard rule). */
    public boolean blackjackSplitAcesOneCard = true;

    // ---- video poker ---------------------------------------------------------------------
    // Gross return per unit staked. 1 means the stake comes back; 0 would mean the hand pays
    // nothing at all.

    public int pokerPayRoyalFlush = 250;
    public int pokerPayStraightFlush = 50;
    public int pokerPayFourKind = 25;
    public int pokerPayFullHouse = 9;
    public int pokerPayFlush = 6;
    public int pokerPayStraight = 4;
    public int pokerPayThreeKind = 3;
    public int pokerPayTwoPair = 2;
    public int pokerPayJacksOrBetter = 1;

    // ---- plumbing ------------------------------------------------------------------------

    public static CasinoConfig get() {
        return active;
    }

    private static Path path() {
        var server = VsCasino.server;
        if (server == null) return null;
        return server.getWorldPath(LevelResource.ROOT).resolve("vscasino").resolve("casino.json");
    }

    /** Load from the current world, writing a default file if none exists, and publish it. */
    public static CasinoConfig load() {
        Path path = path();
        CasinoConfig cfg = new CasinoConfig();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    CasinoConfig loaded = GSON.fromJson(Files.readString(path), CasinoConfig.class);
                    if (loaded != null) cfg = loaded;
                } else {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, GSON.toJson(cfg));
                }
            } catch (Exception e) {
                VsCasino.LOGGER.error("Failed to load casino.json, using defaults", e);
                cfg = new CasinoConfig();
            }
        }
        cfg.clamp();
        active = cfg;
        return cfg;
    }

    /** Keep hand-edited files inside sane bounds — especially anything that could break the economy. */
    private void clamp() {
        minBet = Math.max(1, minBet);
        maxBet = Math.max(minBet, maxBet);
        netWinCap = Math.max(0, netWinCap);
        bigWinLogThreshold = Math.max(1, bigWinLogThreshold);

        betStepSmall = Math.max(1, betStepSmall);
        betStepMedium = Math.max(1, betStepMedium);
        betStepLarge = Math.max(1, betStepLarge);

        soundVolume = Math.max(0f, Math.min(1f, soundVolume));

        // A large luck bonus could push a game past break-even; cap it well short of that.
        luckMaxBonusPercent = Math.max(0, Math.min(25, luckMaxBonusPercent));
        luckReferenceValue = Math.max(0.1, luckReferenceValue);

        slotsRtpPercent = Math.max(50, Math.min(99, slotsRtpPercent));   // never >= 100
        slotsSpinTicks = Math.max(0, Math.min(200, slotsSpinTicks));
        slotsReelStaggerTicks = Math.max(0, Math.min(60, slotsReelStaggerTicks));
        if (slotsSymbols == null || slotsSymbols.isEmpty()) slotsSymbols = defaultSymbols();
        slotsSymbols.values().removeIf(s -> s == null);
        for (SymbolSettings s : slotsSymbols.values()) {
            s.weight = Math.max(0, s.weight);
            s.payThree = Math.max(0, s.payThree);
            s.payTwo = Math.max(0, s.payTwo);
        }
        if (slotsSymbols.values().stream().mapToInt(s -> s.weight).sum() <= 0) {
            slotsSymbols = defaultSymbols();      // an all-zero table would divide by zero
        }

        blackjackNaturalPercent = Math.max(100, Math.min(300, blackjackNaturalPercent));
        blackjackDealerStandsOn = Math.max(12, Math.min(21, blackjackDealerStandsOn));

        pokerPayRoyalFlush = Math.max(0, pokerPayRoyalFlush);
        pokerPayStraightFlush = Math.max(0, pokerPayStraightFlush);
        pokerPayFourKind = Math.max(0, pokerPayFourKind);
        pokerPayFullHouse = Math.max(0, pokerPayFullHouse);
        pokerPayFlush = Math.max(0, pokerPayFlush);
        pokerPayStraight = Math.max(0, pokerPayStraight);
        pokerPayThreeKind = Math.max(0, pokerPayThreeKind);
        pokerPayTwoPair = Math.max(0, pokerPayTwoPair);
        pokerPayJacksOrBetter = Math.max(0, pokerPayJacksOrBetter);
    }
}
