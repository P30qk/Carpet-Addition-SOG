package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁止傻子村民生成 / 禁止傻子村民占用床。
 *
 * <p>生成：26.2 的傻子职业只随结构（村庄）实体数据进来——{@code finalizeSpawn}
 * 执行时 NBT 已应用，规则开启时把 NITWIT 改写为 NONE，新村民变成无业，
 * 之后仍可正常认领工作站点。已存在的傻子不受影响。</p>
 *
 * <p>床：村民认领床走 brain 的 HOME 记忆（AcquirePoi 写入）。规则开启时，
 * 在每个 AI 步进<b>开始时</b>先把 HOME 退订，把床还回可认领池；之后 brain tick
 * 里傻子想再认领会被 {@link NitwitBrainMixin} 直接拦掉，所以它不会再占床，
 * 也不会躺上去睡。</p>
 */
@Mixin(Villager.class)
public abstract class NitwitVillagerMixin {
    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void sog$denyNitwitSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            EntitySpawnReason reason, SpawnGroupData spawnData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (!SogSettings.nitwitSpawnDisabled) {
            return;
        }
        Villager self = (Villager) (Object) this;
        VillagerData data = self.getVillagerData();
        if (data.getProfession() == VillagerProfession.NITWIT) {
            // 1.21.4 职业还是普通对象：直接把 NONE 写回 VillagerData。
            self.setVillagerData(data.setProfession(VillagerProfession.NONE));
        }
    }

    /**
     * 在每个 AI 步进的最前面清掉傻子身上的床，并把已经躺在床上的傻子立刻踢下床。
     *
     * <p>放在 HEAD 而不是 TAIL：{@code customServerAiStep} 一进来就 tick brain，
     * 如果等到末尾才清理，傻子已经在这一 tick 里把床抢走并睡下了。</p>
     *
     * <p>光调 {@code releasePoi} 是不够的：它只把床还回可认领池，并不清除 HOME 记忆，
     * 也不复位睡觉姿态。所以规则开启前就认过床的傻子会一直拿着那张床的坐标，
     * 每隔几秒（{@code SleepInBed} 醒后 100 tick 冷却）又走过去躺下、被踢起来，
     * 如此反复。这里把三件事一起做掉：{@link Villager#stopSleeping()} 复位
     * sleepingPos + Pose.SLEEPING 并清掉床方块 OCCUPIED 标记；{@code releasePoi}
     * 归还 POI 票据；{@code eraseMemory(HOME)} 抹掉床坐标，让它连“走向床”这个念头
     * 都没有（{@code SleepInBed} 与 {@code SetWalkTargetFromBlockMemory} 都要求 HOME
     * 存在）。配合 {@link NitwitBrainMixin} 与 {@link NitwitSleepMixin}，傻子不会上床。</p>
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"))
    private void sog$nitwitGiveUpBed(ServerLevel level, CallbackInfo ci) {
        if (!SogSettings.nitwitBedDisabled) {
            return;
        }
        Villager self = (Villager) (Object) this;
        if (self.getVillagerData().getProfession() != VillagerProfession.NITWIT) {
            return;
        }
        // 已经躺在床上的（规则开启前睡下的、或从存档/其他路径睡进去的）立刻踢下床。
        if (self.isSleeping()) {
            self.stopSleeping();
        }
        if (self.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
            // 顺序不能反：releasePoi 要靠 HOME 才知道该归还哪张床。
            self.releasePoi(MemoryModuleType.HOME);
            self.getBrain().eraseMemory(MemoryModuleType.HOME);
        }
    }
}
