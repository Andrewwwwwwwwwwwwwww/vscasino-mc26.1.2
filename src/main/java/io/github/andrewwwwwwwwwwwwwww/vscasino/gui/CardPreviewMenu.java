package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import io.github.andrewwwwwwwwwwwwwww.vscasino.card.Card;
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

import java.util.List;

/**
 * An operator tool: every card in the deck laid out at once, so custom card art can be checked
 * without playing hands until the card you want turns up.
 *
 * <p>Four rows of thirteen, one row per suit, Ace through King. A card with no texture yet simply
 * renders as plain paper, which makes it obvious at a glance which art is still missing.
 */
public class CardPreviewMenu extends ChestMenu {
    private static final int CLOSE_SLOT = 53;

    private final ServerPlayer player;
    private final SimpleContainer container;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new CardPreviewMenu(syncId, inv, (ServerPlayer) p),
                Component.literal(Lang.tr(player, "vscasino.preview.title", "Card art preview"))));
    }

    private CardPreviewMenu(int syncId, Inventory inv, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, inv, new SimpleContainer(54), 6);
        this.player = player;
        this.container = (SimpleContainer) getContainer();
        populate();
    }

    private void populate() {
        for (int i = 0; i < container.getContainerSize(); i++) container.setItem(i, ItemStack.EMPTY);

        // 13 ranks won't fit in a 9-wide row, so each suit takes a row and a half.
        int slot = 0;
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                if (slot >= CLOSE_SLOT) break;
                Card card = new Card(rank, suit);
                ItemStack stack = card.toStack(player,
                        List.of(card.modelId()));       // show the model id it's asking for
                container.setItem(slot++, stack);
            }
        }

        container.setItem(CLOSE_SLOT, Guis.button(Items.BARRIER,
                Lang.tr(player, "vscasino.menu.close", "Close"), ChatFormatting.RED));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clicker) {
        if (clicker instanceof ServerPlayer sp && slotId == CLOSE_SLOT) sp.closeContainer();
        // Everything else is inert — never call super, so nothing can be taken out.
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
