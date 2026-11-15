package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.net.AggroInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import cn.blockforge.generated.sogcarpet.net.HealthChangePayload;
import cn.blockforge.generated.sogcarpet.net.ItemCountPayload;
import cn.blockforge.generated.sogcarpet.net.RuleStatePayload;
import cn.blockforge.generated.sogcarpet.net.ItemFlowStatusPayload;
import cn.blockforge.generated.sogcarpet.net.MotionInfoPayload;
import cn.blockforge.generated.sogcarpet.net.SculkTriggerPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnStatsPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTracePayload;
import cn.blockforge.generated.sogcarpet.net.VillagerBindingPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerRulesPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;

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
            applyClientSyncedRules(payload.data());
        });
        // 逐玩家渲染清单：服务端为每个玩家单独保存，加入时发回来；服务端配置过就以它为准。
        ClientPlayNetworking.registerGlobalReceiver(VisualizerPrefsPayload.TYPE, (payload, context) ->
                VisualizerState.applyPrefsData(payload.configured(), payload.data()));
        // 刷怪轨迹由服务端每 tick 下发；这里只入队，真正并入渲染在客户端 tick 里做。
        ClientPlayNetworking.registerGlobalReceiver(SpawnTracePayload.TYPE, (payload, context) ->
                MobSpawnVisualizer.accept(payload));
        // 刷怪成功概率统计快照：收包即替换，HUD 与出生点标签在客户端渲染。
        ClientPlayNetworking.registerGlobalReceiver(SpawnStatsPayload.TYPE, (payload, context) ->
                SpawnStatsVisualizer.acceptStats(payload));
        // 仇恨/目标快照由服务端每几 tick 下发；收包线程只存引用，解析在客户端 tick 里做。
        ClientPlayNetworking.registerGlobalReceiver(AggroInfoPayload.TYPE, (payload, context) ->
                AggroVisualizer.accept(payload));
        // 外部施加冲量快照由服务端每几 tick 下发，供速度/动量可视化显示“外力”。
        ClientPlayNetworking.registerGlobalReceiver(MotionInfoPayload.TYPE, (payload, context) ->
                MotionImpulseState.accept(payload));
        // 幽匿/坚守者声音检测触发：收包线程只登记时间戳，渲染在客户端 tick。
        ClientPlayNetworking.registerGlobalReceiver(SculkTriggerPayload.TYPE, (payload, context) ->
                SculkVisualizer.accept(payload));
        // 村民与床 / 工作方块的绑定关系：收包线程只入队，渲染在客户端 tick。
        ClientPlayNetworking.registerGlobalReceiver(VillagerBindingPayload.TYPE, (payload, context) ->
                VillagerBindingVisualizer.accept(payload));
        // 面板三态开关：服务端告诉客户端自己是不是 OP、哪些规则是永久开启的。
        ClientPlayNetworking.registerGlobalReceiver(RuleStatePayload.TYPE, (payload, context) ->
                VisualizerState.applyRuleStateData(payload.data()));
        // 生命数值变化：服务端把结算完的伤害与真实回血量发过来；收包线程只入队，渲染在客户端 tick。
        ClientPlayNetworking.registerGlobalReceiver(HealthChangePayload.TYPE, (payload, context) ->
                HealthChangeVisualizer.accept(payload));
        // 物品流向面板的会话列表；收包即刷新面板显示。
        ClientPlayNetworking.registerGlobalReceiver(ItemFlowStatusPayload.TYPE, (payload, context) ->
                ItemFlowPanelState.set(payload.data()));
        // 掉落物总数（按 Tab 显示用）。
        ClientPlayNetworking.registerGlobalReceiver(ItemCountPayload.TYPE, (payload, context) ->
                DropCountVisualizer.acceptServerTotal(payload.total()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VisualizerState.clearSync();
            FlexiblePlacementCorners.clearSync();
            // “紫颂花可催熟”的开关是服务端同步下来的，退服后回到本地默认（关闭）。
            SogSettings.chorusFlowerBonemeal = false;
            SogSettings.customBeaconEffects = false;
            // 信标黑/白名单与随身信标开关同样是服务端真值，退服后复位，免得上个服务器的设置
            // 影响下一个（尤其是连到没装本模组的服务器时）。
            SogSettings.portableBeacon = false;
            SogSettings.beaconEffectFilterMode = SogSettings.BeaconEffectFilterMode.BLACKLIST;
            SogSettings.beaconEffectBlacklist = "";
            SogSettings.beaconEffectWhitelist = "";
            SogFakeRenderState.clear();
            SogFakeToolsState.clear();
            MotionImpulseState.clear();
            DropCountVisualizer.clearServerTotal();
            HealthChangeVisualizer.clear();
        });
        // 木剑右键实体时，直接由客户端交互事件选中它（比逐 tick 轮询右键更可靠）。
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            MotionVisualizer.selectFromInteraction(level, entity);
            AggroRangeVisualizer.selectFromInteraction(level, entity);
            return InteractionResult.PASS;
        });

        // 总控必须先起：各可视化规则要读它的开关状态。
        VisualizerState.init();
        // 怪物生成可视化的统计面板配置改动后同步给服务端（面板由服务端写进 Tab 列表页脚）。
        VisualizerConfig.setOptionChangeListener(rule -> {
            if ("spawnStatsVisualizer".equals(rule)) {
                VisualizerState.pushSpawnStatsConfig();
            }
        });
        FlexiblePlacementCorners.init();
        SculkVisualizer.init();
        CreakingHeartVisualizer.init();
        EnderDragonPathVisualizer.init();
        WitherBreakRangeVisualizer.init();
        EnderDragonBreakRangeVisualizer.init();
        MobSpawnVisualizer.init();
        SpawnStatsVisualizer.init();
        SpawnRangeVisualizer.init();
        VillagerBindingVisualizer.init();
        RandomWalkVisualizer.init();
        WorldEaterHelperVisualizer.init();
        ExplosionVisualizer.init();
        ItemMergeRangeVisualizer.init();
        ProjectileTrajectoryVisualizer.init();
        MotionVisualizer.init();
        AggroVisualizer.init();
        AggroRangeVisualizer.init();
        SweepRangeVisualizer.init();
        DropCountVisualizer.init();
        HealthChangeVisualizer.init();
        IllegalBlockVisualizer.init();
        // 雷击范围可视化：落雷范围 5 秒后消失 + 避雷针本体/引雷范围/影响范围。
        LightningVisualizer.init();
        // 随身信标：Ctrl+右键拦截（客户端先拦住，避免信标被当方块放出去）。
        PortableBeaconClient.init();
        PortableBeaconClient.initResetHook();
    }

    /**
     * 应用“服务端逻辑、但客户端也要知道开关”的规则（{@link SogSettings#CLIENT_SYNC_RULES}）。
     *
     * <p>除了“紫颂花可催熟”（骨粉右键预测）之外，还有信标相关的几条：</p>
     * <ul>
     *   <li>{@code customBeaconEffects}：信标界面据此决定画不画“+”槽位与下界之星图标；</li>
     *   <li>{@code portableBeacon}：客户端据此决定 Ctrl+右键要不要拦下来；</li>
     *   <li>{@code beaconEffectFilterMode} / {@code beaconEffectBlacklist} / {@code beaconEffectWhitelist}：
     *       信标界面的自定义槽位按它显示可选/锁定状态（黑名单与白名单各自一份）。名单是服务端真值，
     *       客户端只用来显示，服务端发放前还会再校验一次；随身信标不受名单影响。</li>
     * </ul>
     */
    private static void applyClientSyncedRules(String data) {
        SogSettings.chorusFlowerBonemeal = readSyncedFlag(data, "chorusFlowerBonemeal",
                SogSettings.chorusFlowerBonemeal);
        // 信标界面要靠它决定要不要画“+”按钮与下界之星图标。
        SogSettings.customBeaconEffects = readSyncedFlag(data, "customBeaconEffects",
                SogSettings.customBeaconEffects);
        // 随身信标：决定 Ctrl+右键是否被本模组接管。
        SogSettings.portableBeacon = readSyncedFlag(data, "portableBeacon",
                SogSettings.portableBeacon);
        String mode = readSyncedValue(data, "beaconEffectFilterMode", null);
        if (mode != null) {
            try {
                SogSettings.beaconEffectFilterMode = SogSettings.BeaconEffectFilterMode
                        .valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 不认识的值保持原样。
            }
        }
        String blacklist = readSyncedValue(data, "beaconEffectBlacklist", null);
        if (blacklist != null) {
            SogSettings.beaconEffectBlacklist = blacklist;
        }
        String whitelist = readSyncedValue(data, "beaconEffectWhitelist", null);
        if (whitelist != null) {
            SogSettings.beaconEffectWhitelist = whitelist;
        }
    }

    /** 从 {@code 名字=值;} 形式的同步串里读一个值；解析不到时返回 {@code fallback}。 */
    private static String readSyncedValue(String data, String name, String fallback) {
        if (data == null) {
            return fallback;
        }
        String prefix = name + "=";
        int start = data.indexOf(prefix);
        if (start < 0) {
            return fallback;
        }
        int end = data.indexOf(';', start);
        return data.substring(start + prefix.length(), end < 0 ? data.length() : end);
    }

    /** 从 {@code 名字=0/1;} 形式的同步串里读一个开关；解析不到时返回 {@code fallback}。 */
    private static boolean readSyncedFlag(String data, String name, boolean fallback) {
        String value = readSyncedValue(data, name, null);
        return value == null ? fallback : "1".equals(value);
    }
}
