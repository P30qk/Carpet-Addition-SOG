package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.HealthChangePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 生命数值变化可视化：实体生命值变化时在头顶浮出数值，受伤是红色带负号的数、回血是绿色带正号的数，
 * 3 秒后消失。
 *
 * <p>数据来源有两条路，保证“服务端装了本模组”和“纯客户端”都能渲染：</p>
 * <ul>
 *   <li><b>服务端装了本模组</b>：服务端采集真实数值——受伤用
 *       {@code ServerLivingEntityEvents.AFTER_DAMAGE} 里结算进血量的伤害，回血用
 *       {@code LivingEntity#heal} 前后的血量差——每 tick 把玩家附近的变化用
 *       {@link HealthChangePayload} 发过来（负数=受伤，正数=回血），这里只负责显示。</li>
 *   <li><b>对面没装本模组（纯客户端）</b>：血量本来就同步到客户端，于是逐 tick 采样玩家附近生物的
 *       {@link LivingEntity#getHealth()}，掉血与回血都按差值显示，不需要服务端配合。估算按配置项
 *       {@code clientEstimate} 开关，默认开。</li>
 * </ul>
 *
 * <p>显示效果：数字出现在实体头顶，向上飘约 {@value #RISE} 格（先快后慢），最后
 * {@value #FADE_TICKS} tick 淡出，满 {@value #LIFETIME_TICKS} tick（3 秒）后移除。受伤显示
 * {@code -6}（红），回血显示 {@code +4}（绿）；数值是整数时只显示整数，半心这种带小数的显示
 * 一位小数。两种颜色都能在 Ctrl+V 的“配置”里改。位置与透明度每帧重算，所以数字跟着实体平滑
 * 移动、不会一卡一跳。</p>
 */
public final class HealthChangeVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "healthChangeVisualizer";

    /** 一条数字从出现到消失的总时长：3 秒。 */
    public static final int LIFETIME_TICKS = 60;
    /** 最后多少 tick 用来淡出。 */
    private static final int FADE_TICKS = 15;
    /** 数字向上飘多少格。 */
    private static final double RISE = 1.0D;
    /** 数字离实体头顶的距离（格）。 */
    private static final double LABEL_GAP = 0.35D;
    /** 文字缩放：比默认稍大一点，混战里也要一眼看清。 */
    private static final float SCALE = 1.2F;
    /** 纯客户端估算时只采样玩家周围这个距离（格）以内的实体。 */
    private static final double SCAN_RANGE = 48.0D;
    private static final double SCAN_RANGE_SQR = SCAN_RANGE * SCAN_RANGE;
    /** 同屏最多同时存在多少个数字（超出时丢掉最老的）。 */
    private static final int MAX_ACTIVE = 192;
    /** 收包队列上限：服务端一 tick 最多发一包，这里留足余量，超了丢新来的。 */
    private static final int MAX_INCOMING = 512;
    /** 血量变化的判定阈值：小于它不算数（避免浮点噪声刷出 ±0.0）。 */
    private static final float MIN_DELTA = 0.05F;

    /** 受伤（掉血）数字的颜色：红。 */
    private static int COLOR_DAMAGE = 0xFFFF3B30;
    /** 回血数字的颜色：绿。 */
    private static int COLOR_HEAL = 0xFF35D94A;

    /** 从用户配置刷新颜色（没有配置时回落到默认的红 / 绿）。 */
    private static void refreshColors() {
        COLOR_DAMAGE = VisualizerConfig.color(RULE, "damage", 0xFFFF3B30);
        COLOR_HEAL = VisualizerConfig.color(RULE, "heal", 0xFF35D94A);
    }

    /** 收包线程写入、客户端 tick 读出的待处理事件。 */
    private static final Queue<Change> INCOMING = new ConcurrentLinkedQueue<>();
    /** 正在显示的数字。 */
    private static final List<Number> ACTIVE = new ArrayList<>();
    /**
     * 纯客户端估算用：实体号 → 上一 tick 的血量。每 tick 整体重建，实体离开视野或消失后
     * 不会在表里留下垃圾。
     */
    private static final Map<Integer, Float> LAST_HEALTH = new HashMap<>();
    /** 当前渲染器绑定的世界；换世界（换维度、重连）时清掉所有数字。 */
    private static ClientLevel cachedLevel;

    private HealthChangeVisualizer() {
    }

    /** 客户端入口调用一次：注册客户端 tick 与世界渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(HealthChangeVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(HealthChangeVisualizer::renderGizmos);
    }

    /**
     * 服务端发来的生命数值变化事件（{@link HealthChangePayload}）。
     *
     * <p>这个方法跑在网络线程上：只把数据入队，不碰实体、不碰渲染状态，真正的处理在客户端 tick。</p>
     */
    public static void accept(HealthChangePayload payload) {
        if (payload == null || payload.entries().isEmpty()) {
            return;
        }
        String dimension = payload.dimension() == null ? "" : payload.dimension();
        for (HealthChangePayload.Entry entry : payload.entries()) {
            if (INCOMING.size() >= MAX_INCOMING) {
                return;
            }
            INCOMING.add(new Change(dimension, entry.entityId(), entry.delta(),
                    entry.x(), entry.y(), entry.z()));
        }
    }

    /** 断开连接时清掉全部状态，免得把上一个服务器的数字带到下一个服务器。 */
    public static void clear() {
        INCOMING.clear();
        ACTIVE.clear();
        LAST_HEALTH.clear();
        cachedLevel = null;
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            clear();
            return;
        }
        if (level != cachedLevel) {
            // 换世界：数字属于上一个世界，直接扔掉。
            ACTIVE.clear();
            LAST_HEALTH.clear();
            INCOMING.clear();
            cachedLevel = level;
        }
        drainIncoming(level);
        // 纯客户端（对面没装本模组）才按血量差估算，免得和服务端的精确值重复显示。
        if (!VisualizerState.serverHasSogRules()
                && VisualizerConfig.option(RULE, "clientEstimate", true)) {
            estimateFromHealth(level, client);
        } else {
            LAST_HEALTH.clear();
        }
        for (Iterator<Number> it = ACTIVE.iterator(); it.hasNext();) {
            Number number = it.next();
            if (++number.age >= LIFETIME_TICKS) {
                it.remove();
            }
        }
    }

    /** 把收包队列里属于当前维度的事件并进渲染列表。 */
    private static void drainIncoming(ClientLevel level) {
        String dimension = level.dimension().identifier().toString();
        Change change;
        while ((change = INCOMING.poll()) != null) {
            if (!change.dimension().isEmpty() && !change.dimension().equals(dimension)) {
                continue;
            }
            add(change.entityId(), change.delta(), change.x(), change.y(), change.z());
        }
    }

    /**
     * 纯客户端估算：逐 tick 比较玩家附近生物的血量，掉血与回血各按差值生成一个数字。
     *
     * <p>血量是原版同步到客户端的实体数据，所以对面没装本模组也能用。掉血差值就是这次真正扣掉的
     * 血量（一击致死时显示的是它当时剩余的那部分）；回血差值就是这次真正回复的血量。</p>
     */
    private static void estimateFromHealth(ClientLevel level, Minecraft client) {
        Vec3 eye = client.player.position();
        Map<Integer, Float> current = new HashMap<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isRemoved()) {
                continue;
            }
            Vec3 pos = living.position();
            if (pos.distanceToSqr(eye) > SCAN_RANGE_SQR) {
                continue;
            }
            float health = living.getHealth();
            current.put(living.getId(), health);
            Float previous = LAST_HEALTH.get(living.getId());
            if (previous == null || Math.abs(health - previous) < MIN_DELTA) {
                continue;
            }
            // 掉血给负数、回血给正数，符号由渲染阶段决定用减号还是加号。
            add(living.getId(), health - previous, pos.x, pos.y + living.getBbHeight(), pos.z);
        }
        LAST_HEALTH.clear();
        LAST_HEALTH.putAll(current);
    }

    /** 记下一个待显示的数字（同屏上限之外的老数字先丢掉）。 */
    private static void add(int entityId, float delta, double x, double y, double z) {
        if (Math.abs(delta) < MIN_DELTA) {
            return;
        }
        if (ACTIVE.size() >= MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Number(entityId, delta, x, y, z));
    }

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE) || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        refreshColors();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        try (Gizmos.TemporaryCollection collection =
                context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (Number number : ACTIVE) {
                float age = number.age + partialTick;
                double progress = Math.min(1.0D, Math.max(0.0D, age / LIFETIME_TICKS));
                // 先快后慢地往上飘：一开始窜得快，接近消失时几乎停住。
                double rise = RISE * (1.0D - (1.0D - progress) * (1.0D - progress));
                Vec3 anchor = anchorOf(level, number, partialTick);
                // 受伤红、回血红；颜色里的 alpha 按剩余寿命衰减。
                int color = (alpha(age) << 24) | (colorOf(number.delta) & 0x00FFFFFF);
                Gizmos.billboardText(format(number.delta),
                        new Vec3(anchor.x, anchor.y + rise, anchor.z),
                        TextGizmo.Style.forColor(color).withScale(SCALE));
            }
        }
    }

    /** 掉血（负数）用受伤色，回血（正数）用回血色。 */
    private static int colorOf(float delta) {
        return delta < 0.0F ? COLOR_DAMAGE : COLOR_HEAL;
    }

    /**
     * 数字的锚点：实体还看得见就跟着它（按插值位置，跟随平滑），否则停在事件发生时记下的位置
     * （例如那一击直接把实体打死了）。
     */
    private static Vec3 anchorOf(ClientLevel level, Number number, float partialTick) {
        Entity entity = level.getEntity(number.entityId);
        if (entity != null && !entity.isRemoved()) {
            Vec3 pos = entity.getPosition(partialTick);
            return new Vec3(pos.x, pos.y + entity.getBbHeight() + LABEL_GAP, pos.z);
        }
        return new Vec3(number.x, number.y + LABEL_GAP, number.z);
    }

    /** 剩余寿命算出的不透明度：一直满，最后 {@value #FADE_TICKS} tick 线性淡出。 */
    private static int alpha(float age) {
        float remaining = LIFETIME_TICKS - age;
        if (remaining >= FADE_TICKS) {
            return 255;
        }
        return (int) (255.0F * Math.max(0.0F, remaining / FADE_TICKS));
    }

    /**
     * 变化的显示文本：掉血带负号、回血带正号，数值取绝对值；整数不拖小数尾巴，半心这种带小数的
     * 显示一位。
     */
    private static String format(double delta) {
        double magnitude = Math.abs(delta);
        String sign = delta < 0.0D ? "-" : "+";
        if (Math.abs(magnitude - Math.round(magnitude)) < 0.05D) {
            return sign + Math.round(magnitude);
        }
        return sign + String.format(Locale.ROOT, "%.1f", magnitude);
    }

    /** 服务端发来的一条生命数值变化事件。 */
    private record Change(String dimension, int entityId, float delta, double x, double y, double z) {
    }

    /** 正在显示的一个数字；{@code age} 在客户端 tick 里自增。 */
    private static final class Number {
        private final int entityId;
        private final float delta;
        private final double x;
        private final double y;
        private final double z;
        private int age;

        private Number(int entityId, float delta, double x, double y, double z) {
            this.entityId = entityId;
            this.delta = delta;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
