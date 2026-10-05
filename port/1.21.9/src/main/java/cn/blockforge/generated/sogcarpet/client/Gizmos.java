package cn.blockforge.generated.sogcarpet.client;

/**
 * 26.2 起原版自带调试几何 API {@code net.minecraft.gizmos.Gizmos}，1.21.7~1.21.10 没有。
 * 这里给旧版本补一份<b>同名同形</b>的替代实现：调用点只改 import（改成本包内），
 * 绘制改用 {@link net.minecraft.client.renderer.RenderType#lines()} 与
 * {@link net.minecraft.client.renderer.RenderType#debugQuads()} 直接写顶点缓冲。
 *
 * <p>与 26.2 的差异：不支持 {@code alwaysOnTop}（透墙显示），调用仅作占位。</p>
 */
public final class Gizmos {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private Gizmos() {
    }

    public static TemporaryCollection begin(SogWorldRenderContext context) {
        Frame previous = CURRENT.get();
        net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getPosition();
        CURRENT.set(new Frame(context.matrices(), context.consumers(), camera));
        return new TemporaryCollection(previous);
    }

    public static final class TemporaryCollection implements AutoCloseable {
        private final Frame previous;

        private TemporaryCollection(Frame previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(this.previous);
            }
        }
    }

    private static final class Frame {
        private final com.mojang.blaze3d.vertex.PoseStack pose;
        private final net.minecraft.client.renderer.MultiBufferSource consumers;
        private final net.minecraft.world.phys.Vec3 camera;

        private Frame(com.mojang.blaze3d.vertex.PoseStack pose,
                net.minecraft.client.renderer.MultiBufferSource consumers,
                net.minecraft.world.phys.Vec3 camera) {
            this.pose = pose;
            this.consumers = consumers;
            this.camera = camera;
        }
    }

    private static Frame frame() {
        return CURRENT.get();
    }

    public static GizmoProperties line(net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 to, int color, float width) {
        Frame frame = frame();
        if (frame == null) {
            return new GizmoProperties();
        }
        net.minecraft.world.phys.Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-8D) {
            return new GizmoProperties();
        }
        net.minecraft.world.phys.Vec3 normal = direction.normalize();
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(Math.max(1.0F, width));
        com.mojang.blaze3d.vertex.VertexConsumer consumer =
                frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        frame.pose.pushPose();
        frame.pose.translate(-frame.camera.x, -frame.camera.y, -frame.camera.z);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = frame.pose.last();
        lineVertex(consumer, pose, from, color, normal);
        lineVertex(consumer, pose, to, color, normal);
        frame.pose.popPose();
        return new GizmoProperties();
    }

    public static GizmoProperties cuboid(net.minecraft.world.phys.AABB box, GizmoStyle style) {
        Frame frame = frame();
        if (frame == null) {
            return new GizmoProperties();
        }
        frame.pose.pushPose();
        frame.pose.translate(-frame.camera.x, -frame.camera.y, -frame.camera.z);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = frame.pose.last();
        if (style.hasStroke()) {
            com.mojang.blaze3d.systems.RenderSystem.lineWidth(Math.max(1.0F, style.strokeWidth()));
            lineBox(frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.lines()), pose,
                    box, style.strokeColor());
        }
        if (style.hasFill()) {
            filledBox(frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads()),
                    pose, box, style.fillColor());
        }
        frame.pose.popPose();
        return new GizmoProperties();
    }

    public static GizmoProperties cuboid(net.minecraft.world.phys.Vec3 pos, float inflate,
            GizmoStyle style) {
        return cuboid(new net.minecraft.world.phys.AABB(pos.x, pos.y, pos.z, pos.x, pos.y, pos.z)
                .inflate(inflate), style);
    }

    public static GizmoProperties cuboid(net.minecraft.core.BlockPos pos, float inflate,
            GizmoStyle style) {
        return cuboid(new net.minecraft.world.phys.AABB(pos).inflate(inflate), style);
    }

    public static void rect(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b,
            net.minecraft.world.phys.Vec3 c, net.minecraft.world.phys.Vec3 d, GizmoStyle style) {
        Frame frame = frame();
        if (frame == null) {
            return;
        }
        frame.pose.pushPose();
        frame.pose.translate(-frame.camera.x, -frame.camera.y, -frame.camera.z);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = frame.pose.last();
        if (style.hasFill()) {
            quad(frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads()), pose,
                    a, b, c, d, style.fillColor());
        }
        if (style.hasStroke()) {
            com.mojang.blaze3d.systems.RenderSystem.lineWidth(Math.max(1.0F, style.strokeWidth()));
            com.mojang.blaze3d.vertex.VertexConsumer consumer =
                    frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
            net.minecraft.world.phys.Vec3 n = b.subtract(a).cross(d.subtract(a));
            if (n.lengthSqr() < 1.0E-8D) {
                n = new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D);
            } else {
                n = n.normalize();
            }
            lineVertex(consumer, pose, a, style.strokeColor(), n);
            lineVertex(consumer, pose, b, style.strokeColor(), n);
            lineVertex(consumer, pose, b, style.strokeColor(), n);
            lineVertex(consumer, pose, c, style.strokeColor(), n);
            lineVertex(consumer, pose, c, style.strokeColor(), n);
            lineVertex(consumer, pose, d, style.strokeColor(), n);
            lineVertex(consumer, pose, d, style.strokeColor(), n);
            lineVertex(consumer, pose, a, style.strokeColor(), n);
        }
        frame.pose.popPose();
    }

    public static void point(net.minecraft.world.phys.Vec3 pos, int color, float size) {
        Frame frame = frame();
        if (frame == null) {
            return;
        }
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera();
        org.joml.Vector3f leftVector = camera.getLeftVector();
        org.joml.Vector3f upVector = camera.getUpVector();
        float half = Math.max(0.01F, size * 0.01F);
        net.minecraft.world.phys.Vec3 left = new net.minecraft.world.phys.Vec3(leftVector.x(),
                leftVector.y(), leftVector.z()).scale(half);
        net.minecraft.world.phys.Vec3 up = new net.minecraft.world.phys.Vec3(upVector.x(),
                upVector.y(), upVector.z()).scale(half);
        frame.pose.pushPose();
        frame.pose.translate(-frame.camera.x, -frame.camera.y, -frame.camera.z);
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = frame.pose.last();
        quad(frame.consumers.getBuffer(net.minecraft.client.renderer.RenderType.debugQuads()), pose,
                pos.subtract(left).subtract(up), pos.add(left).subtract(up), pos.add(left).add(up),
                pos.subtract(left).add(up), color);
        frame.pose.popPose();
    }

    public static void arrow(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
            int color, float width) {
        line(from, to, color, width);
        Frame frame = frame();
        if (frame == null) {
            return;
        }
        net.minecraft.world.phys.Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        net.minecraft.world.phys.Vec3 forward = direction.normalize();
        org.joml.Vector3f upVector = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera().getUpVector();
        net.minecraft.world.phys.Vec3 side = forward
                .cross(new net.minecraft.world.phys.Vec3(upVector.x(), upVector.y(), upVector.z()));
        if (side.lengthSqr() < 1.0E-8D) {
            side = new net.minecraft.world.phys.Vec3(0.0D, 1.0D, 0.0D).cross(forward);
        }
        side = side.normalize();
        double head = Math.min(0.6D, direction.length() * 0.4D);
        net.minecraft.world.phys.Vec3 base = to.subtract(forward.scale(head));
        line(to, base.add(side.scale(head * 0.5D)), color, width);
        line(to, base.subtract(side.scale(head * 0.5D)), color, width);
    }

    private static void lineVertex(com.mojang.blaze3d.vertex.VertexConsumer consumer,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose, net.minecraft.world.phys.Vec3 pos,
            int color, net.minecraft.world.phys.Vec3 normal) {
        consumer.addVertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(red(color), green(color), blue(color), alpha(color))
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer consumer,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose, net.minecraft.world.phys.Vec3 a,
            net.minecraft.world.phys.Vec3 b, net.minecraft.world.phys.Vec3 c,
            net.minecraft.world.phys.Vec3 d, int color) {
        int r = red(color);
        int g = green(color);
        int bl = blue(color);
        int al = alpha(color);
        consumer.addVertex(pose.pose(), (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al);
        consumer.addVertex(pose.pose(), (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al);
        consumer.addVertex(pose.pose(), (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, al);
        consumer.addVertex(pose.pose(), (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, al);
    }

    private static void lineBox(com.mojang.blaze3d.vertex.VertexConsumer consumer,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose, net.minecraft.world.phys.AABB box,
            int color) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        net.minecraft.world.phys.Vec3[] corners = {
                new net.minecraft.world.phys.Vec3(x0, y0, z0),
                new net.minecraft.world.phys.Vec3(x1, y0, z0),
                new net.minecraft.world.phys.Vec3(x1, y0, z1),
                new net.minecraft.world.phys.Vec3(x0, y0, z1),
                new net.minecraft.world.phys.Vec3(x0, y1, z0),
                new net.minecraft.world.phys.Vec3(x1, y1, z0),
                new net.minecraft.world.phys.Vec3(x1, y1, z1),
                new net.minecraft.world.phys.Vec3(x0, y1, z1)};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4},
                {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : edges) {
            net.minecraft.world.phys.Vec3 a = corners[edge[0]];
            net.minecraft.world.phys.Vec3 b = corners[edge[1]];
            net.minecraft.world.phys.Vec3 n = b.subtract(a).normalize();
            lineVertex(consumer, pose, a, color, n);
            lineVertex(consumer, pose, b, color, n);
        }
    }

    private static void filledBox(com.mojang.blaze3d.vertex.VertexConsumer consumer,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose, net.minecraft.world.phys.AABB box,
            int color) {
        double x0 = box.minX;
        double y0 = box.minY;
        double z0 = box.minZ;
        double x1 = box.maxX;
        double y1 = box.maxY;
        double z1 = box.maxZ;
        // 下、上、北、南、西、东六个面，每面四个顶点（debugQuads 关闭了背面剔除）。
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x0, y0, z0),
                new net.minecraft.world.phys.Vec3(x1, y0, z0),
                new net.minecraft.world.phys.Vec3(x1, y0, z1),
                new net.minecraft.world.phys.Vec3(x0, y0, z1), color);
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x0, y1, z0),
                new net.minecraft.world.phys.Vec3(x0, y1, z1),
                new net.minecraft.world.phys.Vec3(x1, y1, z1),
                new net.minecraft.world.phys.Vec3(x1, y1, z0), color);
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x0, y0, z0),
                new net.minecraft.world.phys.Vec3(x0, y1, z0),
                new net.minecraft.world.phys.Vec3(x1, y1, z0),
                new net.minecraft.world.phys.Vec3(x1, y0, z0), color);
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x0, y0, z1),
                new net.minecraft.world.phys.Vec3(x1, y0, z1),
                new net.minecraft.world.phys.Vec3(x1, y1, z1),
                new net.minecraft.world.phys.Vec3(x0, y1, z1), color);
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x0, y0, z0),
                new net.minecraft.world.phys.Vec3(x0, y0, z1),
                new net.minecraft.world.phys.Vec3(x0, y1, z1),
                new net.minecraft.world.phys.Vec3(x0, y1, z0), color);
        quad(consumer, pose, new net.minecraft.world.phys.Vec3(x1, y0, z0),
                new net.minecraft.world.phys.Vec3(x1, y1, z0),
                new net.minecraft.world.phys.Vec3(x1, y1, z1),
                new net.minecraft.world.phys.Vec3(x1, y0, z1), color);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }
}
