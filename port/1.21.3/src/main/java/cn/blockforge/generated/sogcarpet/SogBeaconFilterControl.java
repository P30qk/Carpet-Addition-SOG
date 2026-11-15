package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.BeaconFilterPayload;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 「自定义信标效果 / 随身信标」黑白名单的管理（Op 专用）。
 *
 * <p>名单本身是 carpet 规则（{@link SogSettings#beaconEffectFilterMode} 决定生效哪一份，
 * {@link SogSettings#beaconEffectBlacklist} 与 {@link SogSettings#beaconEffectWhitelist} 各自独立），
 * 所以这里的每一次修改都走
 * <b>carpet 自己的指令通道</b>：{@code /carpet setDefault <规则> <值>}。好处是</p>
 * <ul>
 *   <li>持久化、{@code carpet.conf} 格式、规则 observer 广播全部沿用 carpet；</li>
 *   <li>改完立刻触发 {@link SogRuleSyncService}，所有装了本模组的客户端拿到新名单，
 *       正开着的选择界面会重新构建；</li>
 *   <li>服务端校验读的是同一份静态字段，界面与服务端不会不一致。</li>
 * </ul>
 *
 * <p>入口有两个：效果选择界面里 Op 右键效果 / 点模式按钮（{@link #handle}），
 * 以及 {@code /sogcarpet beaconfilter} 指令（{@link SogBeaconCommand}）。两者都要求
 * {@code COMMANDS_GAMEMASTER}（原版 OP 2 级）。</p>
 */
public final class SogBeaconFilterControl {
    private SogBeaconFilterControl() {
    }

    /** 客户端 C2S：在效果选择界面里锁定/解锁效果，或切换黑/白名单模式。 */
    public static void handle(BeaconFilterPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player == null || payload == null || payload.action() == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null || !player.hasPermissions(2)) {
            return;
        }
        String action = payload.action().toLowerCase(Locale.ROOT);
        switch (action) {
            case BeaconFilterPayload.ACTION_ADD ->
                    editEntry(server, payload.value(), true);
            case BeaconFilterPayload.ACTION_REMOVE ->
                    editEntry(server, payload.value(), false);
            case BeaconFilterPayload.ACTION_TOGGLE -> toggleEffect(server, payload.value());
            case BeaconFilterPayload.ACTION_MODE -> setMode(server, parseMode(payload.value(), nextMode()));
            default -> {
                // 不认识的动作整包忽略。
            }
        }
    }

    /** 解析请求里的模式名；解析不出来时用 {@code fallback}。 */
    private static SogSettings.BeaconEffectFilterMode parseMode(String value,
            SogSettings.BeaconEffectFilterMode fallback) {
        if (value == null) {
            return fallback;
        }
        String name = value.trim().toUpperCase(Locale.ROOT);
        int sep = name.indexOf('|');
        if (sep >= 0) {
            name = name.substring(0, sep);
        }
        try {
            return SogSettings.BeaconEffectFilterMode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * 处理 {@code add}/{@code remove}：{@code value} 形如 {@code WHITELIST|minecraft:speed}。
     *
     * <p>方向由客户端给出，服务端只做“把 id 加入/移出这一份名单”，不再自己翻转，
     * 于是“右键想移除”永远是移除。旧的 {@code 效果 id} 单字段格式仍按当前模式处理。</p>
     */
    private static void editEntry(MinecraftServer server, String value, boolean member) {
        if (server == null || value == null) {
            return;
        }
        SogSettings.BeaconEffectFilterMode mode = SogBeaconFilter.mode();
        String effectId = value;
        int sep = value.indexOf('|');
        if (sep >= 0) {
            mode = parseMode(value.substring(0, sep), mode);
            effectId = value.substring(sep + 1);
        }
        String canonical = SogBeaconFilter.canonical(effectId);
        if (canonical == null) {
            return;
        }
        // 请求里带了模式：把服务端模式也对齐过去，保证“看到的名单”和“改的名单”始终是同一份。
        if (SogBeaconFilter.mode() != mode) {
            runCarpet(server, "beaconEffectFilterMode", mode.name().toLowerCase(Locale.ROOT));
        }
        applyList(server, mode, SogBeaconFilter.withMember(mode, canonical, member));
        SogRuleSyncService.broadcast(server);
    }

    /** 当前模式的下一个模式（黑名单 ⇄ 白名单）。 */
    public static SogSettings.BeaconEffectFilterMode nextMode() {
        return SogBeaconFilter.mode() == SogSettings.BeaconEffectFilterMode.BLACKLIST
                ? SogSettings.BeaconEffectFilterMode.WHITELIST
                : SogSettings.BeaconEffectFilterMode.BLACKLIST;
    }

    /** 切换模式：只改变“生效哪一份名单”，另一份原样保留。 */
    public static void setMode(MinecraftServer server, SogSettings.BeaconEffectFilterMode mode) {
        if (server == null || mode == null) {
            return;
        }
        // carpet 的枚举规则按「小写选项名」匹配（传 WHITELIST 会被判为非法值），这里统一转小写。
        runCarpet(server, "beaconEffectFilterMode", mode.name().toLowerCase(Locale.ROOT));
        // 切到白名单时如果白名单还是空的，先把原版信标那六种效果放进去：
        // 否则界面上会一个可选效果都没有（校验虽然会回退到原版效果，但空白名单看起来像坏了）。
        // 只动白名单那一份，黑名单原样保留。
        if (mode == SogSettings.BeaconEffectFilterMode.WHITELIST
                && SogBeaconFilter.entriesFor(SogSettings.BeaconEffectFilterMode.WHITELIST).isEmpty()) {
            setList(server, mode, String.join(",", SogBeaconHelper.vanillaBeaconEffectIds()));
        }
        SogRuleSyncService.broadcast(server);
    }

    /** 把某个效果在<b>当前模式</b>对应的那一份名单里加/减一次（旧格式用）。 */
    public static void toggleEffect(MinecraftServer server, String effectId) {
        if (server == null) {
            return;
        }
        String canonical = SogBeaconFilter.canonical(effectId);
        if (canonical == null) {
            return;
        }
        SogSettings.BeaconEffectFilterMode mode = SogBeaconFilter.mode();
        applyList(server, mode, SogBeaconFilter.withToggled(mode, canonical));
        SogRuleSyncService.broadcast(server);
    }

    /** 直接写入<b>当前模式</b>对应的那一份名单（会先规范化：小写、去重、按字典序）。 */
    public static void setList(MinecraftServer server, String list) {
        setList(server, SogBeaconFilter.mode(), list);
    }

    /** 直接写入指定模式那一份名单（会先规范化：小写、去重、按字典序）。 */
    public static void setList(MinecraftServer server, SogSettings.BeaconEffectFilterMode mode,
            String list) {
        if (server == null) {
            return;
        }
        runCarpet(server, SogBeaconFilter.ruleNameFor(mode), SogBeaconFilter.normalize(list));
    }

    /**
     * 写入名单并确认真的写进去了。
     *
     * <p>carpet 的 {@code setDefault} 在值被校验器拒绝时会静默返回 0，命令本身不报错——那样
     * 界面上右键“成功”了、名单却没变。这里写完后回读一次：没生效就直接改静态字段兜底
     * （持久化仍由 carpet 在下一次成功写入时完成），保证运行期行为与界面一致。</p>
     */
    private static void applyList(MinecraftServer server, SogSettings.BeaconEffectFilterMode mode,
            String list) {
        String normalized = SogBeaconFilter.normalize(list);
        runCarpet(server, SogBeaconFilter.ruleNameFor(mode), normalized);
        String current = mode == SogSettings.BeaconEffectFilterMode.WHITELIST
                ? SogSettings.beaconEffectWhitelist
                : SogSettings.beaconEffectBlacklist;
        if (!normalized.equals(SogBeaconFilter.normalize(current))) {
            if (mode == SogSettings.BeaconEffectFilterMode.WHITELIST) {
                SogSettings.beaconEffectWhitelist = normalized;
            } else {
                SogSettings.beaconEffectBlacklist = normalized;
            }
        }
    }

    /**
     * 用控制台身份执行 {@code /carpet setDefault <规则> <值>}（与玩家手敲完全同一条路径）。
     *
     * <p>空值必须写成 {@code ""}：carpet 的规则值参数是 greedyString，直接留空会让整条指令
     * 解析失败（于是“清空名单”看起来成功、实际没清）。其它值只含 id 字符，原样传即可。</p>
     */
    private static void runCarpet(MinecraftServer server, String rule, String value) {
        CommandSourceStack source = server.createCommandSourceStack();
        String argument = value == null || value.isEmpty() ? "\"\"" : value;
        server.getCommands().performPrefixedCommand(source,
                "carpet setDefault " + rule + " " + argument);
    }
}
