package cn.blockforge.generated.sogcarpet.client.gizmo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderContext;
import net.minecraft.client.Minecraft;
import cn.blockforge.generated.sogcarpet.client.RenderSafety;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.21.10 的 Gizmo 兼容实现。
 *
 * <p>调用方仍然写 {@code try (Gizmos.TemporaryCollection c = Gizmos.begin(context))}，
 * 中间的 {@code Gizmos.line/cuboid/...} 先排队；close 时一次性用
 * {@code RenderType.lines()} 与 {@code RenderType.debugFilledBox()} 画出来。
 * 1.21.11+ 的 {@code collectPerFrameGizmos()} 在这里被 {@link #begin(WorldRenderContext)} 取代。</p>
 */
public final class Gizmos {
    private static final ThreadLocal<Collector> CURRENT = new ThreadLocal<>();
    /**
     * 在 {@link #begin} 之外收集到的 gizmo（1.21.10 的 {@code SweepRangeVisualizer} 是在
     * extraction 阶段提交的，那时还没有活动的收集器）。先放进这里，下一次 {@code begin()} 时
     * 一并画出来——否则这些可视化在 1.21.10 上永远不渲染。
     */
    private static final java.util.List<Gizmo> PENDING = new java.util.ArrayList<>();
    /** 待画队列的上限，防止某个回调在规则关闭时无限堆积。 */
    private static final int MAX_PENDING = 8192;

    private Gizmos() {
    }

    public static TemporaryCollection begin(WorldRenderContext context) {
        Collector previous = CURRENT.get();
        Collector collector = new Collector(context);
        CURRENT.set(collector);
        // 把上一阶段（extraction）攒下的 gizmo 画进本帧。
        synchronized (PENDING) {
            if (!PENDING.isEmpty()) {
                for (Gizmo pending : PENDING) {
                    try {
                        pending.emit(collector, 1.0F);
                    } catch (RuntimeException ignored) {
                        // 单个 gizmo 出错不影响同一帧其他可视化。
                    }
                }
                PENDING.clear();
            }
        }
        return new TemporaryCollection(collector, previous);
    }

    public static GizmoProperties addGizmo(Gizmo gizmo) {
        Collector collector = CURRENT.get();
        Entry entry = new Entry();
        if (gizmo != null) {
            if (collector != null) {
                gizmo.emit(collector, 1.0F);
            } else {
                synchronized (PENDING) {
                    if (PENDING.size() < MAX_PENDING) {
                        PENDING.add(gizmo);
                    }
                }
            }
        }
        return new GizmoProperties(entry);
    }

    public static GizmoProperties cuboid(AABB box, GizmoStyle style) {
        return cuboid(box, style, false);
    }

    public static GizmoProperties cuboid(AABB box, GizmoStyle style, boolean alwaysOnTop) {
        Entry entry = new Entry();
        entry.alwaysOnTop = alwaysOnTop;
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addBox(box, style);
        }
        return new GizmoProperties(entry);
    }

    public static GizmoProperties cuboid(BlockPos pos, GizmoStyle style) {
        return cuboid(new AABB(pos), style);
    }

    public static GizmoProperties cuboid(BlockPos pos, float inflate, GizmoStyle style) {
        return cuboid(new AABB(pos).inflate(inflate), style);
    }

    public static GizmoProperties circle(Vec3 center, float radius, GizmoStyle style) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addCircle(center, radius, style);
        }
        return new GizmoProperties(new Entry());
    }

    public static GizmoProperties line(Vec3 from, Vec3 to, int color) {
        return line(from, to, color, 1.0F);
    }

    public static GizmoProperties line(Vec3 from, Vec3 to, int color, float width) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addLine(from, to, color, width);
        }
        return new GizmoProperties(new Entry());
    }

    public static GizmoProperties arrow(Vec3 from, Vec3 to, int color) {
        return arrow(from, to, color, 1.0F);
    }

    public static GizmoProperties arrow(Vec3 from, Vec3 to, int color, float width) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addArrow(from, to, color, width);
        }
        return new GizmoProperties(new Entry());
    }

    public static GizmoProperties rect(Vec3 a, Vec3 b, Direction face, GizmoStyle style) {
        // 兼容层里不常用：以 a、b 为一条边，沿面内垂直方向补成正方形。
        Vec3 edge = b.subtract(a);
        Vec3 up = switch (face.getAxis()) {
            case X -> new Vec3(0.0D, edge.length(), 0.0D);
            case Y -> new Vec3(edge.length(), 0.0D, 0.0D);
            case Z -> new Vec3(0.0D, edge.length(), 0.0D);
        };
        return rect(a, b, b.add(up), a.add(up), style);
    }

    public static GizmoProperties rect(Vec3 a, Vec3 b, Vec3 c, Vec3 d, GizmoStyle style) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addQuad(a, b, c, d, style.hasFill() ? style.multipliedFill(1.0F) : 0);
            if (style.hasStroke()) {
                collector.addLine(a, b, style.multipliedStroke(1.0F), style.strokeWidth());
                collector.addLine(b, c, style.multipliedStroke(1.0F), style.strokeWidth());
                collector.addLine(c, d, style.multipliedStroke(1.0F), style.strokeWidth());
                collector.addLine(d, a, style.multipliedStroke(1.0F), style.strokeWidth());
            }
        }
        return new GizmoProperties(new Entry());
    }

    public static GizmoProperties point(Vec3 pos, int color, float size) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addPoint(pos, color, size);
        }
        return new GizmoProperties(new Entry());
    }

    public static GizmoProperties billboardTextOverBlock(String text, BlockPos pos, int yOffset, int color, float scale) {
        return billboardText(text, Vec3.atCenterOf(pos).add(0.0D, yOffset, 0.0D), TextGizmo.Style.forColor(color).withScale(scale));
    }

    public static GizmoProperties billboardTextOverMob(net.minecraft.world.entity.Entity entity, int yOffset, String text,
            int color, float scale) {
        return billboardText(text, entity.position().add(0.0D, entity.getBbHeight() + yOffset, 0.0D),
                TextGizmo.Style.forColor(color).withScale(scale));
    }

    public static GizmoProperties billboardText(String text, Vec3 pos, TextGizmo.Style style) {
        Collector collector = CURRENT.get();
        if (collector != null) {
            collector.addText(pos, text, style);
        }
        return new GizmoProperties(new Entry());
    }

    static final class Entry {
        boolean alwaysOnTop;
    }

    public static final class TemporaryCollection implements AutoCloseable {
        private final Collector collector;
        private final Collector previous;

        private TemporaryCollection(Collector collector, Collector previous) {
            this.collector = collector;
            this.previous = previous;
        }

        @Override
        public void close() {
            CURRENT.set(this.previous);
            this.collector.render();
        }
    }

    private static final class Collector implements GizmoPrimitives {
        private final WorldRenderContext context;
        private final List<Line> lines = new ArrayList<>();
        private final List<Quad> quads = new ArrayList<>();
        private final List<Fan> fans = new ArrayList<>();
        private final List<Text> texts = new ArrayList<>();
        private final List<Box> boxes = new ArrayList<>();

        private Collector(WorldRenderContext context) {
            this.context = context;
        }

        @Override
        public void addPoint(Vec3 pos, int color, float size) {
            if (!RenderSafety.sane(pos) || !Float.isFinite(size)) {
                return;
            }
            double half = Math.max(0.025D, Math.min(4.0D, size) * 0.02D);
            this.lines.add(new Line(pos.add(-half, 0, 0), pos.add(half, 0, 0), color, 2.0F));
            this.lines.add(new Line(pos.add(0, -half, 0), pos.add(0, half, 0), color, 2.0F));
            this.lines.add(new Line(pos.add(0, 0, -half), pos.add(0, 0, half), color, 2.0F));
        }

        @Override
        public void addLine(Vec3 from, Vec3 to, int color, float width) {
            // 坏坐标（NaN / 无穷 / 超出世界边界）会产生覆盖全屏的三角形，直接丢掉。
            if (!RenderSafety.sane(from) || !RenderSafety.sane(to)) {
                return;
            }
            this.lines.add(new Line(from, to, color, width));
        }

        @Override
        public void addTriangleFan(Vec3[] points, int color) {
            if (points == null || points.length < 3) {
                return;
            }
            for (Vec3 point : points) {
                if (!RenderSafety.sane(point)) {
                    return;
                }
            }
            this.fans.add(new Fan(points.clone(), color));
        }

        @Override
        public void addQuad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
            if (((color >>> 24) & 0xFF) == 0) {
                return;
            }
            if (!RenderSafety.sane(a) || !RenderSafety.sane(b)
                    || !RenderSafety.sane(c) || !RenderSafety.sane(d)) {
                return;
            }
            this.quads.add(new Quad(a, b, c, d, color));
        }

        @Override
        public void addText(Vec3 pos, String text, TextGizmo.Style style) {
            if (text == null || text.isEmpty() || !RenderSafety.sane(pos)) {
                return;
            }
            this.texts.add(new Text(pos, text, style));
        }

        private void addBox(AABB box, GizmoStyle style) {
            this.boxes.add(new Box(box, style));
        }

        private void addCircle(Vec3 center, float radius, GizmoStyle style) {
            int segments = 48;
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2.0D * i / segments;
                Vec3 point = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                if (previous != null) {
                    this.lines.add(new Line(previous, point, style.multipliedStroke(1.0F), style.strokeWidth()));
                }
                previous = point;
            }
        }

        private void addArrow(Vec3 from, Vec3 to, int color, float width) {
            this.lines.add(new Line(from, to, color, width));
            Vec3 direction = to.subtract(from);
            double length = direction.length();
            if (length < 1.0E-4D) {
                return;
            }
            Vec3 unit = direction.scale(1.0D / length);
            Vec3 helper = Math.abs(unit.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 side = unit.cross(helper).normalize();
            double head = Math.min(0.45D, length * 0.3D);
            Vec3 base = to.subtract(unit.scale(head));
            this.lines.add(new Line(to, base.add(side.scale(head * 0.55D)), color, width));
            this.lines.add(new Line(to, base.subtract(side.scale(head * 0.55D)), color, width));
            this.lines.add(new Line(to, base.add(side.cross(unit).normalize().scale(head * 0.55D)), color, width));
            this.lines.add(new Line(to, base.subtract(side.cross(unit).normalize().scale(head * 0.55D)), color, width));
        }

        private void render() {
            if (RenderSafety.tripped()) {
                return;
            }
            // 整段包进安全网：任何异常都不许冒到原版渲染循环里（那会让整帧坏掉 = 黑屏）。
            RenderSafety.guard("gizmo", this::renderNow);
        }

        private void renderNow() {
            if (this.lines.isEmpty() && this.quads.isEmpty() && this.fans.isEmpty() && this.texts.isEmpty()
                    && this.boxes.isEmpty()) {
                return;
            }
            PoseStack pose = this.context.matrices();
            MultiBufferSource consumers = this.context.consumers();
            if (pose == null || consumers == null) {
                return;
            }

            // 1.21.10 的 BEFORE_DEBUG_RENDER 给的 PoseStack 是“相机相对”的：原版提交实体/调试
            // 几何时都会自己减去相机位置。本兼容层的调用方沿用了 1.21.11 原生 gizmo 的写法，
            // 传进来的是绝对世界坐标，所以这里必须补一次 -cameraPos；否则所有图元都会整体偏移
            // 到镜头外（表现为完全不渲染），或者在近平面被透视除法放大成一片糊。
            Vec3 camera = cameraPosition();
            pose.pushPose();
            try {
                pose.translate(-camera.x, -camera.y, -camera.z);

                for (Box box : this.boxes) {
                    if (box.style.hasFill()) {
                        int color = box.style.multipliedFill(1.0F);
                        VertexConsumer consumer = consumers.getBuffer(RenderType.debugFilledBox());
                        ShapeRenderer.addChainedFilledBoxVertices(pose, consumer,
                                box.box.minX, box.box.minY, box.box.minZ,
                                box.box.maxX, box.box.maxY, box.box.maxZ,
                                ((color >>> 16) & 0xFF) / 255.0F,
                                ((color >>> 8) & 0xFF) / 255.0F,
                                (color & 0xFF) / 255.0F,
                                ((color >>> 24) & 0xFF) / 255.0F);
                    }
                    if (box.style.hasStroke()) {
                        int color = box.style.multipliedStroke(1.0F);
                        AABB b = box.box;
                        addBoxEdges(b, color, box.style.strokeWidth());
                    }
                }

                if (!this.quads.isEmpty()) {
                    // debugFilledBox 是 TRIANGLE_STRIP 图元：一个四边形 4 个顶点写进同一条带里，
                    // 第二块起会被接成“上一块的末两点 + 下一块的首两点”，画出来是横跨画面的错误
                    // 三角形（叠多了整屏都被盖住，看起来就是“黑屏”）。改用 QUADS 的 debugQuads()。
                    VertexConsumer consumer = consumers.getBuffer(RenderType.debugQuads());
                    for (Quad quad : this.quads) {
                        addQuad(consumer, pose, quad.a, quad.b, quad.c, quad.d, quad.color);
                    }
                }

                if (!this.fans.isEmpty()) {
                    // debugTriangleFan 是 TRIANGLE_FAN 图元：同一 draw call 里多个扇面会共用第一个
                    // 顶点，被合并成一个横跨画面的巨型扇面（叠多了整屏都会被糊住）。改为按 fan 拆成
                    // 三角形，塞进 QUADS 的退化四边形里（第四个顶点与第三个重合）。
                    VertexConsumer consumer = consumers.getBuffer(RenderType.debugQuads());
                    for (Fan fan : this.fans) {
                        addFanAsTriangles(consumer, pose, fan.points, fan.color);
                    }
                }

                if (!this.lines.isEmpty()) {
                    Map<Float, List<Line>> byWidth = new LinkedHashMap<>();
                    List<Float> widths = new ArrayList<>();
                    for (Line line : this.lines) {
                        float width = Math.max(0.5F, line.width);
                        if (!byWidth.containsKey(width)) {
                            widths.add(width);
                            byWidth.put(width, new ArrayList<>());
                        }
                        byWidth.get(width).add(line);
                    }
                    widths.sort(Comparator.naturalOrder());
                    for (float width : widths) {
                        RenderSystem.lineWidth(width);
                        VertexConsumer consumer = consumers.getBuffer(RenderType.lines());
                        for (Line line : byWidth.get(width)) {
                            addLine(consumer, pose, line.from, line.to, line.color);
                        }
                        if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
                            bufferSource.endBatch(RenderType.lines());
                        }
                    }
                    RenderSystem.lineWidth(1.0F);
                }
            } finally {
                // 这是原版共享的 PoseStack：无论中途是否异常都必须恢复，否则整帧世界渲染都会被平移。
                pose.popPose();
            }

            // 文本必须在未平移的 pose 上画：DebugRenderer.renderFloatingText 内部会自己减相机。
            // 另外它的 scale 是原版调试文字的 0.02 基准，而 gizmo 的 scale 是 1.0 基准，
            // 直接传 1.0 会把文字放大 50 倍（糊满屏幕），这里换算一次。
            for (Text text : this.texts) {
                TextGizmo.Style style = text.style == null ? TextGizmo.Style.whiteAndCentered() : text.style;
                DebugRenderer.renderFloatingText(pose, consumers, text.text,
                        text.pos.x, text.pos.y, text.pos.z, style.color(),
                        style.scale() * 0.02F,
                        !style.leftAligned(), 0.0F, true);
            }
            // 我们写进关卡 MultiBufferSource 的顶点必须在本阶段就冲掉：
            // 原版调试渲染之后只 endLastBatch()，剩下的会拖到后面（甚至 GUI 阶段）才画，
            // 那时投影是正交的，世界坐标的顶点会糊满整屏——正是“加入世界后黑屏”的成因。
            RenderSafety.flush(consumers);
        }

        private static Vec3 cameraPosition() {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.gameRenderer == null
                    || client.gameRenderer.getMainCamera() == null
                    || !client.gameRenderer.getMainCamera().isInitialized()) {
                return Vec3.ZERO;
            }
            return client.gameRenderer.getMainCamera().position();
        }

        private void addBoxEdges(AABB b, int color, float width) {
            Vec3 p000 = new Vec3(b.minX, b.minY, b.minZ);
            Vec3 p001 = new Vec3(b.minX, b.minY, b.maxZ);
            Vec3 p010 = new Vec3(b.minX, b.maxY, b.minZ);
            Vec3 p011 = new Vec3(b.minX, b.maxY, b.maxZ);
            Vec3 p100 = new Vec3(b.maxX, b.minY, b.minZ);
            Vec3 p101 = new Vec3(b.maxX, b.minY, b.maxZ);
            Vec3 p110 = new Vec3(b.maxX, b.maxY, b.minZ);
            Vec3 p111 = new Vec3(b.maxX, b.maxY, b.maxZ);
            this.lines.add(new Line(p000, p001, color, width));
            this.lines.add(new Line(p001, p101, color, width));
            this.lines.add(new Line(p101, p100, color, width));
            this.lines.add(new Line(p100, p000, color, width));
            this.lines.add(new Line(p010, p011, color, width));
            this.lines.add(new Line(p011, p111, color, width));
            this.lines.add(new Line(p111, p110, color, width));
            this.lines.add(new Line(p110, p010, color, width));
            this.lines.add(new Line(p000, p010, color, width));
            this.lines.add(new Line(p001, p011, color, width));
            this.lines.add(new Line(p100, p110, color, width));
            this.lines.add(new Line(p101, p111, color, width));
        }

        private static void addColoredVertex(VertexConsumer consumer, PoseStack pose, Vec3 point, int color) {
            consumer.addVertex(pose.last(), (float) point.x, (float) point.y, (float) point.z)
                    .setColor(((color >>> 16) & 0xFF), ((color >>> 8) & 0xFF), (color & 0xFF), ((color >>> 24) & 0xFF));
        }

        private static void addQuad(VertexConsumer consumer, PoseStack pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
            addColoredVertex(consumer, pose, a, color);
            addColoredVertex(consumer, pose, b, color);
            addColoredVertex(consumer, pose, c, color);
            addColoredVertex(consumer, pose, d, color);
        }

        /** 三角扇的替代实现：以首点为扇心摊成三角形，塞进退化四边形（QUADS 图元）。 */
        private static void addFanAsTriangles(VertexConsumer consumer, PoseStack pose, Vec3[] points,
                int color) {
            if (points.length < 3) {
                return;
            }
            for (int i = 1; i < points.length - 1; i++) {
                addColoredVertex(consumer, pose, points[0], color);
                addColoredVertex(consumer, pose, points[i], color);
                addColoredVertex(consumer, pose, points[i + 1], color);
                addColoredVertex(consumer, pose, points[i + 1], color);
            }
        }

        private static void addLine(VertexConsumer consumer, PoseStack pose, Vec3 from, Vec3 to, int color) {
            Vec3 normal = to.subtract(from);
            if (normal.lengthSqr() > 1.0E-8D) {
                normal = normal.normalize();
            } else {
                normal = new Vec3(0.0D, 1.0D, 0.0D);
            }
            consumer.addVertex(pose.last(), (float) from.x, (float) from.y, (float) from.z)
                    .setColor(((color >>> 16) & 0xFF), ((color >>> 8) & 0xFF), (color & 0xFF), ((color >>> 24) & 0xFF))
                    .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
            consumer.addVertex(pose.last(), (float) to.x, (float) to.y, (float) to.z)
                    .setColor(((color >>> 16) & 0xFF), ((color >>> 8) & 0xFF), (color & 0xFF), ((color >>> 24) & 0xFF))
                    .setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private record Line(Vec3 from, Vec3 to, int color, float width) {
    }

    private record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
    }

    private record Fan(Vec3[] points, int color) {
    }

    private record Text(Vec3 pos, String text, TextGizmo.Style style) {
    }

    private record Box(AABB box, GizmoStyle style) {
    }
}
