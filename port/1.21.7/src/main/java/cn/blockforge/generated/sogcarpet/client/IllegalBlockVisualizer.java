package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoProperties;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import cn.blockforge.generated.sogcarpet.client.gizmo.TextGizmo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 非法方块可视化：把平时看不见的方块整块渲染出来，<b>不需要手持</b>对应方块。
 *
 * <p>覆盖的方块（每一类都能在 Ctrl+V 的“配置”里单独开关）：</p>
 * <ul>
 *   <li>屏障（{@code barrier}）、结构空位（{@code structure_void}）、光明方块（{@code light}）；</li>
 *   <li>“其它技术方块”：移动的活塞、活塞头、末地传送门、末地折跃门。</li>
 * </ul>
 *
 * <p>渲染表现与基岩版手持这些方块时相近：每个方块按它自己的颜色画一个整块大小的半透明实心
 * 立方体并描边（颜色优先取方块自身的地图色，取不到、或者是暗到看不见的颜色时用各方的代表色——
 * 屏障红、结构空位灰绿、光明方块暖黄、其它技术方块白），所以不用手持也能一眼看出哪一格是屏障、
 * 哪一格是光明方块。光明方块还会在方块中央浮出它的<b>光照等级</b>（{@code LightBlock#LEVEL}，
 * 0-15）。</p>
 *
 * <p>全部在客户端完成，服务端零负担：扫描只走已加载的区块，范围跟着客户端渲染距离走；每个区块
 * 先用 {@link LevelChunkSection#maybeHas} 在方块调色板上做一次廉价筛选，整节里没有关心的方块时
 * 直接跳过 4096 次查询，所以一 tick 能扫很多区块、方块放下之后一两秒内就会显示出来。</p>
 */
public final class IllegalBlockVisualizer {
    /** 本规则在总控里的名字。 */
    private static final String RULE = "illegalBlockVisualizer";

    /** 扫描半径（区块）跟随客户端渲染距离，夹在这个范围内。 */
    private static final int MIN_RADIUS_CHUNKS = 3;
    private static final int MAX_RADIUS_CHUNKS = 16;
    /** 每 tick 扫描多少个区块（按离玩家距离从近到远轮转，身边先亮）。 */
    private static final int CHUNKS_PER_TICK = 32;
    /** 渲染快照重建间隔（tick）。 */
    private static final int SCAN_INTERVAL_TICKS = 5;
    /** 竖直窗口：玩家脚下、头顶各扫多少格（屏障/光明方块一般不会离玩家太远）。 */
    private static final int VERTICAL_BELOW = 48;
    private static final int VERTICAL_ABOVE = 32;
    /** 玩家竖直移动超过这么多格，整批缓存重扫（竖直窗口要跟着走）。 */
    private static final int VERTICAL_RESET = 16;
    /** 同屏最多画多少个方块、单个区块最多记多少个、缓存总量上限。 */
    private static final int MAX_MARKS = 2048;
    private static final int MAX_CHUNK_MARKS = 256;
    private static final int MAX_CACHED_MARKS = 16384;

    /** 描边宽度与立方体四周的收边量（避免与相邻方块共面打架）。 */
    private static final float STROKE_WIDTH = 2.0F;
    private static final double BOX_DEFLATE = 0.002D;
    /** 填充的不透明度取描边不透明度的多少倍。 */
    private static final double FILL_ALPHA_RATIO = 0.35D;
    /** 光照等级数字的缩放与抬高。 */
    private static final float LIGHT_TEXT_SCALE = 1.0F;
    private static final double LIGHT_TEXT_LIFT = 0.25D;
    /** 方块地图色暗到这个亮度（最大通道值）以下时，算“没有颜色”，改用该类代表色。 */
    private static final int MIN_MAP_BRIGHTNESS = 0x30;

    /** 方块分类：屏障 / 结构空位 / 光明方块 / 其它技术方块。 */
    private static final int TYPE_BARRIER = 0;
    private static final int TYPE_STRUCTURE_VOID = 1;
    private static final int TYPE_LIGHT = 2;
    private static final int TYPE_OTHER = 3;
    private static final int TYPE_COUNT = 4;

    /** 各类方块的代表色（取不到方块地图色时用它，也可以在配置界面里改）。 */
    private static final int[] DEFAULT_COLORS = {
            0xFFFF4D4D,
            0xFF9CAF88,
            0xFFFFE082,
            0xFFFFFFFF
    };
    /** 光明方块光照数值的默认颜色。 */
    private static final int DEFAULT_LIGHT_TEXT = 0xFFFFF6C8;

    private static final int[] COLORS = DEFAULT_COLORS.clone();
    private static int COLOR_LIGHT_TEXT = DEFAULT_LIGHT_TEXT;

    /** 从用户配置刷新颜色（没有配置时回落到默认值）。 */
    private static void refreshColors() {
        COLORS[TYPE_BARRIER] = VisualizerConfig.color(RULE, "barrier", DEFAULT_COLORS[TYPE_BARRIER]);
        COLORS[TYPE_STRUCTURE_VOID] = VisualizerConfig.color(
                RULE, "structureVoid", DEFAULT_COLORS[TYPE_STRUCTURE_VOID]);
        COLORS[TYPE_LIGHT] = VisualizerConfig.color(RULE, "light", DEFAULT_COLORS[TYPE_LIGHT]);
        COLORS[TYPE_OTHER] = VisualizerConfig.color(RULE, "other", DEFAULT_COLORS[TYPE_OTHER]);
        COLOR_LIGHT_TEXT = VisualizerConfig.color(RULE, "lightText", DEFAULT_LIGHT_TEXT);
    }

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static final Map<Long, List<Mark>> chunkCache = new HashMap<>();
    private static long[] chunkOrder = new long[0];
    private static int chunkCursor;
    /** 距离下一次重建渲染快照还有多少 tick。 */
    private static int snapshotCountdown;
    private static ClientLevel cachedLevel;
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int lastRadius = -1;
    private static int lastY = Integer.MIN_VALUE;

    private IllegalBlockVisualizer() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(IllegalBlockVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(IllegalBlockVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null || client.player == null) {
            reset();
            return;
        }
        if (level != cachedLevel) {
            reset();
            cachedLevel = level;
        }
        BlockPos center = client.player.blockPosition();
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
        scanSomeChunks(level, py);
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
            lastChunkX = Integer.MIN_VALUE;
            lastChunkZ = Integer.MIN_VALUE;
            cachedLevel = null;
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
    private static void scanSomeChunks(ClientLevel level, int py) {
        if (chunkOrder.length == 0 || cachedMarkCount() >= MAX_CACHED_MARKS) {
            return;
        }
        int minY = Math.max(level.getMinY(), py - VERTICAL_BELOW);
        int maxY = Math.min(level.getMaxY(), py + VERTICAL_ABOVE);
        boolean[] enabled = new boolean[TYPE_COUNT];
        enabled[TYPE_BARRIER] = VisualizerConfig.option(RULE, "showBarrier", true);
        enabled[TYPE_STRUCTURE_VOID] = VisualizerConfig.option(RULE, "showStructureVoid", true);
        enabled[TYPE_LIGHT] = VisualizerConfig.option(RULE, "showLight", true);
        enabled[TYPE_OTHER] = VisualizerConfig.option(RULE, "showOther", true);
        boolean mapColor = VisualizerConfig.option(RULE, "blockColor", true);
        for (int i = 0; i < CHUNKS_PER_TICK && chunkOrder.length > 0; i++) {
            int index = chunkCursor % chunkOrder.length;
            chunkCursor = (chunkCursor + 1) % chunkOrder.length;
            long key = chunkOrder[index];
            chunkCache.put(key, scanChunk(level, unpackX(key), unpackZ(key), minY, maxY, enabled, mapColor));
        }
    }

    private static int cachedMarkCount() {
        int total = 0;
        for (List<Mark> list : chunkCache.values()) {
            total += list.size();
        }
        return total;
    }

    /**
     * 扫描一个已加载区块：先用方块调色板筛掉整节里没有目标方块的 section（{@link
     * LevelChunkSection#maybeHas}），只有筛中了的 section 才逐格检查。
     */
    private static List<Mark> scanChunk(ClientLevel level, int cx, int cz, int minY, int maxY,
            boolean[] enabled, boolean mapColor) {
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
            if (section == null || section.hasOnlyAir()
                    || !section.maybeHas(IllegalBlockVisualizer::isTargetBlock)) {
                continue;
            }
            int localY = y & 15;
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    BlockState state = section.getBlockState(localX, localY, localZ);
                    int type = typeOf(state);
                    if (type < 0 || !enabled[type]) {
                        continue;
                    }
                    pos.set(baseX + localX, y, baseZ + localZ);
                    marks.add(createMark(level, pos.immutable(), state, type, mapColor));
                    if (marks.size() >= MAX_CHUNK_MARKS) {
                        return marks;
                    }
                }
            }
        }
        return marks;
    }

    /** 调色板筛选用的判定：这一节里到底有没有我们关心的方块。 */
    private static boolean isTargetBlock(BlockState state) {
        return typeOf(state) >= 0;
    }

    /** 方块属于哪一类；不是我们要画的方块时返回 -1。 */
    private static int typeOf(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.BARRIER) {
            return TYPE_BARRIER;
        }
        if (block == Blocks.STRUCTURE_VOID) {
            return TYPE_STRUCTURE_VOID;
        }
        if (block == Blocks.LIGHT) {
            return TYPE_LIGHT;
        }
        if (block == Blocks.MOVING_PISTON || block == Blocks.PISTON_HEAD
                || block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
            return TYPE_OTHER;
        }
        return -1;
    }

    private static Mark createMark(ClientLevel level, BlockPos pos, BlockState state, int type,
            boolean mapColor) {
        int rgb = mapColor ? mapColorRgb(level, pos, state) : -1;
        int lightLevel = type == TYPE_LIGHT ? state.getValue(LightBlock.LEVEL) : -1;
        return new Mark(pos, type, rgb, lightLevel);
    }

    /**
     * 方块自己的颜色（地图色）。{@code MapColor#NONE}、纯黑、以及暗到几乎看不见的颜色
     * （末地传送门与末地折跃门的地图色是 0x191919）都算“没有颜色”，渲染时改用该类的代表色，
     * 免得画出来是一团黑、反而更难发现。
     */
    private static int mapColorRgb(ClientLevel level, BlockPos pos, BlockState state) {
        MapColor color;
        try {
            color = state.getMapColor(level, pos);
        } catch (RuntimeException ignored) {
            // 少数方块的地图色需要真实的读取上下文，取不到就按没有颜色处理。
            return -1;
        }
        if (color == null || color == MapColor.NONE) {
            return -1;
        }
        int rgb = color.col & 0x00FFFFFF;
        int brightest = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));
        return brightest < MIN_MAP_BRIGHTNESS ? -1 : rgb;
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

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        refreshColors();
        Snapshot current = snapshot;
        if (current.marks.isEmpty()) {
            return;
        }
        // 光明方块的光照数值：方块中央浮出的数字，像基岩版那样一眼看到等级。
        boolean lightText = VisualizerConfig.option(RULE, "lightLevelText", true);
        boolean alwaysOnTop = VisualizerConfig.option(RULE, "alwaysOnTop", false);
        try (Gizmos.TemporaryCollection collection =
                Gizmos.begin(context)) {
            for (int i = 0; i < current.marks.size(); i++) {
                Mark mark = current.marks.get(i);
                int stroke = mark.rgb >= 0 ? (0xFF000000 | mark.rgb) : COLORS[mark.type];
                GizmoProperties box = Gizmos.cuboid(boxOf(mark.pos),
                        GizmoStyle.strokeAndFill(stroke, STROKE_WIDTH, fillOf(stroke)));
                if (alwaysOnTop) {
                    // 屏障常常嵌在墙里，需要穿墙看见时可以打开这个开关。
                    box.setAlwaysOnTop();
                }
                if (mark.lightLevel >= 0 && lightText) {
                    GizmoProperties text = Gizmos.billboardText(Integer.toString(mark.lightLevel),
                            new Vec3(mark.pos.getX() + 0.5D,
                                    mark.pos.getY() + 0.5D + LIGHT_TEXT_LIFT,
                                    mark.pos.getZ() + 0.5D),
                            TextGizmo.Style.forColor(COLOR_LIGHT_TEXT).withScale(LIGHT_TEXT_SCALE));
                    if (alwaysOnTop) {
                        text.setAlwaysOnTop();
                    }
                }
            }
        }
    }

    /** 整块立方体，四周收一点点边，避免与相邻方块共面产生深度打架（z-fighting）。 */
    private static AABB boxOf(BlockPos pos) {
        return new AABB(pos).deflate(BOX_DEFLATE);
    }

    /** 填充色：只把描边色的不透明度按比例压低，色相保持一致。 */
    private static int fillOf(int stroke) {
        int alpha = (int) (((stroke >>> 24) & 0xFF) * FILL_ALPHA_RATIO);
        return (alpha << 24) | (stroke & 0x00FFFFFF);
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
     * 一个待绘制的方块。
     *
     * @param pos        方块坐标
     * @param type       分类（决定取哪个配置颜色）
     * @param rgb        方块自身地图色；{@code -1} 表示没有地图色，用分类代表色
     * @param lightLevel 光明方块的光照等级；其它方块为 {@code -1}
     */
    private record Mark(BlockPos pos, int type, int rgb, int lightLevel) {
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
