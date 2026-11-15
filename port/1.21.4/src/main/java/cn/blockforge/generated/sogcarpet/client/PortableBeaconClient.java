package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * 「随身信标」的客户端入口：拦住 <b>Ctrl + 右键</b> 并打开效果选择界面。
 *
 * <p><b>为什么要在客户端拦</b>：手里拿着信标右键时，原版会先尝试“用物品 / 放方块”，
 * 信标本身是方块物品，于是很可能直接把信标放出去了。这里在<b>客户端</b>的两个交互事件
 * （{@link UseBlockCallback} 用于瞄准方块，{@link UseItemCallback} 用于瞄准空气）里先一步
 * 返回 {@link InteractionResult#FAIL}：Fabric 的注入点在 {@code MultiPlayerGameMode#interactBlock}
 * / {@code #interactItem} 头部，返回非 {@code PASS} 就不再走原版使用逻辑、也不会把
 * {@code ServerboundUseItemOnPacket} 发出去，所以信标不会被放置、也不会被服务端误判。</p>
 *
 * <p>条件（全部满足才接管）：</p>
 * <ul>
 *   <li>服务端同步过来的 {@link SogSettings#portableBeacon} 规则是开着的；</li>
 *   <li>Ctrl 键按着；</li>
 *   <li>主手或副手拿着信标；</li>
 *   <li>背包/快捷栏里确实有信标（服务端还会再校验一次，这里只影响“能不能打开界面”）。</li>
 * </ul>
 *
 * <p>规则关闭、或对面服务器没装本模组时，本类完全不介入，右键行为保持原版。</p>
 */
public final class PortableBeaconClient {
    private PortableBeaconClient() {
    }

    /** 客户端入口调用一次。 */
    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
                intercept(player, level, hand));
        UseItemCallback.EVENT.register((player, level, hand) ->
                intercept(player, level, hand));
    }

    /**
     * 命中条件时打开界面并吞掉这次右键。
     *
     * <p>只在客户端自行处理（{@code level.isClientSide()}）：服务端那一侧本来就不会收到这次
     * 交互包，所以不需要也不应该在服务端做任何事。</p>
     */
    private static InteractionResult intercept(Player player, Level level, InteractionHand hand) {
        if (!SogSettings.portableBeacon || level == null || !level.isClientSide()) {
            return InteractionResult.PASS;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || player != client.player || client.screen != null) {
            return InteractionResult.PASS;
        }
        if (!isCtrlDown(client)) {
            return InteractionResult.PASS;
        }
        if (!holdsBeacon(player) || !carriesBeacon(player)) {
            return InteractionResult.PASS;
        }
        // 右键在客户端就被吃掉：既不放置信标，也不向服务端发交互包。
        client.setScreen(BeaconEffectPickerScreen.portable(null));
        return InteractionResult.FAIL;
    }

    private static boolean isCtrlDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** 主手或副手拿着信标。 */
    private static boolean holdsBeacon(Player player) {
        return player.getMainHandItem().is(Items.BEACON)
                || player.getOffhandItem().is(Items.BEACON);
    }

    /** 背包（含快捷栏与装备槽）里有信标。 */
    private static boolean carriesBeacon(Player player) {
        return player.getInventory().contains(stack -> stack.is(Items.BEACON));
    }

    /**
     * 客户端加入时把「随身信标」的开关与本地默认值同步一次。
     *
     * <p>规则值本身由 {@link SogCarpetClient} 在收到 {@code VisualizerRulesPayload} 时写入
     * {@link SogSettings}，这里只是再挂一个断开连接后复位的钩子，避免把上一个服务器的状态
     * 带到下一个服务器（那边可能根本没装本模组）。</p>
     */
    public static void initResetHook() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> SogSettings.portableBeacon = false);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 规则关闭时顺手关掉还开着的「随身信标」界面；信标方块打开的界面不受影响。
            if (!SogSettings.portableBeacon && client.gui != null
                    && client.screen instanceof BeaconEffectPickerScreen picker
                    && picker.sog$isPortable()) {
                client.setScreen(null);
            }
        });
    }
}
