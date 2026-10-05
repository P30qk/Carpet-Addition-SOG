package cn.blockforge.generated.sogcarpet;

import carpet.CarpetServer;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderSetPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsSetPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTraceWantPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsSetPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 模组主入口（客户端与服务端共用的 initialize 阶段）。
 *
 * <p>这里做三件事，顺序不能反：</p>
 * <ol>
 *   <li>注册自定义负载。负载类型必须两边都注册，否则客户端发不出 C2S 包、
 *       也认不出 S2C 包；接收器只在服务端挂 C2S 那几条。</li>
 *   <li>玩家加入时把可视化规则开关同步给他，Ctrl+V 界面与各可视化规则都靠它；
 *       之后再挂服务端 tick 回调，每 tick 把刷怪轨迹下发给装了本模组的客户端。</li>
 *   <li>把自己交给 Carpet 的扩展系统。规则本身的注册在
 *       {@link SogCarpetExtension#onGameStarted()} 里完成——必须等扩展被收进
 *       Carpet 的扩展列表、翻译表刷新之后，才能把 {@link SogSettings} 解析进核心
 *       manager。</li>
 * </ol>
 */
public final class GeneratedMod implements ModInitializer {
    public static final String MOD_ID = "sog_carpet";

    @Override
    public void onInitialize() {
        // C2S：客户端拖动滑条 / 切换工具类别 → 服务端应用设置。
        PayloadTypeRegistry.playC2S()
                .register(FakeRenderSetPayload.TYPE, FakeRenderSetPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S()
                .register(FakeToolsSetPayload.TYPE, FakeToolsSetPayload.STREAM_CODEC);
        // C2S：客户端告知服务端还要不要收刷怪轨迹（本地把可视化关掉时别再发）。
        PayloadTypeRegistry.playC2S()
                .register(SpawnTraceWantPayload.TYPE, SpawnTraceWantPayload.STREAM_CODEC);
        // C2S：客户端 Ctrl+V 改动后把整份“逐玩家渲染清单”上报，服务端存在该玩家名下。
        PayloadTypeRegistry.playC2S()
                .register(VisualizerPrefsSetPayload.TYPE, VisualizerPrefsSetPayload.STREAM_CODEC);
        // S2C：服务端在打开假人背包时回填初值，以及同步可视化规则开关。
        PayloadTypeRegistry.playS2C()
                .register(FakeRenderInfoPayload.TYPE, FakeRenderInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
                .register(FakeToolsInfoPayload.TYPE, FakeToolsInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
                .register(VisualizerRulesPayload.TYPE, VisualizerRulesPayload.STREAM_CODEC);
        // S2C：把“这个玩家自己的”可视化渲染清单发回去（逐玩家，和全局规则分开）。
        PayloadTypeRegistry.playS2C()
                .register(VisualizerPrefsPayload.TYPE, VisualizerPrefsPayload.STREAM_CODEC);
        // S2C：每 tick 把新采集的刷怪轨迹下发给装了本模组的客户端（单机/专用服务器同一路径）。
        PayloadTypeRegistry.playS2C()
                .register(SpawnTracePayload.TYPE, SpawnTracePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FakeRenderSetPayload.TYPE,
                SogFakeRenderService::handleSet);
        ServerPlayNetworking.registerGlobalReceiver(FakeToolsSetPayload.TYPE,
                SogFakeRenderService::handleToolsSet);
        ServerPlayNetworking.registerGlobalReceiver(SpawnTraceWantPayload.TYPE,
                SogSpawnTraceSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(VisualizerPrefsSetPayload.TYPE,
                (payload, context) -> SogVisualizerPrefs.handleEncodedFromClient(
                        context.player(), payload.data()));

        ServerPlayConnectionEvents.JOIN.register(SogRuleSyncService::onJoin);
        // 刷怪轨迹在服务端采集，每 tick 末尾下发给装了本模组的客户端。
        ServerTickEvents.END_SERVER_TICK.register(SogSpawnTraceSync::onEndServerTick);
        // 逐玩家可视化清单：启动时读盘；服务端停下时清掉采集状态，避免单机切换世界时残留。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> SogVisualizerPrefs.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SogSpawnTrace.setRecording(false);
            SogSpawnTrace.clear();
        });
        // /sogcarpet visualizer：在没有客户端模组的情况下也能配置某个玩家的渲染清单。
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> SogVisualizerCommand.register(dispatcher));

        CarpetServer.manageExtension(new SogCarpetExtension());
    }
}
