package cn.blockforge.generated.sogcarpet;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /sogcarpet visualizer} 命令：在服务端配置<b>每个玩家各自</b>的可视化渲染清单。
 *
 * <p>存在的意义是照顾“服务端装了模组、玩家客户端没装”的情况：这种玩家打不开 Ctrl+V
 * 界面，但有管理员权限的人（或玩家自己）可以用命令改清单，服务端存下来，等玩家哪天装了
 * 客户端模组再加入就会按这份清单渲染。每个玩家一份，互不影响。</p>
 *
 * <pre>
 * /sogcarpet visualizer list [玩家]
 * /sogcarpet visualizer set &lt;规则&gt; &lt;on|off&gt; [玩家]
 * /sogcarpet visualizer reset [玩家]
 * </pre>
 *
 * <p>不带玩家参数时作用于自己，任何玩家都能改自己的清单；带玩家参数改别人需要
 * OP2（OP2）。</p>
 */
public final class SogVisualizerCommand {
    private SogVisualizerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sogcarpet")
                .then(Commands.literal("visualizer")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx, self(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("rule", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                SogSettings.VISUALIZER_RULES, builder))
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> set(ctx, self(ctx),
                                                        StringArgumentType.getString(ctx, "rule"),
                                                        BoolArgumentType.getBool(ctx, "value")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> set(ctx,
                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "rule"),
                                                                BoolArgumentType.getBool(ctx, "value")))))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx, self(ctx)))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> reset(ctx, EntityArgument.getPlayer(ctx, "player")))))));
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        Map<String, Boolean> snapshot = SogVisualizerPrefs.snapshot(target.getUUID());
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.visualizer.header",
                target.getName().getString()), false);
        for (Map.Entry<String, Boolean> entry : snapshot.entrySet()) {
            Component state = Component.translatable(entry.getValue()
                    ? "sog_carpet.cmd.state_on" : "sog_carpet.cmd.state_off");
            source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.visualizer.line",
                    entry.getKey(), state), false);
        }
        return snapshot.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String rule,
            boolean value) {
        CommandSourceStack source = ctx.getSource();
        if (!SogSettings.VISUALIZER_RULES.contains(rule)) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.visualizer.unknown_rule", rule));
            return 0;
        }
        if (!mayEditOther(source, target)) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.visualizer.no_permission"));
            return 0;
        }
        SogVisualizerPrefs.set(target.getUUID(), rule, value);
        // 在线的客户端要立刻生效；没装模组的客户端 canSend 会直接跳过。
        SogVisualizerPrefs.sendTo(target);
        Component state = Component.translatable(value
                ? "sog_carpet.cmd.state_on" : "sog_carpet.cmd.state_off");
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.visualizer.set",
                target.getName().getString(), rule, state), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        if (!mayEditOther(source, target)) {
            source.sendFailure(Component.translatable("sog_carpet.cmd.visualizer.no_permission"));
            return 0;
        }
        SogVisualizerPrefs.reset(target.getUUID());
        SogVisualizerPrefs.sendTo(target);
        source.sendSuccess(() -> Component.translatable("sog_carpet.cmd.visualizer.reset",
                target.getName().getString()), true);
        return 1;
    }

    /** 改别人需要 OP2；改自己不需要。 */
    private static boolean mayEditOther(CommandSourceStack source, ServerPlayer target) {
        return target.getUUID().equals(source.getPlayer() == null ? null : source.getPlayer().getUUID())
                || source.hasPermission(2);
    }
}
