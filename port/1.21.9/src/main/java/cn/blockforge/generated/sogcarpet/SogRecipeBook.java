package cn.blockforge.generated.sogcarpet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
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

    /** 规则变化后：给所有在线玩家补解锁并重发整份配方书。 */
    public static void refreshAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refresh(player);
        }
    }

    /** 单个玩家：补解锁 + 重发整份配方书（这样被规则隐藏的条目也会从客户端消失）。 */
    public static void refresh(ServerPlayer player) {
        if (player == null) {
            return;
        }
        unlockEnabled(player);
        // 整体替换：客户端会丢掉已经不该显示的条目，并补上刚开启的那些。
        player.getRecipeBook().sendInitialRecipeBook(player);
    }

    /** 玩家加入时调用：把当前规则已开启的本模组配方补解锁一次。 */
    public static void syncOnJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        unlockEnabled(player);
    }

    /**
     * 把“规则已开启”的本模组配方解锁给这个玩家。
     *
     * <p>{@code addRecipes} 只会处理还没有解锁的那些，并把它们的展示条目一起发给客户端，
     * 所以重复调用是安全的。</p>
     */
    private static void unlockEnabled(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            ResourceKey<Recipe<?>> key = holder.id();
            if (isEnabled(key.location())) {
                holders.add(holder);
            }
        }
        if (!holders.isEmpty()) {
            player.getRecipeBook().addRecipes(holders, player);
        }
    }
}
