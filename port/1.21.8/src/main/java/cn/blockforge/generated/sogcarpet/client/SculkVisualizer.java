package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * “幽匿检测范围可视化”规则的客户端实现。
 *
 * <p><b>显示内容</b></p>
 * <ul>
 *   <li>每个幽匿感测体 / 校频感测体以自身方块中心画一个<b>实心半透明检测球</b>，半径即原版
 *       检测半径（8 格），球的颜色表示它所在传播链的状态（默认淡蓝）；球由上千条四边形
 *       面片拼成球壳，看着是一个浑圆的半透明球，不是贴地的平面圆，也不是线框；</li>
 *   <li>距离在检测半径内、可以互相传递振动的两个节点之间连线；</li>
 *   <li>节点所在的方块罩上一个<b>半透明实心立方体</b>，颜色取该状态的线色，比球浓一档。</li>
 * </ul>
 *
 * <p><b>级别与配色</b>（尖啸体是否“能生成坚守者”取自原版同步到客户端的
 * {@link SculkShriekerBlock#CAN_SUMMON} 方块状态）</p>
 * <table border="1">
 *   <caption>状态表</caption>
 *   <tr><th>归属</th><th>连线 / 方块立方体</th><th>检测球</th></tr>
 *   <tr><td>纯感测体链（含孤立感测体）</td><td>绿</td><td>淡蓝</td></tr>
 *   <tr><td>能生成坚守者的尖啸体</td><td>红</td><td>淡红</td></tr>
 *   <tr><td>不能生成坚守者的尖啸体</td><td>白</td><td>白色（比球内的方框更淡）</td></tr>
 *   <tr><td>通往尖啸体的路径被羊毛挡住</td><td>黄 / 灰</td><td>淡黄 / 浅灰</td></tr>
 * </table>
 *
 * <p><b>“不能召唤”一侧的感测体</b>（含校频感测体变种）：正方形方框与检测球一律为白色，
 * 且球的 alpha（{@link #BALL_WHITE}）严格低于方框填色的 alpha（{@link #CUBE_FILL_ALPHA}），
 * 于是方框那层白比球更实、更清楚。此规则不参与优先级比较——同一个感测体若还能连到
 * “能召唤”的尖啸体，仍按下面的优先级走红 / 黄。</p>
 *
 * <p><b>优先级与逐条连线配色</b>：只要还能连到“能生成坚守者”的尖啸体，节点就按这一类渲染
 * （优先级：能生成 &gt; 不能生成 &gt; 纯感测体），所以同一个感测体同时接上两类尖啸体时，
 * 球与方块立方体走能生成的一侧。连线则按“离哪一侧的尖啸体更近”各自取色：接到不能生成的那条
 * 尖啸体的线仍用该有的白 / 灰，不会跟着优先级一起变成红 / 黄；平手时按能生成的一侧。
 * 每条线是否变黄只看这条线自己有没有被羊毛挡住。</p>
 *
 * <p>只有羊毛算阻挡；不能生成坚守者的尖啸体，其方块始终用白色立方体标出（即使它所在的线
 * 被羊毛挡住也不变灰，只有感测体与连线转灰）。</p>
 *
 * <p><b>数据来源</b>：感测体与尖啸体都是方块实体，客户端只遍历玩家周围 7x7 区块的
 * 方块实体表，不逐方块扫描，每 {@value #SCAN_INTERVAL_TICKS} tick 重建一次快照，
 * 渲染线程只读快照，避免边扫边画。</p>
 *
 * <p><b>绘制方式</b>：26.2 的自定义几何走 {@link Gizmos}。BEFORE_GIZMOS 在
 * {@code LevelRenderer.finalizeGizmoCollection()} 开头触发，此时线程级收集器还没挂上，
 * 所以先用 {@code collectPerFrameGizmos()} 取回本帧收集器再提交，
 * 提交的图形随后被同一个 finalize 流程 drain 并绘制。</p>
 */
public final class SculkVisualizer {
    /** 原版幽匿感测体的监听半径；球半径与“可传递”判定都用它。 */
    private static final double LISTENER_RANGE = 8.0D;

    // —— 实心检测球：球壳面片 ——
    // 本版本的 Gizmos 只有水平圆，没有球体图元，所以球壳自己拆成四边形面片
    // （Gizmos.rect 四个角点即可画一个四边形，fill 样式走半透明填充）。
    // 几何全部在类加载时算好，渲染线程只做“相对球心平移”，不再算三角函数。
    /** 每条纬线上的分段数（经线方向的细分数）。 */
    private static final int SPHERE_LONGITUDES = 20;
    /** 从南极到北极分多少条纬线带。 */
    private static final int SPHERE_LATITUDES = 10;
    /** 球壳顶点：SPHERE_LATITUDES+1 条纬线，每条 SPHERE_LONGITUDES 个点。 */
    private static final Vec3[][] SPHERE_RINGS = new Vec3[SPHERE_LATITUDES + 1][SPHERE_LONGITUDES];
    /** 每个球壳面片的中心（相对球心），用于判断面片是否正对镜头。 */
    private static final Vec3[][] SPHERE_QUAD_CENTERS = new Vec3[SPHERE_LATITUDES][SPHERE_LONGITUDES];
    /** 每个球壳面片朝外的法线（单位向量）。 */
    private static final Vec3[][] SPHERE_QUAD_NORMALS = new Vec3[SPHERE_LATITUDES][SPHERE_LONGITUDES];

    static {
        for (int i = 0; i <= SPHERE_LATITUDES; i++) {
            // 纬度从 -90°（南极）均匀走到 +90°（北极）。
            double latitude = -Math.PI / 2.0D + Math.PI * i / SPHERE_LATITUDES;
            double ringRadius = Math.cos(latitude) * LISTENER_RANGE;
            double ringY = Math.sin(latitude) * LISTENER_RANGE;
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                double longitude = 2.0D * Math.PI * j / SPHERE_LONGITUDES;
                SPHERE_RINGS[i][j] = new Vec3(ringRadius * Math.cos(longitude), ringY,
                        ringRadius * Math.sin(longitude));
            }
        }
        for (int band = 0; band < SPHERE_LATITUDES; band++) {
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                int next = (j + 1) % SPHERE_LONGITUDES;
                Vec3 quadCenter = SPHERE_RINGS[band][j].add(SPHERE_RINGS[band + 1][j])
                        .add(SPHERE_RINGS[band + 1][next]).add(SPHERE_RINGS[band][next]).scale(0.25D);
                SPHERE_QUAD_CENTERS[band][j] = quadCenter;
                // 面片中心本身就是球面上的点，归一化即朝外法线。
                SPHERE_QUAD_NORMALS[band][j] = quadCenter.normalize();
            }
        }
    }

    /** 以玩家为中心扫描的区块半径。 */
    private static final int SCAN_RADIUS_CHUNKS = 3;
    /** 扫描节流：每多少 tick 重建一次快照。 */
    private static final int SCAN_INTERVAL_TICKS = 10;
    /** 节点离玩家超过该距离就忽略，避免把远方的链也画出来。 */
    private static final double MAX_DISTANCE_SQR = 40.0D * 40.0D;
    /** BFS 里表示“到不了”的距离。 */
    private static final int UNREACHABLE = Integer.MAX_VALUE / 2;

    // 状态的基准色（这里都写满不透明的 RGB，实际 alpha 由下面几个常量决定）。
    private static final int COLOR_SENSOR_CHAIN = 0xFF3CE04A;
    private static final int COLOR_SHRIEKER_CHAIN = 0xFFFF3B30;
    private static final int COLOR_BLOCKED = 0xFFFFE14D;
    private static final int COLOR_UNSPAWNABLE = 0xFFFFFFFF;
    private static final int COLOR_UNSPAWNABLE_BLOCKED = 0xFF9E9E9E;

    // 连线的 alpha 特意压到 255 以下：这样线会落进“半透明组”，而一帧里半透明组
    // 总是先画所有面片、再画所有线，于是连线永远叠在半透明的球 / 方块之后绘制，
    // 不会被那层填色冲淡，也不会随视角反复变化。
    private static final int LINE_ALPHA = 0xF0;
    /** 连线宽度（屏幕像素）；默认 3.0，加粗一档更醒目。 */
    private static final float LINE_WIDTH = 4.0F;

    // 实心检测球的填充色（半透明的淡色）。只画正对镜头的那半边（见 drawBall），
    // 每条视线只穿过一层，所以单个 alpha 就是最终观感；不同视角颜色保持一致。
    private static final int BALL_DEFAULT = 0x6666CCFF;
    private static final int BALL_RED = 0x66FF8A8A;
    private static final int BALL_YELLOW = 0x66FFE98A;
    // 连到“不能召唤”的尖啸体的感测体（含校频感测体），检测球用纯白，且 alpha 明显低于
    // 同一节点的立方体填色（{@link #CUBE_FILL_ALPHA}）：球在外、箱在内，球比箱子里的
    // 那层颜色更淡，这样白色方框才是整片白雾里最清楚的那一层。
    private static final int BALL_WHITE = 0x55FFFFFF;
    private static final int BALL_GRAY = 0x66BDBDBD;

    // 节点方块改成半透明实心立方体：色相还是各自状态的线色，填色比球浓一档，
    // 再加一圈几乎不透明的描边，六个棱在各角度都清清楚楚。
    private static final int CUBE_FILL_ALPHA = 0x8C;
    private static final int CUBE_STROKE_ALPHA = 0xE6;
    private static final float CUBE_STROKE_WIDTH = 2.5F;
    /** 立方体比方块本体稍微放大一点，免得六个面和方块面重合导致闪烁。 */
    private static final float CUBE_INFLATE = 0.005F;

    /** 本规则在总控里的名字。 */
    private static final String RULE = "sculkRangeVisualizer";

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static int tickCounter;

    private SculkVisualizer() {
    }

    /** 客户端入口调用一次：tick 扫描与渲染回调（Ctrl+O 总开关在 {@link VisualizerState}）。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SculkVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(SculkVisualizer::renderGizmos);
    }

    private static void clientTick(Minecraft client) {
        if (!VisualizerState.isActive(RULE)) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        Player player = client.player;
        Level level = client.level;
        if (player == null || !(level instanceof ClientLevel)) {
            snapshot = Snapshot.EMPTY;
            return;
        }
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot = scan((ClientLevel) level, player.blockPosition());
    }

    private static Snapshot scan(ClientLevel level, BlockPos center) {
        List<Node> nodes = new ArrayList<>();
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                for (Object value : chunk.getBlockEntities().values()) {
                    if (!(value instanceof BlockEntity)) {
                        continue;
                    }
                    BlockEntity blockEntity = (BlockEntity) value;
                    boolean shrieker;
                    boolean canSummon = false;
                    if (blockEntity instanceof SculkShriekerBlockEntity) {
                        shrieker = true;
                        // 原版把“能否召唤坚守者”同步在方块状态里，客户端可直接读取。
                        BlockState state = blockEntity.getBlockState();
                        canSummon = state.hasProperty(SculkShriekerBlock.CAN_SUMMON)
                                && state.getValue(SculkShriekerBlock.CAN_SUMMON);
                    } else if (blockEntity instanceof SculkSensorBlockEntity) {
                        shrieker = false;
                    } else {
                        continue;
                    }
                    BlockPos pos = blockEntity.getBlockPos();
                    if (center.distSqr(pos) > MAX_DISTANCE_SQR) {
                        continue;
                    }
                    nodes.add(new Node(pos.immutable(), shrieker, canSummon));
                }
            }
        }
        return buildSnapshot(nodes, level);
    }

    private static Snapshot buildSnapshot(List<Node> nodes, Level level) {
        int count = nodes.size();
        if (count == 0) {
            return Snapshot.EMPTY;
        }
        // 1) 两两距离在检测半径内的节点视为“可传递”，连一条边；
        //    顺带记下这条线自己是否被羊毛挡住（阻挡只看这一条线）。
        List<int[]> edges = new ArrayList<>();
        List<List<int[]>> adjacency = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            adjacency.add(new ArrayList<int[]>());
        }
        double rangeSqr = LISTENER_RANGE * LISTENER_RANGE + 0.5D;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                if (nodes.get(i).pos.distSqr(nodes.get(j).pos) <= rangeSqr) {
                    int edgeIndex = edges.size();
                    edges.add(new int[] {i, j});
                    // 邻接表每一项是 {邻居下标, 边下标}，BFS 靠边下标判断能不能走。
                    adjacency.get(i).add(new int[] {j, edgeIndex});
                    adjacency.get(j).add(new int[] {i, edgeIndex});
                }
            }
        }
        boolean[] edgeBlocked = new boolean[edges.size()];
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            edgeBlocked[e] = blockedByWool(level, nodes.get(edge[0]).pos, nodes.get(edge[1]).pos);
        }

        // 2) 多源 BFS：每个节点离“能召唤 / 不能召唤”的尖啸体各有多少跳。
        int[] distSpawnable = hopDistances(nodes, adjacency, edgeBlocked, true, false);
        int[] distUnspawnable = hopDistances(nodes, adjacency, edgeBlocked, false, false);
        // 3) 同样从尖啸体出发，但只走没被羊毛挡住的边：走不到说明通往该级别尖啸体的
        //    路都被挡住了，这一侧的渲染（球 / 框）才转成黄 / 灰。
        int[] openSpawnable = hopDistances(nodes, adjacency, edgeBlocked, true, true);
        int[] openUnspawnable = hopDistances(nodes, adjacency, edgeBlocked, false, true);

        // 4) 节点级别：能生成坚守者的一侧优先级更高。
        ChainLevel[] levels = new ChainLevel[count];
        boolean[] blocked = new boolean[count];
        for (int i = 0; i < count; i++) {
            if (distSpawnable[i] != UNREACHABLE) {
                levels[i] = ChainLevel.SPAWNABLE;
                blocked[i] = openSpawnable[i] == UNREACHABLE;
            } else if (distUnspawnable[i] != UNREACHABLE) {
                levels[i] = ChainLevel.UNSPAWNABLE;
                blocked[i] = openUnspawnable[i] == UNREACHABLE;
            } else {
                levels[i] = ChainLevel.SENSOR_ONLY;
            }
        }

        // 4.5) 尖啸体自己的立方体看它自己：能不能召唤决定白 / 红。
        //      只有从它出发的每一条连线都被羊毛挡住（整条链被完全切断）时才转黄，
        //      否则一个能召唤的尖啸体只要有一条通向感测体的明路，就该老老实实是红色——
        //      之前按“任意一条连线被挡”算，会把它错判成黄色。
        boolean[] hasEdge = new boolean[count];
        boolean[] hasOpenEdge = new boolean[count];
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            hasEdge[edge[0]] = true;
            hasEdge[edge[1]] = true;
            if (!edgeBlocked[e]) {
                hasOpenEdge[edge[0]] = true;
                hasOpenEdge[edge[1]] = true;
            }
        }

        List<Sphere> spheres = new ArrayList<>();
        List<Marked> blocks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Node node = nodes.get(i);
            boolean boxBlocked = node.shrieker ? (hasEdge[i] && !hasOpenEdge[i]) : blocked[i];
            blocks.add(new Marked(node.pos, boxColor(node, levels[i], boxBlocked)));
            if (!node.shrieker) {
                spheres.add(new Sphere(Vec3.atCenterOf(node.pos), ballColor(levels[i], blocked[i])));
            }
        }

        // 5) 连线：按“离哪一侧的尖啸体更近”各自取色；平手时能生成的一侧优先。
        //    这样同一个感测体同时接上两类尖啸体时，球走能生成的一侧（优先级），
        //    但接到不能生成的那条尖啸体的线仍用它的白 / 灰。
        List<Segment> segments = new ArrayList<>();
        for (int e = 0; e < edges.size(); e++) {
            int[] edge = edges.get(e);
            int dSpawnable = Math.min(distSpawnable[edge[0]], distSpawnable[edge[1]]);
            int dUnspawnable = Math.min(distUnspawnable[edge[0]], distUnspawnable[edge[1]]);
            ChainLevel edgeLevel;
            if (dSpawnable != UNREACHABLE && dSpawnable <= dUnspawnable) {
                edgeLevel = ChainLevel.SPAWNABLE;
            } else if (dUnspawnable != UNREACHABLE) {
                edgeLevel = ChainLevel.UNSPAWNABLE;
            } else {
                edgeLevel = ChainLevel.SENSOR_ONLY;
            }
            segments.add(new Segment(Vec3.atCenterOf(nodes.get(edge[0]).pos),
                    Vec3.atCenterOf(nodes.get(edge[1]).pos),
                    withAlpha(lineColor(edgeLevel, edgeBlocked[e]), LINE_ALPHA)));
        }
        return new Snapshot(spheres, segments, blocks);
    }

    /** 把满不透明的基础色换成指定 alpha 的 ARGB。 */
    private static int withAlpha(int opaqueColor, int alpha) {
        return (opaqueColor & 0x00FFFFFF) | (alpha << 24);
    }

    /** 连线颜色：被羊毛挡住时，通往能生成 / 不能生成的尖啸体分别转黄 / 灰。 */
    private static int lineColor(ChainLevel level, boolean blocked) {
        switch (level) {
            case SPAWNABLE:
                return blocked ? COLOR_BLOCKED : COLOR_SHRIEKER_CHAIN;
            case UNSPAWNABLE:
                return blocked ? COLOR_UNSPAWNABLE_BLOCKED : COLOR_UNSPAWNABLE;
            default:
                return COLOR_SENSOR_CHAIN;
        }
    }

    /** 实心检测球的颜色，与连线同一套状态。 */
    private static int ballColor(ChainLevel level, boolean blocked) {
        switch (level) {
            case SPAWNABLE:
                return blocked ? BALL_YELLOW : BALL_RED;
            case UNSPAWNABLE:
                return blocked ? BALL_GRAY : BALL_WHITE;
            default:
                return BALL_DEFAULT;
        }
    }

    /**
     * 方块立方体的颜色。
     *
     * <p>尖啸体看自己：不能生成坚守者的一律白色（即使它所在的线被羊毛挡住也不变灰），能生成
     * 的一侧用红；只有从它出发的连线被羊毛全部切断时才转黄。</p>
     *
     * <p>感测体（含校频感测体）连到“不能召唤”的尖啸体时，方框固定用纯白，与同一节点检测球的
     * 白色配对（球淡、方框浓，见 {@link #BALL_WHITE} 与 {@link #CUBE_FILL_ALPHA}）；只有整条
     * 通往该尖啸体的路都被羊毛挡住才按既有规则转灰。这一支不改动优先级——同一个感测体只要还能
     * 接到“能召唤”的尖啸体，上面的 {@link ChainLevel#SPAWNABLE} 分支会先生效，仍是红 / 黄。</p>
     */
    private static int boxColor(Node node, ChainLevel level, boolean blocked) {
        if (node.shrieker) {
            if (!node.canSummon) {
                return COLOR_UNSPAWNABLE;
            }
            return blocked ? COLOR_BLOCKED : COLOR_SHRIEKER_CHAIN;
        }
        if (level == ChainLevel.UNSPAWNABLE) {
            return blocked ? COLOR_UNSPAWNABLE_BLOCKED : COLOR_UNSPAWNABLE;
        }
        return lineColor(level, blocked);
    }

    /**
     * 多源 BFS：从所有指定级别的尖啸体出发，返回每个节点的最少跳数，走不到记
     * {@link #UNREACHABLE}。{@code skipBlockedEdges} 为真时只沿“没被羊毛挡住”的边走，
     * 用来判断某个节点到该级别尖啸体的路是不是全被挡住了。
     */
    private static int[] hopDistances(List<Node> nodes, List<List<int[]>> adjacency, boolean[] edgeBlocked,
            boolean spawnable, boolean skipBlockedEdges) {
        int count = nodes.size();
        int[] distances = new int[count];
        Arrays.fill(distances, UNREACHABLE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < count; i++) {
            Node node = nodes.get(i);
            if (node.shrieker && node.canSummon == spawnable) {
                distances[i] = 0;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            List<int[]> neighbours = adjacency.get(current);
            for (int n = 0; n < neighbours.size(); n++) {
                int[] link = neighbours.get(n);
                if (skipBlockedEdges && edgeBlocked[link[1]]) {
                    continue;
                }
                int next = link[0];
                if (distances[next] == UNREACHABLE) {
                    distances[next] = distances[current] + 1;
                    queue.add(next);
                }
            }
        }
        return distances;
    }

    /** 只有羊毛算阻挡：沿两方块中心的连线取样，命中任意一个羊毛方块即视为被挡。 */
    private static boolean blockedByWool(Level level, BlockPos from, BlockPos to) {
        double ax = from.getX() + 0.5D;
        double ay = from.getY() + 0.5D;
        double az = from.getZ() + 0.5D;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(2, (int) Math.ceil(distance * 3.0D));
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            BlockPos pos = BlockPos.containing(ax + dx * t, ay + dy * t, az + dz * t);
            if (level.getBlockState(pos).is(BlockTags.WOOL)) {
                return true;
            }
        }
        return false;
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.isEmpty()) {
            return;
        }
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            for (int i = 0; i < current.spheres.size(); i++) {
                drawBall(current.spheres.get(i), camera);
            }
            for (int i = 0; i < current.blocks.size(); i++) {
                Marked marked = current.blocks.get(i);
                // 节点方块画成半透明实心立方体：填色取该状态的线色（比球浓一档），
                // 外面再套一圈几乎不透明的描边，棱角在任何角度都清晰。
                Gizmos.cuboid(marked.pos, CUBE_INFLATE, GizmoStyle.strokeAndFill(
                        withAlpha(marked.color, CUBE_STROKE_ALPHA), CUBE_STROKE_WIDTH,
                        withAlpha(marked.color, CUBE_FILL_ALPHA)));
            }
            // 连线最后提交：同属半透明组的面片一定先画完，线永远叠在最上面。
            for (int i = 0; i < current.segments.size(); i++) {
                Segment segment = current.segments.get(i);
                Gizmos.line(segment.from, segment.to, segment.color, LINE_WIDTH);
            }
        }
    }

    /**
     * 画一个实心半透明检测球：把预先算好的球壳面片按球心平移后提交。
     *
     * <p>这里按镜头做单层化处理：相机在球外时只画<b>正对镜头的那半边</b>面片，相机在球内时
     * 画全部面片（此时每条视线本来也只打到球壳远侧一次）。无论哪种情况，一条视线最多只穿过
     * 一层半透明面片，颜色就固定下来，不会随视角来回叠色、发飘。</p>
     */
    private static void drawBall(Sphere sphere, Vec3 camera) {
        GizmoStyle style = GizmoStyle.fill(sphere.color);
        boolean inside = camera.distanceToSqr(sphere.center) <= LISTENER_RANGE * LISTENER_RANGE;
        for (int band = 0; band < SPHERE_LATITUDES; band++) {
            Vec3[] lower = SPHERE_RINGS[band];
            Vec3[] upper = SPHERE_RINGS[band + 1];
            for (int j = 0; j < SPHERE_LONGITUDES; j++) {
                if (!inside) {
                    Vec3 quadCenter = sphere.center.add(SPHERE_QUAD_CENTERS[band][j]);
                    if (SPHERE_QUAD_NORMALS[band][j].dot(camera.subtract(quadCenter)) <= 0.0D) {
                        continue;
                    }
                }
                int next = (j + 1) % SPHERE_LONGITUDES;
                // 四个角点按“逆时针朝外”的顺序给，正对镜头的一面才画得出来。
                Gizmos.rect(sphere.center.add(lower[j]), sphere.center.add(upper[j]),
                        sphere.center.add(upper[next]), sphere.center.add(lower[next]), style);
            }
        }
    }

    /** 一个节点（或一条连线）归属的级别：能召唤的尖啸体 &gt; 不能召唤的尖啸体 &gt; 纯感测体。 */
    private enum ChainLevel {
        /** 纯感测体链（含孤立感测体）：绿线，检测球保持默认淡蓝。 */
        SENSOR_ONLY,
        /** 能生成坚守者的尖啸体一侧：红线，检测球淡红；被羊毛挡住转黄线、淡黄球。 */
        SPAWNABLE,
        /** 不能生成坚守者的尖啸体一侧：白线，检测球浅白；被羊毛挡住转灰线、浅灰球。 */
        UNSPAWNABLE
    }

    /** 一个节点：感测体，或尖啸体（记录它当时能否生成坚守者）。 */
    private static final class Node {
        private final BlockPos pos;
        private final boolean shrieker;
        private final boolean canSummon;

        private Node(BlockPos pos, boolean shrieker, boolean canSummon) {
            this.pos = pos;
            this.shrieker = shrieker;
            this.canSummon = canSummon;
        }
    }

    /** 一个实心检测球：球心 + 填充色。 */
    private static final class Sphere {
        private final Vec3 center;
        private final int color;

        private Sphere(Vec3 center, int color) {
            this.center = center;
            this.color = color;
        }
    }

    /** 一条传播连线。 */
    private static final class Segment {
        private final Vec3 from;
        private final Vec3 to;
        private final int color;

        private Segment(Vec3 from, Vec3 to, int color) {
            this.from = from;
            this.to = to;
            this.color = color;
        }
    }

    /** 一个高亮方块。 */
    private static final class Marked {
        private final BlockPos pos;
        private final int color;

        private Marked(BlockPos pos, int color) {
            this.pos = pos;
            this.color = color;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Sphere>emptyList(),
                Collections.<Segment>emptyList(), Collections.<Marked>emptyList());

        private final List<Sphere> spheres;
        private final List<Segment> segments;
        private final List<Marked> blocks;

        private Snapshot(List<Sphere> spheres, List<Segment> segments, List<Marked> blocks) {
            this.spheres = spheres;
            this.segments = segments;
            this.blocks = blocks;
        }

        private boolean isEmpty() {
            return this.spheres.isEmpty() && this.segments.isEmpty() && this.blocks.isEmpty();
        }
    }
}
