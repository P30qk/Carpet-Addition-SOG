package cn.blockforge.generated.sogcarpet;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * “简易珍珠远距传送”的服务端逻辑。
 *
 * <p>玩法：把末影珍珠（或雪球）命名为 {@value #NAME_ZH}（英文 {@value #NAME_EN}）后投出，
 * 它悬停在空中。副手拿一张名字是纯数字的纸，主手拿<b>木铲（X 轴）/石铲（Y 轴）/铁铲（Z 轴）</b>，
 * 攻击珍珠一次就沿对应轴叠加纸上的数值（负数反向）。最后主手空手右键珍珠让它飞出，
 * 落地后按原版末影珍珠传送主人。雪球走同一套流程但不传送，只用于调试。</p>
 *
 * <p>1.21.10 没有 1.21.11 才加入的长矛（{@code PiercingWeapon}），所以这里不用长矛突刺，
 * 而是利用 {@link cn.blockforge.generated.sogcarpet.mixin.ProjectilePickableMixin} 让悬停
 * 珍珠可被准星选中，再用普通攻击事件捕获。客户端也要装本模组，准星才能选中投掷物。</p>
 *
 * <p>状态存在服务端的 {@link #ACCUMULATED}（实体 UUID → 累积速度）与 {@link #LAUNCHED}
 * 两个表里；悬停靠 {@link cn.blockforge.generated.sogcarpet.mixin.ThrowableProjectileMixin}
 * 每 tick 把 {@code noGravity} 设为 true 并清零速度来实现（{@code noGravity} 是同步数据，
 * 客户端也会跟着悬停）。攻击与右键分别由 {@code AttackEntityCallback}、
 * {@code UseEntityCallback} 两个 Fabric 事件驱动，无需自己写交互 mixin。</p>
 */
public final class SogPearlTeleport {
    /** 中文名（命名）——用户用中文命名时匹配它。 */
    public static final String NAME_ZH = "远传珍珠";
    /** 英文名（尽量短，避免名字太长）。 */
    public static final String NAME_EN = "FarPearl";
    /**
     * 累积速度<b>不设上限</b>：纸上写多少就叠多少，珍珠就以多少格/tick 飞出。
     *
     * <p>唯一保留的底线是拒绝 {@code NaN} / 无穷——这种值会让实体直接消失。
     * 数值很大时客户端预览会自动改成“子步碰撞 + 直线外推”，不会把客户端拖住
     * （见 {@code ProjectileBallistics#simulate}）。</p>
     */
    /**
     * 单颗珍珠最多悬停多少 tick（这里 20 分钟）。悬停的末影珍珠会为它所在的区块挂票据，
     * 无限悬停会把区块一直压在加载状态；超时后自动以累积速度放飞，避免拖累服务器。
     */
    private static final int MAX_HOVER_TICKS = 24000;

    private static final Map<UUID, Vec3> ACCUMULATED = new ConcurrentHashMap<>();
    /**
     * 已经放飞过的珍珠（UUID → 放飞时的游戏时间）。
     *
     * <p>以前这里是只增不减的 {@code Set<UUID>}：每放飞一颗珍珠就永久留一条，长跑的服务器
     * 会一直涨内存。改成带时间戳，交给 {@link #prune(long)} 定期清掉早已消失的项。</p>
     */
    private static final Map<UUID, Long> LAUNCHED = new ConcurrentHashMap<>();
    /** 每颗悬停珍珠最近一次被读写的游戏时间（配合 {@link #prune(long)} 清理残留的累积速度）。 */
    private static final Map<UUID, Long> ACCUMULATED_TOUCHED = new ConcurrentHashMap<>();
    /** 过期清理的间隔（tick）。 */
    private static final int PRUNE_INTERVAL = 200;
    /** 下一次允许执行过期清理的游戏时间。 */
    private static long nextPruneTick;

    private SogPearlTeleport() {
    }

    // ------------------------------------------------------------------ 判定

    /** 这个自定义名字是不是“远传珍珠”。 */
    public static boolean matchesName(Component name) {
        if (name == null) {
            return false;
        }
        String text = name.getString().trim();
        return NAME_ZH.equals(text) || NAME_EN.equalsIgnoreCase(text);
    }

    /** 这个实体是不是命名过的远传珍珠/雪球。 */
    public static boolean isFarPearl(Entity entity) {
        return entity instanceof ThrowableItemProjectile projectile
                && matchesName(projectile.getItem().getCustomName());
    }

    /** 这个实体现在是不是“悬停中”（还没被放飞）。 */
    public static boolean isHovering(Entity entity) {
        return isFarPearl(entity) && !LAUNCHED.containsKey(entity.getUUID());
    }

    // ------------------------------------------------------------------ 悬停

    /**
     * 由 {@code ThrowableItemProjectile} 的构造 mixin 在服务端创建投掷物时调用。
     *
     * <p>关键点：在投掷物<b>加入世界之前</b>就把 {@code noGravity} 与自定义名设好，
     * 这样它们会随生成包一起同步到客户端——客户端第一 tick 就知道要悬停，不会先按投掷初速度
     * 飞一小段再停下。</p>
     */
    public static void onProjectileConstructed(ThrowableItemProjectile projectile) {
        if (!SogSettings.pearlTeleport) {
            return;
        }
        Component name = projectile.getItem().getCustomName();
        if (!matchesName(name)) {
            return;
        }
        projectile.setNoGravity(true);
        projectile.setCustomName(name);
    }

    /**
     * 由 {@code ThrowableProjectile.tick} 的 mixin 每 tick 调用。未放飞的珍珠清零速度并保持
     * 无重力；放飞后不再干预，让它按原版飞。
     */
    public static void onProjectileTick(ThrowableProjectile projectile) {
        if (!(projectile instanceof ThrowableItemProjectile item)) {
            return;
        }
        if (!matchesName(item.getItem().getCustomName())) {
            return;
        }
        if (projectile.level().isClientSide()) {
            // 客户端用“实体自定义名”作为悬停标记（服务端在构造时就设好、随生成包同步，
            // 放飞时清空），所以从生成的第一 tick 起就悬停，不等 noGravity 的同步延迟。
            if (matchesName(projectile.getCustomName())) {
                projectile.setNoGravity(true);
                projectile.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        if (!SogSettings.pearlTeleport) {
            return;
        }
        UUID id = projectile.getUUID();
        if (LAUNCHED.containsKey(id)) {
            return;
        }
        // 悬停太久就自动放飞，避免末影珍珠的区块票据被一直压着不放。
        if (projectile.tickCount > MAX_HOVER_TICKS) {
            launch(projectile);
            return;
        }
        prune(projectile.level().getGameTime());
        Component name = item.getItem().getCustomName();
        // 只在真的需要时才写同步数据：每 tick 无脑 setCustomName 会一直分配 Component、
        // 并让实体数据同步走一遍脏检查，是悬停珍珠身上最没意义的一笔开销。
        if (name != null && !matchesName(projectile.getCustomName())) {
            projectile.setCustomName(name);
        }
        if (!projectile.isNoGravity()) {
            projectile.setNoGravity(true);
        }
        if (projectile.getDeltaMovement().lengthSqr() > 1.0E-12D) {
            projectile.setDeltaMovement(Vec3.ZERO);
        }
        Vec3 accumulated = ACCUMULATED.computeIfAbsent(id, key -> Vec3.ZERO);
        ACCUMULATED_TOUCHED.put(id, projectile.level().getGameTime());
        // 把当前累积的待施加速度持续登记给“速度/动量可视化”，客户端就能看到这支外力箭头。
        SogMotionSync.setHeld(projectile, accumulated);
    }

    // ------------------------------------------------------------------ 攻击叠加

    /** 依主手的铲子决定轴：木铲=X、石铲=Y、铁铲=Z；其它材质返回 -1。 */
    public static int axisOfShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        if (stack.is(Items.WOODEN_SHOVEL)) {
            return 0;
        }
        if (stack.is(Items.STONE_SHOVEL)) {
            return 1;
        }
        if (stack.is(Items.IRON_SHOVEL)) {
            return 2;
        }
        return -1;
    }

    /** 读副手纸上写的数值；不是纸、没命名或不是纯数字时返回 null。 */
    public static Double paperValue(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PAPER)) {
            return null;
        }
        Component name = stack.getCustomName();
        if (name == null) {
            return null;
        }
        try {
            return Double.parseDouble(name.getString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 沿轴叠加一个速度分量。 */
    public static void addImpulse(ThrowableProjectile projectile, int axis, double value) {
        Vec3 base = switch (axis) {
            case 0 -> new Vec3(1.0D, 0.0D, 0.0D);
            case 1 -> new Vec3(0.0D, 1.0D, 0.0D);
            default -> new Vec3(0.0D, 0.0D, 1.0D);
        };
        if (!Double.isFinite(value)) {
            // 只有 NaN / 无穷才拒绝：那种值会让实体直接消失，谈不上“上限”。
            return;
        }
        ACCUMULATED.merge(projectile.getUUID(), base.scale(value), Vec3::add);
        ACCUMULATED_TOUCHED.put(projectile.getUUID(), projectile.level().getGameTime());
    }

    /**
     * 过期清理：珍珠被销毁 / 区块卸载 / 早已落地消失时，{@link #ACCUMULATED} 与
     * {@link #LAUNCHED} 会留下残项。这里按游戏时间清，最多每 {@value #PRUNE_INTERVAL} tick 走一次。
     */
    private static void prune(long gameTime) {
        if (gameTime < nextPruneTick) {
            return;
        }
        nextPruneTick = gameTime + PRUNE_INTERVAL;
        ACCUMULATED.entrySet().removeIf(entry -> {
            Long touched = ACCUMULATED_TOUCHED.get(entry.getKey());
            return touched == null || gameTime - touched > MAX_HOVER_TICKS;
        });
        ACCUMULATED_TOUCHED.entrySet().removeIf(entry -> gameTime - entry.getValue() > MAX_HOVER_TICKS);
        LAUNCHED.entrySet().removeIf(entry -> gameTime - entry.getValue() > MAX_HOVER_TICKS);
    }

    // ------------------------------------------------------------------ 放飞

    /** 以累积速度放飞。 */
    public static void launch(ThrowableProjectile projectile) {
        UUID id = projectile.getUUID();
        Vec3 velocity = ACCUMULATED.getOrDefault(id, Vec3.ZERO);
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y)
                || !Double.isFinite(velocity.z)) {
            velocity = Vec3.ZERO;
        }
        LAUNCHED.put(id, projectile.level().getGameTime());
        ACCUMULATED.remove(id);
        ACCUMULATED_TOUCHED.remove(id);
        // 清掉“悬停标记”（实体自定义名），客户端据此立刻停止悬停、改用服务端下发的速度。
        projectile.setCustomName(null);
        projectile.setNoGravity(false);
        projectile.setDeltaMovement(velocity);
    }

    // ------------------------------------------------------------------ 事件入口

    /** 主手木/石/铁铲 + 副手数字纸攻击悬停珍珠 → 沿对应轴叠加速度。 */
    public static InteractionResult onAttack(Player player, Entity target) {
        if (player == null || player.level().isClientSide() || !SogSettings.pearlTeleport) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof ThrowableProjectile projectile) || !isHovering(projectile)) {
            return InteractionResult.PASS;
        }
        int axis = axisOfShovel(player.getMainHandItem());
        if (axis < 0) {
            return InteractionResult.PASS;
        }
        Double value = paperValue(player.getOffhandItem());
        if (value == null) {
            return InteractionResult.PASS;
        }
        addImpulse(projectile, axis, value);
        return InteractionResult.SUCCESS;
    }

    /** {@code UseEntityCallback}：主手空手右键自己投出的悬停珍珠 → 放飞。 */
    public static InteractionResult onUse(Player player, InteractionHand hand, Entity target) {
        if (player == null || player.level().isClientSide() || !SogSettings.pearlTeleport) {
            return InteractionResult.PASS;
        }
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(target instanceof ThrowableProjectile projectile) || !isHovering(projectile)) {
            return InteractionResult.PASS;
        }
        Entity owner = projectile.getOwner();
        if (owner != null && owner != player) {
            return InteractionResult.PASS;
        }
        launch(projectile);
        return InteractionResult.SUCCESS;
    }

    /** 服务端停用时清掉状态，避免单机切换世界后残留。 */
    public static void clear() {
        ACCUMULATED.clear();
        ACCUMULATED_TOUCHED.clear();
        LAUNCHED.clear();
        nextPruneTick = 0L;
    }
}
