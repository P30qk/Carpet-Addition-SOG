package cn.blockforge.generated.sogcarpet.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.phys.Vec3;

/**
 * “嘎吱之心可视化”规则的客户端实现。
 *
 * <p>攻击（打中）一只绑定了嘎吱之心的嘎吱怪之后，把它的<b>嘎吱之心方块用红框</b>框住，
 * 并用<b>黄线</b>把嘎吱之心与嘎吱怪连起来，{@value #SHOW_TICKS} tick 后自动消失；
 * 再次攻击会刷新计时。嘎吱怪死掉、走得太远或离开视野时立即停止显示。</p>
 *
 * <p><b>怎么知道“被打中了”</b>：嘎吱怪被攻击且绑着嘎吱之心时，原版会在服务端设置
 * 无敌帧动画并 {@code broadcastEntityEvent(this, (byte) 66)}。这个广播是<b>原版行为</b>，
 * 所以即使服务端没装本模组、甚至只是一个原版服务器，客户端照样收得到——
 * 这条可视化在<b>纯客户端</b>下同样可用。
 * {@link cn.blockforge.generated.sogcarpet.mixin.client.CreakingHurtMixin}
 * 在客户端截下事件 66 并把“嘎吱怪实体 id”投递进来。</p>
 *
 * <p><b>嘎吱之心的位置</b>：原版把嘎吱怪绑定的家（就是嘎吱之心方块）同步在实体数据
 * {@code HOME_POS} 里，客户端 {@link Creaking#getHomePos()} 直接可读，不需要自己扫方块。
 * 本类把 {@code HOME_POS} 当作<b>权威坐标</b>：每次 tick 都会重新读一次，嘎吱怪重置绑定
 * （白天消散、心被破坏后重新绑定等）时红框会自动跟到新的心上去，不会停在旧方块上。</p>
 *
 * <p><b>相对 1.21.10 原实现做的优化</b>：</p>
 * <ol>
 *   <li>权威坐标如上，事件只用来触发，不缓存事件里的方块坐标；</li>
 *   <li>红框填充做<b>心跳脉动</b>，剩余时间进入最后 {@value #FADE_TICKS} tick 时改为快速闪烁，
 *       看一眼就知道“快消失了”；</li>
 *   <li>超过 {@value #MAX_DISTANCE} 格不画、同屏最多 {@value #MAX_VISIBLE} 个且<b>保留最近的</b>，
 *       刷怪塔/观光场景不会刷屏；</li>
 *   <li>tick 线程<b>零分配</b>：追踪表与渲染快照都用预分配缓冲双缓冲交换，
 *       不再每 tick 新建 {@code ArrayList} 与连线对象。</li>
 * </ol>
 *
 * <p>线程约定与 {@link SculkVisualizer} 一致：投递的标记走无锁队列，tick 线程整理成
 * 只读快照整体替换，渲染线程整体读取。</p>
 */
public final class CreakingHeartVisualizer {
    /** 一次攻击之后红框与黄线保留的 tick 数；与原版嘎吱之心受击后喷粒子的时长一致。 */
    private static final int SHOW_TICKS = 100;
    /** 红框填充的心跳周期（tick）：一个完整“收缩—放松”。 */
    private static final int PULSE_PERIOD = 12;
    /** 剩余时间少于这么多 tick 时改成快速闪烁，提示即将消失。 */
    private static final int FADE_TICKS = 20;
    /** 超过这个距离（格）就不再画，避免远处一堆框。 */
    private static final int MAX_DISTANCE = 96;
    /** 同屏最多同时显示多少个，超出时保留离玩家最近的。 */
    private static final int MAX_VISIBLE = 16;

    /** 嘎吱之心方块的框色（红）。 */
    private static int COLOR_HEART = 0xFFFF3B30;
    /** 红框的淡填充色（同色相、低 alpha），让框内的方块也看得清楚。 */
    private static int COLOR_HEART_FILL = 0x33FF3B30;
    /** 嘎吱之心与嘎吱怪之间的连线颜色（黄）。 */
    private static int COLOR_LINK = 0xFFFFE14D;
    /** 方块框往外套一点，避免和方块自己的面重合导致闪烁 / 特定角度看不见。 */
    private static final float HEART_INFLATE = 0.004F;
    /** 框线和连线都加粗一档，远看也清楚。 */
    private static final float HEART_STROKE_WIDTH = 2.5F;
    private static final float LINK_WIDTH = 4.0F;

    /** 客户端捕到实体事件时投递的待处理标记（只带实体 id，坐标以 HOME_POS 为准）。 */
    private static final ConcurrentLinkedQueue<Integer> PENDING = new ConcurrentLinkedQueue<>();
    /** 仍在显示窗口内的嘎吱之心：方块位置打包值 -&gt; 追踪信息。 */
    private static final Map<Long, Tracked> TRACKED = new HashMap<>();
    /** 本规则在总控里的名字。 */
    private static final String RULE = "creakingHeartVisualizer";

    /**
     * 渲染快照双缓冲：tick 线程写“下一份”，写完整体换引用；渲染线程只读当前那份。
     * 两份缓冲都是预分配的，所以每 tick 只换一个引用，不产生新对象。
     */
    private static final Bucket BUCKET_A = new Bucket(createBuffer());
    private static final Bucket BUCKET_B = new Bucket(createBuffer());
    private static volatile Bucket snapshot = BUCKET_A;
    private static Bucket nextBucket = BUCKET_B;

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
    public static void onCreakingHurt(int creakingId) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        PENDING.add(creakingId);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            PENDING.clear();
            TRACKED.clear();
            publishEmpty();
            return;
        }
        long now = level.getGameTime();
        Integer id;
        while ((id = PENDING.poll()) != null) {
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Creaking creaking)) {
                continue;
            }
            BlockPos home = creaking.getHomePos();
            if (home == null) {
                continue;
            }
            TRACKED.put(home.asLong(), new Tracked(creaking.getId(), now + SHOW_TICKS));
        }

        Bucket bucket = nextBucket;
        int count = 0;
        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();
        double maxSqr = (double) MAX_DISTANCE * MAX_DISTANCE;
        Iterator<Map.Entry<Long, Tracked>> iterator = TRACKED.entrySet().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next().getValue();
            Entity entity = level.getEntity(tracked.creakingId);
            // 计时到了、或嘎吱怪已经不在客户端视野里，就清掉，别留一条指不到怪的线。
            if (tracked.expireTick <= now || !(entity instanceof Creaking creaking)) {
                iterator.remove();
                continue;
            }
            // 权威坐标：HOME_POS 变了（心被破坏后重新绑定、白天重置等）就跟着换方块。
            BlockPos home = creaking.getHomePos();
            if (home == null) {
                iterator.remove();
                continue;
            }
            double distanceSqr = sqr(entity.getX() - playerX)
                    + sqr(entity.getY() - playerY) + sqr(entity.getZ() - playerZ);
            if (distanceSqr > maxSqr) {
                continue;
            }
            int index;
            if (count < MAX_VISIBLE) {
                index = count++;
            } else {
                // 已满：只有比当前最远那个更近才挤掉它（保持“最近的在画面里”）。
                index = farthestIndex(bucket, count);
                if (bucket.slots[index].distanceSqr <= distanceSqr) {
                    continue;
                }
            }
            Slot slot = bucket.slots[index];
            slot.heartAsLong = home.asLong();
            slot.heart = home;
            slot.x = entity.getX();
            slot.y = entity.getY() + entity.getBbHeight() * 0.5D;
            slot.z = entity.getZ();
            slot.remaining = (int) Math.max(0L, tracked.expireTick - now);
            slot.distanceSqr = distanceSqr;
        }
        bucket.count = count;
        snapshot = bucket;
        nextBucket = bucket == BUCKET_A ? BUCKET_B : BUCKET_A;
    }

    private static double sqr(double value) {
        return value * value;
    }

    /** 当前快照里距离最远的那个槽位下标。 */
    private static int farthestIndex(Bucket bucket, int count) {
        int index = 0;
        double worst = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            if (bucket.slots[i].distanceSqr > worst) {
                worst = bucket.slots[i].distanceSqr;
                index = i;
            }
        }
        return index;
    }

    private static void publishEmpty() {
        Bucket bucket = nextBucket;
        bucket.count = 0;
        snapshot = bucket;
        nextBucket = bucket == BUCKET_A ? BUCKET_B : BUCKET_A;
    }

    /** 从用户配置刷新本规则的配色；每帧渲染前调用一次，允许运行时改色。 */
    private static void refreshColors() {
        COLOR_HEART = VisualizerConfig.color(RULE, "heart", 0xFFFF3B30);
        COLOR_HEART_FILL = VisualizerConfig.color(RULE, "heartFill", 0x33FF3B30);
        COLOR_LINK = VisualizerConfig.color(RULE, "link", 0xFFFFE14D);
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Bucket current = snapshot;
        if (current.count == 0) {
            return;
        }
        refreshColors();
        Minecraft client = Minecraft.getInstance();
        long now = client.level == null ? 0L : client.level.getGameTime();
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            for (int i = 0; i < current.count; i++) {
                Slot slot = current.slots[i];
                GizmoStyle heart = GizmoStyle.strokeAndFill(COLOR_HEART, HEART_STROKE_WIDTH,
                        fadedFill(slot.remaining, now));
                Gizmos.cuboid(slot.heart, HEART_INFLATE, heart);
                Gizmos.line(new Vec3(slot.heart.getX() + 0.5D, slot.heart.getY() + 0.5D,
                                slot.heart.getZ() + 0.5D),
                        new Vec3(slot.x, slot.y, slot.z), COLOR_LINK, LINK_WIDTH);
            }
        }
    }

    /**
     * 心跳式淡填充：平时按 {@value #PULSE_PERIOD} tick 的节奏轻微脉动，
     * 剩余时间进入最后 {@value #FADE_TICKS} tick 时改成 8 tick 一轮的快速闪烁。
     */
    private static int fadedFill(int remaining, long now) {
        if (remaining <= FADE_TICKS) {
            // 到期提示：亮度在 100% 与 35% 之间跳，闪得明显但不刺眼。
            return remaining % 8 < 4 ? COLOR_HEART_FILL : scaleAlpha(COLOR_HEART_FILL, 0.35D);
        }
        double phase = (now % PULSE_PERIOD) / (double) PULSE_PERIOD;
        double pulse = 0.75D + 0.25D * Math.sin(phase * Math.PI * 2.0D);
        return scaleAlpha(COLOR_HEART_FILL, pulse);
    }

    /** 只缩放 alpha，色相保持不变。 */
    private static int scaleAlpha(int argb, double factor) {
        int alpha = (int) Math.round(((argb >>> 24) & 0xFF) * factor);
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static Slot[] createBuffer() {
        Slot[] slots = new Slot[MAX_VISIBLE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
        return slots;
    }

    /** 一只正在显示窗口内的嘎吱之心。 */
    private static final class Tracked {
        private final int creakingId;
        private final long expireTick;

        private Tracked(int creakingId, long expireTick) {
            this.creakingId = creakingId;
            this.expireTick = expireTick;
        }
    }

    /** 一条待渲染的“嘎吱之心 -&gt; 嘎吱怪”连线；字段可变，由双缓冲复用。 */
    private static final class Slot {
        private long heartAsLong;
        private BlockPos heart;
        private double x;
        private double y;
        private double z;
        private int remaining;
        private double distanceSqr;
    }

    /** 一份只读渲染快照：预分配数组 + 有效长度。 */
    private static final class Bucket {
        private final Slot[] slots;
        private int count;

        private Bucket(Slot[] slots) {
            this.slots = slots;
        }
    }
}
