package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.AggroInfoPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * “仇恨可视化”的客户端绘制。
 *
 * <p>服务端每隔几 tick 发来一份“谁锁定了谁”的快照（{@link AggroInfoPayload}），客户端用
 * 实体号找回本地实体，在它们头顶画标签、再用线把关系连起来：</p>
 * <ul>
 *   <li><b>被锁定目标</b>：红色瞄准环（水平圆环）画在目标头顶；</li>
 *   <li><b>尝试攻击者</b>：红色感叹号 {@code !} 画在攻击者头顶；</li>
 *   <li><b>连线</b>：攻击者头顶到目标身上画一条红线；</li>
 *   <li><b>坚守者疑问</b>：被声音吸引但未锁定时，头顶画黄色问号 {@code ?}，
 *       并用黄线连到声音源所在的方块。</li>
 * </ul>
 *
 * <p>所有生物都会参与：玩家只会作为“被锁定目标”出现（瞄准环），其它生物可以同时既是攻击者
 * 又是目标，于是头顶会同时出现感叹号和瞄准环。</p>
 *
 * <p><b>渲染优化</b>：同屏最多处理 {@value #MAX_ENTRIES} 条关系；只处理攻击者落在玩家
 * {@value #RENDER_RANGE} 格以内的关系；服务端快照超过 {@value #SNAPSHOT_TTL_MILLIS} 毫秒
 * 没更新就整份丢弃，避免服务端停发后留下残影；位置在客户端 tick 线程解析成不可变图元，
 * 渲染线程只读快照。</p>
 */
public final class AggroVisualizer {
    private static final String RULE = "aggroVisualizer";

    /** 服务端快照多久没更新就丢弃（服务端每 4 tick 发一次，留足冗余）。 */
    private static final long SNAPSHOT_TTL_MILLIS = 1500L;
    /** 攻击者超过这个距离就不画。 */
    private static final double RENDER_RANGE = 96.0D;
    private static final double RENDER_RANGE_SQR = RENDER_RANGE * RENDER_RANGE;
    /** 同屏最多处理多少条关系。 */
    private static final int MAX_ENTRIES = 48;

    /** 攻击者/目标：红。 */
    private static int COLOR_ATTACK = 0xFFFF3B30;
    /** 坚守者疑问：黄。 */
    private static int COLOR_QUESTION = 0xFFFFD21E;
    private static final float LINE_WIDTH = 2.0F;
    private static final float RING_RADIUS = 0.45F;
    private static final float RING_WIDTH = 2.0F;
    private static final float LABEL_SCALE = 1.0F;
    /** 标签离头顶的高度。 */
    private static final double LABEL_Y_OFFSET = 0.7D;

    /** 从用户配置刷新颜色（没有配置时回落到默认值）。 */
    private static void refreshColors() {
        COLOR_ATTACK = VisualizerConfig.color(RULE, "attack", 0xFFFF3B30);
        COLOR_QUESTION = VisualizerConfig.color(RULE, "question", 0xFFFFD21E);
    }

    private static volatile RawSnapshot raw = RawSnapshot.EMPTY;
    private static volatile long lastUpdateMillis;
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private AggroVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AggroVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(AggroVisualizer::renderGizmos);
    }

    /** 网络包接收器（{@code AggroInfoPayload}）送来的快照，只在收包线程做最少的工作。 */
    public static void accept(AggroInfoPayload payload) {
        if (payload == null) {
            return;
        }
        raw = new RawSnapshot(payload.dimension(), payload.entries());
        lastUpdateMillis = System.currentTimeMillis();
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        RawSnapshot current = raw;
        if (current.entries.isEmpty()
                || System.currentTimeMillis() - lastUpdateMillis > SNAPSHOT_TTL_MILLIS) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (!level.dimension().identifier().toString().equals(current.dimension)) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        Vec3 eye = client.player == null ? null : client.player.position();
        snapshot = build(level, current.entries, eye);
    }

    private static Snapshot build(ClientLevel level, List<AggroInfoPayload.Entry> entries, Vec3 eye) {
        List<Mark> marks = new ArrayList<>();
        int count = 0;
        for (AggroInfoPayload.Entry entry : entries) {
            if (count >= MAX_ENTRIES) {
                break;
            }
            Entity attacker = level.getEntity(entry.attackerId());
            if (attacker == null || attacker.isRemoved()) {
                continue;
            }
            if (entry.hasQuestion()) {
                if (entry.soundPos() == null) {
                    continue;
                }
                Vec3 head = labelPos(attacker);
                if (eye != null && head.distanceToSqr(eye) > RENDER_RANGE_SQR) {
                    continue;
                }
                marks.add(Mark.link(head, Vec3.atCenterOf(entry.soundPos()), COLOR_QUESTION));
                marks.add(Mark.label(head, "?", COLOR_QUESTION));
                count++;
            } else if (entry.hasTarget()) {
                Entity target = level.getEntity(entry.targetId());
                if (target == null || target.isRemoved()) {
                    continue;
                }
                Vec3 head = labelPos(attacker);
                if (eye != null && head.distanceToSqr(eye) > RENDER_RANGE_SQR) {
                    continue;
                }
                Vec3 targetPos = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
                marks.add(Mark.link(head, targetPos, COLOR_ATTACK));
                marks.add(Mark.label(head, "!", COLOR_ATTACK));
                marks.add(Mark.ring(targetPos, COLOR_ATTACK));
                count++;
            }
        }
        return new Snapshot(marks);
    }

    private static Vec3 labelPos(Entity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() + LABEL_Y_OFFSET, 0.0D);
    }

    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot current = snapshot;
        if (current.marks.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection =
                context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < current.marks.size(); i++) {
                Mark mark = current.marks.get(i);
                switch (mark.kind) {
                    case Mark.KIND_LINK ->
                            Gizmos.line(mark.from, mark.to, mark.color, LINE_WIDTH);
                    case Mark.KIND_RING ->
                            Gizmos.circle(mark.from, RING_RADIUS, GizmoStyle.stroke(mark.color, RING_WIDTH));
                    default ->
                            Gizmos.billboardText(mark.text, mark.from,
                                    TextGizmo.Style.forColor(mark.color).withScale(LABEL_SCALE));
                }
            }
        }
    }

    /** 一个待绘制标记：连线、圆环或文字。 */
    private static final class Mark {
        private static final int KIND_LINK = 0;
        private static final int KIND_RING = 1;
        private static final int KIND_LABEL = 2;

        private final int kind;
        private final Vec3 from;
        private final Vec3 to;
        private final int color;
        private final String text;

        private Mark(int kind, Vec3 from, Vec3 to, int color, String text) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.color = color;
            this.text = text;
        }

        private static Mark link(Vec3 from, Vec3 to, int color) {
            return new Mark(KIND_LINK, from, to, color, null);
        }

        private static Mark ring(Vec3 at, int color) {
            return new Mark(KIND_RING, at, null, color, null);
        }

        private static Mark label(Vec3 at, String text, int color) {
            return new Mark(KIND_LABEL, at, null, color, text);
        }
    }

    /** 服务端原始快照（收包线程写、tick 线程读）。 */
    private static final class RawSnapshot {
        private static final RawSnapshot EMPTY =
                new RawSnapshot("", Collections.<AggroInfoPayload.Entry>emptyList());

        private final String dimension;
        private final List<AggroInfoPayload.Entry> entries;

        private RawSnapshot(String dimension, List<AggroInfoPayload.Entry> entries) {
            this.dimension = dimension;
            this.entries = entries;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Mark>emptyList());

        private final List<Mark> marks;

        private Snapshot(List<Mark> marks) {
            this.marks = marks;
        }
    }
}
