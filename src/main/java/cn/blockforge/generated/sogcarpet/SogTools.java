package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;

/**
 * “什么算工具”的统一判定，供“假人只拾取工具”规则使用。
 *
 * <p>覆盖用户列举的剑、斧、镐、铲、锄、弓、弩、矛、重锤、竿、剪：前六类走物品标签
 * （{@link ItemTags#SWORDS}、{@link ItemTags#AXES}、{@link ItemTags#PICKAXES}、
 * {@link ItemTags#SHOVELS}、{@link ItemTags#HOES}、{@link ItemTags#SPEARS}），
 * 这样带上其它模组追加的同标签工具也能识别；弓、弩、重锤、钓鱼竿、剪刀没有对应物品标签，
 * 按物品类判定（重锤的 {@code MACE_ENCHANTABLE} 只是附魔标签，不如类判定直白；
 * 三叉戟归进“矛”这一格）。</p>
 *
 * <p>把工具分成 {@value #CATEGORY_COUNT} 类后，每个假人用一个位掩码记住“哪几类要捡”，
 * GCA 假人背包界面右侧那一列开关就是按这个掩码逐位切换的。</p>
 *
 * <p>26.2 的 {@link ItemStack} 只保留 {@code is(Predicate<Holder<Item>>)}，
 * 标签检查改走 {@link ItemStack#typeHolder()} 上的 {@code Holder#is(TagKey)}。</p>
 */
public final class SogTools {
    /** 工具类别数。 */
    public static final int CATEGORY_COUNT = 11;

    public static final int SWORD = 0;
    public static final int AXE = 1;
    public static final int PICKAXE = 2;
    public static final int SHOVEL = 3;
    public static final int HOE = 4;
    public static final int BOW = 5;
    public static final int CROSSBOW = 6;
    public static final int SPEAR = 7;
    public static final int MACE = 8;
    public static final int FISHING_ROD = 9;
    public static final int SHEARS = 10;

    /** 全部类别都开（默认值，等价于原来的“捡所有工具”）。 */
    public static final int ALL = (1 << CATEGORY_COUNT) - 1;

    /** 每类的翻译键。 */
    private static final String[] LABEL_KEYS = {
        "sog_carpet.ui.tools.sword", "sog_carpet.ui.tools.axe", "sog_carpet.ui.tools.pickaxe",
        "sog_carpet.ui.tools.shovel", "sog_carpet.ui.tools.hoe", "sog_carpet.ui.tools.bow",
        "sog_carpet.ui.tools.crossbow", "sog_carpet.ui.tools.spear", "sog_carpet.ui.tools.mace",
        "sog_carpet.ui.tools.rod", "sog_carpet.ui.tools.shears"
    };

    private SogTools() {
    }

    /** 该物品属于哪几类工具（位掩码），不是工具返回 0。 */
    public static int categoryMask(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int mask = 0;
        Holder<Item> holder = stack.typeHolder();
        if (holder.is(ItemTags.SWORDS)) {
            mask |= 1 << SWORD;
        }
        if (holder.is(ItemTags.AXES)) {
            mask |= 1 << AXE;
        }
        if (holder.is(ItemTags.PICKAXES)) {
            mask |= 1 << PICKAXE;
        }
        if (holder.is(ItemTags.SHOVELS)) {
            mask |= 1 << SHOVEL;
        }
        if (holder.is(ItemTags.HOES)) {
            mask |= 1 << HOE;
        }
        if (holder.is(ItemTags.SPEARS)) {
            mask |= 1 << SPEAR;
        }
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            mask |= 1 << BOW;
        }
        if (item instanceof CrossbowItem) {
            mask |= 1 << CROSSBOW;
        }
        if (item instanceof MaceItem) {
            mask |= 1 << MACE;
        }
        if (item instanceof FishingRodItem) {
            mask |= 1 << FISHING_ROD;
        }
        if (item instanceof ShearsItem) {
            mask |= 1 << SHEARS;
        }
        if (item instanceof TridentItem) {
            mask |= 1 << SPEAR;
        }
        return mask;
    }

    /** 该物品是不是工具（不限类别）。 */
    public static boolean isTool(ItemStack stack) {
        return categoryMask(stack) != 0;
    }

    /** 在给定的类别开关掩码下，这个物品该不该被捡。 */
    public static boolean isTool(ItemStack stack, int allowedMask) {
        return (categoryMask(stack) & allowedMask) != 0;
    }

    /** 某一类的界面名字。 */
    public static Component categoryName(int category) {
        if (category < 0 || category >= LABEL_KEYS.length) {
            return Component.empty();
        }
        return Component.translatable(LABEL_KEYS[category]);
    }

    /** 某一类的界面短名字（按钮上用，尽量两三字）。 */
    public static Component categoryShortName(int category) {
        if (category < 0 || category >= LABEL_KEYS.length) {
            return Component.empty();
        }
        return Component.translatable(LABEL_KEYS[category] + ".short");
    }
}
