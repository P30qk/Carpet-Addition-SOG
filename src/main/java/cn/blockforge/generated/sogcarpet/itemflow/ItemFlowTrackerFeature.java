package cn.blockforge.generated.sogcarpet.itemflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.itemflow.track.HighlightManager;
import cn.blockforge.generated.sogcarpet.itemflow.track.TrackMark;
import cn.blockforge.generated.sogcarpet.itemflow.track.Tracking;
import cn.blockforge.generated.sogcarpet.net.ItemFlowActionPayload;
import cn.blockforge.generated.sogcarpet.net.ItemFlowStatusPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 物品流向跟踪（移植自 ItemFlowTracker）。
 *
 * <p>操作全部走 Ctrl+V 里的“物品流向”面板（{@link cn.blockforge.generated.sogcarpet.client.ItemFlowConfigScreen}），
 * 不再使用 {@code /ift} 指令。被标记的物品无论流进哪个容器都会在服务端生成发光显示实体把容器
 * 高亮出来，于是连没装本模组的纯客户端也能看到；可选按间隔记录物品移动路径。</p>
 *
 * <p>整套机制全部在服务端运行：mixins 在物品堆、容器、漏斗、背包菜单等位置传播标记，
 * {@link HighlightManager} 每隔几 tick 生成/维护 {@code BlockDisplay}/{@code ItemDisplay}。
 * 由 {@link SogSettings#itemFlowTracker} 规则统一开关，关闭时立即清除所有高亮。</p>
 */
public final class ItemFlowTrackerFeature {
    public static final String MOD_ID = "itemflowtracker";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private ItemFlowTrackerFeature() {
    }

    /** 主入口调用一次。 */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> Tracking.sweepExhaustedSessions());
        ServerTickEvents.END_LEVEL_TICK.register(HighlightManager::tick);

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> HighlightManager.onEntityLoad(entity));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> HighlightManager.onEntityUnload(entity));

        ServerLifecycleEvents.SERVER_STOPPING.register(ItemFlowTrackerFeature::clear);
    }

    /** C2S：面板上的一次操作（标记手持 / 清除 / 刷新列表），处理完回一份最新列表。 */
    public static void handleAction(ItemFlowActionPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        int action = payload == null ? ItemFlowActionPayload.LIST : payload.action();

        if (action == ItemFlowActionPayload.CLEAR) {
            MinecraftServer server = sender.level().getServer();
            if (server != null) {
                clear(server);
            }
        } else if (action == ItemFlowActionPayload.MARK_HELD && SogSettings.itemFlowTracker) {
            ItemStack stack = sender.getMainHandItem();
            if (!stack.isEmpty()) {
                int interval = Math.max(0, payload.pathInterval());
                TrackMark mark = Tracking.newMark(payload.rgb() & 0xFFFFFF, stack.getCount(), interval);
                Tracking.set(stack, mark);
            }
        }
        sendStatus(sender);
    }

    /** 把当前会话列表发回给这个玩家（没装本模组的客户端直接跳过）。 */
    public static void sendStatus(ServerPlayer player) {
        if (player == null || !ServerPlayNetworking.canSend(player, ItemFlowStatusPayload.TYPE)) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        if (!SogSettings.itemFlowTracker) {
            sb.append("rule_off");
        }
        for (TrackMark mark : Tracking.activeSessions()) {
            sb.append(mark.label()).append('|').append(mark.budget()).append('|')
                    .append(mark.capacity()).append('\n');
        }
        ServerPlayNetworking.send(player, new ItemFlowStatusPayload(sb.toString()));
    }

    /** 规则关闭/服务器停止时清掉全部追踪状态与高亮显示实体。 */
    public static void clear(MinecraftServer server) {
        Tracking.clearAll();
        HighlightManager.clearAll(server);
        HighlightManager.sweepLoadedDisplays(server);
    }
}
