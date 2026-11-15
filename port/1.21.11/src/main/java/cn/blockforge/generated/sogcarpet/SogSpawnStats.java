package cn.blockforge.generated.sogcarpet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.MobCategory;

/**
 * “刷怪成功概率/统计”的服务端计数器。
 *
 * <p>原版自然刷怪的六参 {@code NaturalSpawner#spawnCategoryForPosition} 会对每个候选位置先做
 * 一次“能否在此刷这种生物”的校验（{@code isValidSpawnPostitionForType}），校验通过且后续
 * 步骤都成功才会真正把生物放进世界。把</p>
 * <ul>
 *   <li><b>尝试次数</b>计为“进入校验的次数”（每个候选位置一次，按 {@link MobCategory} 分类）；</li>
 *   <li><b>成功次数</b>计为“生物真正刷出”的次数（{@code AfterSpawnCallback} 触发时）。</li>
 * </ul>
 * <p>于是“刷怪成功概率”＝成功 ÷ 尝试，正好是“一个通过距离筛选的候选位置最终刷出生物的概率”。
 * 只统计自然刷怪，刷怪蛋、指令、结构刷怪笼都不经过这里。</p>
 *
 * <p>没有装了本模组、且明确要看这条可视化的玩家在线时（{@link #isRecording()} 为假），
 * 连计数都不做，专用服务器上不会为一个没人看的开关白跑。计数只在服务端主线程发生，
 * 下发快照时整份读出。</p>
 */
public final class SogSpawnStats {
    /** 分类 → [尝试次数, 成功次数]。 */
    private static final Map<MobCategory, long[]> DATA = new ConcurrentHashMap<>();
    private static volatile boolean recording;

    /** 刷怪流程里的检查环节（顺序即原版执行顺序）。key 用于配置开关。 */
    public static final String[] CHECK_KEYS = {
            "redstone", "distance", "mobExists", "position", "spawnPotential", "obstruction"};
    /** 每个检查环节的显示名。 */
    private static final String[] CHECK_NAMES = {
            "排除红石导体", "距离过近", "生物存在", "位置", "生成势", "距离过远/障碍"};
    /** 每个检查环节的 [到达次数, 通过次数]。服务端主线程读写。 */
    private static final long[][] CHECKS = new long[CHECK_KEYS.length][2];

    private SogSpawnStats() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static void setRecording(boolean value) {
        recording = value;
    }

    /** 记录一次候选位置校验尝试。 */
    public static void attempt(MobCategory category) {
        if (category == null) {
            return;
        }
        DATA.computeIfAbsent(category, key -> new long[2])[0]++;
    }

    /** 记录一次真正刷出生物。 */
    public static void success(MobCategory category) {
        if (category == null) {
            return;
        }
        DATA.computeIfAbsent(category, key -> new long[2])[1]++;
    }

    /** 进入某个检查环节。 */
    public static void checkReached(int index) {
        if (index >= 0 && index < CHECKS.length) {
            CHECKS[index][0]++;
        }
    }

    /** 通过某个检查环节。 */
    public static void checkPassed(int index) {
        if (index >= 0 && index < CHECKS.length) {
            CHECKS[index][1]++;
        }
    }

    /** 取出当前统计快照（分类名 + 尝试 + 成功），按尝试次数从多到少排序。 */
    public static List<CategoryStat> snapshot() {
        List<CategoryStat> list = new ArrayList<>();
        for (Map.Entry<MobCategory, long[]> entry : DATA.entrySet()) {
            long[] counts = entry.getValue();
            list.add(new CategoryStat(entry.getKey().getSerializedName(), counts[0], counts[1]));
        }
        list.sort((a, b) -> Long.compare(b.attempts(), a.attempts()));
        return list;
    }

    /** 每个检查环节的快照（key、显示名、到达、通过）。 */
    public static List<CheckStat> checkSnapshot() {
        List<CheckStat> list = new ArrayList<>(CHECK_KEYS.length);
        for (int i = 0; i < CHECK_KEYS.length; i++) {
            list.add(new CheckStat(CHECK_KEYS[i], CHECK_NAMES[i], CHECKS[i][0], CHECKS[i][1]));
        }
        return list;
    }

    /** 关掉规则时清空累计值。 */
    public static void clear() {
        DATA.clear();
        for (long[] check : CHECKS) {
            check[0] = 0L;
            check[1] = 0L;
        }
    }

    /** 一个刷怪分类的统计。 */
    public record CategoryStat(String category, long attempts, long successes) {
    }

    /** 一个检查环节的统计。 */
    public record CheckStat(String key, String name, long reached, long passed) {
    }
}
