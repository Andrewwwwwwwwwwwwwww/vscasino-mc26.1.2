package io.github.andrewwwwwwwwwwwwwww.vscasino;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Casino sound effects, played to one player only.
 *
 * <p>Sounds are looked up from the registry by the id strings in {@link CasinoConfig}, so a server
 * owner can point any cue at any sound — including sounds added by their own resource pack —
 * without a code change. An id that doesn't resolve plays nothing rather than throwing.
 *
 * <p>They are sent as packets aimed at the player's own position rather than played into the world,
 * so nobody else hears someone else's slot machine.
 */
public final class Sfx {
    private Sfx() {}

    public static void betChange(ServerPlayer player) {
        play(player, CasinoConfig.get().soundBetChange, 1.0f);
    }

    public static void deal(ServerPlayer player) {
        play(player, CasinoConfig.get().soundDeal, 1.0f);
    }

    /** A single card landing. Pitch drifts up slightly per card so a dealt hand sounds like a run. */
    public static void card(ServerPlayer player, int index) {
        play(player, CasinoConfig.get().soundCard, 1.0f + Math.min(index, 6) * 0.06f);
    }

    /** One reel coming to a stop; pitch rises for each successive reel. */
    public static void reelStop(ServerPlayer player, int reel) {
        play(player, CasinoConfig.get().soundReelStop, 0.9f + reel * 0.15f);
    }

    public static void win(ServerPlayer player) {
        play(player, CasinoConfig.get().soundWin, 1.0f);
    }

    public static void bigWin(ServerPlayer player) {
        play(player, CasinoConfig.get().soundBigWin, 1.0f);
    }

    public static void jackpot(ServerPlayer player) {
        play(player, CasinoConfig.get().soundJackpot, 1.2f);
    }

    public static void lose(ServerPlayer player) {
        play(player, CasinoConfig.get().soundLose, 1.0f);
    }

    /** Play a registry sound id to this player. Silently does nothing if sounds are off. */
    public static void play(ServerPlayer player, String soundId, float pitch) {
        CasinoConfig cfg = CasinoConfig.get();
        if (!cfg.soundsEnabled || cfg.soundVolume <= 0f) return;
        if (player == null || soundId == null || soundId.isBlank()) return;

        Identifier id = Identifier.tryParse(soundId);
        if (id == null) return;
        Holder.Reference<SoundEvent> sound = BuiltInRegistries.SOUND_EVENT.get(id).orElse(null);
        if (sound == null) return;

        player.connection.send(new ClientboundSoundPacket(
                sound, SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                cfg.soundVolume, pitch,
                player.getRandom().nextLong()));
    }
}
