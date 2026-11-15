package cn.blockforge.generated.sogcarpet.mixin.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.client.BeaconEffectSlot;
import cn.blockforge.generated.sogcarpet.client.SogBeaconScreenAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「自定义信标效果」的界面接线（两个槽位 + 支付物图标行）。
 *
 * <p><b>槽位</b>：主/副效果各一个 {@link BeaconEffectSlot}，它本身就是一个原版样式的槽位，
 * 没选效果时中间显示「+」，点一下打开效果选择界面；选好后同一个槽位里直接显示该效果图标，
 * 再点重新挑、图标就地替换。主副严格分开，互不影响。位置按 beacon.png 的实际内容摆放：
 * 主效果槽贴在主效果面板右端 (89,72)，副效果槽在副效果面板里那一行 (169,49)。</p>
 *
 * <p>两个槽位在 {@code init} 里<b>总是</b>创建，可见性交给 {@code containerTick} 按实时层数
 * 决定（主效果 ≥1 层、副效果 ≥4 层）。不能在 init 里按层数提前 return：服务端先发
 * {@code ClientboundOpenScreenPacket}（客户端立刻 init，此时层数还是 0），之后才用
 * {@code ClientboundContainerSetDataPacket} 把层数送过来。</p>
 *
 * <p><b>支付物图标行</b>：规则开启时在整行最左边补一个下界之星（在原版下界合金锭左侧），
 * 6 个图标按原版 22 像素间距重排。原版贴图自带 4 条格间分隔线，重排后会压在图标上，所以这里
 * 把原版分隔线**原样复制**到新的 5 个格边界（第 k 格图标左侧 3 像素，与原版完全同一位置关系），
 * 再用贴图里一块纯背景色条把旧的 4 条盖掉——全过程只用原版贴图自己的像素，所以任何资源包下
 * 都是“原版样式”，不写死颜色。</p>
 */
@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin extends AbstractContainerScreen<BeaconMenu>
        implements SogBeaconScreenAccess {
    /** 图标间距（原版）：相邻两个支付物图标相距 22 像素。 */
    @Unique
    private static final int SOG_ICON_PITCH = 22;
    /** 支付物图标行的左端（给下界之星留出的位置；原版第一个图标在 20）。 */
    @Unique
    private static final int SOG_PAYMENT_ROW_X = 4;
    /** 图标行的竖直位置（原版就是 109）。 */
    @Unique
    private static final int SOG_PAYMENT_ROW_Y = 109;
    /** 分隔线/背景条的贴图高度与宽度。 */
    @Unique
    private static final int SOG_DIVIDER_W = 2;
    @Unique
    private static final int SOG_DIVIDER_H = 18;
    /** 原版贴图里一条分隔线的取样位置，以及一块纯背景的取样位置。 */
    @Unique
    private static final float SOG_DIVIDER_U = 38.0F;
    @Unique
    private static final float SOG_DIVIDER_V = 108.0F;
    @Unique
    private static final float SOG_BACKGROUND_U = 8.0F;
    /** 原版贴图尺寸。 */
    @Unique
    private static final int SOG_TEXTURE_SIZE = 256;
    /** 原版那 4 条分隔线所在的 x（重排后需要盖掉）。 */
    @Unique
    private static final int[] SOG_VANILLA_DIVIDERS = {38, 60, 82, 104};

    /**
     * 主效果槽位 / 副效果槽位的位置（相对界面左上角）。
     *
     * <p>按 beacon.png 的实际内容量出来（面板内部区域：主效果 x 10-114、y 7-97，副效果
     * x 117-219）：主效果的槽位放在主效果面板<b>右端</b>（也就是主效果按钮那一行的末端），
     * 紧接最后一行主效果按钮（力量，65-86）之后，间距正好是原版按钮间距 24，
     * 竖直方向按原版按钮 22 高居中（72+2=74），整块都在主效果面板里。
     * 旧位置 (145,72) 其实落进了副效果面板（x≥117），所以才要挪回主效果这一端。</p>
     */
    @Unique
    private static final int SOG_PRIMARY_SLOT_X = 89;
    @Unique
    private static final int SOG_PRIMARY_SLOT_Y = 74;
    @Unique
    private static final int SOG_SECONDARY_SLOT_X = 169;
    @Unique
    private static final int SOG_SECONDARY_SLOT_Y = 49;
    /**
     * 原版“把主效果同时也设成副效果（II 级）”快捷按钮的位置。
     *
     * <p>它平时隐形，但一旦选了主效果就会出现在 (168, 47)，正好被副效果槽位 (169, 49) 压住；
     * 控件事件按添加顺序派发，先添加的它会把副效果槽的点击截走。规则开启时把它右移到
     * (191, 47)——那里是 beacon.png 面板内一块纯背景（x 191-213、y 47-69 全是 #212121，
     * 右边界框在 x≥220），既不压原版再生按钮（144-166），也不压我们的两个槽位。</p>
     */
    @Unique
    private static final int SOG_UPGRADE_VANILLA_X = 168;
    @Unique
    private static final int SOG_UPGRADE_MOVED_X = 191;

    @Shadow
    @Final
    private static ResourceLocation BEACON_LOCATION;

    /** 原版按钮列表；只用来在规则开启时把 II 级快捷按钮挪开一格。 */
    @Shadow
    @Final
    private java.util.List<?> beaconButtons;

    @Shadow
    private Holder<MobEffect> primary;

    @Shadow
    private Holder<MobEffect> secondary;

    /**
     * 槽位控件之间共享的“当前选择”读取器。
     *
     * <p>{@link BeaconEffectSlot} 每次绘制都要知道该显示「+」还是效果图标，直接读本类的
     * {@code primary}/{@code secondary} 字段即可，因此这里只传一个 lambda。</p>
     */
    @Unique
    private BeaconEffectSlot sog$primarySlot;

    @Unique
    private BeaconEffectSlot sog$secondarySlot;

    protected BeaconScreenMixin(BeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void sog$addEffectSlots(CallbackInfo ci) {
        this.sog$primarySlot = null;
        this.sog$secondarySlot = null;
        // 这里绝不能按 getLevels() 决定建不建槽：服务端先发 OpenScreen（客户端立刻 init），
        // 之后才发 ContainerSetData 把层数同步过来，init 时读到的永远是 0 层。旧代码据此
        // 直接 return，于是满级信标也永远看不到槽位——这就是“方框不显示”的根因。
        // 现在总是建槽，再由 containerTick 按实时层数与规则开关更新可见/可点状态。
        this.sog$primarySlot = this.addRenderableWidget(new BeaconEffectSlot(
                this.leftPos + SOG_PRIMARY_SLOT_X, this.topPos + SOG_PRIMARY_SLOT_Y,
                this, this, true, () -> this.primary));
        this.sog$secondarySlot = this.addRenderableWidget(new BeaconEffectSlot(
                this.leftPos + SOG_SECONDARY_SLOT_X, this.topPos + SOG_SECONDARY_SLOT_Y,
                this, this, false, () -> this.secondary));
        this.sog$updateEffectSlots();
    }

    /** 层数/规则开关会在界面打开之后才同步或变化，所以每 tick 重新摆一次。 */
    @Inject(method = "containerTick", at = @At("TAIL"))
    private void sog$tickEffectSlots(CallbackInfo ci) {
        this.sog$updateEffectSlots();
    }

    /**
     * 按当前规则与信标层数更新两个槽位。
     *
     * <p>主效果在原版里 1 层即可用（自定义校验也只要求 ≥1 层），副效果保持原版 4 层门槛。
     * 规则关闭时两个槽都隐藏，界面与原版完全一致。</p>
     */
    @Unique
    private void sog$updateEffectSlots() {
        boolean enabled = SogSettings.customBeaconEffects;
        int levels = this.getMenu().getLevels();
        if (this.sog$primarySlot != null) {
            this.sog$primarySlot.visible = enabled && levels >= 1;
            this.sog$primarySlot.active = this.sog$primarySlot.visible;
        }
        if (this.sog$secondarySlot != null) {
            this.sog$secondarySlot.visible = enabled && levels >= 4;
            this.sog$secondarySlot.active = this.sog$secondarySlot.visible;
        }
        this.sog$layoutUpgradeButton(enabled);
    }

    /**
     * 规则开启时把原版 II 级快捷按钮从 (168, 47) 挪到 (191, 47)，给副效果槽位让位；
     * 规则关闭时挪回原位。
     */
    @Unique
    private void sog$layoutUpgradeButton(boolean ruleEnabled) {
        int x = this.leftPos + (ruleEnabled ? SOG_UPGRADE_MOVED_X : SOG_UPGRADE_VANILLA_X);
        for (Object button : this.beaconButtons) {
            if (button instanceof AbstractWidget widget
                    && "BeaconUpgradePowerButton".equals(widget.getClass().getSimpleName())
                    && widget.getX() != x) {
                widget.setX(x);
            }
        }
    }

    /**
     * 支付物图标行：补一个下界之星、6 个图标重排，并把原版分隔线搬到新格边界上。
     *
     * <p>只处理原版那一行（y 对得上、x 落在左侧这块区域），其它 {@code item} 调用原样放行。</p>
     */
    // 1.21.11 的背景绘制入口叫 renderBg(GuiGraphics, float, int, int)，支付物图标就在里面画。
    @WrapOperation(method = "renderBg",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem("
                            + "Lnet/minecraft/world/item/ItemStack;II)V"))
    private void sog$reorderPaymentIcons(GuiGraphics extractor, ItemStack stack, int x,
            int y, Operation<Void> original) {
        if (!SogSettings.customBeaconEffects) {
            original.call(extractor, stack, x, y);
            return;
        }
        int rowY = this.topPos + SOG_PAYMENT_ROW_Y;
        int index = sog$paymentIconIndex(stack);
        if (index < 0 || y != rowY || x < this.leftPos || x > this.leftPos + 140) {
            original.call(extractor, stack, x, y);
            return;
        }
        // 原版 5 个图标整体右移一格（下标 1..5），下标 0 那格画下界之星。
        int newX = this.leftPos + SOG_PAYMENT_ROW_X + index * SOG_ICON_PITCH;
        if (index == 1) {
            // 画下界合金锭时先把下界之星画在它左边一格。
            extractor.renderItem(new ItemStack(Items.NETHER_STAR), this.leftPos + SOG_PAYMENT_ROW_X, y);
        }
        original.call(extractor, stack, newX, y);
    }

    /** 原版支付物图标在新布局里的下标：合金锭=1、绿宝石=2、钻石=3、金锭=4、铁锭=5。 */
    @Unique
    private static int sog$paymentIconIndex(ItemStack stack) {
        if (stack.is(Items.NETHERITE_INGOT)) {
            return 1;
        }
        if (stack.is(Items.EMERALD)) {
            return 2;
        }
        if (stack.is(Items.DIAMOND)) {
            return 3;
        }
        if (stack.is(Items.GOLD_INGOT)) {
            return 4;
        }
        if (stack.is(Items.IRON_INGOT)) {
            return 5;
        }
        return -1;
    }

    /**
     * 重画支付物图标行的格间分隔线。
     *
     * <p>顺序很关键：先把新分隔线贴好（源像素来自贴图，不受帧缓冲影响），再用纯背景条把原版那
     * 4 条旧线盖掉，否则旧线会横穿新位置的图标。</p>
     */
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void sog$redrawPaymentDividers(GuiGraphics extractor, float partialTick,
            int mouseX, int mouseY, CallbackInfo ci) {
        if (!SogSettings.customBeaconEffects) {
            return;
        }
        int rowY = this.topPos + (int) SOG_DIVIDER_V;
        // 1) 新的 5 条分隔线：第 k 格图标左侧 3 像素，与原版「图标→分隔线」的位置关系一致。
        for (int k = 1; k < 6; k++) {
            int boundary = this.leftPos + SOG_PAYMENT_ROW_X + k * SOG_ICON_PITCH - 3;
            // 1.21.3 的 blit 需要显式给 RenderType（原版同样传 RenderType::guiTextured）。
            extractor.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BEACON_LOCATION,
                    boundary, rowY, SOG_DIVIDER_U, SOG_DIVIDER_V, SOG_DIVIDER_W, SOG_DIVIDER_H,
                    SOG_TEXTURE_SIZE, SOG_TEXTURE_SIZE);
        }
        // 2) 盖掉原版那 4 条：用同一张贴图里一块纯背景色条，资源包换了颜色也能对上。
        for (int old : SOG_VANILLA_DIVIDERS) {
            extractor.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BEACON_LOCATION,
                    this.leftPos + old, rowY, SOG_BACKGROUND_U, SOG_DIVIDER_V,
                    SOG_DIVIDER_W, SOG_DIVIDER_H, SOG_TEXTURE_SIZE, SOG_TEXTURE_SIZE);
        }
    }

    @Override
    public Holder<MobEffect> sog$getPendingPrimary() {
        return this.primary;
    }

    @Override
    public Holder<MobEffect> sog$getPendingSecondary() {
        return this.secondary;
    }

    @Override
    public void sog$setPendingEffect(Holder<MobEffect> effect, boolean primarySlot) {
        if (primarySlot) {
            this.primary = effect;
        } else {
            this.secondary = effect;
        }
    }
}
