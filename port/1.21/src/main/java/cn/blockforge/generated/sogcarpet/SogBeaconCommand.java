package cn.blockforge.generated.sogcarpet;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;

/**
 * {@code /sogcarpet beaconfilter}：Op 管理「自定义信标效果」的黑/白名单。
 *
 * <p>黑白名单<b>各自一份</b>：{@code add/remove/clear} 只改<b>当前模式</b>对应的那一份，
 * 另一份原样保留。名单只影响信标方块里的自定义效果槽位，<b>不影响随身信标</b>。</p>
 *
 * <pre>
 * /sogcarpet beaconfilter list
 * /sogcarpet beaconfilter mode blacklist|whitelist
 * /sogcarpet beaconfilter add &lt;effect&gt;
 * /sogcarpet beaconfilter remove &lt;effect&gt;
 * /sogcarpet beaconfilter clear
 * </pre>
 *
 * <p>这条指令挂在已有的 {@code /sogcarpet} 下面（{@link SogVisualizerCommand#register}），
 * 所以不新增根指令。所有修改都通过 {@link SogBeaconFilterControl} 走 carpet 的
 * {@code /carpet setDefault}，因此随 {@code carpet.conf} 持久化，并且改完立刻同步给所有客户端。</p>
 *
 * <p>权限：{@link Permissions#COMMANDS_GAMEMASTER}（原版 OP 2 级）。普通玩家连这条分支都
 * 看不到，也执行不了；效果选择界面里的右键锁定同样由服务端再校验一次权限。</p>
 *
 * <p>中文/英文文案都在 {@code lang} 里（{@code sog_carpet.cmd.beaconfilter.*}），
 * 效果名用原版的效果显示名，所以模组效果也会显示成它们自己的名字。</p>
 */
public final class SogBeaconCommand {
    private static final String EFFECT_ARG = "effect";
    /** 随身信标的效果列表参数（一个 greedy 参数，指令内部按空白切分）。 */
    private static final String EFFECTS_ARG = "effects";

    private SogBeaconCommand() {
    }

    /**
     * 构建 {@code portablebeacon} 分支（由 {@link SogVisualizerCommand#register} 挂到根上）。
     *
     * <pre>
     * /sogcarpet portablebeacon &lt;效果&gt; [第二个效果]
     * </pre>
     *
     * <p>参数用 {@code greedyString} 整体接收再按空白切开：效果 id 里的冒号
     * （{@code minecraft:speed}）在 Brigadier 的 word()/string() 无引号规则里是非法的，
     * 两个 greedy 参数又不允许，所以在指令内部切分——这样 {@code minecraft:speed}、
     * {@code speed}（自动补 {@code minecraft:}）都能直接写，两个效果用空格隔开。</p>
     *
     * <p>走的是与 Ctrl+右键界面<b>完全相同</b>的一条服务端路径
     * （{@link SogPortableBeacon#apply}）：同样要求规则开着、背包里带着信标、两个效果都通过
     * 黑/白名单。存在的意义有两个：没装客户端模组（纯原版客户端）的玩家也能用随身信标；
     * 服务端测试可以真正跑一遍发放与维持逻辑。</p>
     */
    public static LiteralArgumentBuilder<CommandSourceStack> portable() {
        return Commands.literal("portablebeacon")
                .then(Commands.argument(EFFECTS_ARG, StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                BuiltInRegistries.MOB_EFFECT.keySet().stream()
                                        .map(ResourceLocation::toString),
                                builder))
                        .executes(SogBeaconCommand::portable));
    }

    /** 随身信标：用与网络包相同的一条服务端路径发放效果。 */
    private static int portable(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.portable.player_only"));
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, EFFECTS_ARG).trim();
        String[] parts = raw.isEmpty() ? new String[0] : raw.split("\\s+");
        if (parts.length == 0 || parts.length > 2) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.portable.usage"));
            return 0;
        }
        // off / none / - ：关闭随身信标效果（与界面里的“关闭效果”按钮同一条路径）。
        if (parts.length == 1 && ("off".equalsIgnoreCase(parts[0])
                || "none".equalsIgnoreCase(parts[0]) || "-".equals(parts[0]))) {
            return SogPortableBeacon.apply(player, "", null, true) ? 1 : 0;
        }
        String primary = parts[0];
        String secondary = parts.length > 1 ? parts[1] : null;
        if (!SogPortableBeacon.apply(player, primary, secondary, true)) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.portable.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.portable.applied", raw), false);
        return 1;
    }

    /** 构建 {@code beaconfilter} 分支（由 {@link SogVisualizerCommand#register} 挂到根上）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("beaconfilter")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(SogBeaconCommand::list))
                .then(Commands.literal("mode")
                        .then(Commands.literal("blacklist")
                                .executes(ctx -> setMode(ctx,
                                        SogSettings.BeaconEffectFilterMode.BLACKLIST)))
                        .then(Commands.literal("whitelist")
                                .executes(ctx -> setMode(ctx,
                                        SogSettings.BeaconEffectFilterMode.WHITELIST))))
                .then(Commands.literal("add")
                        // 必须用 greedyString()：带命名空间的效果 id（minecraft:speed）里的冒号
                        // 在 word()/string() 的“无引号字符串”规则里是非法的，会在冒号处报错。
                        .then(Commands.argument(EFFECT_ARG, StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        BuiltInRegistries.MOB_EFFECT.keySet().stream()
                                                .map(ResourceLocation::toString),
                                        builder))
                                .executes(ctx -> add(ctx, true))))
                .then(Commands.literal("remove")
                        // 必须用 greedyString()：带命名空间的效果 id（minecraft:speed）里的冒号
                        // 在 word()/string() 的“无引号字符串”规则里是非法的，会在冒号处报错。
                        .then(Commands.argument(EFFECT_ARG, StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        BuiltInRegistries.MOB_EFFECT.keySet().stream()
                                                .map(ResourceLocation::toString),
                                        builder))
                                .executes(ctx -> add(ctx, false))))
                .then(Commands.literal("clear").executes(SogBeaconCommand::clear));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.header"), false);
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.mode",
                Component.translatable("sog_carpet.cmd.beaconfilter.mode."
                        + SogBeaconFilter.mode().name().toLowerCase(java.util.Locale.ROOT))), false);
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.blacklist",
                SogBeaconFilter.describe(SogBeaconFilter.entriesFor(
                        SogSettings.BeaconEffectFilterMode.BLACKLIST))), false);
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.whitelist",
                SogBeaconFilter.describe(SogBeaconFilter.entriesFor(
                        SogSettings.BeaconEffectFilterMode.WHITELIST))), false);
        return SogBeaconFilter.entries().size();
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx,
            SogSettings.BeaconEffectFilterMode mode) {
        MinecraftServer server = ctx.getSource().getServer();
        SogBeaconFilterControl.setMode(server, mode);
        ctx.getSource().sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.mode_set",
                Component.translatable("sog_carpet.cmd.beaconfilter.mode."
                        + mode.name().toLowerCase(java.util.Locale.ROOT))), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, boolean member) {
        CommandSourceStack source = ctx.getSource();
        String raw = StringArgumentType.getString(ctx, EFFECT_ARG);
        ResourceLocation id = ResourceLocation.tryParse(raw.trim().toLowerCase(java.util.Locale.ROOT));
        Holder<MobEffect> effect = id == null ? null : SogBeaconFilter.holderOf(id);
        if (effect == null) {
            source.sendFailure(Component.translatable(
                    "sog_carpet.cmd.beaconfilter.unknown_effect", raw));
            return 0;
        }
        MinecraftServer server = source.getServer();
        SogSettings.BeaconEffectFilterMode mode = SogBeaconFilter.mode();
        String canonical = SogBeaconFilter.idOf(effect);
        SogBeaconFilterControl.setList(server, mode, SogBeaconFilter.withMember(mode, canonical, member));
        SogRuleSyncService.broadcast(server);
        Component name = effect.value().getDisplayName();
        Component listName = Component.translatable("sog_carpet.cmd.beaconfilter.mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
        source.sendSuccess(() -> Component.translatable(member
                        ? "sog_carpet.cmd.beaconfilter.added"
                        : "sog_carpet.cmd.beaconfilter.removed", name, listName), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        SogBeaconFilterControl.setList(server, "");
        SogRuleSyncService.broadcast(server);
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.beaconfilter.cleared"), true);
        return 1;
    }
}
