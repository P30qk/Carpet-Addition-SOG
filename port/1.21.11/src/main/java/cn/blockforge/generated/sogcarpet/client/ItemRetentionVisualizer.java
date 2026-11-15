package cn.blockforge.generated.sogcarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 物品留存时长可视化。
 *
 * <p>在掉落物上方画它距离自动消失还剩多久。原版掉落物的 {@code age} 每 tick 加一，达到
 * {@value #LIFETIME} 后由服务端清除；这个 {@code age} 客户端本地也会逐 tick 累加（只有真正的
 * 清除是服务端行为），所以<b>纯客户端</b>与装了本模组的服务端都能算出剩余时长，不需要任何
 * 服务端数据。</p>
 *
 * <p>两种制式（Ctrl+V 里的“配置”界面切换）：</p>
 * <ul>
 *   <li><b>六十制</b>（默认）：{@code x:xx}，例如 {@code 4:59}；</li>
 *   <li><b>游戏刻制</b>：{@code xgt}，例如 {@code 5900gt}。</li>
 * </ul>
 *
 * <p>默认与“掉落物数量标签”联动：剩余时长直接接在 {@code 名称 x数量} 后面（由
 * {@link DropCountVisualizer} 负责拼接），此时本类不再单独画标签；关掉联动、或数量标签没开时，
 * 本类会在每个掉落物上方单独画一条剩余时长。掉落物永不消失（{@code age == -32768}）时显示
 * {@code ∞}。</p>
 *
 * <p>为了在刷石机、分类机这种掉一堆物品的场合也不拖垮客户端，只扫摄像机周围
 * {@value #RANGE} 格以内、且通过视锥裁剪的掉落物，同屏最多画 {@value #MAX_LABELS} 条
 * （优先保留离得最近的），重叠的标签自动向上错开。</p>
 */
public final class ItemRetentionVisualizer {
    /** 本规则名。 */
    public static final String RULE = "itemRetentionVisualizer";

    /** 原版掉落物寿命（tick）；达到该 age 后服务端清除。 */
    private static final int LIFETIME = 6000;
    /** 掉落物“永不消失”的 age 标记（{@code ItemEntity#setUnlimitedLifetime}）。 */
    private static final int UNLIMITED_AGE = -32768;

    /** 联动的数量标签规则名。 */
    private static final String DROP_COUNT_RULE = "dropCountVisualizer";

    /** 名称牌式全亮光照。 */
    private static final int FULL_BRIGHT = 15728880;
    /** 与掉落物数量标签一致的字符像素缩放。 */
    private static final float TEXT_SCALE = 0.015F;
    private static final float LABEL_HEIGHT = 9.0F;
    private static final float LINE_STEP = 9.0F;

    /** 只给这个距离（格）以内的掉落物画剩余时长。 */
    private static final double RANGE = 32.0;
    /** 标签离掉落物顶部的距离。 */
    private static final double LABEL_LIFT = 0.35;
    /** 数量标签也开着但没联动时，往上再多抬一点，避免两条文字压在一起。 */
    private static final double SEPARATE_LIFT = 0.25;
    /** 同屏最多画多少条，避免掉落物爆炸时刷爆文字渲染。 */
    private static final int MAX_LABELS = 256;

    /**
     * 上一帧每个标签的槽位（按掉落物 id 记录），用于帧间滞后：
     * 两条标签距离接近时排序会来回翻转，槽位若跟着对调，文字就会上下反复跳动（渲染闪烁）。
     * 沿用上一帧槽位、几何上真放不下才往上挪，画面就稳定了。
     */
    private static final Map<Integer, Integer> LAST_SLOTS = new HashMap<>();
    private static final Map<Integer, Integer> NEXT_SLOTS = new HashMap<>();

    private ItemRetentionVisualizer() {
    }

    // ------------------------------------------------------------------ 查询/文本

    /** 该掉落物距离自动消失还剩多少 tick；永不消失返回 {@link Integer#MAX_VALUE}。 */
    public static int remainingTicks(ItemEntity entity) {
        int age = entity.getAge();
        if (age == UNLIMITED_AGE) {
            return Integer.MAX_VALUE;
        }
        return LIFETIME - age;
    }

    /** 按配置的制式把剩余 tick 格式化成 {@code x:xx} 或 {@code xgt}。 */
    public static String formatRemaining(int remaining) {
        if (remaining == Integer.MAX_VALUE) {
            return "∞";
        }
        if (remaining < 0) {
            remaining = 0;
        }
        if (VisualizerConfig.option(RULE, "gtMode", false)) {
            return remaining + "gt";
        }
        // 六十制：向上取整，保证最后一秒消失前还看得到 0:01 而不是 0:00。
        int seconds = (remaining + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /** 剩余时长文字（带颜色），供本类与掉落物数量标签共用。 */
    public static MutableComponent retentionText(int remaining, int color) {
        return Component.literal(formatRemaining(remaining)).withColor(color);
    }

    /** 本规则自身是否开启。 */
    public static boolean isEnabled() {
        return VisualizerState.isActive(RULE);
    }

    /**
     * 是否与掉落物数量标签联动：本规则、数量标签都开着，且联动选项打开。
     *
     * <p>联动时剩余时长由 {@link DropCountVisualizer} 拼到 {@code 名称 x数量} 后面，本类
     * 自己不再重复画。</p>
     */
    public static boolean linkedToDropCount() {
        return VisualizerState.isActive(RULE)
                && VisualizerState.isActive(DROP_COUNT_RULE)
                && VisualizerConfig.option(RULE, "linkDropCount", true);
    }

    // ------------------------------------------------------------------ 渲染

    /** 独立渲染剩余时长（联动时直接返回，交给数量标签）。 */
    public static void render(PoseStack poseStack, SubmitNodeCollector collector, CameraSnapshot camera) {
        if (!VisualizerState.isActive(RULE) || linkedToDropCount()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        int timeColor = VisualizerConfig.color(RULE, "time", 0xFFFFD24A);
        int outlineColor = VisualizerConfig.color(RULE, "outline", 0xFF000000);

        Vec3 cam = camera.pos;
        double rangeSq = RANGE * RANGE;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // 数量标签也开着（只是没联动）时抬高一档，两条文字不会压在一起。
        double lift = LABEL_LIFT
                + (VisualizerState.isActive(DROP_COUNT_RULE) ? SEPARATE_LIFT : 0.0);

        List<Label> labels = new ArrayList<>();
        AABB area = new AABB(cam.x - RANGE, cam.y - RANGE, cam.z - RANGE,
                cam.x + RANGE, cam.y + RANGE, cam.z + RANGE);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area, e -> !e.getItem().isEmpty())) {
            Vec3 pos = entity.getPosition(partialTick);
            double distanceSq = pos.distanceToSqr(cam);
            if (distanceSq > rangeSq) {
                continue;
            }
            if (!camera.isVisible(entity.getBoundingBox())) {
                continue;
            }
            int remaining = remainingTicks(entity);
            MutableComponent text = retentionText(remaining, timeColor);
            float width = mc.font.width(text);
            double top = ItemLabelGeometry.modelTopY(entity, pos, partialTick)
                    + ItemLabelGeometry.topFaceClearance(entity, camera);
            Vec3 anchor = new Vec3(pos.x, top + lift, pos.z);
            labels.add(new Label(entity.getId(), anchor, distanceSq, text, width,
                    width * TEXT_SCALE / 2.0F));
        }
        if (labels.isEmpty()) {
            return;
        }

        // 最近的先画，保留最近的 N 个，避免同屏文字过多；距离相同时按实体 id 固定次序，
        // 保证排序结果在帧与帧之间可复现。
        labels.sort(Comparator.comparingDouble(Label::distanceToCameraSq)
                .thenComparingInt(Label::id));
        if (labels.size() > MAX_LABELS) {
            labels = new ArrayList<>(labels.subList(0, MAX_LABELS));
        }

        int[] slot = computeSlots(labels);
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            poseStack.pushPose();
            poseStack.translate(label.anchor().x - cam.x, label.anchor().y - cam.y, label.anchor().z - cam.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

            FormattedCharSequence ordered = label.text().getVisualOrderText();
            float x = -label.width() / 2.0F;
            float y = -slot[i] * LINE_STEP;

            collector.submitText(poseStack, x, y, ordered, false, Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT, timeColor, 0, 0);
            collector.submitText(poseStack, x, y, ordered, false, Font.DisplayMode.NORMAL,
                FULL_BRIGHT, timeColor, 0, outlineColor);

            poseStack.popPose();
        }
    }

    /**
     * 与掉落物数量标签相同的重叠错开算法：最近的一条占原位，其余往上叠。
     *
     * <p>槽位优先沿用上一帧的位置（按掉落物 id 记忆），几何上实在放不下才往上挪。
     * 相邻两条标签的距离只差一点时，排序在两帧之间翻来覆去不至于让槽位跟着对调，
     * 文字也就不会上下反复跳动。</p>
     */
    private static int[] computeSlots(List<Label> labels) {
        int[] slot = new int[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            Label label = labels.get(i);
            Integer last = LAST_SLOTS.get(label.id());
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
        // 松弛一轮：下方空出来时允许逐帧下降一格，避免上层标签消失后它一直悬在高处；
        // 每帧最多降一格，下降也是平滑的一次性动画，不会来回抖。
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
            NEXT_SLOTS.put(labels.get(i).id(), slot[i]);
        }
        LAST_SLOTS.clear();
        LAST_SLOTS.putAll(NEXT_SLOTS);
        return slot;
    }

    private static boolean overlaps(Label a, int slotA, Label b, int slotB) {
        double aY = a.anchor().y + slotA * TEXT_SCALE * LINE_STEP;
        double bY = b.anchor().y + slotB * TEXT_SCALE * LINE_STEP;
        if (Math.abs(aY - bY) >= TEXT_SCALE * LABEL_HEIGHT) {
            return false;
        }
        double dx = a.anchor().x - b.anchor().x;
        double dz = a.anchor().z - b.anchor().z;
        return Math.sqrt(dx * dx + dz * dz) < a.halfWidth() + b.halfWidth();
    }

    private record Label(int id, Vec3 anchor, double distanceToCameraSq, Component text,
            float width, float halfWidth) {
    }
}
