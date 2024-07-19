package ru.nern.prisonplus.structure;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import ru.nern.prisonplus.PrisonPlus;

public class PrisonItems {
    public static ItemStack HANDCUFFS = getHandcuffs();
    public static ItemStack CHESTPLATE = getChestplate();
    public static ItemStack BATON = getBaton();
    public static ItemStack SCISSORS = getScissors();


    private static ItemStack getChestplate() {
        ItemStack stack = new ItemStack(Items.LEATHER_CHESTPLATE);
        stack.setCustomName(Text.of(PrisonPlus.config.items.handcuffsChestplateName));
        stack.setSubNbt("HandcuffsChestplate", NbtByte.of(true));
        if(PrisonPlus.config.items.specialItemGlint) {
            NbtList list = new NbtList();
            list.add(new NbtCompound());
            stack.setSubNbt("Enchantments", list);
        }
        stack.addEnchantment(Enchantments.BINDING_CURSE, 1);
        stack.addEnchantment(Enchantments.VANISHING_CURSE, 1);


        return stack;
    }

    private static ItemStack getHandcuffs() {
        ItemStack stack = new ItemStack(Items.LEAD);
        stack.setCustomName(Text.of(PrisonPlus.config.items.handcuffsName));
        stack.setSubNbt("Handcuffs", NbtByte.of(true));
        if(PrisonPlus.config.items.specialItemGlint) {
            NbtList list = new NbtList();
            list.add(new NbtCompound());
            stack.setSubNbt("Enchantments", list);
        }


        return stack;
    }

    private static ItemStack getBaton() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.setCustomName(Text.of(PrisonPlus.config.items.batonName));
        stack.setSubNbt("Baton", NbtByte.of(true));

        if(PrisonPlus.config.items.specialItemGlint) {
            NbtList list = new NbtList();
            list.add(new NbtCompound());
            stack.setSubNbt("Enchantments", list);
        }

        return stack;
    }


    private static ItemStack getScissors() {
        ItemStack stack = new ItemStack(Items.SHEARS);
        stack.setCustomName(Text.of(PrisonPlus.config.items.scissorsName));
        stack.setSubNbt("Scissors", NbtByte.of(true));
        stack.addEnchantment(Enchantments.UNBREAKING, PrisonPlus.config.items.scissorsUnbreakingLevel);

        return stack;
    }

    public static void recompileStacks() {
        HANDCUFFS = getHandcuffs();
        CHESTPLATE = getChestplate();
        BATON = getBaton();
        SCISSORS = getScissors();
    }

    public static boolean isBaton(ItemStack stack){
        return stack.getNbt().contains("Baton");
    }
}
