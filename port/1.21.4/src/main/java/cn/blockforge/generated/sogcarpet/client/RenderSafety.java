package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 世界渲染的安全网。
 *
 * <p><b>为什么需要它</b>：本模组的所有图元都提交在
 * {@code WorldRenderEvents.BEFORE_DEBUG_RENDER} 里，用的是原版那一份 {@code PoseStack} 与
 * 关卡级的 {@code MultiBufferSource}。这条路线上只要抛一次异常、或者把一次没画完的批留到
 * 后面的渲染阶段，后果都不是“少画几个框”，而是<b>整帧坏掉</b>——玩家看到的就是加入世界后
 * 一片黑屏。原版对自己的调试渲染也没有做隔离，所以这里自己兜底：</p>
 *
 * <ul>
 *   <li>{@link #guard}：把每个渲染回调整个包起来，异常只记录下来，绝不让它冒到原版渲染循环里；</li>
 *   <li>{@link #flush}：把我们用到的 RenderType 立刻冲掉，避免顶点泄漏到后续（GUI）阶段——
 *       那一阶段的投影是正交的，世界坐标的顶点会糊满整个屏幕；</li>
 *   <li>{@link #sane}：丢弃 NaN / 无穷 / 坐标大到离谱的顶点，它们会产生覆盖全屏的三角形；</li>
 *   <li>{@link #fault}：连续出错 {@value #MAX_FAULTS} 次就<b>永久停用</b>本模组的全部可视化
 *       （{@link #tripped()}），并在聊天栏说明一次。宁可少画，也不能让玩家玩不了。</li>
 * </ul>
 *
 * <p>这套逻辑不依赖任何服务端规则，纯客户端生效；对面没装本模组时同样工作。</p>
 */
public final class RenderSafety {
    /** 连续多少次渲染异常后停用全部可视化。 */
    private static final int MAX_FAULTS = 3;
    /** 坐标绝对值超过这个量级就认定是坏数据（原版世界边界的量级是 3e7）。 */
    private static final double MAX_COORDINATE = 3.0E7D;

    private static int faults;
    private static volatile boolean tripped;
    private static boolean reported;

    private RenderSafety() {
    }

    /** 本模组的可视化是否已被安全网停用。 */
    public static boolean tripped() {
        return tripped;
    }

    /**
     * 跑一段渲染代码，把任何 {@link Throwable} 关在里面。
     *
     * <p>注意只捕获、不吞掉“程序错误”的可见性：第一次出错会在日志里留下完整堆栈，连续出错才会
     * 停用全部可视化。</p>
     */
    public static void guard(String what, Runnable body) {
        if (tripped) {
            return;
        }
        try {
            body.run();
        } catch (Throwable t) {
            fault(what, t);
        }
    }

    /** 记录一次渲染异常；达到阈值后停用全部可视化并提示玩家。 */
    public static void fault(String what, Throwable error) {
        faults++;
        System.err.println("[sog_carpet] 渲染回调异常（" + what + "）：" + error);
        error.printStackTrace();
        if (faults >= MAX_FAULTS) {
            trip();
        }
    }

    private static void trip() {
        if (tripped) {
            return;
        }
        tripped = true;
        System.err.println("[sog_carpet] 已停用本模组的全部可视化渲染（连续 " + faults + " 次异常）");
        if (reported) {
            return;
        }
        reported = true;
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                client.player.displayClientMessage(Component.translatable(
                        "sog_carpet.ui.render_safety_off").withStyle(ChatFormatting.RED), false);
            }
        } catch (Throwable ignored) {
            // 连提示都发不出去也无所谓，日志里已经有了。
        }
    }

    /** 顶点是否可用：有限、且坐标在合理量级。 */
    public static boolean sane(Vec3 vec) {
        if (vec == null) {
            return false;
        }
        return sane(vec.x) && sane(vec.y) && sane(vec.z);
    }

    /** 单个坐标分量是否可用。 */
    public static boolean sane(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
    }

    /**
     * 立刻把我们写进关卡 {@code MultiBufferSource} 的几何冲掉。
     *
     * <p>原版在调试渲染之后只调用 {@code endLastBatch()}（只冲最后一个 RenderType），
     * 其余批次会留到这一帧后面某个 {@code endBatch()}。如果在 GUI 阶段才被冲掉，
     * 世界坐标的顶点会以正交投影画出来——那就是一片糊满屏幕的三角形。这里主动收尾，
     * 保证我们提交的东西一定在本阶段的正确投影下画完。</p>
     */
    public static void flush(MultiBufferSource consumers) {
        if (!(consumers instanceof MultiBufferSource.BufferSource bufferSource)) {
            return;
        }
        try {
            bufferSource.endBatch(RenderType.lines());
        } catch (Throwable ignored) {
            // 类型不匹配（不同小版本存在与否）就跳过。
        }
        try {
            bufferSource.endBatch(RenderType.debugQuads());
        } catch (Throwable ignored) {
        }
        try {
            bufferSource.endBatch(RenderType.debugFilledBox());
        } catch (Throwable ignored) {
        }
        try {
            bufferSource.endBatch();
        } catch (Throwable ignored) {
        }
    }
}
