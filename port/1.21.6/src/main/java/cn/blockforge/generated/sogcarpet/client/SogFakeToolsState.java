package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.net.FakeToolsInfoPayload;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 客户端缓存：当前打开的 GCA 假人背包对应的“可拾取工具类别”掩码。
 *
 * <p>与 {@link SogFakeRenderState} 同一套识别方式：服务端在菜单构造时下发
 * {@link FakeToolsInfoPayload}，客户端用容器 id + 标题确认身份。只有
 * {@code fakePlayerToolsOnly} 规则开着时服务端才会发，所以“收到了包”本身就等价于
 * “规则开着、该显示这一列”。</p>
 */
public final class SogFakeToolsState {
    private static FakeToolsInfoPayload current;

    private SogFakeToolsState() {
    }

    public static void set(FakeToolsInfoPayload payload) {
        current = payload;
    }

    public static void clear() {
        current = null;
    }

    public static FakeToolsInfoPayload current() {
        return current;
    }

    /** 这个菜单是不是刚收到工具设置的那个假人背包。 */
    public static boolean matches(AbstractContainerMenu menu) {
        return current != null && menu != null && current.syncId() == menu.containerId;
    }
}
