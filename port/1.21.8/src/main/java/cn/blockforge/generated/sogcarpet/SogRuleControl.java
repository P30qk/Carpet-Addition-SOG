package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.RuleSetPayload;
import cn.blockforge.generated.sogcarpet.net.RuleStatePayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 可视化调控面板里的「直接开关规则」：仅 OP 可用，三种状态。
 *
 * <p>面板改规则走的是 <b>carpet 自己的指令通道</b>（{@code /carpet <规则> <值>}、
 * {@code /carpet setDefault ...}、{@code /carpet removeDefault ...}），因此：</p>
 * <ul>
 *   <li>规则值和 CARPET 的持久化完全一致（永久开启写进世界的 {@code carpet.conf}，重启仍在）；</li>
 *   <li>所有观察器（规则同步、刷怪采集开关、物品流向清理……）照常被触发，行为与手敲指令一模一样；</li>
 *   <li>不需要在模组里另造一套规则持久化格式。</li>
 * </ul>
 *
 * <p>权限：发包者必须拥有 {@code COMMANDS_GAMEMASTER}（原 OP2），否则整包丢弃；规则名也必须属于
 * 本模组的可视化清单（{@link SogSettings#VISUALIZER_RULES}，全是布尔规则），不能借这个通道去改
 * 原版 carpet 或别的附属的规则。</p>
 *
 * <p>「是不是永久」只存在于 carpet 配置文件里，所以 {@link #sendTo} 会读一遍
 * {@code <世界目录>/carpet.conf} 再告诉客户端；玩家加入、任意规则变动、面板改完之后都会重发。</p>
 */
public final class SogRuleControl {
    /** carpet 的配置文件：核心 manager 的 identifier 是 {@code carpet}，写在世界目录下。 */
    private static final String CONFIG_FILE = "carpet.conf";

    private SogRuleControl() {
    }

    /** 客户端 C2S：把某条可视化规则切成关闭 / 暂时开启 / 永久开启。 */
    public static void handleSet(RuleSetPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        MinecraftServer server = sender.level().getServer();
        if (server == null || payload == null || payload.rule() == null) {
            return;
        }
        if (!sender.hasPermissions(2)) {
            return;
        }
        String rule = payload.rule();
        if (!SogSettings.VISUALIZER_RULES.contains(rule)) {
            return;
        }
        applyMode(server, rule, payload.mode());
        SogRuleSyncService.broadcast(server);
    }

    /**
     * 把一条可视化规则切到某个模式（已在别处校验完权限与规则名）。
     *
     * <p>三条优先级：模式只决定“要不要写配置”，规则值本身一律先用 {@code /carpet <规则> ...}
     * 设到位，这样观察器立刻触发、客户端马上收到同步。</p>
     */
    public static void applyMode(MinecraftServer server, String rule, int mode) {
        switch (mode) {
            case RuleSetPayload.MODE_TEMPORARY_ON -> {
                // 先清掉持久项，再临时打开：重启后回到默认（关闭）。
                run(server, "carpet removeDefault " + rule);
                run(server, "carpet " + rule + " true");
            }
            case RuleSetPayload.MODE_PERMANENT_ON -> {
                // 先立即生效，再写进配置。
                run(server, "carpet " + rule + " true");
                run(server, "carpet setDefault " + rule + " true");
            }
            default -> {
                // 关闭：当场关掉，并把持久项一起清掉，免得重启后又自己打开。
                run(server, "carpet " + rule + " false");
                run(server, "carpet removeDefault " + rule);
            }
        }
    }

    /** 用控制台身份执行一条 carpet 指令（权限最高，且与玩家所在的维度无关）。 */
    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
    }

    /** 玩家加入时告诉他能不能改规则、以及哪些规则是永久开启的。 */
    public static void sendTo(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, RuleStatePayload.TYPE)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("op=").append(player.hasPermissions(2)
                ? '1' : '0').append(';');
        for (String rule : persistedRules(server)) {
            sb.append("perm:").append(rule).append("=1;");
        }
        ServerPlayNetworking.send(player, new RuleStatePayload(sb.toString()));
    }

    /** 规则或权限可能变了：给全部在线且装了本模组的玩家重发一次状态。 */
    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    /**
     * 从世界的 {@code carpet.conf} 里读出「被显式设成 true」的本模组规则。
     *
     * <p>格式是 carpet 自己写的「规则名 值」一行一条；这里只认本模组的规则名、并且值必须是
     * {@code true}。文件不存在（从没设过持久项）或读不出来时返回空集合，面板就全部显示为
     * 「暂时开启 / 关闭」，不会误报为永久。</p>
     */
    public static Set<String> persistedRules(MinecraftServer server) {
        Set<String> result = new LinkedHashSet<>();
        if (server == null) {
            return result;
        }
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            return result;
        }
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int split = line.indexOf(' ');
                String name = split < 0 ? line : line.substring(0, split).trim();
                String value = split < 0 ? "" : line.substring(split + 1).trim();
                if (SogSettings.VISUALIZER_RULES.contains(name) && "true".equalsIgnoreCase(value)) {
                    result.add(name);
                }
            }
        } catch (IOException ignored) {
            // 读不出来就当作没有持久项，面板照常可用。
        }
        return result;
    }
}
