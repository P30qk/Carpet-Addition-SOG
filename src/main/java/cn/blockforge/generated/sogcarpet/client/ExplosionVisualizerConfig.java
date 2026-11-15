package cn.blockforge.generated.sogcarpet.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * “爆炸可视化”的显示项配置（纯客户端，保存在 {@code config/sog_carpet-explosion.txt}）。
 *
 * <p>选项与 lucidity 的 ExplosionVisualizerConfigs 对齐：方块破坏、方块射线投射、爆炸中心、
 * 实体采样点、采样点射线投射、爆炸计时器；另外本模组自己多加了一个“渲染圆球”开关
 * （lucidity 没有那个浅绿色范围球）。这些开关只影响本客户端怎么画，不影响服务端规则，
 * 所以不走逐玩家同步，直接落本地文件，每个玩家各自一份。</p>
 */
public final class ExplosionVisualizerConfig {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-explosion.txt");

    /** 会被炸掉的方块（橙色半透明）。 */
    private static boolean blockDestruction = true;
    /** 爆炸射线上的采样点（蓝色小点）。 */
    private static boolean blockRayCast = true;
    /** 被波及实体身上的伤害采样点（绿/红）。 */
    private static boolean entitySamplePoints = true;
    /** 采样点到爆心/命中点的射线。 */
    private static boolean samplePointRayCast = true;
    /** 爆炸中心点。 */
    private static boolean explosionCenter = true;
    /** 爆炸计时器（TNT/苦力怕引信倒计时文字）。 */
    private static boolean explodeTimer = true;
    /** 爆炸范围最外围的浅绿色半透明圆球（本模组新增）。 */
    private static boolean sphere = true;

    private static boolean loaded;

    private ExplosionVisualizerConfig() {
    }

    public static void ensure() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                boolean value = Boolean.parseBoolean(line.substring(eq + 1).trim());
                switch (key) {
                    case "blockDestruction" -> blockDestruction = value;
                    case "blockRayCast" -> blockRayCast = value;
                    case "entitySamplePoints" -> entitySamplePoints = value;
                    case "samplePointRayCast" -> samplePointRayCast = value;
                    case "explosionCenter" -> explosionCenter = value;
                    case "explodeTimer" -> explodeTimer = value;
                    case "sphere" -> sphere = value;
                    default -> {
                    }
                }
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
    }

    public static boolean blockDestruction() {
        ensure();
        return blockDestruction;
    }

    public static void setBlockDestruction(boolean value) {
        blockDestruction = value;
        save();
    }

    public static boolean blockRayCast() {
        ensure();
        return blockRayCast;
    }

    public static void setBlockRayCast(boolean value) {
        blockRayCast = value;
        save();
    }

    public static boolean entitySamplePoints() {
        ensure();
        return entitySamplePoints;
    }

    public static void setEntitySamplePoints(boolean value) {
        entitySamplePoints = value;
        save();
    }

    public static boolean samplePointRayCast() {
        ensure();
        return samplePointRayCast;
    }

    public static void setSamplePointRayCast(boolean value) {
        samplePointRayCast = value;
        save();
    }

    public static boolean explosionCenter() {
        ensure();
        return explosionCenter;
    }

    public static void setExplosionCenter(boolean value) {
        explosionCenter = value;
        save();
    }

    public static boolean explodeTimer() {
        ensure();
        return explodeTimer;
    }

    public static void setExplodeTimer(boolean value) {
        explodeTimer = value;
        save();
    }

    public static boolean sphere() {
        ensure();
        return sphere;
    }

    public static void setSphere(boolean value) {
        sphere = value;
        save();
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("# sog_carpet 爆炸可视化显示项（可手工编辑）\n");
        sb.append("blockDestruction=").append(blockDestruction).append('\n');
        sb.append("blockRayCast=").append(blockRayCast).append('\n');
        sb.append("entitySamplePoints=").append(entitySamplePoints).append('\n');
        sb.append("samplePointRayCast=").append(samplePointRayCast).append('\n');
        sb.append("explosionCenter=").append(explosionCenter).append('\n');
        sb.append("explodeTimer=").append(explodeTimer).append('\n');
        sb.append("sphere=").append(sphere).append('\n');
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 落盘失败只影响下次启动。
        }
    }
}
