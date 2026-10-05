package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端入口：接收假人渲染/工具设置与可视化规则同步（S2C），并注册各条可视化规则的
 * 渲染回调与 Ctrl+O / Ctrl+V 总控。
 */
public final class SogCarpetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FakeRenderInfoPayload.TYPE, (payload, context) -> {
            SogFakeRenderState.set(payload);
            SogFakeRenderState.refreshOpenScreen(context.client());
        });
        ClientPlayNetworking.registerGlobalReceiver(FakeToolsInfoPayload.TYPE, (payload, context) -> {
            SogFakeToolsState.set(payload);
            SogFakeRenderState.refreshOpenScreen(context.client());
        });
        ClientPlayNetworking.registerGlobalReceiver(VisualizerRulesPayload.TYPE, (payload, context) -> {
            VisualizerState.applySyncData(payload.data());
            FlexiblePlacementCorners.applySyncData(payload.data());
        });
        // 逐玩家渲染清单：服务端为每个玩家单独保存，加入时发回来；服务端配置过就以它为准。
        ClientPlayNetworking.registerGlobalReceiver(VisualizerPrefsPayload.TYPE, (payload, context) ->
                VisualizerState.applyPrefsData(payload.configured(), payload.data()));
        // 刷怪轨迹由服务端每 tick 下发；这里只入队，真正并入渲染在客户端 tick 里做。
        ClientPlayNetworking.registerGlobalReceiver(SpawnTracePayload.TYPE, (payload, context) ->
                MobSpawnVisualizer.accept(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VisualizerState.clearSync();
            FlexiblePlacementCorners.clearSync();
            SogFakeRenderState.clear();
            SogFakeToolsState.clear();
        });
        // 总控必须先起：各可视化规则要读它的开关状态。
        VisualizerState.init();
        FlexiblePlacementCorners.init();
        SculkVisualizer.init();
        CreakingHeartVisualizer.init();
        EnderDragonPathVisualizer.init();
        WitherBreakRangeVisualizer.init();
        EnderDragonBreakRangeVisualizer.init();
        MobSpawnVisualizer.init();
        WorldEaterHelperVisualizer.init();
        ExplosionVisualizer.init();
    }
}
