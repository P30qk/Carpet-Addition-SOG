package cn.blockforge.generated.sogcarpet;

import carpet.api.settings.CarpetRule;
import carpet.patches.EntityPlayerMPFake;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderSetPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsSetPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 假人渲染设置的服务端入口：校验权限、找到假人、应用并回发最新值。
 */
public final class SogFakeRenderService {
    /** GCA 假人背包菜单类名（不直接依赖 GCA 编译，运行时按名字识别）。 */
    public static final String GCA_MENU_CLASS = "dev.dubhe.gugle.carpet.tools.player.PlayerInventoryMenu";

    private SogFakeRenderService() {
    }

    /**
     * 规则开关的观察器：让“假人渲染设置”的现存假人立刻跟随规则生效或停用。
     *
     * <p>只调整实际生效的视距与票据，不改动每个假人各自保存的渲染设置，因此：开启规则
     * 前就存在的假人保持原值（跟随的此刻才真正抬到服务器视距），改过设置的假人关掉规则
     * 后数值仍在，重新开启即恢复。</p>
     */
    public static void onRuleChanged(CommandSourceStack source, CarpetRule<?> rule, String value) {
        if (!"fakePlayerRenderSettings".equals(rule.name())) {
            return;
        }
        MinecraftServer server = source.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!(player instanceof SogFakeRender settings)) {
                    continue;
                }
                if (SogSettings.fakePlayerRenderSettings) {
                    settings.sog$setRenderDistance(settings.sog$getRenderDistance());
                    settings.sog$setSimulationDistance(settings.sog$getSimulationDistance());
                } else {
                    settings.sog$refreshRenderTickets();
                }
            }
        }
    }

    public static void handleSet(FakeRenderSetPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!SogSettings.fakePlayerRenderSettings) {
            return;
        }
        // 改假人的加载/模拟范围属于管理操作，要求 OP2（与 carpet 假人指令同级）。
        // 26.2 不再有 hasPermissions(int)，权限走 PermissionSet；GAMEMASTER 即原 OP2。
        if (!sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        EntityPlayerMPFake fake = findFake(sender.level().getServer(), payload.fake());
        if (!(fake instanceof SogFakeRender settings)) {
            return;
        }
        settings.sog$setRenderDistance(
                clampOrFollow(payload.render(), SogFakeRender.SOG_MIN_RENDER_DISTANCE,
                        SogFakeRender.SOG_MAX_RENDER_DISTANCE));
        settings.sog$setSimulationDistance(
                clampOrFollow(payload.simulation(), SogFakeRender.SOG_MIN_SIMULATION_DISTANCE,
                        SogFakeRender.SOG_MAX_SIMULATION_DISTANCE));
        // 故意不回发结果：滑条本地已经把值限制在游戏范围内（或 -1 跟随），服务端
        // clamp 不会改变它；回发会触发客户端重建界面，正在拖动的手柄会被
        // clearWidgets 打断。真正的同步发生在下次打开背包时（由菜单构造器下发）。
    }

    public static FakeRenderInfoPayload infoOf(int syncId, ServerPlayer fake, SogFakeRender settings,
            MinecraftServer server) {
        // 视距与模拟距离都挂在玩家列表上（MinecraftServer 自身没有这两个 getter）。
        int defaultRender = server.getPlayerList().getViewDistance();
        int defaultSimulation = server.getPlayerList().getSimulationDistance();
        return new FakeRenderInfoPayload(syncId, fake.getName().getString(),
                settings.sog$getRenderDistance(), settings.sog$getSimulationDistance(),
                defaultRender, defaultSimulation);
    }

    /** 客户端切换某个假人“可拾取工具类别”的开关。 */
    public static void handleToolsSet(FakeToolsSetPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        if (!SogSettings.fakePlayerToolsOnly) {
            return;
        }
        if (!sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }
        EntityPlayerMPFake fake = findFake(sender.level().getServer(), payload.fake());
        if (!(fake instanceof SogFakeTools tools)) {
            return;
        }
        tools.sog$setToolMask(payload.mask());
    }

    /** 打开假人背包时下发给客户端的工具类别掩码。 */
    public static FakeToolsInfoPayload toolsInfoOf(int syncId, ServerPlayer fake, SogFakeTools tools) {
        return new FakeToolsInfoPayload(syncId, fake.getName().getString(), tools.sog$getToolMask());
    }

    private static int clampOrFollow(int value, int min, int max) {
        if (value < 0) {
            return -1;
        }
        return Math.min(max, Math.max(min, value));
    }

    private static EntityPlayerMPFake findFake(MinecraftServer server, String name) {
        if (name == null || name.isEmpty() || name.length() > 16) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (player instanceof EntityPlayerMPFake && player.getName().getString().equals(name)) {
                    return (EntityPlayerMPFake) player;
                }
            }
        }
        return null;
    }
}
