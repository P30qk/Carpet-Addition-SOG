package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * “嘎吱之心可视化”规则的客户端实现。
 *
 * <p>攻击（打中）一只绑定了嘎吱之心的嘎吱怪之后，把它的<b>嘎吱之心方块用红框</b>框住，
 * 并用<b>黄线</b>把嘎吱之心与嘎吱怪连起来，{@value #SHOW_TICKS} tick 后自动消失；
 * 再次攻击会刷新计时。嘎吱怪死掉或离开视野时立即停止显示。</p>
 *
 * <p><b>怎么知道“被打中了”</b>：嘎吱怪被攻击且绑着嘎吱之心时，原版会在服务端设置
 * 无敌帧动画并 {@code broadcastEntityEvent(this, (byte) 66)}，客户端收到的正是这个
 * 实体事件。{@link cn.blockforge.generated.sogcarpet.mixin.client.CreakingHurtMixin}
 * 在客户端截下事件 66 并把“嘎吱之心方块 + 嘎吱怪实体 id”投递进来。</p>
 *
 * <p><b>嘎吱之心的位置</b>：原版把嘎吱怪绑定的家（就是嘎吱之心方块）同步在实体数据
 * {@code HOME_POS} 里，客户端 {@link Creaking#getHomePos()} 直接可读，不需要自己扫方块。
 * 嘎吱怪的位置在渲染时从实体 id 现取，所以黄线会跟着怪走。</p>
 *
 * <p>线程约定与 {@link SculkVisualizer} 一致：投递的标记走无锁队列，tick 线程整理成
 * 只读快照整体替换，渲染线程整体读取。</p>
 */
public final class CreakingHeartVisualizer {
    /** 一次攻击之后红框与黄线保留的 tick 数；与原版嘎吱之心受击后喷粒子的时长一致。 */
    private static final int SHOW_TICKS = 100;
    /** 嘎吱之心方块的框色（红）。 */
    private static final int COLOR_HEART = 0xFFFF3B30;
    /** 红框的淡填充色（同色相、低 alpha），让框内的方块也看得清楚。 */
    private static final int COLOR_HEART_FILL = 0x33FF3B30;
    /** 嘎吱之心与嘎吱怪之间的连线颜色（黄）。 */
    private static final int COLOR_LINK = 0xFFFFE14D;
    /** 方块框往外套一点，避免和方块自己的面重合导致闪烁 / 特定角度看不见。 */
    private static final float HEART_INFLATE = 0.004F;
    /** 框线和连线都加粗一档，远看也清楚。 */
    private static final float HEART_STROKE_WIDTH = 2.5F;
    private static final float LINK_WIDTH = 4.0F;

    /** 客户端捕到实体事件时投递的待处理标记。 */
    private static final ConcurrentLinkedQueue<Mark> PENDING = new ConcurrentLinkedQueue<>();
    /** 仍在显示窗口内的嘎吱之心：方块位置打包值 -&gt; 追踪信息。 */
    private static final Map<Long, Tracked> TRACKED = new HashMap<>();
    /** 本规则在总控里的名字。 */
    private static final String RULE = "creakingHeartVisualizer";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private CreakingHeartVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CreakingHeartVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(CreakingHeartVisualizer::renderGizmos);
    }

    /**
     * 客户端收到“绑着嘎吱之心的嘎吱怪被攻击”时调用（实体事件 66）。
     * 可能来自网络包处理线程，只做一次入队。
     */
    public static void onCreakingHurt(BlockPos heart, int creakingId) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        PENDING.add(new Mark(heart.immutable(), creakingId));
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            PENDING.clear();
            TRACKED.clear();
            snapshot = Snapshot.EMPTY;
            return;
        }
        long now = level.getGameTime();
        Mark mark;
        while ((mark = PENDING.poll()) != null) {
            TRACKED.put(mark.heart.asLong(), new Tracked(mark.heart, mark.creakingId, now + SHOW_TICKS));
        }
        List<Link> links = new ArrayList<>();
        Iterator<Map.Entry<Long, Tracked>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next().getValue();
            Entity entity = level.getEntity(tracked.creakingId);
            // 计时到了、或嘎吱怪已经不在客户端视野里，就清掉，别留一条指不到怪的线。
            if (tracked.expireTick <= now || entity == null) {
                iterator.remove();
                continue;
            }
            links.add(new Link(tracked.heart, entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ()));
        }
        snapshot = new Snapshot(links);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.links.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            GizmoStyle heart = GizmoStyle.strokeAndFill(COLOR_HEART, HEART_STROKE_WIDTH, COLOR_HEART_FILL);
            for (int i = 0; i < current.links.size(); i++) {
                Link link = current.links.get(i);
                Gizmos.cuboid(link.heart, HEART_INFLATE, heart);
                Gizmos.line(Vec3.atCenterOf(link.heart), new Vec3(link.x, link.y, link.z), COLOR_LINK,
                        LINK_WIDTH);
            }
        }
    }

    /** 嘎吱怪受击事件带来的待处理标记。 */
    private static final class Mark {
        private final BlockPos heart;
        private final int creakingId;

        private Mark(BlockPos heart, int creakingId) {
            this.heart = heart;
            this.creakingId = creakingId;
        }
    }

    /** 一只正在显示窗口内的嘎吱之心。 */
    private static final class Tracked {
        private final BlockPos heart;
        private final int creakingId;
        private final long expireTick;

        private Tracked(BlockPos heart, int creakingId, long expireTick) {
            this.heart = heart;
            this.creakingId = creakingId;
            this.expireTick = expireTick;
        }
    }

    /** 一条待渲染的“嘎吱之心 -&gt; 嘎吱怪”连线。 */
    private static final class Link {
        private final BlockPos heart;
        private final double x;
        private final double y;
        private final double z;

        private Link(BlockPos heart, double x, double y, double z) {
            this.heart = heart;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Link>emptyList());

        private final List<Link> links;

        private Snapshot(List<Link> links) {
            this.links = links;
        }
    }
}
