package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * “Tweakeroo 灵活放置四角三角”的客户端实现（规则 {@code flexiblePlacementCorners}）。
 *
 * <p>Tweakeroo 的“如何放置”界面（灵活方块放置覆盖层）把被瞄准的面按“中心方块 + 上下左右四条
 * 梯形 + 四个角”分成九块：中心与四条梯形各对应一次面内偏移，而四个角原本只按主导轴并到某条
 * 梯形里。本模组给这四个角各加一个<b>三角形放置区</b>：按住“偏移位置”快捷键瞄准某个角时，
 * 方块同时沿该面的两个方向各偏移一格——在墙面上就是斜上方／斜下方。</p>
 *
 * <p>实现分两半：</p>
 * <ul>
 *   <li><b>放置</b>：{@code FlexiblePlacementOffsetMixin} 包装 Tweakeroo
 *       {@code PlacementTweaks.tryPlaceBlock} 里偏移分支的那次 {@link BlockPos#relative}，
 *       把单轴偏移扩成双轴偏移（见 {@link #adjustOffset}）。</li>
 *   <li><b>显示</b>：{@code FlexiblePlacementOverlayMixin} 拦掉 Tweakeroo 对
 *       malilib 覆盖层的调用，改由本类用 {@link Gizmos} 重画同一套几何——这样四个角被瞄准时
 *       高亮的是三角形本身，而不是原来那条梯形。</li>
 * </ul>
 *
 * <p>所有对 Tweakeroo / malilib 的访问都走反射，解析失败（没装 Tweakeroo、或版本改了字段名）
 * 就整体静默停用：既不重画覆盖层，也不改放置，原版行为不受影响。</p>
 *
 * <p>几何一律用 {@code (h, v)} 面内坐标描述：{@code h}、{@code v} 就是 malilib
 * {@code PositionUtils.getHitPartPositions} 里的那一对坐标（0..1，中心 0.5），
 * 而世界方向由 {@link #frame} 给出——它和 malilib 覆盖层绘制时的旋转严格一致，
 * 因此高亮位置与 Tweakeroo 认定的“瞄准区域”永远对得上。</p>
 */
public final class FlexiblePlacementCorners {
    /** 本规则在服务端同步串里的键名。 */
    private static final String RULE = "flexiblePlacementCorners";

    /** 被瞄准的角：从 0.25 到 0.5 的那一格。 */
    private static final double CORNER_INNER = 0.25D;
    private static final double CORNER_OUTER = 0.5D;
    /** 面内坐标超出这个距离就算离开了中心方块。 */
    private static final double CENTER_HALF = 0.25D;

    /** 覆盖层相对方块中心沿外法线外推的距离（0.5 是面，多出的 0.01 防止和面共面闪烁）。 */
    private static final double FACE_OFFSET = 0.51D;

    /** 整面底色：白色、alpha 45，和 malilib 覆盖层的底色一致。 */
    private static final int FACE_COLOR = 0x2DFFFFFF;
    /** 没被瞄准的三个角：同色但很淡，让“四个三角形”常驻可见。 */
    private static final int CORNER_IDLE_ALPHA = 0x38;
    /** 线框颜色（中心方块 + 四条角对角线）。 */
    private static final int WIRE_COLOR = 0xFFFFFFFF;
    private static final float WIRE_WIDTH = 1.6F;

    /** 读不到 Tweakeroo 配置时的兜底高亮色：Tweakeroo 默认值 #C03030F0。 */
    private static final int DEFAULT_HIGHLIGHT = 0xC03030F0;

    private static final String HOTKEY_OFFSET = "FLEXIBLE_BLOCK_PLACEMENT_OFFSET";
    private static final String HOTKEY_ROTATION = "FLEXIBLE_BLOCK_PLACEMENT_ROTATION";
    private static final String HOTKEY_ADJACENT = "FLEXIBLE_BLOCK_PLACEMENT_ADJACENT";

    /** 服务端同步过来的规则开关；null = 用本地静态字段（单机 / 集成服务端）。 */
    private static volatile Boolean synced;
    /** 上一帧是否成功画过覆盖层：只有画过才敢拦掉 Tweakeroo 自己的绘制，避免覆盖层整个消失。 */
    private static volatile boolean drewLastFrame;

    // ------------------------------------------------------------------ 反射缓存
    private static boolean reflectionDone;
    private static boolean reflectionOk;
    private static Object flexiblePlacementToggle;
    private static Method getBooleanValue;
    private static Class<?> hotkeysClass;
    private static Field offsetHotkeyField;
    private static Field rotationHotkeyField;
    private static Field adjacentHotkeyField;
    private static Method getKeybind;
    private static Method isKeybindHeld;
    private static Method colorGetter;
    private static Object colorConfig;
    private static Field colorR;
    private static Field colorG;
    private static Field colorB;
    private static Field colorA;

    private FlexiblePlacementCorners() {
    }

    /** 客户端入口调用一次：注册覆盖层重画回调。 */
    public static void init() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(FlexiblePlacementCorners::render);
    }

    // ------------------------------------------------------------------ 规则开关

    /** 规则是否生效：服务端同步值优先，没有同步值时退回本地静态字段。 */
    public static boolean enabled() {
        Boolean value = synced;
        return value != null ? value : SogSettings.flexiblePlacementCorners;
    }

    /** 解析服务端发来的 {@code 规则名=0/1;} 串；不认识的名字忽略。 */
    public static void applySyncData(String data) {
        if (data == null) {
            return;
        }
        for (String part : data.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (RULE.equals(part.substring(0, eq).trim())) {
                synced = "1".equals(part.substring(eq + 1).trim());
            }
        }
    }

    /** 断开连接时清掉同步值，免得把上一个服务器的规则状态带过来。 */
    public static void clearSync() {
        synced = null;
        drewLastFrame = false;
    }

    // ------------------------------------------------------------------ 放置：单轴偏移 → 双轴偏移

    /**
     * Tweakeroo 偏移分支里那次 {@code posNew.relative(...)} 的替身。
     *
     * <p>不是四角（或规则没开、没装 Tweakeroo、当前瞄的不是方块）时原样返回
     * {@code pos.relative(dir)}，Tweakeroo 的行为一字不改；只有瞄准落在四个角的
     * 三角形里时，才在该方向上再补一次另一条轴的偏移。</p>
     */
    public static BlockPos adjustOffset(BlockPos pos, Direction dir) {
        BlockPos plain = pos.relative(dir);
        Direction second = secondAxis(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        return second == null ? plain : plain.relative(second);
    }

    /**
     * Tweakeroo 偏移分支里那次 {@code hitVec = hitVec.add(...)} 的替身。
     *
     * <p>偏移模式下 Tweakeroo 会把命中点平移同一格，模拟“在新方块上的同一相对位置”。
     * 双轴偏移必须把另一条轴也一起平移：服务端 {@code handleUseItemOn} 要求命中点到方块中心
     * 每个轴的距离都小于 1（{@code ServerGamePacketListenerImpl}），位置与命中点错开一格就会
     * 被判定为 “Location too far away from hit block” 而整包拒收。{@code offset} 里已经含的
     * 那条轴不再重复加。</p>
     */
    public static Vec3 adjustHitVec(Vec3 hitVec, Vec3 offset) {
        Vec3 plain = hitVec.add(offset);
        Direction second = secondAxis((int) Math.round(offset.x), (int) Math.round(offset.y),
                (int) Math.round(offset.z));
        return second == null ? plain
                : plain.add(new Vec3(second.getStepX(), second.getStepY(), second.getStepZ()));
    }

    /**
     * 需要补的那条轴：{@code (sx, sy, sz)} 是 Tweakeroo 自己算出的那条偏移轴，它必须正好是
     * 面内两条轴之一（角上必然如此）才继续，否则返回 {@code null} 不做任何猜测——这样
     * {@link #adjustOffset} 与 {@link #adjustHitVec} 永远补同一条轴，位置和命中点不会错开。
     */
    private static Direction secondAxis(int sx, int sy, int sz) {
        Direction[] axes = cornerAxes();
        if (axes == null) {
            return null;
        }
        if (isStep(axes[0], sx, sy, sz)) {
            return axes[1];
        }
        if (isStep(axes[1], sx, sy, sz)) {
            return axes[0];
        }
        return null;
    }

    private static boolean isStep(Direction axis, int sx, int sy, int sz) {
        return axis.getStepX() == sx && axis.getStepY() == sy && axis.getStepZ() == sz;
    }

    /**
     * 规则开着、偏移快捷键按住、且瞄准点落在某个角的三角形里时，返回两条偏移轴方向
     * （{@code [0]} 是面内横轴、{@code [1]} 是面内纵轴）；否则返回 {@code null}。
     */
    private static Direction[] cornerAxes() {
        if (!enabled() || !prepare() || !hotkeyHeld(offsetHotkeyField)) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (!(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || client.player == null) {
            return null;
        }
        Direction side = hit.getDirection();
        Direction facing = client.player.getDirection();
        double[] hv = hitParts(side, facing, hit.getBlockPos(), hit.getLocation());
        if (hv == null) {
            return null;
        }
        if (!(Math.abs(hv[0] - 0.5D) > CENTER_HALF) || !(Math.abs(hv[1] - 0.5D) > CENTER_HALF)) {
            return null;
        }
        Direction[] frame = frame(side, facing);
        Direction horizontal = hv[0] < 0.5D ? frame[0].getOpposite() : frame[0];
        Direction vertical = hv[1] < 0.5D ? frame[1].getOpposite() : frame[1];
        return new Direction[]{horizontal, vertical};
    }

    // ------------------------------------------------------------------ 覆盖层

    /**
     * Tweakeroo 是否正要画灵活放置覆盖层（与它 {@code RenderHandler.renderOverlays} 的
     * 判断条件一致：功能开着 + 瞄着方块 + 三个灵活放置快捷键按住了至少一个）。
     */
    public static boolean overlayVisible(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return false;
        }
        if (!(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!prepare() || !flexiblePlacementOn()) {
            return false;
        }
        return hotkeyHeld(offsetHotkeyField) || hotkeyHeld(rotationHotkeyField)
                || hotkeyHeld(adjacentHotkeyField);
    }

    /** 拦截判断：规则开着、覆盖层该显示、且上一帧我们确实画出来了，才让 Tweakeroo 别画。 */
    public static boolean shouldReplaceOverlay(Minecraft client) {
        return enabled() && drewLastFrame && overlayVisible(client);
    }

    private static void render(WorldRenderContext context) {
        drewLastFrame = false;
        if (!enabled()) {
            return;
        }
        // 画崩了就当这帧没画：下一帧覆盖层会退回 Tweakeroo 自己画，绝不把异常丢进渲染循环。
        try {
            if (drawOverlay(context)) {
                drewLastFrame = true;
            }
        } catch (RuntimeException | LinkageError ignored) {
            drewLastFrame = false;
        }
    }

    /** 真正画覆盖层；返回 true 表示这一帧画成了（可以放心拦掉 Tweakeroo 的绘制）。 */
    private static boolean drawOverlay(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!overlayVisible(client) || client.player == null
                || !(client.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        Direction side = hit.getDirection();
        Direction facing = client.player.getDirection();
        double[] hv = hitParts(side, facing, hit.getBlockPos(), hit.getLocation());
        if (hv == null) {
            return false;
        }
        Vec3 center = Vec3.atCenterOf(hit.getBlockPos());
        Direction[] frame = frame(side, facing);
        int highlight = overlayColor();
        boolean cornerMode = hotkeyHeld(offsetHotkeyField);
        double offH = Math.abs(hv[0] - 0.5D);
        double offV = Math.abs(hv[1] - 0.5D);
        int cornerSignH = hv[0] < 0.5D ? -1 : 1;
        int cornerSignV = hv[1] < 0.5D ? -1 : 1;
        try (Gizmos.TemporaryCollection collection =
                     Gizmos.begin(context)) {
            // 整面底色（和 malilib 一样：白色 alpha 45）。
            quad(center, frame, -CORNER_OUTER, -CORNER_OUTER, CORNER_OUTER, CORNER_OUTER,
                    FACE_COLOR);
            // 偏移模式：四个角常驻淡色三角形，一眼能看出这里有四个放置区。
            if (cornerMode) {
                int idle = withAlpha(highlight, CORNER_IDLE_ALPHA);
                for (int sh = -1; sh <= 1; sh += 2) {
                    for (int sv = -1; sv <= 1; sv += 2) {
                        cornerQuad(center, frame, sh, sv, idle);
                    }
                }
            }
            // 当前瞄准的区域高亮：偏移模式下四个角各自高亮三角形本身，其余照抄 malilib。
            if (cornerMode && offH > CENTER_HALF && offV > CENTER_HALF) {
                cornerQuad(center, frame, cornerSignH, cornerSignV, highlight);
            } else if (offH <= CENTER_HALF && offV <= CENTER_HALF) {
                quad(center, frame, -CENTER_HALF, -CENTER_HALF, CENTER_HALF, CENTER_HALF,
                        highlight);
            } else if (offH > offV) {
                if (hv[0] < 0.5D) {
                    poly(center, frame, highlight,
                            -0.5D, -0.5D, -0.25D, -0.25D, -0.25D, 0.25D, -0.5D, 0.5D);
                } else {
                    poly(center, frame, highlight,
                            0.5D, -0.5D, 0.5D, 0.5D, 0.25D, 0.25D, 0.25D, -0.25D);
                }
            } else if (hv[1] < 0.5D) {
                poly(center, frame, highlight,
                        -0.5D, -0.5D, 0.5D, -0.5D, 0.25D, -0.25D, -0.25D, -0.25D);
            } else {
                poly(center, frame, highlight,
                        -0.5D, 0.5D, -0.25D, 0.25D, 0.25D, 0.25D, 0.5D, 0.5D);
            }
            // 线框：中心方块四边 + 四条角对角线（角上的三角形由这两者围出来）。
            wire(center, frame, -CENTER_HALF, -CENTER_HALF, CENTER_HALF, -CENTER_HALF);
            wire(center, frame, CENTER_HALF, -CENTER_HALF, CENTER_HALF, CENTER_HALF);
            wire(center, frame, CENTER_HALF, CENTER_HALF, -CENTER_HALF, CENTER_HALF);
            wire(center, frame, -CENTER_HALF, CENTER_HALF, -CENTER_HALF, -CENTER_HALF);
            for (int sh = -1; sh <= 1; sh += 2) {
                for (int sv = -1; sv <= 1; sv += 2) {
                    wire(center, frame, sh * CORNER_OUTER, sv * CORNER_OUTER,
                            sh * CENTER_HALF, sv * CENTER_HALF);
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 几何

    /**
     * 面内坐标系到世界方向的映射：{@code [0] = h 轴}、{@code [1] = v 轴}、{@code [2] = 外法线}。
     *
     * <p>取值和 malilib {@code RenderUtils.blockTargetingOverlayTranslations} 的旋转完全等价，
     * 也和 {@code PositionUtils.getHitPartPositions} 的 h/v 定义一致：
     * 水平面（东南西北）的 h 轴是“面朝外看时的右侧”，v 轴永远是世界竖直向上；
     * 垂直面（上/下）的 h、v 轴由玩家朝向决定。</p>
     */
    private static Direction[] frame(Direction side, Direction facing) {
        Direction h;
        Direction v;
        if (side.getAxis().isVertical()) {
            h = facing.getClockWise();
            v = side == Direction.UP ? facing : facing.getOpposite();
        } else {
            h = side.getCounterClockWise();
            v = Direction.UP;
        }
        return new Direction[]{h, v, side};
    }

    /**
     * 命中点在面上的 (h, v) 坐标，逐字对应 malilib
     * {@code PositionUtils.getHitPartPositions}：0..1，中心 0.5。
     */
    private static double[] hitParts(Direction side, Direction facing, BlockPos pos, Vec3 hit) {
        double x = hit.x - pos.getX();
        double y = hit.y - pos.getY();
        double z = hit.z - pos.getZ();
        if (side == Direction.EAST) {
            return new double[]{1.0D - z, y};
        }
        if (side == Direction.WEST) {
            return new double[]{z, y};
        }
        if (side == Direction.NORTH) {
            return new double[]{1.0D - x, y};
        }
        if (side == Direction.SOUTH) {
            return new double[]{x, y};
        }
        double h;
        double v;
        if (facing == Direction.EAST) {
            h = z;
            v = x;
        } else if (facing == Direction.WEST) {
            h = 1.0D - z;
            v = 1.0D - x;
        } else if (facing == Direction.SOUTH) {
            h = 1.0D - x;
            v = z;
        } else {
            h = x;
            v = 1.0D - z;
        }
        if (side == Direction.DOWN) {
            v = 1.0D - v;
        }
        return new double[]{h, v};
    }

    private static Vec3 point(Vec3 center, Direction[] frame, double h, double v) {
        Direction hAxis = frame[0];
        Direction vAxis = frame[1];
        Direction normal = frame[2];
        return new Vec3(
                center.x + hAxis.getStepX() * h + vAxis.getStepX() * v + normal.getStepX() * FACE_OFFSET,
                center.y + hAxis.getStepY() * h + vAxis.getStepY() * v + normal.getStepY() * FACE_OFFSET,
                center.z + hAxis.getStepZ() * h + vAxis.getStepZ() * v + normal.getStepZ() * FACE_OFFSET);
    }

    private static void quad(Vec3 center, Direction[] frame, double h0, double v0, double h1,
            double v1, int color) {
        poly(center, frame, color, h0, v0, h1, v0, h1, v1, h0, v1);
    }

    /** 一个角上的三角形区（同色四角顺序按 (h,v) 逆时针，保证正面朝外能画出来）。 */
    private static void cornerQuad(Vec3 center, Direction[] frame, int signH, int signV, int color) {
        double hInner = signH > 0 ? CENTER_HALF : -CORNER_OUTER;
        double hOuter = signH > 0 ? CORNER_OUTER : -CENTER_HALF;
        double vInner = signV > 0 ? CENTER_HALF : -CORNER_OUTER;
        double vOuter = signV > 0 ? CORNER_OUTER : -CENTER_HALF;
        quad(center, frame, hInner, vInner, hOuter, vOuter, color);
    }

    private static void poly(Vec3 center, Direction[] frame, int color, double... hv) {
        if (hv.length < 8) {
            return;
        }
        Gizmos.rect(
                point(center, frame, hv[0], hv[1]),
                point(center, frame, hv[2], hv[3]),
                point(center, frame, hv[4], hv[5]),
                point(center, frame, hv[6], hv[7]),
                GizmoStyle.fill(color));
    }

    private static void wire(Vec3 center, Direction[] frame, double h0, double v0, double h1,
            double v1) {
        Gizmos.line(point(center, frame, h0, v0), point(center, frame, h1, v1), WIRE_COLOR,
                WIRE_WIDTH);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------ Tweakeroo 反射

    /** 只在第一次真正需要时解析一次；失败就永久关掉本功能。 */
    private static boolean prepare() {
        if (reflectionDone) {
            return reflectionOk;
        }
        reflectionDone = true;
        try {
            Class<?> featureToggle = Class.forName("fi.dy.masa.tweakeroo.config.FeatureToggle");
            flexiblePlacementToggle =
                    featureToggle.getField("TWEAK_FLEXIBLE_BLOCK_PLACEMENT").get(null);
            getBooleanValue = Class.forName("fi.dy.masa.malilib.config.IConfigBoolean")
                    .getMethod("getBooleanValue");
            hotkeysClass = Class.forName("fi.dy.masa.tweakeroo.config.Hotkeys");
            offsetHotkeyField = hotkeysClass.getField(HOTKEY_OFFSET);
            rotationHotkeyField = hotkeysClass.getField(HOTKEY_ROTATION);
            adjacentHotkeyField = hotkeysClass.getField(HOTKEY_ADJACENT);
            getKeybind = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey")
                    .getMethod("getKeybind");
            isKeybindHeld = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind")
                    .getMethod("isKeybindHeld");
            reflectionOk = true;
        } catch (Throwable ignored) {
            reflectionOk = false;
        }
        if (reflectionOk) {
            prepareColor();
        }
        return reflectionOk;
    }

    private static boolean flexiblePlacementOn() {
        try {
            return (Boolean) getBooleanValue.invoke(flexiblePlacementToggle);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hotkeyHeld(Field field) {
        if (field == null) {
            return false;
        }
        try {
            Object keybind = getKeybind.invoke(field.get(null));
            return (Boolean) isKeybindHeld.invoke(keybind);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 读 Tweakeroo 的“灵活放置高亮颜色”配置；读不到就用默认值。 */
    private static void prepareColor() {
        try {
            Class<?> generic = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic");
            colorConfig = generic.getField("FLEXIBLE_PLACEMENT_OVERLAY_COLOR").get(null);
            colorGetter = colorConfig.getClass().getMethod("getColor");
            colorR = Class.forName("fi.dy.masa.malilib.util.data.Color4f").getField("r");
            colorG = Class.forName("fi.dy.masa.malilib.util.data.Color4f").getField("g");
            colorB = Class.forName("fi.dy.masa.malilib.util.data.Color4f").getField("b");
            colorA = Class.forName("fi.dy.masa.malilib.util.data.Color4f").getField("a");
        } catch (Throwable ignored) {
            colorConfig = null;
        }
    }

    private static int overlayColor() {
        if (colorConfig == null || colorGetter == null) {
            return DEFAULT_HIGHLIGHT;
        }
        try {
            Object color = colorGetter.invoke(colorConfig);
            int a = channel((Float) colorA.get(color));
            int r = channel((Float) colorR.get(color));
            int g = channel((Float) colorG.get(color));
            int b = channel((Float) colorB.get(color));
            return (a << 24) | (r << 16) | (g << 8) | b;
        } catch (Throwable ignored) {
            return DEFAULT_HIGHLIGHT;
        }
    }

    private static int channel(float value) {
        int scaled = (int) (value * 255.0F);
        if (scaled < 0) {
            return 0;
        }
        return scaled > 255 ? 255 : scaled;
    }
}
