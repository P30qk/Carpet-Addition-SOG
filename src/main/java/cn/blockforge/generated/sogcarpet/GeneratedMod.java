package cn.blockforge.generated.sogcarpet;

import carpet.CarpetServer;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderSetPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsSetPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.api.ModInitializer;
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
 *   <li>玩家加入时把可视化规则开关同步给他，Ctrl+V 界面与各可视化规则都靠它。</li>
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
        PayloadTypeRegistry.serverboundPlay()
                .register(FakeRenderSetPayload.TYPE, FakeRenderSetPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay()
                .register(FakeToolsSetPayload.TYPE, FakeToolsSetPayload.STREAM_CODEC);
        // S2C：服务端在打开假人背包时回填初值，以及同步可视化规则开关。
        PayloadTypeRegistry.clientboundPlay()
                .register(FakeRenderInfoPayload.TYPE, FakeRenderInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(FakeToolsInfoPayload.TYPE, FakeToolsInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay()
                .register(VisualizerRulesPayload.TYPE, VisualizerRulesPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FakeRenderSetPayload.TYPE,
                SogFakeRenderService::handleSet);
        ServerPlayNetworking.registerGlobalReceiver(FakeToolsSetPayload.TYPE,
                SogFakeRenderService::handleToolsSet);

        ServerPlayConnectionEvents.JOIN.register(SogRuleSyncService::onJoin);

        CarpetServer.manageExtension(new SogCarpetExtension());
    }
}
