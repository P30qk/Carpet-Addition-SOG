package cn.blockforge.generated.sogcarpet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * 本模组自带配方的“规则开关 + 配方书同步”。
 *
 * <p><b>配方为什么不出现（本次修复的问题）</b>：26.2 的配方书不再下发完整配方表，服务端只为
 * 玩家<b>已解锁</b>的配方逐条解析 {@code RecipeDisplayEntry} 再发过去（{@code ServerRecipeBook}
 * → {@code RecipeManager#listDisplaysForRecipe}）。这套数据只在两个时机生成：</p>
 * <ol>
 *   <li>玩家<b>加入</b>时一次性发送（{@code sendInitialRecipeBook}）；</li>
 *   <li>某条配方<b>刚刚解锁</b>时（{@code addRecipes} → {@code ClientboundRecipeBookAddPacket}）。</li>
 * </ol>
 *
 * <p>于是旧实现里有两个必然出现的“配方书里看不到”的情况：</p>
 * <ul>
 *   <li>解锁发生在规则关闭时——{@code RecipeManagerMixin} 按规则把展示条目拦掉了，客户端
 *       永远没收到它；之后把规则打开也没人重发，非重启不可；</li>
 *   <li>玩家已经解锁了配方（背包里有过材料），但配方从未被“显式解锁”过（例如管理员刚打开
 *       规则，或玩家在规则打开前就拿到了材料）。</li>
 * </ul>
 *
 * <p>修复方式：</p>
 * <ul>
 *   <li>{@link #syncOnJoin}：玩家加入时把“当前规则已开启”的本模组配方全部补解锁一次
 *       （已经解锁过的会被 {@code ServerRecipeBook} 自动跳过）；</li>
 *   <li>{@link #refreshAll}：四条相关规则任意一条变化时，给所有在线玩家补解锁 + 重发整份
 *       配方书（{@code sendInitialRecipeBook} 带 replace 标志，客户端会整体替换，不会残留
 *       已经该隐藏的条目）。于是打开规则后配方<b>立刻</b>出现在原版配方书和 EasierCrafting
 *       这类读配方书的模组里，关掉规则后也立刻消失，不需要重登。</li>
 * </ul>
 *
 * <p>{@link #isEnabled} 是配方的唯一门控判断，{@link RecipeManagerMixin} 与这里共用它，
 * 所以“能不能合成”“配方书里有没有”永远一致。</p>
 */
public final class SogRecipeBook {
    /** 本模组的数据包命名空间。 */
    public static final String NAMESPACE = "sog_carpet";
    /** 影响配方书的四条规则。 */
    public static final Set<String> RULES = Set.of(
            "tintedGlassEasyCraft", "easyElytraCraft", "snowCraftable", "woodStrippingRecipe");

    private SogRecipeBook() {
    }

    /** 这条配方现在该不该存在（规则默认全部关闭）。 */
    public static boolean isEnabled(String path) {
        if (path == null) {
            return false;
        }
        // 木头去皮配方是一批文件（stripping_oak_log … stripping_warped_hyphae），
        // 统一挂在 stripping_ 前缀下，由同一条规则门控。
        if (path.startsWith("stripping_")) {
            return SogSettings.woodStrippingRecipe;
        }
        return switch (path) {
            case "tinted_glass_easy" -> SogSettings.tintedGlassEasyCraft;
            case "simple_elytra" -> SogSettings.easyElytraCraft;
            case "snow_from_block" -> SogSettings.snowCraftable;
            // 不是本模组的配方名：不归这里管，交给调用方按命名空间判断。
            default -> true;
        };
    }

    /** 这条（本模组的）配方现在该不该存在。 */
    public static boolean isEnabled(ResourceLocation id) {
        return id != null && NAMESPACE.equals(id.getNamespace()) && isEnabled(id.getPath());
    }

    /** 这条配方是否属于本模组、且当前规则已把它关掉（其它模组/原版配方一律返回 false）。 */
    public static boolean isDisabled(ResourceLocation id) {
        return id != null && NAMESPACE.equals(id.getNamespace()) && !isEnabled(id.getPath());
    }

    /** 规则变化后：给所有在线玩家补解锁并重发整份配方书。 */
    public static void refreshAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    /**
     * 单个玩家：把当前规则状态同步进配方书。
     *
     * <p>1.21.x 的配方书没有 1.21.2+ 那套 {@code RecipeDisplayEntry} 逐条解析层，客户端看到的
     * 就是服务端 {@code ServerRecipeBook} 里的“已解锁集合”，所以这里直接两步走：</p>
     * <ol>
     *   <li>{@code removeRecipes}：规则已关闭的本模组配方从集合里撤掉，并让客户端移除；</li>
     *   <li>{@code addRecipes}：规则已开启的补解锁，客户端随即收到新增条目。</li>
     * </ol>
     * <p>两步都是幂等的（移除不存在的、解锁已解锁的都是空操作），所以规则反复切换也不会串味。</p>
     */
    public static void refresh(ServerPlayer player) {
        if (player == null) {
            return;
        }
        sync(player);
    }

    /**
     * 玩家加入时调用：把配方书对齐到当前规则。
     *
     * <p>玩家数据里可能还留着上次会话解锁过的配方（那时规则是开的），所以加入时不只要补解锁，
     * 还要把现在已经该隐藏的撤掉，否则重登后配方书里会留着一条合不出来的配方。</p>
     */
    public static void syncOnJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        sync(player);
    }

    /**
     * 把本模组配方的“该有 / 不该有”一次性对齐到玩家身上。
     *
     * <p>{@code addRecipes} 只处理还没解锁的，{@code removeRecipes} 只处理已经解锁的，
     * 所以重复调用是安全的。</p>
     */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        List<RecipeHolder<?>> enabled = new ArrayList<>();
        List<RecipeHolder<?>> disabled = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceLocation id = holder.id();
            if (!NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            if (isEnabled(id)) {
                enabled.add(holder);
            } else {
                disabled.add(holder);
            }
        }
        if (!disabled.isEmpty()) {
            player.getRecipeBook().removeRecipes(disabled, player);
        }
        if (!enabled.isEmpty()) {
            player.getRecipeBook().addRecipes(enabled, player);
        }
    }
}
