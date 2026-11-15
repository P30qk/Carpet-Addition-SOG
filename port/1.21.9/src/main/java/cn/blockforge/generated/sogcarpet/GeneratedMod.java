package cn.blockforge.generated.sogcarpet;

import carpet.CarpetServer;
import cn.blockforge.generated.sogcarpet.itemflow.ItemFlowTrackerFeature;
import cn.blockforge.generated.sogcarpet.net.AggroInfoPayload;
import cn.blockforge.generated.sogcarpet.net.AggroWantPayload;
import cn.blockforge.generated.sogcarpet.net.BeaconFilterPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderSetPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsSetPayload;
import cn.blockforge.generated.sogcarpet.net.HealthChangePayload;
import cn.blockforge.generated.sogcarpet.net.HealthChangeWantPayload;
import cn.blockforge.generated.sogcarpet.net.ItemCountPayload;
import cn.blockforge.generated.sogcarpet.net.ItemFlowActionPayload;
import cn.blockforge.generated.sogcarpet.net.ItemFlowStatusPayload;
import cn.blockforge.generated.sogcarpet.net.MotionInfoPayload;
import cn.blockforge.generated.sogcarpet.net.MotionWantPayload;
import cn.blockforge.generated.sogcarpet.net.PortableBeaconUsePayload;
import cn.blockforge.generated.sogcarpet.net.RuleSetPayload;
import cn.blockforge.generated.sogcarpet.net.RuleStatePayload;
import cn.blockforge.generated.sogcarpet.net.SculkTriggerPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnStatsConfigPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnStatsPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTraceWantPayload;
import cn.blockforge.generated.sogcarpet.net.VillagerBindingPayload;
import cn.blockforge.generated.sogcarpet.net.VillagerBindingWantPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsSetPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
        // C2S：客户端告知服务端还要不要收“仇恨/目标”快照。
        PayloadTypeRegistry.playC2S()
                .register(AggroWantPayload.TYPE, AggroWantPayload.STREAM_CODEC);
        // C2S：客户端告知服务端还要不要收“外部施加冲量”快照（速度/动量可视化）。
        PayloadTypeRegistry.playC2S()
                .register(MotionWantPayload.TYPE, MotionWantPayload.STREAM_CODEC);
        // C2S：客户端告知服务端还要不要收“村民绑定”快照（村民功能方块绑定可视化）。
        PayloadTypeRegistry.playC2S()
                .register(VillagerBindingWantPayload.TYPE, VillagerBindingWantPayload.STREAM_CODEC);
        // C2S：客户端告知服务端还要不要收“生命数值变化”事件（生命数值变化可视化）。
        PayloadTypeRegistry.playC2S()
                .register(HealthChangeWantPayload.TYPE, HealthChangeWantPayload.STREAM_CODEC);
        // C2S：客户端 Ctrl+V 改动后把整份“逐玩家渲染清单”上报，服务端存在该玩家名下。
        PayloadTypeRegistry.playC2S()
                .register(VisualizerPrefsSetPayload.TYPE, VisualizerPrefsSetPayload.STREAM_CODEC);
        // C2S：客户端上报“怪物生成可视化”统计面板的配置（整体开关 + 每个分类开关）。
        PayloadTypeRegistry.playC2S()
                .register(SpawnStatsConfigPayload.TYPE, SpawnStatsConfigPayload.STREAM_CODEC);
        // C2S：物品流向面板的一次操作（标记手持/清除/列表）。
        PayloadTypeRegistry.playC2S()
                .register(ItemFlowActionPayload.TYPE, ItemFlowActionPayload.STREAM_CODEC);
        // C2S：面板里把某条可视化规则切成关闭 / 暂时开启 / 永久开启（仅 OP）。
        PayloadTypeRegistry.playC2S()
                .register(RuleSetPayload.TYPE, RuleSetPayload.STREAM_CODEC);
        // C2S：随身信标选好效果后请求服务端发放（服务端重新校验规则、背包信标与黑白名单）。
        PayloadTypeRegistry.playC2S()
                .register(PortableBeaconUsePayload.TYPE, PortableBeaconUsePayload.STREAM_CODEC);
        // C2S：Op 在效果选择界面里锁定/解锁效果、切换黑/白名单模式。
        PayloadTypeRegistry.playC2S()
                .register(BeaconFilterPayload.TYPE, BeaconFilterPayload.STREAM_CODEC);
        // S2C：服务端在打开假人背包时回填初值，以及同步可视化规则开关。
        PayloadTypeRegistry.playS2C()
                .register(FakeRenderInfoPayload.TYPE, FakeRenderInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
                .register(FakeToolsInfoPayload.TYPE, FakeToolsInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
                .register(VisualizerRulesPayload.TYPE, VisualizerRulesPayload.STREAM_CODEC);
        // S2C：告诉客户端能不能改规则（OP）以及哪些规则是永久开启的。
        PayloadTypeRegistry.playS2C()
                .register(RuleStatePayload.TYPE, RuleStatePayload.STREAM_CODEC);
        // S2C：把“这个玩家自己的”可视化渲染清单发回去（逐玩家，和全局规则分开）。
        PayloadTypeRegistry.playS2C()
                .register(VisualizerPrefsPayload.TYPE, VisualizerPrefsPayload.STREAM_CODEC);
        // S2C：每 tick 把新采集的刷怪轨迹下发给装了本模组的客户端（单机/专用服务器同一路径）。
        PayloadTypeRegistry.playS2C()
                .register(SpawnTracePayload.TYPE, SpawnTracePayload.STREAM_CODEC);
        // S2C：约每秒把“刷怪成功概率”统计快照下发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(SpawnStatsPayload.TYPE, SpawnStatsPayload.STREAM_CODEC);
        // S2C：把玩家附近的“仇恨/目标”快照发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(AggroInfoPayload.TYPE, AggroInfoPayload.STREAM_CODEC);
        // S2C：把玩家附近“近期被施加过外部冲量”的实体发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(MotionInfoPayload.TYPE, MotionInfoPayload.STREAM_CODEC);
        // S2C：把玩家附近村民与床 / 工作方块的绑定关系发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(VillagerBindingPayload.TYPE, VillagerBindingPayload.STREAM_CODEC);
        // S2C：把玩家附近的生命数值变化（受伤负、回血正）发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(HealthChangePayload.TYPE, HealthChangePayload.STREAM_CODEC);
        // S2C：把幽匿感测体/坚守者的“声音检测被触发”事件发给装了本模组的客户端。
        PayloadTypeRegistry.playS2C()
                .register(SculkTriggerPayload.TYPE, SculkTriggerPayload.STREAM_CODEC);
        // S2C：物品流向面板的会话列表、掉落物总数（按 Tab 显示用）。
        PayloadTypeRegistry.playS2C()
                .register(ItemFlowStatusPayload.TYPE, ItemFlowStatusPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
                .register(ItemCountPayload.TYPE, ItemCountPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FakeRenderSetPayload.TYPE,
                SogFakeRenderService::handleSet);
        ServerPlayNetworking.registerGlobalReceiver(FakeToolsSetPayload.TYPE,
                SogFakeRenderService::handleToolsSet);
        ServerPlayNetworking.registerGlobalReceiver(SpawnTraceWantPayload.TYPE,
                SogSpawnTraceSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(AggroWantPayload.TYPE,
                SogAggroSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(MotionWantPayload.TYPE,
                SogMotionSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(VillagerBindingWantPayload.TYPE,
                SogVillagerBindingSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(HealthChangeWantPayload.TYPE,
                SogHealthChangeSync::handleWant);
        ServerPlayNetworking.registerGlobalReceiver(VisualizerPrefsSetPayload.TYPE,
                (payload, context) -> SogVisualizerPrefs.handleEncodedFromClient(
                        context.player(), payload.data()));
        ServerPlayNetworking.registerGlobalReceiver(SpawnStatsConfigPayload.TYPE,
                SogSpawnStatsHud::handleConfig);
        // C2S：物品流向面板操作。
        ServerPlayNetworking.registerGlobalReceiver(ItemFlowActionPayload.TYPE,
                ItemFlowTrackerFeature::handleAction);
        // C2S：可视化调控面板直接开关规则（服务端校验 OP）。
        ServerPlayNetworking.registerGlobalReceiver(RuleSetPayload.TYPE,
                SogRuleControl::handleSet);
        // C2S：随身信标请求发放效果 + Op 修改信标效果黑白名单。
        ServerPlayNetworking.registerGlobalReceiver(PortableBeaconUsePayload.TYPE,
                SogPortableBeacon::handleUse);
        ServerPlayNetworking.registerGlobalReceiver(BeaconFilterPayload.TYPE,
                SogBeaconFilterControl::handle);

        ServerPlayConnectionEvents.JOIN.register(SogRuleSyncService::onJoin);
        // 随身信标：玩家退出时停止续期；每 4 秒给还带着信标的玩家续一次效果，
        // 信标离开背包（丢弃/存箱子/被拿走）时立刻撤掉效果。
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SogPortableBeacon.onDisconnect(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(SogPortableBeacon::onEndServerTick);
        ServerTickEvents.END_SERVER_TICK.register(SogSpawnTraceSync::onEndServerTick);
        // 刷怪成功概率统计：把面板写进玩家的 Tab 列表页脚（长按 Tab 与在线玩家一起显示）。
        ServerTickEvents.END_SERVER_TICK.register(SogSpawnStatsHud::onEndServerTick);
        // 仇恨/目标快照每几 tick 扫一次并下发。
        ServerTickEvents.END_SERVER_TICK.register(SogAggroSync::onEndServerTick);
        // 外部施加冲量每几 tick 扫一次并下发给附近玩家（速度/动量可视化用）。
        ServerTickEvents.END_SERVER_TICK.register(SogMotionSync::onEndServerTick);
        // 村民与床 / 工作方块的绑定关系每几 tick 扫一次并下发给附近玩家。
        ServerTickEvents.END_SERVER_TICK.register(SogVillagerBindingSync::onEndServerTick);
        // 生命数值变化：受伤在伤害结算完成时采集（真实伤害），回血由 LivingEntityHealMixin
        // 在 heal 前后取血量差；同一 tick 末尾再结算下发给附近明确要收的玩家。
        ServerLivingEntityEvents.AFTER_DAMAGE.register(SogHealthChangeSync::onAfterDamage);
        ServerTickEvents.END_SERVER_TICK.register(SogHealthChangeSync::onEndServerTick);
        // 巨人旧版 AI：新召唤 / 区块加载时给巨人装上 14w50a 的 AI 目标。
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD
                .register((entity, level) -> {
                    if (entity instanceof net.minecraft.world.entity.monster.Giant giant) {
                        ((GiantAiAccess) giant).sog$ensureOldAi();
                    }
                });
        // 幽匿/坚守者的声音检测触发事件每 tick 下发一次。
        ServerTickEvents.END_SERVER_TICK.register(SogSculkTriggerSync::onEndServerTick);
        // 掉落物总数：约每秒给需要它的客户端发一次（掉落物数量可视化按 Tab 显示）。
        ServerTickEvents.END_SERVER_TICK.register(SogItemCountSync::onEndServerTick);
        // 简易珍珠远距传送：铲子攻击悬停珍珠，沿对应轴叠加速度；空手右键放飞。
        // 1.21.10 的攻击改为在 Player.attack(Entity) 的 mixin 里捕获（见 PlayerAttackMixin），
        // 因为 AttackEntityCallback 在这个版本上不可靠；右键放飞仍走 Fabric 交互事件。
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
                SogPearlTeleport.onUse(player, hand, entity));
        // 逐玩家可视化清单：启动时读盘；服务端停下时清掉采集状态，避免单机切换世界时残留。
        ServerLifecycleEvents.SERVER_STARTING.register(server -> SogVisualizerPrefs.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SogSpawnTrace.setRecording(false);
            SogSpawnTrace.clear();
            SogSpawnStats.setRecording(false);
            SogSpawnStats.clear();
            SogSpawnStatsHud.clear();
            SogSculkTrigger.setRecording(false);
            SogSculkTrigger.clear();
            SogPearlTeleport.clear();
            SogMotionSync.clear();
            SogItemCountSync.clear();
            SogHealthChangeSync.clear();
            SogPortableBeacon.clear();
        });
        // /sogcarpet visualizer：在没有客户端模组的情况下也能配置某个玩家的渲染清单。
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> SogVisualizerCommand.register(dispatcher));

        // 物品流向跟踪（移植自 ItemFlowTracker）：服务端事件 + 面板操作包。
        ItemFlowTrackerFeature.register();

        CarpetServer.manageExtension(new SogCarpetExtension());
    }
}
