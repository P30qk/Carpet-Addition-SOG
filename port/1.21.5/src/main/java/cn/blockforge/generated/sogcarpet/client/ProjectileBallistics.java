package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 投掷物弹道模拟（纯客户端），供「投掷物轨迹可视化」画<b>还没走过的那段路</b>。
 *
 * <p>物理模型与 26.2 原版逐 tick 步进一致（对着字节码核对过）：</p>
 * <ul>
 *   <li>{@code ThrowableProjectile}：每 tick 先重力后阻力，
 *       {@code v = (v + (0,-0.03,0)) · 0.99}，然后 {@code pos += v}；</li>
 *   <li>{@code AbstractArrow}：阻力 0.99、重力 0.05（顺序上重力在阻力之后，
 *       这里统一按“先重力后阻力”处理，单 tick 差约 0.0003 格，看不出来）。</li>
 * </ul>
 *
 * <p>重力与阻力系数直接取自 {@link MotionPhysics}（速度/动量可视化画“外力”用的同一套参数），
 * 所以两处对同一个实体给出的 g 与 f 完全一致；手持物品还没有实体，就按物品类型取原版初速与
 * 该类型的 g/f。</p>
 *
 * <p>碰撞只用方块判定（{@code ClipContext.Block.COLLIDER} + 不撞流体），每 tick 一段直线 clip，
 * 命中即停。实体命中不预测——原版箭矢/珍珠打中生物后的行为（弹开、传送、破碎）差异太大，
 * 提前画会把落点指错。</p>
 */
public final class ProjectileBallistics {
    /** 可投掷物（雪球/鸡蛋/末影珍珠/风弹）的每 tick 重力。 */
    public static final double THROWABLE_GRAVITY = 0.03D;
    /** 箭、三叉戟这类 {@code AbstractArrow} 的每 tick 重力。 */
    public static final double ARROW_GRAVITY = 0.05D;
    /** 空气阻力系数（两种弹射物都是 0.99）。 */
    public static final double AIR_DRAG = 0.99D;
    /** 水中的每 tick 速度保留比例：投掷物 0.8、箭 0.6（原版 inertia 常量）。 */
    public static final double THROWABLE_WATER_DRAG = 0.8D;
    public static final double ARROW_WATER_DRAG = 0.6D;
    /**
     * 投掷物真实的出生点相对眼睛的偏移：原版 {@code ThrowableItemProjectile} 的构造器用
     * {@code getEyeY() - 0.1}，而不是“眼睛 + 0.3”。预览从眼睛前方 0.3 格起步会让整条弹道
     * 比实际高出一大截、落点也偏近，是“预览轨迹与实际不符”的主要原因之一。
     */
    public static final double SPAWN_EYE_DROP = 0.1D;
    /** 单条预览最多模拟多少 tick；再远就没有观看价值，也能兜住“区块没加载”时一路飞出去。 */
    public static final int MAX_PREVIEW_TICKS = 160;
    /**
     * 碰撞检测的子步长度（格）。
     *
     * <p>“远传珍珠”的速度不设上限，一次 tick 的位移可能横跨上千格；原版 {@code BlockGetter#clip}
     * 是逐格遍历（O(跨越方块数)），直接拿整段位移去 clip 会把客户端线程拖住。这里把每 tick 拆成
     * 不超过这个长度的子步逐个检测，成本与“实际跨越的方块数”同级，而且命中就停。</p>
     */
    private static final double MAX_CLIP_STEP = 8.0D;
    /**
     * 单条预览的碰撞检测次数上限。
     *
     * <p>超过之后剩下的 tick 只做直线外推（不再逐格检测）：速度大到这个程度时，逐格检测既expensive
     * 又看不出差别，而客户端的流畅度必须优先保证。</p>
     */
    private static final int MAX_CLIP_STEPS = 2048;

    /**
     * 一种投掷方式的弹道参数。
     *
     * @param speed   初速度（格/tick）
     * @param gravity 每 tick 重力
     * @param drag    每 tick 速度保留比例（原版 0.99）
     * @param kind    物品注册名路径，仅用于调试与文案
     */
    public record Profile(double speed, double gravity, double drag, String kind) {
    }

    /**
     * 一次模拟的结果。
     *
     * @param points  从起点到落点（或模拟上限）的折线点，至少一个点
     * @param hit     命中方块的位置；没有命中时为 {@code null}
     * @param blocked 是否被方块挡住
     */
    public record Result(List<Vec3> points, Vec3 hit, boolean blocked) {
    }

    private ProjectileBallistics() {
    }

    /**
     * 手持物品的弹道参数；不是可投掷物时返回 {@code null}（不画预览）。
     *
     * <p>初速取自原版：雪球/鸡蛋/末影珍珠/风弹 1.5，喷溅与滞留药水 0.5，附魔之瓶 0.7，
     * 三叉戟 2.5，弩 3.15，弓是 {@code getPowerForTime(拉弓 tick) × 3.0}。</p>
     *
     * <p><b>没有在拉弓的弓直接返回 {@code null}</b>：以前按满弓画一条“上限预览”，但玩家松手
     * 时多数情况下蓄力不足一半，看到的虚线和真实落点差得很远，所以宁可不画。弩是否已装填
     * 由调用方判断（{@code CrossbowItem.isCharged}）。</p>
     */
    public static Profile heldProfile(ItemStack stack, int useTicks, boolean using) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();
        return switch (path) {
            case "snowball", "egg", "ender_pearl", "wind_charge" ->
                    new Profile(1.5D, THROWABLE_GRAVITY, AIR_DRAG, path);
            case "splash_potion", "lingering_potion" ->
                    new Profile(0.5D, THROWABLE_GRAVITY, AIR_DRAG, path);
            case "experience_bottle" ->
                    new Profile(0.7D, THROWABLE_GRAVITY, AIR_DRAG, path);
            case "trident" -> new Profile(2.5D, ARROW_GRAVITY, AIR_DRAG, path);
            case "crossbow" -> new Profile(3.15D, ARROW_GRAVITY, AIR_DRAG, path);
            case "bow" -> {
                // 只在真的拉弓时预览：用当前蓄力换算初速，和松手时射出的一致。
                if (!using || useTicks <= 0) {
                    yield null;
                }
                float power = BowItem.getPowerForTime(useTicks);
                if (power < 0.1F) {
                    yield null;
                }
                yield new Profile(3.0D * power, ARROW_GRAVITY, AIR_DRAG, path);
            }
            default -> null;
        };
    }

    /**
     * 一个实体的弹道参数：速度取它的实际速度，重力取它自己的 {@code getGravity()}，
     * 阻力按“空气中 0.99 / 水中 0.8（箭 0.6）”取——这样外推出来的虚线与实际飞行的
     * 折线基本重合，不会出现“水中预览比实际飞得远”的偏差。
     */
    public static Profile entityProfile(Entity entity) {
        if (entity == null) {
            return null;
        }
        Vec3 velocity = entity.getDeltaMovement();
        double gravity = entity.getGravity();
        if (gravity <= 0.0D) {
            gravity = -MotionPhysics.gravity(entity).y;
        }
        boolean arrow = entity instanceof AbstractArrow;
        double drag = entity.isInWater()
                ? (arrow ? ARROW_WATER_DRAG : THROWABLE_WATER_DRAG)
                : AIR_DRAG;
        return new Profile(velocity.length(), gravity, drag, entity.getType().toString());
    }

    /**
     * 从 {@code start} 以 {@code velocity} 开始模拟，逐 tick 推进并检测方块碰撞。
     *
     * @param owner 用来构造碰撞上下文（玩家的碰撞箱尺寸等），不能为 null
     */
    public static Result simulate(Level level, Vec3 start, Vec3 velocity, double gravity,
            double drag, int maxTicks, Entity owner) {
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        if (level == null || owner == null || velocity == null || !isFinite(velocity)) {
            return new Result(List.copyOf(points), null, false);
        }
        double dragFactor = drag <= 0.0D ? 1.0D : drag;
        int ticks = Math.max(1, Math.min(maxTicks, MAX_PREVIEW_TICKS));
        int clipBudget = MAX_CLIP_STEPS;
        Vec3 pos = start;
        Vec3 vel = velocity;
        for (int i = 0; i < ticks; i++) {
            // 原版每个 tick 的顺序是「先重力、再阻力、最后按新速度位移」（ThrowableProjectile.tick
            // 里 applyGravity → applyInertia → setPos），所以第一步也先把速度改一次再走。
            vel = new Vec3(vel.x * dragFactor, (vel.y - gravity) * dragFactor,
                    vel.z * dragFactor);
            if (vel.lengthSqr() < 1.0E-8D) {
                break;
            }
            Vec3 next = pos.add(vel);
            if (!isFinite(next)) {
                break;
            }
            // 位移太大就切成子步：既保证碰撞正确，也让最坏情况的工作量有上界。
            double distance = pos.distanceTo(next);
            int steps = (int) Math.ceil(distance / MAX_CLIP_STEP);
            if (steps < 1) {
                steps = 1;
            }
            Vec3 from = pos;
            for (int step = 1; step <= steps; step++) {
                Vec3 to = step == steps
                        ? next
                        : pos.lerp(next, step / (double) steps);
                if (clipBudget <= 0) {
                    // 预算用完：剩下的子步（以及后面所有 tick）只做直线外推。
                    break;
                }
                clipBudget--;
                BlockHitResult clip = level.clip(new ClipContext(from, to,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                if (clip.getType() != HitResult.Type.MISS) {
                    Vec3 hit = clip.getLocation();
                    points.add(hit);
                    return new Result(List.copyOf(points), hit, true);
                }
                from = to;
            }
            pos = next;
            points.add(pos);
        }
        return new Result(List.copyOf(points), null, false);
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
    }
}
