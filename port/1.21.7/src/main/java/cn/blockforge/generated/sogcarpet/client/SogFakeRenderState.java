package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogFakeRenderService;
import cn.blockforge.generated.sogcarpet.net.FakeRenderInfoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

/**
 * 客户端缓存：当前打开的 GCA 假人背包对应的假人渲染设置。
 *
 * <p>服务端在假人背包菜单构造时下发 {@link FakeRenderInfoPayload}；
 * 该包可能稍晚于界面 init 到达，因此收到后如果界面已经打开，
 * 用 {@code setScreen(同一实例)} 触发一次 init 重建滑条。</p>
 *
 * <p>身份识别不能只看菜单类名：GCA 用 {@code MenuType.GENERIC_9x6} 打开背包，
 * 客户端实际拿到的是原版 {@link ChestMenu}，而不是 GCA 的
 * {@code PlayerInventoryMenu}（那只是服务端的实现）。所以额外用
 * “容器 id + 标题翻译键 {@code gca.player.inventory}”来确认，
 * 避免把普通箱子界面也画上滑条。</p>
 */
public final class SogFakeRenderState {
    /** GCA 打开假人背包时用的标题翻译键（标题里带假人名字，如“hsds的背包”）。 */
    public static final String GCA_INVENTORY_KEY = "gca.player.inventory";

    private static FakeRenderInfoPayload current;

    private SogFakeRenderState() {
    }

    public static void set(FakeRenderInfoPayload payload) {
        current = payload;
    }

    public static void clear() {
        current = null;
    }

    public static FakeRenderInfoPayload current() {
        return current;
    }

    /**
     * 是否是 GCA 的假人背包菜单。
     *
     * <p>服务端构造菜单时类名就是 GCA 的 {@code PlayerInventoryMenu}；客户端拿到的是
     * 原版 6 行箱子（GCA 用 {@code MenuType.GENERIC_9x6} 打开），只能靠标题翻译键
     * {@code gca.player.inventory} 来确认。GCA 的另外两个 6 行／3 行菜单
     * （{@code gca.player.other_controller}、{@code gca.player.ender_chest}）键不同，
     * 不会被误判。</p>
     */
    public static boolean isGcaFakeMenu(AbstractContainerMenu menu, Component title) {
        if (menu == null) {
            return false;
        }
        if (SogFakeRenderService.GCA_MENU_CLASS.equals(menu.getClass().getName())) {
            return true;
        }
        if (current == null) {
            return false;
        }
        // 假人背包一定是 6 行（54 格）。
        if (!(menu instanceof ChestMenu chest) || chest.getRowCount() != 6) {
            return false;
        }
        return title != null && title.getContents() instanceof TranslatableContents contents
                && GCA_INVENTORY_KEY.equals(contents.getKey());
    }

    public static void refreshOpenScreen(Minecraft minecraft) {
        // 26.2 的当前界面挂在 Gui 上（Minecraft 不再直接暴露 screen 字段）。
        Screen screen = minecraft.screen;
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            // 渲染设置或工具类别任一到齐都要重建界面，把滑条 / 工具列画上去。
            if (isGcaFakeMenu(containerScreen.getMenu(), containerScreen.getTitle())
                    || SogFakeToolsState.matches(containerScreen.getMenu())) {
                // 对同一实例再次 setScreen 会先 removed 再 init，控件按刚收到的设置重建。
                minecraft.setScreen(screen);
            }
        }
    }
}
