package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.SpawnStatsConfigPayload;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * “怪物生成可视化”的统计面板（类似 carpet 的 TPS/mobcaps 统计）。
 *
 * <p>面板写进玩家的 <b>Tab 列表页脚</b>（标准 {@link ClientboundTabListPacket}）：<b>长按 Tab 时
 * 和在线玩家列表一起显示</b>。用的是原版数据包，连没装本模组的“纯客户端”也能看到。</p>
 *
 * <p>面板可配置：客户端在配置界面里开关“统计面板”或某个刷怪分类后，用
 * {@link SpawnStatsConfigPayload} 把清单发上来，服务端按发包者 UUID 保存，只发他勾选的内容。
 * 没发过配置的（含纯客户端）按默认——面板开、每个分类都显示。</p>
 *
 * <p>每 {@value #INTERVAL_TICKS} tick（约一秒）发一次；规则关掉或玩家关掉后补发一次空页脚清掉。</p>
 */
public final class SogSpawnStatsHud {
    /** 刷新间隔（服务端 tick），约一秒一次。 */
    private static final int INTERVAL_TICKS = 20;

    /** 已经收到过我们页脚、需要时负责清掉的玩家。 */
    private static final Set<UUID> SHOWN = ConcurrentHashMap.newKeySet();
    /** 每个玩家的面板配置（key→是否显示）；没配过的按默认 true。 */
    private static final Map<UUID, Map<String, Boolean>> CONFIG = new ConcurrentHashMap<>();

    private static int tickCounter;

    private SogSpawnStatsHud() {
    }

    /** C2S：客户端上报自己的统计面板配置。 */
    public static void handleConfig(SpawnStatsConfigPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        Map<String, Boolean> map = new ConcurrentHashMap<>();
        String data = payload == null ? null : payload.data();
        if (data != null) {
            for (String part : data.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = part.substring(0, eq).trim();
                if (!key.isEmpty()) {
                    map.put(key, "1".equals(part.substring(eq + 1).trim()));
                }
            }
        }
        CONFIG.put(sender.getUUID(), map);
    }

    public static void onEndServerTick(MinecraftServer server) {
        boolean ruleOn = SogSettings.spawnStatsVisualizer;
        if (ruleOn && ++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (ruleOn) {
            pruneOffline(server);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            boolean want = ruleOn
                    && SogVisualizerPrefs.isRenderEnabled(uuid, "spawnStatsVisualizer")
                    && panelEnabled(uuid);
            if (want) {
                Component footer = buildFooter(uuid, SogSpawnStats.snapshot());
                player.connection.send(new ClientboundTabListPacket(Component.empty(), footer));
                SHOWN.add(uuid);
            } else if (SHOWN.remove(uuid)) {
                // 之前显示过，现在不要了：补发一次空页脚把内容清掉。
                player.connection.send(new ClientboundTabListPacket(Component.empty(), Component.empty()));
            }
        }
    }

    private static boolean panelEnabled(UUID player) {
        Map<String, Boolean> map = CONFIG.get(player);
        if (map == null) {
            return true;
        }
        Boolean value = map.get("statsPanel");
        return value == null || value;
    }

    private static boolean categoryEnabled(UUID player, String category) {
        Map<String, Boolean> map = CONFIG.get(player);
        if (map == null) {
            return true;
        }
        Boolean value = map.get(optionKey(category));
        return value == null || value;
    }

    /** 组装页脚：标题 + 勾选了的各分类“成功/尝试 成功率” + 各检查环节成功率 + 合计。 */
    private static Component buildFooter(UUID player, List<SogSpawnStats.CategoryStat> stats) {
        // 纯客户端没有我们的翻译，所以都用固定文本，不写 translatable。
        MutableComponent out = Component.literal("[SOG] 怪物生成统计").withStyle(ChatFormatting.GOLD);
        long totalAttempts = 0L;
        long totalSuccesses = 0L;
        boolean any = false;
        for (SogSpawnStats.CategoryStat stat : stats) {
            totalAttempts += stat.attempts();
            totalSuccesses += stat.successes();
            if (!categoryEnabled(player, stat.category())) {
                continue;
            }
            any = true;
            float rate = rateOf(stat.successes(), stat.attempts());
            out.append("\n").append(Component.literal(categoryName(stat.category()) + ": "
                    + stat.successes() + "/" + stat.attempts() + "  "
                    + percent(stat.successes(), stat.attempts())).withStyle(rateColor(rate)));
        }
        // 刷怪流程里各检查环节的成功率：通过 / 到达。
        List<SogSpawnStats.CheckStat> checks = SogSpawnStats.checkSnapshot();
        for (int i = 0; i < checks.size(); i++) {
            if (!checkEnabled(player, i)) {
                continue;
            }
            SogSpawnStats.CheckStat check = checks.get(i);
            if (check.reached() <= 0L) {
                continue;
            }
            any = true;
            float rate = rateOf(check.passed(), check.reached());
            out.append("\n").append(Component.literal("· " + check.name() + ": "
                    + check.passed() + "/" + check.reached() + "  "
                    + percent(check.passed(), check.reached())).withStyle(rateColor(rate)));
        }
        if (!any) {
            out.append("\n").append(Component.literal("…").withStyle(ChatFormatting.GRAY));
            return out;
        }
        out.append("\n").append(Component.literal("total: " + totalSuccesses + "/" + totalAttempts
                        + "  " + percent(totalSuccesses, totalAttempts))
                .withStyle(ChatFormatting.AQUA));
        return out;
    }

    private static boolean checkEnabled(UUID player, int index) {
        Map<String, Boolean> map = CONFIG.get(player);
        if (map == null) {
            return true;
        }
        Boolean value = map.get("statCheck" + index);
        return value == null || value;
    }

    private static String optionKey(String serialized) {
        return switch (serialized == null ? "" : serialized) {
            case "monster" -> "statMonster";
            case "creature" -> "statCreature";
            case "ambient" -> "statAmbient";
            case "axolotls" -> "statAxolotls";
            case "underground_water_creature" -> "statUndergroundWaterCreature";
            case "water_creature" -> "statWaterCreature";
            case "water_ambient" -> "statWaterAmbient";
            case "misc" -> "statMisc";
            default -> "stat" + serialized;
        };
    }

    private static String categoryName(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return "?";
        }
        return switch (serialized) {
            case "monster" -> "怪物";
            case "creature" -> "动物";
            case "ambient" -> "环境";
            case "axolotls" -> "美西螈";
            case "underground_water_creature" -> "地下水生";
            case "water_creature" -> "水生";
            case "water_ambient" -> "水生环境";
            case "misc" -> "其它";
            default -> serialized;
        };
    }

    private static float rateOf(long successes, long attempts) {
        return attempts <= 0L ? -1.0F : (float) successes / (float) attempts;
    }

    private static String percent(long successes, long attempts) {
        if (attempts <= 0L) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0F * successes / attempts);
    }

    private static ChatFormatting rateColor(float rate) {
        if (rate < 0.0F) {
            return ChatFormatting.GRAY;
        }
        if (rate < 0.34F) {
            return ChatFormatting.RED;
        }
        if (rate < 0.67F) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.GREEN;
    }

    /** 服务器停下时清掉记录。 */
    public static void clear() {
        SHOWN.clear();
        CONFIG.clear();
        tickCounter = 0;
    }

    /** 让“已显示”集合里离线玩家的残留项被清掉（数量大时顺手清理）。 */
    public static void pruneOffline(MinecraftServer server) {
        if (SHOWN.size() > 256) {
            SHOWN.removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
        if (CONFIG.size() > 256) {
            CONFIG.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        }
    }
}
