package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSpawnTrace;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * “刷怪游走可视化”的服务端采集。
 *
 * <p><b>本轮起只在“真正刷出生物”那一瞬间记一条出生事件。</b> 客户端拿到
 * {@code 实体号 + 出生坐标} 后自己盯这只生物每一步移动，把<b>它真实走过的路</b>画成轨迹；
 * 以前那种把 {@code NaturalSpawner} 挑位置的候选点整条记下来的做法已经删掉——
 * 那条路线属于“刷怪器挑位置”，不是生物走的路，画出来只会对不上。</p>
 *
 * <p>目标方法仍是原版真正干活的六参重载
 * {@code NaturalSpawner#spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess, BlockPos, SpawnPredicate, AfterSpawnCallback)}，
 * 但只保留一个注入点：包装 {@code spawnCallback.run(mob, chunk)}。</p>
 * <ul>
 *   <li>{@code spawnCallback.run} 紧跟在生物进世界之后，是原版唯一“这只生物真的刷出来了”的
 *       时机；先原样回调，再按 {@code mob.getId()} 与 {@code mob.blockPosition()} 记一条事件。
 *       这里刻意不去包装 Carpet 已经占用的 {@code addFreshEntityWithPassengers}，避免冲突。</li>
 *   <li>不再需要 {@code @Inject HEAD/RETURN}，也不再包装 {@code BlockPos.MutableBlockPos#set}：
 *       每 tick 刷出上百只生物时，服务端省掉了整条候选点列表的分配与复制。</li>
 * </ul>
 *
 * <p><b>为什么用 {@code @WrapOperation} 而不是原版 {@code @Redirect}：</b>
 * 26.2 工具链（sponge-mixin 0.17.4）把 {@code @Redirect.at()} 改成了数组签名，javac 编出的
 * 字节码里 {@code at} 是 {@code List}；而 fabric-loader 0.19.3 自带的 mixinextras 0.5.4 在
 * {@code FactoryRedirectWrapperMixinTransformer} 里仍把该值强转成单个 {@code AnnotationNode}，
 * 于是目标类 {@link NaturalSpawner} 一被加载就抛 {@code ClassCastException}（"Exception ticking
 * world"）把服务器打崩。{@code @WrapOperation} 是 mixinextras 自己的注入器注解，它在 0.5.4 里
 * 就是按数组形态解析 {@code at} 的，不存在这个兼容问题（详见 {@link ServerLevelMixin} 注释）。</p>
 *
 * <p>只在 {@link SogSpawnTrace#isRecording()} 为真时记录，也就是“规则开启 &amp;&amp; 至少一个
 * 装了本模组的玩家在线”。没人看的时候注入点仍在，但连一条事件都不记，全部空转，
 * 专用服务器上不会为一个没人看的开关白做分配。</p>
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    /** 六参重载的完整描述符（Mixin 用它区分另一个三参重载）。 */
    private static final String SPAWN_CATEGORY_FOR_POSITION =
            "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
                    + "Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/world/level/chunk/ChunkAccess;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
                    + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V";

    @WrapOperation(method = SPAWN_CATEGORY_FOR_POSITION, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;run(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/chunk/ChunkAccess;)V"))
    private static void sog$captureSpawn(NaturalSpawner.AfterSpawnCallback callback, Mob mob,
            ChunkAccess chunk, Operation<Void> original) {
        original.call(callback, mob, chunk);
        if (SogSpawnTrace.isRecording()) {
            SogSpawnTrace.spawned(mob.level().dimension(), mob.getId(), mob.blockPosition());
        }
    }
}
