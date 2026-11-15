package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.VillagerBindingPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderContext;
import cn.blockforge.generated.sogcarpet.client.render.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import cn.blockforge.generated.sogcarpet.client.gizmo.GizmoStyle;
import cn.blockforge.generated.sogcarpet.client.gizmo.Gizmos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * “村民功能方块绑定可视化”规则的客户端实现。
 *
 * <p>对玩家附近的每个村民：</p>
 * <ul>
 *   <li>用<b>绿线</b>把村民和它绑定的<b>床</b>连起来，并用<b>绿框</b>框住床；</li>
 *   <li>用<b>红线</b>把村民和它绑定的<b>工作方块</b>连起来，并用<b>红框</b>框住工作方块。</li>
 * </ul>
 *
 * <p><b>数据来源</b>：村民的床 / 工作方块绑定只存在于服务端的 {@code Brain} 记忆里，
 * 所以首选服务端快照（{@link VillagerBindingPayload}，见
 * {@code SogVillagerBindingSync}）。连到的服务器没装本模组（纯客户端）时，退回本地近似：
 * 扫玩家附近的床与工作站方块，把每个村民连到最近的床、以及与自己职业匹配的最近工作站，
 * 保证纯客户端也能渲染。</p>
 *
 * <p>线程约定与其它可视化一致：收包线程只入队，tick 线程整理成只读快照整体替换，
 * 渲染线程整体读取。</p>
 */
public final class VillagerBindingVisualizer {
    /** 服务端快照的有效时长（tick）；服务端每 8 tick 发一次，留足冗余。 */
    private static final int SERVER_TTL_TICKS = 60;
    /** 纯客户端近似扫描的间隔（tick）。 */
    private static final int FALLBACK_INTERVAL_TICKS = 40;
    /** 纯客户端近似只处理玩家周围这个距离内的村民。 */
    private static final double FALLBACK_VILLAGER_RANGE = 32.0D;
    /** 纯客户端近似扫描方块的水平/竖直半径。 */
    private static final int FALLBACK_SCAN_HORIZONTAL = 16;
    private static final int FALLBACK_SCAN_VERTICAL = 6;
    /** 纯客户端近似最多处理多少个村民。 */
    private static final int FALLBACK_MAX_VILLAGERS = 16;

    /** 村民中心到连线端点的高度偏移（大致取胸口）。 */
    private static final double LINE_HEIGHT = 0.9D;
    /** 方框往外膨胀一点，避免和方块面重合闪烁。 */
    private static final float BOX_INFLATE = 0.004F;
    private static final float BOX_STROKE_WIDTH = 2.5F;
    private static final float LINE_WIDTH = 3.0F;

    private static final int DEFAULT_BED_LINE = 0xFF35D94A;
    private static final int DEFAULT_BED_BOX = 0xE635D94A;
    private static final int DEFAULT_JOB_LINE = 0xFFFF3B30;
    private static final int DEFAULT_JOB_BOX = 0xE6FF3B30;

    private static final String RULE = "villagerBindingVisualizer";

    /** 收包线程投递的服务端快照。 */
    private static final ConcurrentLinkedQueue<VillagerBindingPayload> PENDING = new ConcurrentLinkedQueue<>();
    /** 村民实体号 → 仍在有效期内的服务端绑定。 */
    private static final Map<Integer, ServerBinding> SERVER = new HashMap<>();

    /** 纯客户端近似结果（只在客户端 tick 线程改动）。 */
    private static final List<Binding> FALLBACK = new ArrayList<>();
    private static int fallbackTimer;

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static ClientLevel lastLevel;

    private VillagerBindingVisualizer() {
    }

    /** 客户端入口调用一次：注册 tick 与渲染回调。 */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(VillagerBindingVisualizer::clientTick);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(VillagerBindingVisualizer::renderGizmos);
    }

    /** 服务端快照接收器；收包线程只入队。 */
    public static void accept(VillagerBindingPayload payload) {
        if (payload != null && !payload.entries().isEmpty()) {
            PENDING.add(payload);
        }
    }

    private static void clientTick(Minecraft client) {
        ClientLevel level = client.level;
        if (!VisualizerState.isActive(RULE) || level == null) {
            reset();
            return;
        }
        if (level != lastLevel) {
            resetTracking();
            lastLevel = level;
        }
        long now = level.getGameTime();
        if (drainServer(now)) {
            snapshot = new Snapshot(buildServerLinks(level));
            return;
        }
        // 没有服务端数据（纯客户端）时退回本地近似。
        updateFallback(client, level);
        snapshot = new Snapshot(FALLBACK);
    }

    private static void reset() {
        resetTracking();
        lastLevel = null;
        snapshot = Snapshot.EMPTY;
    }

    private static void resetTracking() {
        PENDING.clear();
        SERVER.clear();
        FALLBACK.clear();
        fallbackTimer = 0;
    }

    /** 把收包线程送来的快照并入服务端绑定表；有任何服务端数据时返回 true。 */
    private static boolean drainServer(long now) {
        VillagerBindingPayload payload;
        while ((payload = PENDING.poll()) != null) {
            for (VillagerBindingPayload.Entry entry : payload.entries()) {
                SERVER.put(entry.villagerId(),
                        new ServerBinding(entry.bedPos(), entry.jobPos(), now));
            }
        }
        Iterator<Map.Entry<Integer, ServerBinding>> iterator = SERVER.entrySet().iterator();
        while (iterator.hasNext()) {
            ServerBinding binding = iterator.next().getValue();
            if (now - binding.lastSeen > SERVER_TTL_TICKS) {
                iterator.remove();
            }
        }
        return !SERVER.isEmpty();
    }

    /** 服务端绑定 → 世界坐标渲染项。 */
    private static List<Binding> buildServerLinks(ClientLevel level) {
        List<Binding> links = new ArrayList<>();
        for (Map.Entry<Integer, ServerBinding> entry : SERVER.entrySet()) {
            Vec3 anchor = villagerAnchor(level, entry.getKey());
            if (anchor == null) {
                continue;
            }
            ServerBinding binding = entry.getValue();
            if (binding.bed != null || binding.job != null) {
                links.add(new Binding(anchor, binding.bed, binding.job));
            }
        }
        return links;
    }

    /** 用实体号找回村民并取它的连线锚点（胸口）；找不到返回 null。 */
    private static Vec3 villagerAnchor(ClientLevel level, int id) {
        if (!(level.getEntity(id) instanceof Villager villager)) {
            return null;
        }
        return new Vec3(villager.getX(), villager.getY() + LINE_HEIGHT, villager.getZ());
    }

    /**
     * 纯客户端近似：周期性扫玩家附近的床与工作站方块，把每个村民连到最近的床与
     * 与其职业匹配的最近工作站。
     */
    private static void updateFallback(Minecraft client, ClientLevel level) {
        if (!VisualizerConfig.option(RULE, "clientFallback", true)) {
            FALLBACK.clear();
            return;
        }
        if (++fallbackTimer < FALLBACK_INTERVAL_TICKS) {
            return;
        }
        fallbackTimer = 0;
        if (client.player == null) {
            FALLBACK.clear();
            return;
        }
        FALLBACK.clear();

        List<BlockPos> beds = new ArrayList<>();
        Map<Block, List<BlockPos>> workstations = new HashMap<>();
        scanBlocks(client, level, beds, workstations);

        List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
                client.player.getBoundingBox().inflate(FALLBACK_VILLAGER_RANGE));
        int count = 0;
        for (Villager villager : villagers) {
            if (count >= FALLBACK_MAX_VILLAGERS) {
                break;
            }
            count++;
            Vec3 anchor = new Vec3(villager.getX(), villager.getY() + LINE_HEIGHT, villager.getZ());
            BlockPos bed = nearest(anchor, beds);
            BlockPos job = nearestMatching(anchor, workstations, workstationFor(villager));
            if (bed != null || job != null) {
                FALLBACK.add(new Binding(anchor, bed, job));
            }
        }
    }

    private static void scanBlocks(Minecraft client, ClientLevel level, List<BlockPos> beds,
            Map<Block, List<BlockPos>> workstations) {
        BlockPos origin = client.player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -FALLBACK_SCAN_HORIZONTAL; dx <= FALLBACK_SCAN_HORIZONTAL; dx++) {
            for (int dz = -FALLBACK_SCAN_HORIZONTAL; dz <= FALLBACK_SCAN_HORIZONTAL; dz++) {
                for (int dy = -FALLBACK_SCAN_VERTICAL; dy <= FALLBACK_SCAN_VERTICAL; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(BlockTags.BEDS)) {
                        beds.add(cursor.immutable());
                        continue;
                    }
                    Block block = state.getBlock();
                    if (isWorkstation(block)) {
                        workstations.computeIfAbsent(block, key -> new ArrayList<>())
                                .add(cursor.immutable());
                    }
                }
            }
        }
    }

    /** 离锚点最近的方块（限 {@link #FALLBACK_VILLAGER_RANGE} 格以内）；没有返回 null。 */
    private static BlockPos nearest(Vec3 anchor, List<BlockPos> candidates) {
        BlockPos best = null;
        double bestDistance = FALLBACK_VILLAGER_RANGE * FALLBACK_VILLAGER_RANGE;
        for (BlockPos candidate : candidates) {
            double distance = anchor.distanceToSqr(Vec3.atCenterOf(candidate));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 离锚点最近的、属于指定工作站的方块；没有返回 null。 */
    private static BlockPos nearestMatching(Vec3 anchor, Map<Block, List<BlockPos>> workstations,
            Block block) {
        if (block == null) {
            return null;
        }
        List<BlockPos> candidates = workstations.get(block);
        return candidates == null ? null : nearest(anchor, candidates);
    }

    /** 村民职业对应的原版工作站方块；无业 / 傻子返回 null。 */
    private static Block workstationFor(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        if (profession.is(VillagerProfession.ARMORER)) {
            return Blocks.BLAST_FURNACE;
        }
        if (profession.is(VillagerProfession.BUTCHER)) {
            return Blocks.SMOKER;
        }
        if (profession.is(VillagerProfession.CARTOGRAPHER)) {
            return Blocks.CARTOGRAPHY_TABLE;
        }
        if (profession.is(VillagerProfession.CLERIC)) {
            return Blocks.BREWING_STAND;
        }
        if (profession.is(VillagerProfession.FARMER)) {
            return Blocks.COMPOSTER;
        }
        if (profession.is(VillagerProfession.FISHERMAN)) {
            return Blocks.BARREL;
        }
        if (profession.is(VillagerProfession.FLETCHER)) {
            return Blocks.FLETCHING_TABLE;
        }
        if (profession.is(VillagerProfession.LEATHERWORKER)) {
            return Blocks.CAULDRON;
        }
        if (profession.is(VillagerProfession.LIBRARIAN)) {
            return Blocks.LECTERN;
        }
        if (profession.is(VillagerProfession.MASON)) {
            return Blocks.STONECUTTER;
        }
        if (profession.is(VillagerProfession.SHEPHERD)) {
            return Blocks.LOOM;
        }
        if (profession.is(VillagerProfession.TOOLSMITH)) {
            return Blocks.SMITHING_TABLE;
        }
        if (profession.is(VillagerProfession.WEAPONSMITH)) {
            return Blocks.GRINDSTONE;
        }
        return null;
    }

    /** 这个方块是不是原版的工作站（POI 工作站点）方块。 */
    private static boolean isWorkstation(Block block) {
        return block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.BREWING_STAND
                || block == Blocks.COMPOSTER
                || block == Blocks.BARREL
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.CAULDRON
                || block == Blocks.LECTERN
                || block == Blocks.STONECUTTER
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.GRINDSTONE;
    }

    private static void renderGizmos(WorldRenderContext context) {
        if (!VisualizerState.isActive(RULE)) {
            return;
        }
        Snapshot current = snapshot;
        if (current.links.isEmpty()) {
            return;
        }
        int bedLine = VisualizerConfig.color(RULE, "bedLine", DEFAULT_BED_LINE);
        int bedBox = VisualizerConfig.color(RULE, "bedBox", DEFAULT_BED_BOX);
        int jobLine = VisualizerConfig.color(RULE, "jobLine", DEFAULT_JOB_LINE);
        int jobBox = VisualizerConfig.color(RULE, "jobBox", DEFAULT_JOB_BOX);
        try (Gizmos.TemporaryCollection collection = Gizmos.begin(context)) {
            GizmoStyle bedStyle = GizmoStyle.stroke(bedBox, BOX_STROKE_WIDTH);
            GizmoStyle jobStyle = GizmoStyle.stroke(jobBox, BOX_STROKE_WIDTH);
            for (int i = 0; i < current.links.size(); i++) {
                Binding link = current.links.get(i);
                if (link.bed != null) {
                    Gizmos.cuboid(link.bed, BOX_INFLATE, bedStyle);
                    Gizmos.line(link.anchor, Vec3.atCenterOf(link.bed), bedLine, LINE_WIDTH);
                }
                if (link.job != null) {
                    Gizmos.cuboid(link.job, BOX_INFLATE, jobStyle);
                    Gizmos.line(link.anchor, Vec3.atCenterOf(link.job), jobLine, LINE_WIDTH);
                }
            }
        }
    }

    /** 服务端绑定：床 / 工作方块坐标 + 最近一次收到它的 tick。 */
    private static final class ServerBinding {
        private final BlockPos bed;
        private final BlockPos job;
        private final long lastSeen;

        private ServerBinding(BlockPos bed, BlockPos job, long lastSeen) {
            this.bed = bed;
            this.job = job;
            this.lastSeen = lastSeen;
        }
    }

    /** 一个待渲染的村民绑定：连线锚点 + 可选的床 / 工作方块。 */
    private static final class Binding {
        private final Vec3 anchor;
        private final BlockPos bed;
        private final BlockPos job;

        private Binding(Vec3 anchor, BlockPos bed, BlockPos job) {
            this.anchor = anchor;
            this.bed = bed;
            this.job = job;
        }
    }

    /** 只读渲染快照，tick 线程整体替换、渲染线程整体读取。 */
    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(Collections.<Binding>emptyList());

        private final List<Binding> links;

        private Snapshot(List<Binding> links) {
            this.links = links;
        }
    }
}
