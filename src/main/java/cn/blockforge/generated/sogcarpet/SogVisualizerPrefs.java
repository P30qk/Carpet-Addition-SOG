package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * “可视化渲染列表”的<b>服务端逐玩家副本</b>。
 *
 * <p>为什么要有服务端副本：渲染本身发生在客户端，但一个玩家可能根本没装本模组
 * （服务端装了就够了）。这种玩家开不了 Ctrl+V 界面，也就没法配置自己的可视化清单；
 * 而同一个服务端上每个玩家的喜好又必须互不影响。于是服务端为每个玩家保存一份
 * “规则名 → 是否渲染”的覆盖表：</p>
 * <ul>
 *   <li><b>没装客户端模组的玩家</b>可以让管理员用
 *       {@code /sogcarpet visualizer set <规则> <on|off> <玩家>} 配置，纯服务端路径，
 *       不需要客户端配合；</li>
 *   <li><b>装了客户端模组的玩家</b>在 Ctrl+V 里改动时，客户端把整份清单通过
 *       {@link cn.blockforge.generated.sogcarpet.net.VisualizerPrefsSetPayload} 发上来，
 *       服务端存下来并在下次加入时发回；</li>
 *   <li>每个玩家一个 {@link UUID}，设置<b>相互独立</b>，谁也不会盖掉谁。</li>
 * </ul>
 *
 * <p>服务端只用这份表做两件事：把清单同步回客户端，以及决定要不要给该玩家继续下发
 * 刷怪游走轨迹（{@link SogSpawnTraceSync}）。没有配置过的玩家一律按“全部渲染开启”
 * 处理，和客户端 {@code VisualizerState} 的默认值保持一致，所以老玩家升级后行为不变。</p>
 *
 * <p>持久化在 {@code config/sog_carpet-visualizers-server.txt}，随服务端启动读取、
 * 每次改动即时落盘。文件是纯文本，一行一个覆盖项：{@code <uuid> <规则>=<0|1>}。</p>
 */
public final class SogVisualizerPrefs {
    /** 服务端逐玩家清单的落盘位置（与服务端实例同级，和 carpet.conf 一样）。 */
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-visualizers-server.txt");

    /** UUID → （规则名 → 显式覆盖值）。没有覆盖项的规则按默认“开”处理。 */
    private static final Map<UUID, Map<String, Boolean>> OVERRIDES = new ConcurrentHashMap<>();

    private static volatile boolean loaded;

    private SogVisualizerPrefs() {
    }

    /** 服务端启动时读一次；读不出来就用空表（全部默认开）。 */
    public static synchronized void load() {
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
                int space = line.indexOf(' ');
                int eq = line.indexOf('=', space + 1);
                if (space <= 0 || eq <= space) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(line.substring(0, space));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String rule = line.substring(space + 1, eq).trim();
                if (!SogSettings.VISUALIZER_RULES.contains(rule)) {
                    continue;
                }
                boolean value = "1".equals(line.substring(eq + 1).trim());
                OVERRIDES.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).put(rule, value);
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
    }

    /** 这个玩家有没有被显式配置过（命令配过或客户端上报过）。 */
    public static boolean isConfigured(UUID player) {
        return OVERRIDES.containsKey(player);
    }

    /**
     * 这个玩家应不应该渲染某条可视化：有显式覆盖用覆盖值，没有则默认开。
     *
     * <p>服务端在 {@link SogSpawnTraceSync} 里用它决定是否继续给该玩家下发轨迹；
     * 客户端也会在加入时收到同一份值。</p>
     */
    public static boolean isRenderEnabled(UUID player, String rule) {
        Map<String, Boolean> map = OVERRIDES.get(player);
        if (map == null) {
            return true;
        }
        Boolean value = map.get(rule);
        return value == null ? true : value;
    }

    /** 覆盖一条规则；只动这一个玩家、这一条规则。 */
    public static synchronized void set(UUID player, String rule, boolean value) {
        if (!SogSettings.VISUALIZER_RULES.contains(rule)) {
            return;
        }
        OVERRIDES.computeIfAbsent(player, key -> new ConcurrentHashMap<>()).put(rule, value);
        save();
    }

    /** 整份替换某个玩家的清单（客户端 Ctrl+V 上报的完整值）。 */
    public static synchronized void applyEncoded(UUID player, String data) {
        Map<String, Boolean> map = new ConcurrentHashMap<>();
        for (String part : splitPairs(data)) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String rule = part.substring(0, eq).trim();
            if (SogSettings.VISUALIZER_RULES.contains(rule)) {
                map.put(rule, "1".equals(part.substring(eq + 1).trim()));
            }
        }
        if (map.isEmpty()) {
            return;
        }
        OVERRIDES.put(player, map);
        save();
    }

    /** 清掉某个玩家的覆盖，回到“全部默认开”。 */
    public static synchronized void reset(UUID player) {
        if (OVERRIDES.remove(player) != null) {
            save();
        }
    }

    /** 把这个玩家的清单编成 {@code 规则名=0/1;} 串，用于同步。 */
    public static String encode(UUID player) {
        StringBuilder sb = new StringBuilder(256);
        for (String rule : SogSettings.VISUALIZER_RULES) {
            sb.append(rule).append('=').append(isRenderEnabled(player, rule) ? '1' : '0').append(';');
        }
        return sb.toString();
    }

    /** 这个玩家当前完整的清单快照，供命令列出。 */
    public static Map<String, Boolean> snapshot(UUID player) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String rule : SogSettings.VISUALIZER_RULES) {
            result.put(rule, isRenderEnabled(player, rule));
        }
        return result;
    }

    /**
     * 把某个玩家的清单发给他本人。
     *
     * <p>{@code configured=false} 表示服务端没有这个玩家的记录，客户端应保留本地文件里的
     * 设置；{@code configured=true} 表示以服务端值为准。没装本模组的客户端由
     * {@link ServerPlayNetworking#canSend} 直接跳过，不会因为拒收自定义负载而掉线。</p>
     */
    public static void sendTo(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, VisualizerPrefsPayload.TYPE)) {
            return;
        }
        UUID uuid = player.getUUID();
        ServerPlayNetworking.send(player,
                new VisualizerPrefsPayload(isConfigured(uuid), encode(uuid)));
    }

    /** 规则或玩家在别处改动后，把最新清单广播给全部在线且装了本模组的玩家。 */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    /** 客户端上报：只覆盖上报者自己的清单，别的玩家不受影响。 */
    public static void handleEncodedFromClient(ServerPlayer sender, String data) {
        applyEncoded(sender.getUUID(), data);
    }

    /** 列表里可以出现的全部规则名，供命令补全。 */
    public static List<String> ruleNames() {
        return SogSettings.VISUALIZER_RULES;
    }

    private static String[] splitPairs(String data) {
        if (data == null || data.isEmpty()) {
            return new String[0];
        }
        return data.split(";");
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# sog_carpet 服务端逐玩家可视化渲染清单（可手工编辑）\n");
        sb.append("# 一行一个覆盖项：<玩家 UUID> <规则名>=<0|1>；没有覆盖项的规则按开处理。\n");
        for (Map.Entry<UUID, Map<String, Boolean>> entry : OVERRIDES.entrySet()) {
            for (Map.Entry<String, Boolean> rule : entry.getValue().entrySet()) {
                sb.append(entry.getKey()).append(' ').append(rule.getKey()).append('=')
                        .append(rule.getValue() ? '1' : '0').append('\n');
            }
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
