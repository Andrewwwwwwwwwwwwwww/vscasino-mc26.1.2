package io.github.andrewwwwwwwwwwwwwww.vscasino;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.andrewwwwwwwwwwwwwww.vanillaskills.VanillaSkills;
import io.github.andrewwwwwwwwwwwwwww.vanillaskills.api.SkillMenuExtensions;
import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.CasinoMenu;
import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.Guis;
import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.BlackjackMenu;
import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.SlotsMenu;
import io.github.andrewwwwwwwwwwwwwww.vscasino.gui.VideoPokerMenu;
import io.github.andrewwwwwwwwwwwwwww.vscasino.text.Lang;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * VanillaSkills Casino — an add-on that puts a casino button on the skill-tree screen.
 *
 * <p>Entirely server-side: every screen is a chest menu drawn by the server and translated per
 * player, so vanilla clients can play with nothing installed. All wagering runs on VanillaSkills'
 * Quest Shards through {@link Wager}.
 */
public class VsCasino implements ModInitializer {
    public static final String MOD_ID = "vscasino";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Set while a world is loaded; used for per-world config and language overrides. */
    public static MinecraftServer server;

    /** Slot directly above the Stats head on the skill-tree home screen. */
    private static final int CASINO_SLOT = 44;

    /** Distinct from VanillaSkills' pack id so both packs stack instead of replacing each other. */
    private static final java.util.UUID RESOURCE_PACK_ID =
            java.util.UUID.fromString("7c1d4e92-3a6b-4f18-b2c7-5e9d0a3f6b41");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            CasinoConfig.load();
            Lang.invalidate();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            server = null;
            Wager.resetAll();
        });

        // Drives the slot reel animation. Cheap when nothing is spinning.
        ServerTickEvents.END_SERVER_TICK.register(srv -> SlotsMenu.tickAll());

        // Push the texture pack on join. The casino's cards and buttons are textured vanilla items,
        // so without this a player with no mod installed sees plain paper and dye.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            CasinoConfig cfg = CasinoConfig.get();
            if (!cfg.pushResourcePack || cfg.resourcePackUrl == null || cfg.resourcePackUrl.isBlank()) return;
            // Solo single-player reads textures straight from the mod jar; pushing there just
            // triggers a pointless download prompt.
            if (srv.isSingleplayer() && !srv.isPublished()) return;
            handler.send(new ClientboundResourcePackPushPacket(
                    RESOURCE_PACK_ID, cfg.resourcePackUrl, cfg.resourcePackSha1,
                    cfg.requireResourcePack,
                    java.util.Optional.of(Component.literal(Lang.tr(handler.getPlayer(),
                            "vscasino.pack.prompt", "Casino card and chip textures")))));
        });

        SkillMenuExtensions.register(MOD_ID, CASINO_SLOT, VsCasino::casinoButton, CasinoMenu::open);

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
                dispatcher.register(casinoCommand()));

        LOGGER.info("VanillaSkills Casino ready");
    }

    /** The button shown on the skill screen. Rebuilt per open, so the balance line stays current. */
    private static ItemStack casinoButton(ServerPlayer player) {
        return Guis.button(Items.GOLD_NUGGET,
                Lang.tr(player, "vscasino.menu.casino.button", "Casino"), ChatFormatting.GOLD,
                List.of(
                        Lang.tr(player, "vscasino.menu.casino.button.desc", "Try your luck with Quest Shards."),
                        Lang.tr(player, "vscasino.menu.balance", "Balance: %d Quest Shards",
                                Wager.balance(player))),
                "vscasino:casino_logo");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> casinoCommand() {
        return Commands.literal("casino")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayer();
                    if (sp == null) return 0;
                    CasinoMenu.open(sp);
                    return 1;
                })
                // Used by the "[Back to the game]" links inside the rules books. Vanilla's own Done
                // button can't be intercepted server-side, so the books link back explicitly.
                .then(Commands.literal("game")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    b.suggest("slots");
                                    b.suggest("blackjack");
                                    b.suggest("poker");
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayer();
                                    if (sp == null) return 0;
                                    switch (StringArgumentType.getString(ctx, "id")) {
                                        case "slots" -> SlotsMenu.open(sp);
                                        case "blackjack" -> BlackjackMenu.open(sp);
                                        case "poker" -> VideoPokerMenu.open(sp);
                                        default -> CasinoMenu.open(sp);
                                    }
                                    return 1;
                                })))
                .then(Commands.literal("cards")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp == null) return 0;
                            io.github.andrewwwwwwwwwwwwwww.vscasino.gui.CardPreviewMenu.open(sp);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            CasinoConfig.load();
                            Lang.invalidate();
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    Lang.tr(sp, "vscasino.cmd.reloaded", "Casino config and language files reloaded.")), true);
                            return 1;
                        }))
                .then(Commands.literal("resetcaps")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            Wager.resetAll();
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    Lang.tr(sp, "vscasino.cmd.caps_reset", "Winnings caps reset for all players.")), true);
                            return 1;
                        }));
    }

    // ---- VanillaSkills bridge -------------------------------------------------------------
    // Every call into the host mod goes through here, so a change on their side is a one-file fix.

    public static int questShards(ServerPlayer player) {
        return VanillaSkills.PLAYERS.questShards(player);
    }

    public static boolean questShardsSpend(ServerPlayer player, int amount) {
        return VanillaSkills.PLAYERS.spendQuestShards(player, amount);
    }

    public static void questShardsAdd(ServerPlayer player, int amount) {
        VanillaSkills.PLAYERS.addQuestShards(player, amount);
    }
}
