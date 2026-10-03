package cn.blockforge.generated.sogcarpet;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TridentItem;

/**
 * “什么算工具”的统一判定，供“假人只拾取工具”规则使用。
 *
 * <p>覆盖用户列举的剑、斧、镐、铲、锄、弓、弩、矛、重锤、竿、剪，以及本轮新增的
 * <b>打火石</b>。判定分两层：</p>
 * <ol>
 *   <li><b>物品标签</b>：剑/斧/镐/铲/锄/矛走
 *       {@link ItemTags#SWORDS}、{@link ItemTags#AXES}、{@link ItemTags#PICKAXES}、
 *       {@link ItemTags#SHOVELS}、{@link ItemTags#HOES}、{@link ItemTags#SPEARS}，
 *       别的模组用同标签追加的工具也能认出来。26.2 里剑和镐没有专门的物品类
 *       （没有 {@code SwordItem}/{@code PickaxeItem}），这三个标签是唯一可靠的判据。</li>
 *   <li><b>类/ID 兜底</b>：弓、弩、重锤、钓鱼竿、剪刀、三叉戟、打火石按物品类判定，
 *       另外再按注册名后缀兜底（{@code _sword}/{@code _pickaxe}/…/{@code flint_and_steel}）。
 *       某些时刻物品标签还没绑定，{@code Holder#is(TagKey)} 会抛
 *       {@code IllegalStateException("Tags not bound")}——上一版没有兜底、异常又没人接，
 *       {@code playerTouch} 直接崩在判定里，结果就是“开了规则连工具也捡不起来”。
 *       现在标签读取单独 try/catch，异常时完全退到类/ID 判定，工具照样认得出。</li>
 * </ol>
 *
 * <p>把工具分成 {@value #CATEGORY_COUNT} 类后，每个假人用一个位掩码记住“哪几类要捡”，
 * GCA 假人背包界面右侧那一列开关就是按这个掩码逐位切换的。</p>
 */
public final class SogTools {
    /** 工具类别数。 */
    public static final int CATEGORY_COUNT = 12;

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
    /** 打火石（本轮新增）。 */
    public static final int FLINT_AND_STEEL = 11;

    /** 全部类别都开（默认值，等价于原来的“捡所有工具”）。 */
    public static final int ALL = (1 << CATEGORY_COUNT) - 1;

    /** 每类的翻译键。 */
    private static final String[] LABEL_KEYS = {
        "sog_carpet.ui.tools.sword", "sog_carpet.ui.tools.axe", "sog_carpet.ui.tools.pickaxe",
        "sog_carpet.ui.tools.shovel", "sog_carpet.ui.tools.hoe", "sog_carpet.ui.tools.bow",
        "sog_carpet.ui.tools.crossbow", "sog_carpet.ui.tools.spear", "sog_carpet.ui.tools.mace",
        "sog_carpet.ui.tools.rod", "sog_carpet.ui.tools.shears", "sog_carpet.ui.tools.flint"
    };

    private SogTools() {
    }

    /** 该物品属于哪几类工具（位掩码），不是工具返回 0。 */
    public static int categoryMask(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        int mask = 0;
        // 标签判定可能因为“标签尚未绑定”抛异常：单独兜住，异常时退到下面的类/ID 判定。
        try {
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
        } catch (RuntimeException ignored) {
            // 标签没绑定时 Holder#is(TagKey) 会抛 IllegalStateException，交给 ID 兜底。
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
        if (item instanceof FlintAndSteelItem) {
            mask |= 1 << FLINT_AND_STEEL;
        }
        // 注册名兜底：剑/镐在 26.2 没有专门的类，只能靠标签或名字；别的模组用
        // 标准后缀命名（`xxx_sword`）时这里也能认出来。
        String path = registryPath(item);
        if (path.endsWith("_sword")) {
            mask |= 1 << SWORD;
        }
        if (path.endsWith("_axe")) {
            mask |= 1 << AXE;
        }
        if (path.endsWith("_pickaxe")) {
            mask |= 1 << PICKAXE;
        }
        if (path.endsWith("_shovel")) {
            mask |= 1 << SHOVEL;
        }
        if (path.endsWith("_hoe")) {
            mask |= 1 << HOE;
        }
        if (path.endsWith("_spear")) {
            mask |= 1 << SPEAR;
        }
        if (path.equals("bow") || path.endsWith("_bow")) {
            mask |= 1 << BOW;
        }
        if (path.equals("crossbow") || path.endsWith("_crossbow")) {
            mask |= 1 << CROSSBOW;
        }
        if (path.equals("mace") || path.endsWith("_mace")) {
            mask |= 1 << MACE;
        }
        if (path.contains("fishing_rod")) {
            mask |= 1 << FISHING_ROD;
        }
        if (path.equals("shears")) {
            mask |= 1 << SHEARS;
        }
        if (path.equals("trident") || path.endsWith("_trident")) {
            mask |= 1 << SPEAR;
        }
        if (path.equals("flint_and_steel") || path.endsWith("_flint_and_steel")) {
            mask |= 1 << FLINT_AND_STEEL;
        }
        return mask;
    }

    /** 物品的注册名路径（命名空间去掉），取不到时返回空串。 */
    private static String registryPath(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.getPath();
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
