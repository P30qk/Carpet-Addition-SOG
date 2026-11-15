package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 掉落物数量标签可视化（移植自 drop-count）。
 *
 * <p>在掉落物上方以名牌的方式画出 {@code 名称 x数量}。同名物品、包围盒水平外扩
 * {@value #MERGE_EXPAND} 格后会挨在一起的掉落物合并成一个标签并累加数量（正好是不能叠进
 * 同一个物品堆、会被原版合并的那些），重叠的标签自动向上错开。</p>
 *
 * <p>渲染方式与原模组一致：仍是在 {@code LevelRenderer#submitEntities} 的末尾，以世界空间的
 * 方式提交两遍文字（先 SEE_THROUGH 无描边保证隔墙可见，再 NORMAL 带黑色描边），随距离像名牌
 * 一样缩放。完全在客户端根据同步下来的物品实体计算与绘制，服务端零负担；由
 * {@code dropCountVisualizer} 规则与 Ctrl+O 总开关控制。</p>
 */
public final class DropCountVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "dropCountVisualizer";

    /** Fullbright light coords; the label is information, not scenery. */
    private static final int FULL_BRIGHT = 15728880;

    /** Vanilla name-tag scale: one glyph pixel is 0.025 blocks. */
    private static final float TEXT_SCALE = 0.015F;
    /** Font line height in glyph pixels; also the height a label occupies in text space. */
    private static final float LABEL_HEIGHT = 9.0F;
    /** Distance between two stacked labels, in glyph pixels. */
    private static final float LINE_STEP = 9.0F;

    /** Labels are collected and drawn within this radius of the camera. */
    private static final double RANGE = 16.0;
    /** How far a drop's box is grown horizontally when looking for twins to merge with. */
    private static final double MERGE_EXPAND = 0.5;
    /** How far above the top of the drop(s) the label floats. */
    private static final double LABEL_LIFT = 0.35;
    /**
     * 合并判定外扩（格）：上一帧已经合并过的物品对再放宽这么多才拆开，
     * 避免物品在合并边界附近微动时数量在 x2 / 两个 x1 之间来回翻转。
     */
    private static final double MERGE_HYSTERESIS = 0.1;

    /** 按 Tab 显示总物品数时本地重算的间隔（客户端 tick）。 */
    private static final int TOTAL_INTERVAL_TICKS = 5;

    /** 服务端下发的掉落物总数；没有服务端数据时用本地统计。 */
    private static volatile int serverTotal;
    private static volatile boolean hasServerTotal;
    /** 纯客户端本地统计（已同步的掉落物数量之和）。 */
    private static volatile int localTotal;
    private static int totalTickCounter;

    /** 服务端最近一次写进 Tab 列表页脚的文本（原样保留，我们把总数接在后面）。 */
    private static volatile Component rawTabFooter;
    /** 我们调用 setFooter 时置位，避免把叠加后的文本又当成服务端原文记下来。 */
    private static boolean applyingFooter;
    private static Component lastAppliedFooter;
    private static Component lastAppliedRawFooter;
    private static int lastAppliedTotal = -1;
    private static boolean lastAppliedActive;

    /** 上一帧被合并过的物品对（按两个实体 id 编码），用于合并判定加滞回。 */
    private static Set<Long> lastMergedPairs = new HashSet<>();
    /**
     * 上一帧每个标签的槽位（按簇内最小实体 id 记录），用于帧间滞后：
     * 两条标签距离接近时排序会来回翻转，槽位若跟着对调，文字就会上下反复跳动（渲染闪烁）。
     */
    private static final Map<Integer, Integer> LAST_SLOTS = new HashMap<>();
    private static final Map<Integer, Integer> NEXT_SLOTS = new HashMap<>();

    private DropCountVisualizer() {
    }

    /** 客户端入口调用一次：只注册本地统计；总数接在 Tab 列表页脚里显示（见 PlayerTabOverlayMixin）。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DropCountVisualizer::clientTick);
    }

    /** 服务端下发的掉落物总数（{@code ItemCountPayload}）。 */
    public static void acceptServerTotal(int total) {
        serverTotal = total;
        hasServerTotal = true;
    }

    /** 断开连接时清掉服务端总数，免得带到下一个服务器。 */
    public static void clearServerTotal() {
        hasServerTotal = false;
        serverTotal = 0;
        rawTabFooter = null;
        lastAppliedFooter = null;
        lastAppliedActive = false;
        lastAppliedTotal = -1;
    }

    /** 记下服务端写进页脚的最新文本（物品总数要接在它后面）。由 PlayerTabOverlayMixin 调用。 */
    public static void setTabFooter(Component footer) {
        if (applyingFooter) {
            return;
        }
        rawTabFooter = footer;
    }

    private static void clientTick(Minecraft client) {
        if (VisualizerState.serverHasSogRules() && hasServerTotal) {
            // 服务端装了本模组且已下发总数：直接用服务端的统计，这里不用本地统计。
            applyTabFooter(client);
            return;
        }
        if (VisualizerState.isActive(RULE) && client.level != null) {
            if (++totalTickCounter >= TOTAL_INTERVAL_TICKS) {
                totalTickCounter = 0;
                int total = 0;
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ItemEntity item && !item.getItem().isEmpty()) {
                        total += item.getItem().getCount();
                    }
                }
                localTotal = total;
            }
        } else {
            localTotal = 0;
        }
        applyTabFooter(client);
    }

    /** 把“服务端页脚 + 掉落物总数”写回玩家的 Tab 列表页脚（纯客户端也能显示本地统计）。 */
    private static void applyTabFooter(Minecraft client) {
        if (client == null || client.gui == null || client.level == null) {
            return;
        }
        net.minecraft.client.gui.components.PlayerTabOverlay tabList = client.gui.hud.getTabList();
        if (tabList == null) {
            return;
        }
        boolean active = VisualizerState.isActive(RULE);
        int total = VisualizerState.serverHasSogRules() && hasServerTotal ? serverTotal : localTotal;
        Component desired;
        if (active) {
            MutableComponent line = Component.translatable("sog_carpet.hud.drop_count.total", total);
            Component base = rawTabFooter;
            desired = base == null || base.getString().isEmpty()
                    ? line
                    : Component.empty().append(base).append("\n").append(line);
        } else {
            desired = rawTabFooter;
        }
        if (total == lastAppliedTotal && active == lastAppliedActive
                && rawTabFooter == lastAppliedRawFooter) {
            return;
        }
        applyingFooter = true;
        try {
            tabList.setFooter(desired);
        } catch (Throwable ignored) {
            // 页脚属于锦上添花，任何意外都不该影响渲染。
        } finally {
            applyingFooter = false;
        }
        lastAppliedFooter = desired;
        lastAppliedRawFooter = rawTabFooter;
        lastAppliedTotal = total;
        lastAppliedActive = active;
    }

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        int baseColor = VisualizerConfig.color(RULE, "name", 0xFFFFFFFF);
        int countColor = VisualizerConfig.color(RULE, "count", 0xFF00FFFF);
        int outlineColor = VisualizerConfig.color(RULE, "outline", 0xFF000000);
        // 与“物品留存时长”联动时，把剩余时长接在“名称 x数量”后面。
        boolean withRetention = ItemRetentionVisualizer.linkedToDropCount();
        int retentionColor = VisualizerConfig.color(
                ItemRetentionVisualizer.RULE, "time", 0xFFFFD24A);

        Vec3 cam = camera.pos;
        double rangeSq = RANGE * RANGE;

        List<Drop> drops = new ArrayList<>();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        AABB area = new AABB(cam.x - RANGE, cam.y - RANGE, cam.z - RANGE, cam.x + RANGE, cam.y + RANGE, cam.z + RANGE);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, e -> !e.getItem().isEmpty())) {
            Vec3 pos = entity.getPosition(partialTick);
            if (pos.distanceToSqr(cam) > rangeSq) {
                continue;
            }
            if (!camera.cullFrustum.isVisible(entity.getBoundingBox())) {
                continue;
            }
            double top = ItemLabelGeometry.modelTopY(entity, pos, partialTick)
                    + ItemLabelGeometry.topFaceClearance(entity, camera);
            drops.add(new Drop(entity.getId(), entity.getItem(), pos, entity.getBoundingBox(),
                    top, pos.distanceToSqr(cam),
                    ItemRetentionVisualizer.remainingTicks(entity)));
        }
        if (drops.isEmpty()) {
            return;
        }

        List<Label> labels = new ArrayList<>();
        for (List<Drop> bucket : groupByItem(drops)) {
            for (List<Drop> cluster : groupByBox(bucket)) {
                labels.add(Label.of(cluster, mc.font, countColor, retentionColor, withRetention));
            }
        }

        // Nearest first: the closest drop keeps the anchor position and the rest stack above it.
        // Ties fall back to the cluster key so the order is reproducible between frames.
        labels.sort(Comparator.comparingDouble(Label::distanceToCameraSq)
                .thenComparingInt(Label::key));

        int[] slot = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            Integer last = LAST_SLOTS.get(label.key());
            int candidate = last != null && last >= 0 ? last : 0;
            boolean moved = true;
            while (moved) {
                moved = false;
                for (int j = 0; j < i; j++) {
                    if (slot[j] + 1 > candidate && overlaps(label, candidate, labels.get(j), slot[j])) {
                        candidate = slot[j] + 1;
                        moved = true;
                    }
                }
            }
            slot[i] = candidate;
        }
        // Relax one step: when the space below frees up, a label walks back down one slot per
        // frame instead of floating high forever after the stack above it disappears.
        for (int i = 0; i < labels.size(); i++) {
            if (slot[i] <= 0) {
                continue;
            }
            boolean blocked = false;
            for (int j = 0; j < labels.size(); j++) {
                if (j == i || slot[j] != slot[i] - 1) {
                    continue;
                }
                if (overlaps(labels.get(i), slot[i] - 1, labels.get(j), slot[j])) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) {
                slot[i]--;
            }
        }
        NEXT_SLOTS.clear();
        for (int i = 0; i < labels.size(); i++) {
            NEXT_SLOTS.put(labels.get(i).key(), slot[i]);
        }
        LAST_SLOTS.clear();
        LAST_SLOTS.putAll(NEXT_SLOTS);

        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            poseStack.pushPose();
            poseStack.translate(label.anchor().x - cam.x, label.anchor().y - cam.y, label.anchor().z - cam.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

            FormattedCharSequence ordered = label.text().getVisualOrderText();
            float x = -label.width() / 2.0F;
            // Text-space +y is down, so a higher slot goes up.
            float y = -slot[i] * LINE_STEP;

            // See-through pass: no outline here, because an outline makes the text renderer drop
            // the see-through flag, and this pass is the only reason a label survives behind a wall.
            collector.submitText(poseStack, x, y, ordered, false, Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT, baseColor, 0, 0);
            // Opaque pass: background 0 (no box), black outline instead.
            collector.submitText(poseStack, x, y, ordered, false, Font.DisplayMode.NORMAL,
                FULL_BRIGHT, baseColor, 0, outlineColor);

            poseStack.popPose();
        }
    }

    /** Buckets drops by item id; only drops of the same item can ever share a label. */
    private static Collection<List<Drop>> groupByItem(List<Drop> drops) {
        Map<Integer, List<Drop>> byItem = new HashMap<>();
        for (Drop drop : drops) {
            byItem.computeIfAbsent(Item.getId(drop.stack().getItem()), id -> new ArrayList<>()).add(drop);
        }
        return byItem.values();
    }

    /**
     * Merges drops whose boxes touch once grown by {@value #MERGE_EXPAND} horizontally. Two stacks
     * only stay separate entities when their combined count exceeds one stack, so those are exactly
     * the pairs that should read as one amount.
     *
     * <p>Pairs merged last frame stay merged until they separate by {@value #MERGE_HYSTERESIS} more,
     * so a drop drifting right on the merge boundary cannot make the count flip between one x2 label
     * and two x1 labels every tick.</p>
     */
    private static Collection<List<Drop>> groupByBox(List<Drop> drops) {
        int size = drops.size();
        int[] parent = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }

        Set<Long> mergedNow = new HashSet<>();
        for (int i = 0; i < size; i++) {
            AABB grown = drops.get(i).box().inflate(MERGE_EXPAND, 0.0, MERGE_EXPAND);
            AABB held = drops.get(i).box().inflate(MERGE_EXPAND + MERGE_HYSTERESIS, 0.0,
                    MERGE_EXPAND + MERGE_HYSTERESIS);
            for (int j = i + 1; j < size; j++) {
                long pairKey = pairKey(drops.get(i).id(), drops.get(j).id());
                boolean merge = grown.intersects(drops.get(j).box())
                        || (lastMergedPairs.contains(pairKey) && held.intersects(drops.get(j).box()));
                if (merge) {
                    union(parent, i, j);
                    mergedNow.add(pairKey);
                }
            }
        }
        lastMergedPairs = mergedNow;

        Map<Integer, List<Drop>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            clusters.computeIfAbsent(find(parent, i), root -> new ArrayList<>()).add(drops.get(i));
        }
        return clusters.values();
    }

    /** Packs two entity ids into one stable pair key (unordered). */
    private static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootB] = rootA;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /**
     * Would these two labels collide on screen? Both the label size and the gap are in blocks, and
     * both shrink by the same factor with distance, so the answer is distance independent and can be
     * measured straight in world space.
     */
    private static boolean overlaps(Label a, int slotA, Label b, int slotB) {
        // A bigger slot sits higher on screen, which is the same as being higher in the world here.
        double aY = a.anchor().y + slotA * TEXT_SCALE * LINE_STEP;
        double bY = b.anchor().y + slotB * TEXT_SCALE * LINE_STEP;
        if (Math.abs(aY - bY) >= TEXT_SCALE * LABEL_HEIGHT) {
            return false;
        }

        double dx = a.anchor().x - b.anchor().x;
        double dz = a.anchor().z - b.anchor().z;
        // Horizontal distance is used whole for the sideways gap: which way the camera faces decides
        // how much of it lands on the screen's x axis, and the whole is the worst case.
        return Math.sqrt(dx * dx + dz * dz) < a.halfWidth() + b.halfWidth();
    }

    /**
     * @param id      entity id, gives the cluster (and its label slot) a stable identity across frames
     * @param box     the real collision box, for the merge test
     * @param topY    top of the drop at the interpolated position -- the box itself only moves once per
     *                tick, and anchoring to it makes the label stutter next to a smoothly drawn item
     */
    private record Drop(int id, ItemStack stack, Vec3 position, AABB box, double topY,
            double distanceToCameraSq, int remaining) {
    }

    private record Label(int key, Vec3 anchor, double distanceToCameraSq, Component text, float width,
            float halfWidth) {
        static Label of(List<Drop> cluster, Font font, int countColor, int retentionColor, boolean withRetention) {
            int count = 0;
            int minRemaining = Integer.MAX_VALUE;
            int key = Integer.MAX_VALUE;
            double x = 0.0;
            double z = 0.0;
            double top = Double.NEGATIVE_INFINITY;
            double nearest = Double.MAX_VALUE;

            for (Drop drop : cluster) {
                count += drop.stack().getCount();
                minRemaining = Math.min(minRemaining, drop.remaining());
                key = Math.min(key, drop.id());
                x += drop.position().x;
                z += drop.position().z;
                top = Math.max(top, drop.topY());
                nearest = Math.min(nearest, drop.distanceToCameraSq());
            }

            int size = cluster.size();
            // Between the drops horizontally, clear of the tallest one vertically.
            Vec3 anchor = new Vec3(x / size, top + LABEL_LIFT, z / size);

            MutableComponent text = Component.empty()
                .append(cluster.get(0).stack().getHoverName())
                .append(Component.literal(" x" + count).withColor(countColor));
            if (withRetention) {
                // 用整簇里最快消失的那个的剩余时长，避免逐个叠加把文字撑爆。
                text.append(Component.literal(" "))
                    .append(ItemRetentionVisualizer.retentionText(minRemaining, retentionColor));
            }

            float width = font.width(text);
            return new Label(key, anchor, nearest, text, width, width * TEXT_SCALE / 2.0F);
        }
    }
}
