package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * “世吞小助手”的客户端实现（移植自 lucidity 的 World Eater Mine Helper）。
 *
 * <p>世界吞噬者用 TNT 一路炸过去，总有一批方块炸不掉、必须人工补挖：不可推动的方块
 * （黑曜石、基岩、附魔台、重生锚……）、各种矿石与远古残骸、紫水晶晶簇与母岩、海带，
 * 以及所有含水的方块。原实现把这些方块在离本体 {@value #EXTRUDE_HEIGHT} 格高处<b>再画一遍</b>，
 * 从世吞上方俯视时就能一眼看到“还剩哪些没挖”。</p>
 *
 * <p><b>含水方块</b>：除了画方块本体的模型，还会在它外面再套一层半透明水色立方体
 * （略大于方块、包住里面的模型），并描一圈浅蓝边——也就是“外部的水包裹着内部的方块”。
 * 这样海底废墟 / 传送门废墟那些含水方块一眼就能和普通方块区分开。</p>
 *
 * <p><b>渲染范围</b>：扫描不再固定在玩家周围 24 格，而是跟着客户端的渲染距离走
 * （{@code 3..16} 个区块，超出的按 16 算），并且只扫客户端已经加载的区块，做到
 * “只要在加载范围内就渲染”。为了避免一帧扫完整张地图卡死，扫描按区块分帧进行：
 * 每 tick 只扫几个离玩家最近的区块，结果按区块缓存，渲染快照每
 * {@value #SCAN_INTERVAL_TICKS} tick 重建一次；区块卸载或玩家换维度时缓存自动失效。</p>
 *
 * <p>幽灵方块本身仍按方块自己的模型和贴图绘制，并按分类在最外围描一圈整块立方体的线框；
 * 如果幽灵原位还埋在实心方块里（机器太高压过来、或者方块本身埋得深），会沿同一列往上找到
 * 最近的空位再画，描边与连线也始终置顶，保证埋住的方块照样看得见；想退回旧的“只标露头”
 * 行为可在配置界面里把“显示被覆盖方块”关掉。标记数超上限时优先保留离玩家最近的那些。
 * Ctrl+O 切换显示；Ctrl+E 打开配置界面，可在界面上逐块开关、增删方块，
 * 并单独控制可含水方块的“含水样式”。</p>
 */
public final class WorldEaterHelperVisualizer {
    /** 幽灵方块相对本体抬高多少格（对齐原实现默认高度 7）。 */
    public static final float EXTRUDE_HEIGHT = 7.0F;

    /** 渲染快照重建间隔（tick）。 */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 每 tick 扫描多少个区块（区块按离玩家距离从近到远轮转，保证身边先亮）。 */
    private static final int CHUNKS_PER_TICK = 4;
    /** 扫描半径（区块）的下限：原版最小渲染距离 2 时也至少扫这么远。 */
    private static final int MIN_RADIUS_CHUNKS = 3;
    /** 扫描半径（区块）的上限：再大也是扫加载范围，但太大会拖慢每帧提交。 */
    private static final int MAX_RADIUS_CHUNKS = 16;
    /** 玩家脚下往下扫多少格。 */
    private static final int VERTICAL_BELOW = 40;
    /** 玩家头顶往上扫多少格。 */
    private static final int VERTICAL_ABOVE = 16;
    /** 玩家 y 变化超过这个数就整体重扫（扫描的竖直窗口跟着玩家走）。 */
    private static final int VERTICAL_RESET = 16;
    /** 一帧最多标注多少方块，防止矿洞里铺满。 */
    private static final int MAX_MARKS = 2000;
    /** 每个区块最多缓存多少标记，防止单个区块内存爆炸。 */
    private static final int MAX_CHUNK_MARKS = 400;
    /** 所有区块缓存加起来的软上限，超过后不再扩大扫描。 */
    private static final int MAX_CACHED_MARKS = 16000;

    /**
     * 幽灵方块被实心方块埋住时，最多沿这一列往上抬多少格去找空位。正常地形远用不到
     * 这么多；设上限只是防止在世界顶部/基岩层附近一路空转。
     */
    private static final int MAX_GHOST_LIFT = 48;

    /** 幽灵方块描边宽度 / 连接线宽度。 */
    private static final float LINK_WIDTH = 1.5F;
    /** 幽灵方块用满亮度渲染，免得埋在阴影里看不清贴图。 */
    private static final int GHOST_LIGHT = 0xF000F0;

    /** 含水方块外面那层“水壳”：包住内部方块模型，半透明水色。 */
    private static final double WATER_INFLATE = 0.06D;
    private static final int WATER_FILL = 0x66C6E6FF;
    private static final int WATER_RIM = 0xCCE6F6FF;

    /** 颜色分类与方块白名单统一放在 {@link WorldEaterConfig} 里，配置界面共用同一套定义。 */

    /** Ctrl+E 打开配置界面。 */
    private static KeyMapping configKey;
    /** 本规则在总控里的名字。 */
    private static final String RULE = "worldEaterHelper";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    /** 按区块缓存的标记：key 是 (cx<<32)|cz，value 是该区块扫出来的标记（可能为空表）。 */
    private static final Map<Long, List<Mark>> chunkCache = new HashMap<>();
    /** 当前扫描半径内、按离玩家距离从近到远排好的区块列表（手动打包成 long）。 */
    private static long[] chunkOrder = new long[0];
    /** 轮转游标：下一次从 chunkOrder 的哪里接着扫。 */
    private static int chunkCursor;
    /** 距离下一次重建渲染快照还有多少 tick。 */
    private static int snapshotCountdown;
    /** 上一次重建扫描顺序时的玩家区块 / 半径 / y。 */
    private static ClientLevel cachedLevel;
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int lastRadius = -1;
    private static int lastY = Integer.MIN_VALUE;

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
            reset();
            return;
        }
        if (level != cachedLevel) {
            reset();
            cachedLevel = level;
        }
        BlockPos center = player.blockPosition();
        int pcx = center.getX() >> 4;
        int pcz = center.getZ() >> 4;
        int py = center.getY();
        int radius = radiusChunks(client);
        boolean verticalJump = Math.abs(py - lastY) > VERTICAL_RESET;
        if (verticalJump) {
            // 扫描的竖直窗口跟着玩家走，y 变化太大时整批缓存都不再可信。
            chunkCache.clear();
        }
        if (verticalJump || pcx != lastChunkX || pcz != lastChunkZ || radius != lastRadius
                || chunkOrder.length == 0) {
            rebuildOrder(pcx, pcz, radius);
            lastChunkX = pcx;
            lastChunkZ = pcz;
            lastRadius = radius;
            lastY = py;
        }
        scanSomeChunks(level, center, py);
        if (--snapshotCountdown <= 0) {
            snapshotCountdown = SCAN_INTERVAL_TICKS;
            snapshot = buildSnapshot(center);
        }
    }

    private static void reset() {
        if (!chunkCache.isEmpty() || snapshot != Snapshot.EMPTY) {
            chunkCache.clear();
            chunkOrder = new long[0];
            chunkCursor = 0;
            snapshotCountdown = 0;
            lastRadius = -1;
            lastY = Integer.MIN_VALUE;
            snapshot = Snapshot.EMPTY;
        }
    }

    /** 扫描半径：跟随客户端“渲染距离”选项，夹在 {@value #MIN_RADIUS_CHUNKS}..{@value #MAX_RADIUS_CHUNKS} 区块。 */
    private static int radiusChunks(Minecraft client) {
        int option = client.options.getEffectiveRenderDistance();
        return Math.max(MIN_RADIUS_CHUNKS, Math.min(MAX_RADIUS_CHUNKS, option));
    }

    /** 重建区块轮转顺序：只留半径内的缓存，并按离玩家的距离排序让近处先扫。 */
    private static void rebuildOrder(int pcx, int pcz, int radius) {
        List<Long> order = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                order.add(pack(pcx + dx, pcz + dz));
            }
        }
        order.sort(Comparator.comparingLong(key -> {
            int cx = unpackX(key) - pcx;
            int cz = unpackZ(key) - pcz;
            return (long) cx * cx + (long) cz * cz;
        }));
        chunkOrder = new long[order.size()];
        for (int i = 0; i < order.size(); i++) {
            chunkOrder[i] = order.get(i);
        }
        chunkCursor = 0;
        // 掉出半径的区块缓存直接扔掉（换区块、改渲染距离时）。
        Iterator<Map.Entry<Long, List<Mark>>> it = chunkCache.entrySet().iterator();
        while (it.hasNext()) {
            long key = it.next().getKey();
            if (Math.abs(unpackX(key) - pcx) > radius || Math.abs(unpackZ(key) - pcz) > radius) {
                it.remove();
            }
        }
    }

    /** 每 tick 轮转扫几个区块，把结果写进缓存。 */
    private static void scanSomeChunks(ClientLevel level, BlockPos center, int py) {
        if (chunkOrder.length == 0 || cachedMarkCount() >= MAX_CACHED_MARKS) {
            return;
        }
        int minY = Math.max(level.getMinY(), py - VERTICAL_BELOW);
        int maxY = Math.min(level.getMaxY(), py + VERTICAL_ABOVE);
        boolean markHidden = WorldEaterConfig.isMarkHidden();
        for (int i = 0; i < CHUNKS_PER_TICK && chunkOrder.length > 0; i++) {
            int index = chunkCursor % chunkOrder.length;
            chunkCursor = (chunkCursor + 1) % chunkOrder.length;
            long key = chunkOrder[index];
            int cx = unpackX(key);
            int cz = unpackZ(key);
            chunkCache.put(key, scanChunk(level, cx, cz, minY, maxY, markHidden));
        }
    }

    private static int cachedMarkCount() {
        int total = 0;
        for (List<Mark> list : chunkCache.values()) {
            total += list.size();
        }
        return total;
    }

    /** 扫描一个已加载区块：只走有方块的 section，命中白名单就生成幽灵标记。 */
    private static List<Mark> scanChunk(ClientLevel level, int cx, int cz, int minY, int maxY,
            boolean markHidden) {
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
        if (chunk == null || chunk.isEmpty()) {
            return Collections.emptyList();
        }
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int chunkMinY = chunk.getMinY();
        List<Mark> marks = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            int index = (y - chunkMinY) >> 4;
            if (index < 0 || index >= sections.length) {
                continue;
            }
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int localY = y & 15;
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    int category = categoryOf(state);
                    if (category == 0) {
                        continue;
                    }
                    pos.set(baseX + localX, y, baseZ + localZ);
                    if (!markHidden && !isVisibleFromAbove(level, pos)) {
                        continue;
                    }
                    marks.add(createMark(level, pos.immutable(), state, category));
                    if (marks.size() >= MAX_CHUNK_MARKS) {
                        return marks;
                    }
                }
            }
        }
        return marks;
    }

    /** 把缓存里所有区块的标记合成一份渲染快照，超上限时留离玩家最近的。 */
    private static Snapshot buildSnapshot(BlockPos center) {
        List<Mark> all = new ArrayList<>();
        for (List<Mark> list : chunkCache.values()) {
            if (list != null && !list.isEmpty()) {
                all.addAll(list);
            }
        }
        if (all.isEmpty()) {
            return Snapshot.EMPTY;
        }
        if (all.size() > MAX_MARKS) {
            all.sort(Comparator.comparingDouble((Mark mark) -> mark.pos.distSqr(center)));
            all = new ArrayList<>(all.subList(0, MAX_MARKS));
        }
        return new Snapshot(all);
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }

    /**
     * 方块正上方是不是一路通气到幽灵方块所在高度。只在配置里关掉
     * “显示被覆盖方块”时才用到：旧行为只标能从世吞上方看到的方块，
     * 被完整埋住的矿石既看不见、也还没轮到挖，标出来只是干扰。
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

    /**
     * 生成一个幽灵标记。幽灵方块默认画在方块上方 {@value #EXTRUDE_HEIGHT} 格；如果那个位置
     * 本身还埋在实心方块里（机器压得太高，或者方块埋得深），就沿同一列往上找到第一格空位
     * 再画，免得方块模型整个陷在实心的东西里看不见。埋住的那些还会把描边和连接线设为
     * 始终置顶，从上往下看能透过地形找到它，也能顺线找回地下本体。
     */
    private static Mark createMark(ClientLevel level, BlockPos pos, BlockState state, int category) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        int baseY = pos.getY() + Math.round(EXTRUDE_HEIGHT);
        int ghostY = baseY;
        boolean buried = isOccluding(level, pos.getX(), baseY, pos.getZ());
        if (buried) {
            int cap = Math.min(level.getMaxY(), pos.getY() + MAX_GHOST_LIFT);
            while (ghostY <= cap && isOccluding(level, pos.getX(), ghostY, pos.getZ())) {
                ghostY++;
            }
            if (ghostY > level.getMaxY()) {
                ghostY = level.getMaxY();
            }
        }
        Vec3 from = new Vec3(x, pos.getY() + 0.5D, z);
        Vec3 to = new Vec3(x, ghostY + 0.5D, z);
        // 描边颜色按方块属性给，一个分类有多种颜色时按位置交替。
        int[] palette = WorldEaterConfig.outlinePalette(category);
        int outline = palette[Math.floorMod(pos.hashCode(), palette.length)];
        // 幽灵方块外框：抬高后的整块立方体，向外撑一点点避免和方块模型面共面闪烁。
        AABB ghostBox = new AABB(pos.getX(), ghostY, pos.getZ(),
                pos.getX() + 1.0D, ghostY + 1.0D, pos.getZ() + 1.0D).inflate(0.002D);
        boolean water = state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
        return new Mark(from, to, pos.immutable(), state, outline, ghostBox, ghostY, buried, water);
    }

    /** 该格是不是不透光的实心方块：幽灵方块放这里会被整个挡住。 */
    private static boolean isOccluding(ClientLevel level, int x, int y, int z) {
        if (y < level.getMinY() || y > level.getMaxY()) {
            return false;
        }
        return level.getBlockState(new BlockPos(x, y, z)).isSolidRender();
    }

    /** 只画连接线、描边与含水方块的水壳（幽灵方块本体走 {@link #collectSubmits} 的方块模型渲染）。 */
    private static void renderGizmos(LevelRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.marks.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = context.levelRenderer().collectPerFrameGizmos()) {
            for (int i = 0; i < current.marks.size(); i++) {
                Mark mark = current.marks.get(i);
                GizmoProperties line = Gizmos.line(mark.from, mark.to, mark.outline, LINK_WIDTH);
                // 含水方块：先套一层略大的半透明水色立方体，包住里面的方块模型。
                if (mark.water) {
                    GizmoProperties water = Gizmos.cuboid(mark.ghostBox.inflate(WATER_INFLATE),
                            GizmoStyle.strokeAndFill(WATER_RIM, LINK_WIDTH, WATER_FILL));
                    if (mark.buried) {
                        water.setAlwaysOnTop();
                    }
                }
                // 描边改成整块立方体的 12 条棱：之前把颜色交给方块模型的描边通道，
                // 那条通道会用方块贴图的 alpha 做遮罩（rendertype_outline 里 alpha==0 直接
                // discard），一旦贴图带透明像素（海带/海草、紫水晶晶簇、含水样式等）就会
                // 缺边，而且扁平的十字模型从侧面看几乎没有剪影，某些角度整块描边都消失。
                // 用几何线框画棱不吃贴图 alpha、也不依赖剪影后处理，任意角度都完整。
                GizmoProperties box = Gizmos.cuboid(mark.ghostBox,
                        GizmoStyle.stroke(mark.outline, LINK_WIDTH));
                if (mark.buried) {
                    // 本体埋在实心方块里：幽灵已经抬到空位，但描边和连线还是可能被山体挡住，
                    // 设为始终置顶，保证从上往下也能看见，并能顺线找回地下本体。
                    line.setAlwaysOnTop();
                    box.setAlwaysOnTop();
                }
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
        Vec3 camera = client.gameRenderer.getMainCamera().position();
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
                            mark.ghostY - camera.y,
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
        /** 幽灵方块所在的 y（原位被埋住时会抬到实心方块上方）。 */
        private final int ghostY;
        /** 幽灵方块原位是否埋在实心方块里：是的话描边与连线始终置顶。 */
        private final boolean buried;
        /** 本体是不是含水的方块：是的话外面再套一层水壳。 */
        private final boolean water;

        private Mark(Vec3 from, Vec3 to, BlockPos pos, BlockState state, int outline,
                AABB ghostBox, int ghostY, boolean buried, boolean water) {
            this.from = from;
            this.to = to;
            this.pos = pos;
            this.state = state;
            this.outline = outline;
            this.ghostBox = ghostBox;
            this.ghostY = ghostY;
            this.buried = buried;
            this.water = water;
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
