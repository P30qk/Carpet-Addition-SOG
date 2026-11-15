package cn.blockforge.generated.sogcarpet.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 所有可视化的“颜色 / 选项”统一配置（纯客户端，保存在
 * {@code config/sog_carpet-visualizer-config.txt}）。
 *
 * <p>每条可视化在 {@link #register()} 里注册自己有几个颜色部件、几个布尔选项以及各自的默认值。
 * 总控 Ctrl+V 里每条规则的“配置”按钮会打开 {@link VisualizerConfigScreen}，在里面可以改这些
 * 颜色（HSV 调整）与选项。改动即时生效并落盘，每个玩家各自一份。</p>
 *
 * <p>颜色用 ARGB 整数保存；各可视化在每次绘制/更新时把配置值读进自己的颜色字段，于是不用在
 * 每个使用点改代码。</p>
 */
public final class VisualizerConfig {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-visualizer-config.txt");

    /** 一个颜色部件：键、显示名语言键、默认 ARGB。 */
    public record Part(String key, String lang, int defaultArgb) {
    }

    /** 一个布尔选项：键、显示名语言键、默认值。 */
    public record Option(String key, String lang, boolean defaultValue) {
    }

    private static final Map<String, List<Part>> COLORS = new LinkedHashMap<>();
    private static final Map<String, List<Option>> OPTIONS = new LinkedHashMap<>();
    private static final Map<String, Integer> COLOR_VALUES = new HashMap<>();
    private static final Map<String, Boolean> OPTION_VALUES = new HashMap<>();
    private static boolean loaded;

    /** 选项变化时的回调（客户端用它把“怪物生成可视化”的配置同步到服务端）。 */
    public interface OptionChangeListener {
        void onOptionChanged(String rule);
    }

    private static volatile OptionChangeListener optionChangeListener;

    public static void setOptionChangeListener(OptionChangeListener listener) {
        optionChangeListener = listener;
    }

    private VisualizerConfig() {
    }

    // ------------------------------------------------------------------ 注册

    private static void colors(String rule, Part... parts) {
        COLORS.computeIfAbsent(rule, key -> new ArrayList<>()).addAll(List.of(parts));
    }

    private static void defOption(String rule, String key, boolean defaultValue) {
        OPTIONS.computeIfAbsent(rule, key0 -> new ArrayList<>())
                .add(new Option(key, "sog_carpet.option." + rule + "." + key, defaultValue));
    }

    private static Part part(String rule, String key, int argb) {
        return new Part(key, "sog_carpet.color." + rule + "." + key, argb);
    }

    static {
        colors("sculkRangeVisualizer",
                part("sculkRangeVisualizer", "sensorChain", 0xFF3CE04A),
                part("sculkRangeVisualizer", "shriekerChain", 0xFFFF3B30),
                part("sculkRangeVisualizer", "blocked", 0xFFFFE14D),
                part("sculkRangeVisualizer", "unspawnable", 0xFFFFFFFF),
                part("sculkRangeVisualizer", "warden", 0xFF2FD6C8),
                part("sculkRangeVisualizer", "warning", 0xFFFF8A1E),
                part("sculkRangeVisualizer", "trigger", 0xFFFF3AD8),
                part("sculkRangeVisualizer", "ballDefault", 0x6666CCFF));
        colors("creakingHeartVisualizer",
                part("creakingHeartVisualizer", "heart", 0xFFFF3B30),
                part("creakingHeartVisualizer", "heartFill", 0x33FF3B30),
                part("creakingHeartVisualizer", "link", 0xFFFFE14D));
        colors("enderDragonPathVisualizer",
                part("enderDragonPathVisualizer", "trail", 0xF0FF0076),
                part("enderDragonPathVisualizer", "tip", 0xFFFFE14D),
                part("enderDragonPathVisualizer", "nodeInner", 0xFFFF0076),
                part("enderDragonPathVisualizer", "nodeOuter", 0xFF00FF89));
        colors("witherBreakRangeVisualizer",
                part("witherBreakRangeVisualizer", "rangeFill", 0x64FF0000),
                part("witherBreakRangeVisualizer", "rangeStroke", 0xE6FF6B6B));
        colors("enderDragonBreakRangeVisualizer",
                part("enderDragonBreakRangeVisualizer", "partFill", 0x648965FF),
                part("enderDragonBreakRangeVisualizer", "partStroke", 0xE68965FF));
        colors("mobSpawnVisualizer",
                part("mobSpawnVisualizer", "start", 0xFF35FF6A),
                part("mobSpawnVisualizer", "path", 0xFFE6F2FF),
                part("mobSpawnVisualizer", "arrow", 0xFFFFE24A),
                part("mobSpawnVisualizer", "head", 0xFFFF4136));
        // 怪物生成可视化：Y 轴生成高度柱 + 成功率统计面板（面板可整体开关，每个分类/检查可单独开关）。
        colors("spawnStatsVisualizer",
                part("spawnStatsVisualizer", "heightColumn", 0x5533D6FF),
                part("spawnStatsVisualizer", "heightColumnStroke", 0xCC33D6FF));
        defOption("spawnStatsVisualizer", "heightColumn", true);
        defOption("spawnStatsVisualizer", "statsPanel", true);
        defOption("spawnStatsVisualizer", "statMonster", true);
        defOption("spawnStatsVisualizer", "statCreature", true);
        defOption("spawnStatsVisualizer", "statAmbient", true);
        defOption("spawnStatsVisualizer", "statAxolotls", true);
        defOption("spawnStatsVisualizer", "statUndergroundWaterCreature", true);
        defOption("spawnStatsVisualizer", "statWaterCreature", true);
        defOption("spawnStatsVisualizer", "statWaterAmbient", true);
        defOption("spawnStatsVisualizer", "statMisc", true);
        // 每个检查环节的成功率（到达/通过）。
        defOption("spawnStatsVisualizer", "statCheck0", true);
        defOption("spawnStatsVisualizer", "statCheck1", true);
        defOption("spawnStatsVisualizer", "statCheck2", true);
        defOption("spawnStatsVisualizer", "statCheck3", true);
        defOption("spawnStatsVisualizer", "statCheck4", true);
        defOption("spawnStatsVisualizer", "statCheck5", true);
        // 随机游走路径（已并入“怪物生成可视化”）：真正的随机游走（服务端数据 + 纯客户端模拟）。
        colors("spawnStatsVisualizer",
                part("spawnStatsVisualizer", "roam", 0xCCFF3AD8),
                part("spawnStatsVisualizer", "roamSuccess", 0xFFFFFFFF));
        defOption("spawnStatsVisualizer", "roamPath", true);
        defOption("spawnStatsVisualizer", "simulate", true);
        // 可刷怪方块生成范围可视化：红色半透明长方体 + 中心刷怪方块的黄色线框，按方块类型可分别开关。
        colors("spawnRangeVisualizer",
                part("spawnRangeVisualizer", "rangeFill", 0x40FF3B30),
                part("spawnRangeVisualizer", "rangeStroke", 0xE6FF3B30),
                part("spawnRangeVisualizer", "centerBox", 0xFFFFE14D));
        defOption("spawnRangeVisualizer", "showSpawner", true);
        defOption("spawnRangeVisualizer", "showTrialSpawner", true);
        defOption("spawnRangeVisualizer", "showShrieker", true);
        defOption("spawnRangeVisualizer", "showCreakingHeart", true);
        // 村民功能方块绑定可视化：床（绿）与工作方块（红）的连线 / 方框颜色，以及纯客户端近似开关。
        colors("villagerBindingVisualizer",
                part("villagerBindingVisualizer", "bedLine", 0xFF35D94A),
                part("villagerBindingVisualizer", "bedBox", 0xE635D94A),
                part("villagerBindingVisualizer", "jobLine", 0xFFFF3B30),
                part("villagerBindingVisualizer", "jobBox", 0xE6FF3B30));
        defOption("villagerBindingVisualizer", "clientFallback", true);
        colors("explosionVisualizer",
                part("explosionVisualizer", "destruction", 0xFFFF7A1E),
                part("explosionVisualizer", "center", 0xFFDCFF00),
                part("explosionVisualizer", "ray", 0xFF2300FF),
                part("explosionVisualizer", "safe", 0xA500FF1E),
                part("explosionVisualizer", "exposed", 0xF0FF0000),
                part("explosionVisualizer", "sphere", 0x66B4F0B4));
        colors("itemMergeRangeVisualizer",
                part("itemMergeRangeVisualizer", "fill", 0x64FFFF00),
                part("itemMergeRangeVisualizer", "stroke", 0xE6FFFF00));
        // 横扫攻击范围可视化：目标方框线框/面框 + 攻击者 3 格触及球。
        colors("sweepRangeVisualizer",
                part("sweepRangeVisualizer", "boxStroke", 0xFFFF5050),
                part("sweepRangeVisualizer", "boxFill", 0x40FF5050),
                part("sweepRangeVisualizer", "sphere", 0x40FF5050));
        defOption("sweepRangeVisualizer", "boxStrokeEnabled", true);
        defOption("sweepRangeVisualizer", "boxFillEnabled", true);
        defOption("sweepRangeVisualizer", "sphereEnabled", true);
        // 横扫“总是渲染”模式：不要求持剑/瞄准，自身触及球常显，瞄准到实体时再画其方框。
        defOption("sweepRangeVisualizer", "alwaysRender", false);
        // 掉落物数量标签：名称、数量、描边的颜色。
        colors("dropCountVisualizer",
                part("dropCountVisualizer", "name", 0xFFFFFFFF),
                part("dropCountVisualizer", "count", 0xFF00FFFF),
                part("dropCountVisualizer", "outline", 0xFF000000));
        // 物品留存时长：剩余时长文字、描边；可选择 gt 制式与是否联动掉落物计数。
        colors("itemRetentionVisualizer",
                part("itemRetentionVisualizer", "time", 0xFFFFD24A),
                part("itemRetentionVisualizer", "outline", 0xFF000000));
        defOption("itemRetentionVisualizer", "linkDropCount", true);
        defOption("itemRetentionVisualizer", "gtMode", false);
        // 生命数值变化可视化：受伤色（负号·红）、回血色（加号·绿），以及“对面没装本模组时
        // 按血量变化估算”的开关。
        colors("healthChangeVisualizer",
                part("healthChangeVisualizer", "damage", 0xFFFF3B30),
                part("healthChangeVisualizer", "heal", 0xFF35D94A));
        defOption("healthChangeVisualizer", "clientEstimate", true);
        // 非法方块可视化：每一类方块的描边色（填充由它按比例推出）与光明方块光照数值的颜色，
        // 以及逐类开关、是否用方块自身地图色、是否穿墙可见。
        colors("illegalBlockVisualizer",
                part("illegalBlockVisualizer", "barrier", 0xFFFF4D4D),
                part("illegalBlockVisualizer", "structureVoid", 0xFF9CAF88),
                part("illegalBlockVisualizer", "light", 0xFFFFE082),
                part("illegalBlockVisualizer", "other", 0xFFFFFFFF),
                part("illegalBlockVisualizer", "lightText", 0xFFFFF6C8));
        defOption("illegalBlockVisualizer", "showBarrier", true);
        defOption("illegalBlockVisualizer", "showStructureVoid", true);
        defOption("illegalBlockVisualizer", "showLight", true);
        defOption("illegalBlockVisualizer", "showOther", true);
        defOption("illegalBlockVisualizer", "lightLevelText", true);
        defOption("illegalBlockVisualizer", "blockColor", true);
        defOption("illegalBlockVisualizer", "alwaysOnTop", false);
        // 物品流向可视化：标记颜色 + 路径记录间隔（0 = 不记录路径）。
        colors("itemFlowTracker",
                part("itemFlowTracker", "mark", 0xFFFFFFFF));
        defOption("itemFlowTracker", "trackPath", true);
        colors("projectileTrajectoryVisualizer",
                part("projectileTrajectoryVisualizer", "start", 0xFF5CFF9D),
                part("projectileTrajectoryVisualizer", "path", 0xFF43D6FF),
                part("projectileTrajectoryVisualizer", "head", 0xFFFFC83D),
                part("projectileTrajectoryVisualizer", "preview", 0xB0B47BFF),
                part("projectileTrajectoryVisualizer", "previewImpact", 0xFFFF6BD5));
        // 轨迹预览：未投之前（手持投掷物）、飞行中未走到的那段、悬停远传珍珠的落点。
        defOption("projectileTrajectoryVisualizer", "previewHeld", true);
        defOption("projectileTrajectoryVisualizer", "previewFlight", true);
        defOption("projectileTrajectoryVisualizer", "previewHovered", true);
        defOption("projectileTrajectoryVisualizer", "previewDashed", true);
        defOption("projectileTrajectoryVisualizer", "previewText", true);
        colors("motionVisualizer",
                part("motionVisualizer", "x", 0xFFFF5555),
                part("motionVisualizer", "y", 0xFF55FF55),
                part("motionVisualizer", "z", 0xFF5599FF),
                part("motionVisualizer", "resultant", 0xFFFFFFFF),
                part("motionVisualizer", "accel", 0xFFFFAA33),
                part("motionVisualizer", "gravity", 0xFF55FF88),
                part("motionVisualizer", "drag", 0xFF33DDDD),
                part("motionVisualizer", "external", 0xFFFF55FF),
                part("motionVisualizer", "axisDim", 0xFF555555),
                part("motionVisualizer", "info", 0xFFB0F0FF));
        colors("aggroVisualizer",
                part("aggroVisualizer", "attack", 0xFFFF3B30),
                part("aggroVisualizer", "question", 0xFFFFD21E));
        colors("aggroRangeVisualizer",
                part("aggroRangeVisualizer", "sphere", 0x55FF3B30));
        // 索敌范围“选择模式”：只显示木剑右键选中的那一个目标。
        defOption("aggroRangeVisualizer", "selectMode", false);
        colors("worldEaterHelper",
                part("worldEaterHelper", "waterFill", 0x66C6E6FF),
                part("worldEaterHelper", "waterRim", 0xCCE6F6FF));
        // 雷击范围可视化：落雷影响范围的填充/描边，避雷针本体的方框色、引雷范围圆环色，
        // 以及“落在该避雷针上时的影响范围”的填充/描边。三组开关分别控制落雷范围、
        // 避雷针本身与引雷圆环。
        colors("lightningRangeVisualizer",
                part("lightningRangeVisualizer", "strikeFill", 0x40FFF27A),
                part("lightningRangeVisualizer", "strikeStroke", 0xE6FFF27A),
                part("lightningRangeVisualizer", "rodBox", 0xFF8CD2FF),
                part("lightningRangeVisualizer", "rodAttract", 0x99A0E8FF),
                part("lightningRangeVisualizer", "rodImpactFill", 0x2E7AD7FF),
                part("lightningRangeVisualizer", "rodImpactStroke", 0xB37AD7FF));
        defOption("lightningRangeVisualizer", "showStrikes", true);
        defOption("lightningRangeVisualizer", "showRods", true);
        defOption("lightningRangeVisualizer", "showAttractRange", true);
    }

    // ------------------------------------------------------------------ 读写

    private static synchronized void ensure() {
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
                String value = line.substring(eq + 1).trim();
                // 键格式：c.<rule>.<part> 或 o.<rule>.<option>
                if (key.startsWith("c.")) {
                    try {
                        COLOR_VALUES.put(key.substring(2), (int) Long.parseLong(value, 16));
                    } catch (NumberFormatException ignored) {
                        // 非法颜色值忽略。
                    }
                } else if (key.startsWith("o.")) {
                    OPTION_VALUES.put(key.substring(2), Boolean.parseBoolean(value));
                }
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
    }

    public static int color(String rule, String part, int defaultArgb) {
        ensure();
        Integer value = COLOR_VALUES.get(rule + "." + part);
        return value == null ? defaultArgb : value;
    }

    public static synchronized void setColor(String rule, String part, int argb) {
        ensure();
        COLOR_VALUES.put(rule + "." + part, argb);
        save();
    }

    public static synchronized void resetColor(String rule, String part) {
        ensure();
        COLOR_VALUES.remove(rule + "." + part);
        save();
    }

    public static boolean option(String rule, String key, boolean defaultValue) {
        ensure();
        Boolean value = OPTION_VALUES.get(rule + "." + key);
        return value == null ? defaultValue : value;
    }

    public static synchronized void setOption(String rule, String key, boolean value) {
        ensure();
        OPTION_VALUES.put(rule + "." + key, value);
        save();
        OptionChangeListener listener = optionChangeListener;
        if (listener != null) {
            listener.onOptionChanged(rule);
        }
    }

    public static List<Part> parts(String rule) {
        return COLORS.getOrDefault(rule, Collections.emptyList());
    }

    public static List<Option> options(String rule) {
        return OPTIONS.getOrDefault(rule, Collections.emptyList());
    }

    // ------------------------------------------------------------------ 颜色工具

    /** #AARRGGBB 字符串。 */
    public static String hex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    /** 调整颜色的 HSV 分量；hue ±1 为一整圈的比例，sat/val ±1 为满量程比例。 */
    public static int adjust(int argb, float hueDelta, float satDelta, float valDelta) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float max = Math.max(r, Math.max(g, b)) / 255.0F;
        float min = Math.min(r, Math.min(g, b)) / 255.0F;
        float value = max;
        float sat = max <= 0.0F ? 0.0F : (max - min) / max;
        float hue = 0.0F;
        float delta = max - min;
        if (delta > 1.0E-6F) {
            if (max == r / 255.0F) {
                hue = ((g - b) / 255.0F) / delta;
            } else if (max == g / 255.0F) {
                hue = 2.0F + ((b - r) / 255.0F) / delta;
            } else {
                hue = 4.0F + ((r - g) / 255.0F) / delta;
            }
            hue /= 6.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
        }
        hue = (hue + hueDelta) % 1.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        sat = Math.max(0.0F, Math.min(1.0F, sat + satDelta));
        value = Math.max(0.0F, Math.min(1.0F, value + valDelta));
        int rgb = hsvToRgb(hue, sat, value);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6.0F);
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        int ri = Math.round(r * 255.0F);
        int gi = Math.round(g * 255.0F);
        int bi = Math.round(b * 255.0F);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("# sog_carpet 可视化颜色/选项（可手工编辑；颜色为 #AARRGGBB）\n");
        for (Map.Entry<String, Integer> entry : COLOR_VALUES.entrySet()) {
            sb.append("c.").append(entry.getKey()).append('=')
                    .append(String.format(Locale.ROOT, "%08X", entry.getValue())).append('\n');
        }
        for (Map.Entry<String, Boolean> entry : OPTION_VALUES.entrySet()) {
            sb.append("o.").append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
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
