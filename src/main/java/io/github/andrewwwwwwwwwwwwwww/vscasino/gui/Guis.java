package io.github.andrewwwwwwwwwwwwwww.vscasino.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;

/** Shared builders for the casino's chest-menu buttons. */
public final class Guis {
    private Guis() {}

    /** Strip the vanilla attribute tooltip lines so buttons read as UI, not gear. */
    public static void hideStats(ItemStack stack) {
        stack.set(DataComponents.TOOLTIP_DISPLAY,
                TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
    }

    public static Component styled(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withItalic(false));
    }

    /** A menu button: named item with optional grey description lines. */
    public static ItemStack button(Item item, String name, ChatFormatting color, List<String> lore) {
        ItemStack stack = new ItemStack(item);
        hideStats(stack);
        stack.set(DataComponents.CUSTOM_NAME, styled(name, color));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) lines.add(styled(line, ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    public static ItemStack button(Item item, String name, ChatFormatting color) {
        return button(item, name, color, null);
    }

    /**
     * Ask for a custom model, e.g. {@code vscasino:spin_lever}.
     *
     * <p>Elements with no texture yet simply aren't stamped, so they fall through to the plain
     * vanilla item — art can be added one piece at a time without anything else breaking.
     */
    public static ItemStack model(ItemStack stack, String modelId) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(
                List.of(), List.of(), List.of(modelId), List.of()));
        return stack;
    }

    /** A menu button that also requests a custom model. */
    public static ItemStack button(Item item, String name, ChatFormatting color,
                                   List<String> lore, String modelId) {
        return model(button(item, name, color, lore), modelId);
    }

    /** A button whose lore lines carry their own colours. */
    public static ItemStack coloured(Item item, String name, ChatFormatting color, List<Component> lore) {
        ItemStack stack = new ItemStack(item);
        hideStats(stack);
        stack.set(DataComponents.CUSTOM_NAME, styled(name, color));
        if (lore != null && !lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }
}
