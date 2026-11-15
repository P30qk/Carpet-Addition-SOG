package cn.blockforge.generated.sogcarpet.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * “敌对生物仇恨范围可视化”的逐生物开关（纯客户端，保存在
 * {@code config/sog_carpet-aggro-range.txt}）。
 *
 * <p>默认全部开启。配置界面（Ctrl+V → 本规则的“配置”）列出所有敌对生物（{@link MobCategory#MONSTER}
 * 类别，含只能靠指令召唤的巨人、幻术师等），外加一个“杀手兔”条目，每一行都能单独开关；
 * 关掉的生物不再画仇恨范围球。改动只影响本客户端绘制，不影响服务端规则，所以不走逐玩家同步，
 * 每个玩家各自一份。</p>
 */
public final class AggroRangeConfig {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-aggro-range.txt");
    /** 杀手兔不是独立的 EntityType，用一个合成键表示。 */
    public static final String KILLER_BUNNY_KEY = "minecraft:rabbit#killer";

    /** 键（实体 ID，或杀手兔合成键）→ 是否绘制；缺省为开。 */
    private static final Map<String, Boolean> VALUES = new LinkedHashMap<>();
    private static boolean loaded;
    private static List<Entry> cache;

    private AggroRangeConfig() {
    }

    /** 配置界面的列表项：键、显示名、当前是否开启。 */
    public record Entry(String key, Component name, boolean enabled) {
    }

    public static synchronized void ensure() {
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
                if (key.isEmpty()) {
                    continue;
                }
                VALUES.put(key, Boolean.parseBoolean(line.substring(eq + 1).trim()));
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
        cache = null;
    }

    /** 这个键对应的生物是否绘制（没有显式配置时默认开）。 */
    public static boolean isEnabled(String key) {
        ensure();
        Boolean value = VALUES.get(key);
        return value == null || value;
    }

    /** 按实体类型判断是否绘制。 */
    public static boolean isEnabled(EntityType<?> type) {
        if (type == null) {
            return true;
        }
        return isEnabled(EntityType.getKey(type).toString());
    }

    public static boolean isKillerBunnyEnabled() {
        return isEnabled(KILLER_BUNNY_KEY);
    }

    public static synchronized void setEnabled(String key, boolean value) {
        ensure();
        VALUES.put(key, value);
        cache = null;
        save();
    }

    /** 全部可选条目：所有 MONSTER 类别生物 + 杀手兔，按显示名排序。 */
    public static synchronized List<Entry> entries() {
        ensure();
        if (cache != null) {
            return cache;
        }
        List<Entry> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() == MobCategory.MONSTER) {
                String key = EntityType.getKey(type).toString();
                list.add(new Entry(key, type.getDescription(), isEnabled(key)));
            }
        }
        list.add(new Entry(KILLER_BUNNY_KEY,
                Component.translatable("sog_carpet.ui.aggro.killer_bunny"), isEnabled(KILLER_BUNNY_KEY)));
        list.sort(Comparator.comparing(entry -> entry.name().getString(),
                String.CASE_INSENSITIVE_ORDER));
        cache = list;
        return list;
    }

    public static synchronized void resetDefaults() {
        ensure();
        VALUES.clear();
        cache = null;
        save();
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# sog_carpet 敌对生物仇恨范围逐生物开关（可手工编辑，缺省为开）\n");
        sb.append("# 一行一个：<实体 ID 或 minecraft:rabbit#killer>=<true|false>\n");
        for (Map.Entry<String, Boolean> entry : VALUES.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
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
