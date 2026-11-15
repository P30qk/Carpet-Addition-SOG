package cn.blockforge.generated.sogcarpet.client;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;

/**
 * 信标界面上的「自定义效果槽位」——一个原版槽位样式的方块，点它打开效果选择界面。
 *
 * <p>为什么要做成一个整体控件：之前是“空槽 + 旁边一个灰色按钮（+）”两块东西，既挤又和原版
 * 风格不搭。现在合成一块：</p>
 * <ul>
 *   <li><b>还没选效果</b>：画成原版槽位的凹陷样式，正中间一个「+」，提示“点这里挑效果”；</li>
 *   <li><b>已经选好</b>：同一个槽位里直接显示该效果的图标；再点一次重新挑，图标就地替换。</li>
 * </ul>
 *
 * <p>槽位样式的颜色与像素尺寸都照抄原版 {@code textures/gui/container/beacon.png} 里的背包槽位：
 * 上/左 1 像素 {@code #373737}、底色 {@code #8B8B8B}、下/右 1 像素 {@code #FFFFFF}，
 * 合计 18×18（与原版效果按钮同尺寸），所以放在信标界面上看不出是模组画上去的。</p>
 */
public final class BeaconEffectSlot extends AbstractWidget {
    /** 原版槽位底色。 */
    private static final int SLOT_BODY = 0xFF8B8B8B;
    /** 原版槽位左上暗边。 */
    private static final int SLOT_SHADE = 0xFF373737;
    /** 原版槽位右下亮边。 */
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
    /** 槽位尺寸：与原版效果按钮一致。 */
    public static final int SIZE = 18;

    private final SogBeaconScreenAccess access;
    private final boolean primarySlot;
    private final Supplier<Holder<MobEffect>> current;
    /** 所属的信标界面；从选择界面返回时要回到它。 */
    private final Screen parent;

    public BeaconEffectSlot(int x, int y, Screen parent, SogBeaconScreenAccess access,
            boolean primarySlot, Supplier<Holder<MobEffect>> current) {
        super(x, y, SIZE, SIZE, Component.translatable(primarySlot
                ? "sog_carpet.ui.beacon.pick_primary"
                : "sog_carpet.ui.beacon.pick_secondary"));
        this.parent = parent;
        this.access = access;
        this.primarySlot = primarySlot;
        this.current = current;
        this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("sog_carpet.ui.beacon.slot_hint")));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
            float partialTick) {
        int x = this.getX();
        int y = this.getY();
        // 原版槽位的一圈“凹陷”边框 + 底色。
        extractor.fill(x, y, x + SIZE, y + SIZE, SLOT_BODY);
        extractor.fill(x, y, x + SIZE, y + 1, SLOT_SHADE);
        extractor.fill(x, y, x + 1, y + SIZE, SLOT_SHADE);
        extractor.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, SLOT_HIGHLIGHT);
        extractor.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, SLOT_HIGHLIGHT);

        Holder<MobEffect> effect = this.current.get();
        if (effect == null) {
            // 空槽：正中一个「+」，颜色用暗边色，看起来像刻在槽里。
            extractor.centeredText(Minecraft.getInstance().font, "+", x + SIZE / 2, y + 5,
                    SLOT_SHADE);
            return;
        }
        extractor.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(effect),
                x + 1, y + 1, 16, 16);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.active || !this.visible || event.button() != 0
                || !this.isMouseOver(event.x(), event.y())) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 与按钮一致：切换界面前先放一声音效。
        this.playDownSound(minecraft.getSoundManager());
        minecraft.setScreenAndShow(new BeaconEffectPickerScreen(this.parent, this.access,
                this.primarySlot));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
