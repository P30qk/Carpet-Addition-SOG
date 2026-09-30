package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * “世吞小助手”的客户端实现（移植自 lucidity 的 World Eater Mine Helper）。
 *
 * <p>世界吞噬者用 TNT 一路炸过去，总有一批方块炸不掉、必须人工补挖：不可推动的方块
 * （黑曜石、基岩、附魔台、重生锚……）、各种矿石与远古残骸、紫水晶晶簇与母岩、海带/海草，
 * 以及所有含水的方块。原实现把这些方块在离本体 {@value #EXTRUDE_HEIGHT} 格高处<b>再画一遍</b>，
 * 从世吞上方俯视时就能一眼看到“还剩哪些没挖”。</p>
 *
 * <p>映射方块用<b>方块自己的模型贴图</b>绘制，和下面那个被映射的方块长得一模一样；
 * 再按它所属的属性用特定颜色在最外围描一圈<b>整块立方体的线框边</b>，
 * 一个分类给了多种颜色时按位置交替使用，方便区分相邻方块。线框不经过贴图透明通道、
 * 也不依赖剪影后处理，因此任意视角下描边都完整可见（旧的模型描边通道会被贴图透明像素
 * 遮掉，扁平的十字模型从侧面看还会整块缺边）。只标注<b>正上方一路通气到
 * 幽灵高度</b>的方块，也就是真正能从世吞上方看到的那些；扫描每
 * {@value #SCAN_INTERVAL_TICKS} tick 一次，范围是以玩家为中心的水平 24 格、
 * 向下 40 格、向上 16 格。Ctrl+O 切换显示；Ctrl+E 打开配置界面，
 * 可在界面上逐块开关、增删方块，并单独控制可含水方块的“含水样式”。</p>
 */
public final class WorldEaterHelperVisualizer {
    /** 幽灵方块相对本体抬高多少格（对齐原实现默认高度 7）。 */
    public static final float EXTRUDE_HEIGHT = 7.0F;

    /** 扫描节流：每多少 tick 重扫一次。 */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 水平扫描半径（格）。 */
    private static final int HORIZONTAL_RADIUS = 24;
    /** 玩家脚下往下扫多少格。 */
    private static final int VERTICAL_BELOW = 40;
    /** 玩家头顶往上扫多少格。 */
    private static final int VERTICAL_ABOVE = 16;
    /** 一帧最多标注多少方块，防止矿洞里铺满。 */
    private static final int MAX_MARKS = 2000;

    /** 幽灵方块描边宽度 / 连接线宽度。 */
    private static final float LINK_WIDTH = 1.5F;
    /** 幽灵方块用满亮度渲染，免得埋在阴影里看不清贴图。 */
    private static final int GHOST_LIGHT = 0xF000F0;

    /** 颜色分类与方块白名单统一放在 {@link WorldEaterConfig} 里，配置界面共用同一套定义。 */

    /** Ctrl+E 打开配置界面。 */
    private static KeyMapping configKey;
    /** 本规则在总控里的名字。 */
    private static final String RULE = "worldEaterHelper";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private WorldEaterHelperVisualizer() {
    }

    /** 客户端入口调用一次：注册按键、tick 扫描与渲染回调。 */
    public static void init() {
        WorldEaterConfig.ensureLoaded();
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sog_carpet.world_eater_config", GLFW.GLFW_KEY_E, KeyMapping.Category.MISC));
        // Ctrl+E 必须赶在 handleKeybinds() 之前处理，否则物品栏会先被 E 打开。
        ClientTickEvents.START_CLIENT_TICK.register(WorldEaterHelperVisualizer::clientTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(WorldEaterHelperVisualizer::clientTick);
        LevelRenderEvents.BEFORE_GIZMOS.register(WorldEaterHelperVisualizer::renderGizmos);
        LevelRenderEvents.COLLECT_SUBMITS.register(WorldEaterHelperVisualizer::collectSubmits);
    }

    /**
     * Ctrl+E 打开配置界面。E 同时绑着原版物品栏，这里把物品栏的点击也一并消费掉，
     * 保证只打开配置界面。配置界面只改客户端白名单，所以不再要求先开服务端规则。
     */
    private static void clientTickStart(Minecraft client) {
        while (configKey.consumeClick()) {
            if (!isCtrlDown(client) || client.level == null) {
                continue;
            }
            while (client.options.keyInventory.consumeClick()) {
                // 吞掉同一个 E 触发的物品栏，避免配置界面被它顶掉。
            }
            client.setScreenAndShow(new WorldEaterConfigScreen());
            return;
        }
    }

    /** Ctrl 是否按下（配合 E 打开配置界面）。 */
    private static boolean isCtrlDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        Player player = client.player;
        if (!VisualizerState.isActive(RULE) || level == null || player == null) {
            tickCounter = 0;
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot = scan(level, player);
    }


    private static Snapshot scan(ClientLevel level, Player player) {
        BlockPos center = player.blockPosition();
        int minX = center.getX() - HORIZONTAL_RADIUS;
        int maxX = center.getX() + HORIZONTAL_RADIUS;
        int minZ = center.getZ() - HORIZONTAL_RADIUS;
        int maxZ = center.getZ() + HORIZONTAL_RADIUS;
        int minY = Math.max(level.getMinY(), center.getY() - VERTICAL_BELOW);
        int maxY = Math.min(level.getMaxY(), center.getY() + VERTICAL_ABOVE);

        List<Mark> marks = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY && marks.size() < MAX_MARKS; y++) {
            for (int z = minZ; z <= maxZ && marks.size() < MAX_MARKS; z++) {
                for (int x = minX; x <= maxX && marks.size() < MAX_MARKS; x++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    int category = categoryOf(state);
                    if (category == 0 || !isVisibleFromAbove(level, pos)) {
                        continue;
                    }
                    marks.add(createMark(pos, state, category));
                }
            }
        }
        if (marks.isEmpty()) {
            return Snapshot.EMPTY;
        }
        return new Snapshot(marks);
    }

    /**
     * 方块正上方是不是一路通气到幽灵方块所在高度。只标注“从上面看得见”的方块：
     * 活着的世吞会把头顶清空，剩下的补挖方块自然处于露天或沟底；被完整埋住的矿石
     * 既看不见、也还没轮到挖，标出来只是干扰。
     */
    private static boolean isVisibleFromAbove(ClientLevel level, BlockPos pos) {
        int column = Math.round(EXTRUDE_HEIGHT) + 1;
        int maxY = level.getMaxY();
        for (int dy = 1; dy <= column; dy++) {
            int y = pos.getY() + dy;
            if (y > maxY) {
                return true;
            }
            if (level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())).isSolidRender()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回该方块属于哪一类需要补挖的方块；0 表示不属于。
     *
     * <p>判定完全交给 {@link WorldEaterConfig}：白名单里关掉的方块直接不标，含水方块按
     * “含水样式”开关决定要不要标成蓝色，本体分类沿用内置规则或玩家手动指定的颜色。</p>
     */
    private static int categoryOf(BlockState state) {
        Block block = state.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return 0;
        }
        boolean waterloggable = state.hasProperty(BlockStateProperties.WATERLOGGED);
        if (!WorldEaterConfig.isEnabled(id, waterloggable)) {
            return 0;
        }
        if (waterloggable && state.getValue(BlockStateProperties.WATERLOGGED)
                && WorldEaterConfig.isWaterlogMarked(id, true)) {
            return WorldEaterConfig.CAT_WATERLOGGED;
        }
        return WorldEaterConfig.effectiveCategory(id);
    }

    private static Mark createMark(BlockPos pos, BlockState state, int category) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        Vec3 from = new Vec3(x, y, z);
        Vec3 to = new Vec3(x, y + EXTRUDE_HEIGHT, z);
        // 描边颜色按方块属性给，一个分类有多种颜色时按位置交替。
        int[] palette = WorldEaterConfig.outlinePalette(category);
        int outline = palette[Math.floorMod(pos.hashCode(), palette.length)];
        // 幽灵方块外框：抬高后的整块立方体，向外撑一点点避免和方块模型面共面闪烁。
        AABB ghostBox = new AABB(pos).move(0.0D, EXTRUDE_HEIGHT, 0.0D).inflate(0.002D);
        return new Mark(from, to, pos.immutable(), state, outline, ghostBox);
    }

    /** 只画连接线（幽灵方块本体走 {@link #collectSubmits} 的方块模型渲染）。 */
    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.marks.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            for (int i = 0; i < current.marks.size(); i++) {
                Mark mark = current.marks.get(i);
                Gizmos.line(mark.from, mark.to, mark.outline, LINK_WIDTH);
                // 描边改成整块立方体的 12 条棱：之前把颜色交给方块模型的描边通道，
                // 那条通道会用方块贴图的 alpha 做遮罩（rendertype_outline 里 alpha==0 直接
                // discard），一旦贴图带透明像素（海带/海草、紫水晶晶簇、含水样式等）就会
                // 缺边，而且扁平的十字模型从侧面看几乎没有剪影，某些角度整块描边都消失。
                // 用几何线框画棱不吃贴图 alpha、也不依赖剪影后处理，任意角度都完整。
                Gizmos.cuboid(mark.ghostBox, GizmoStyle.stroke(mark.outline, LINK_WIDTH));
            }
        }
    }

    /**
     * 把每个需要补挖的方块在抬高 {@value #EXTRUDE_HEIGHT} 格的位置用它的真实方块模型再画一遍；
     * 属性色描边由 {@link #renderGizmos} 画成立方体线框（不吃贴图 alpha，任意角度都完整）。
     * 方块模型按 {@link BlockState} 缓存，同一帧里同种方块只解析一次。
     */
    private static void collectSubmits(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.marks.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        Vec3 camera = client.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        if (poseStack == null || collector == null) {
            return;
        }
        BlockModelResolver resolver = new BlockModelResolver(client.getModelManager());
        BlockDisplayContext displayContext = BlockDisplayContext.create();
        Map<BlockState, BlockModelRenderState> cache = new HashMap<>();
        try {
            for (int i = 0; i < current.marks.size(); i++) {
                Mark mark = current.marks.get(i);
                BlockModelRenderState model = cache.get(mark.state);
                if (model == null) {
                    model = new BlockModelRenderState();
                    resolver.update(model, mark.state, displayContext);
                    cache.put(mark.state, model);
                }
                poseStack.pushPose();
                try {
                    poseStack.translate(mark.pos.getX() - camera.x,
                            mark.pos.getY() + EXTRUDE_HEIGHT - camera.y,
                            mark.pos.getZ() - camera.z);
                    // 最后一个参数是模型自带的描边通道，这里传 0 关掉：描边统一走
                    // renderGizmos 里的立方体线框，避免贴图透明像素把描边遮掉。
                    model.submit(poseStack, collector, GHOST_LIGHT, OverlayTexture.NO_OVERLAY, 0);
                } finally {
                    poseStack.popPose();
                }
            }
        } catch (RuntimeException ignored) {
            // 资源重载等极端情况下解析不出模型：这一帧跳过，不影响别的东西。
        }
    }

    /** 一个需要补挖的方块及其幽灵标记。 */
    private static final class Mark {
        private final Vec3 from;
        private final Vec3 to;
        private final BlockPos pos;
        private final BlockState state;
        private final int outline;
        /** 幽灵立方体的世界坐标包围盒，用于画描边线框。 */
        private final AABB ghostBox;

        private Mark(Vec3 from, Vec3 to, BlockPos pos, BlockState state, int outline, AABB ghostBox) {
            this.from = from;
            this.to = to;
            this.pos = pos;
            this.state = state;
            this.outline = outline;
            this.ghostBox = ghostBox;
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
