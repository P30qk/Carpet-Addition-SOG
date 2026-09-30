package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
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
 * 傻子的 AI 步进末尾检测到 HOME 就调用 {@code releasePoi(HOME)} 立即退订，
 * 床回到可认领池，供其他村民使用。</p>
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
        if (data.profession().is(VillagerProfession.NITWIT)) {
            // 26.2 里职业是注册表条目：VillagerProfession.NONE 是 ResourceKey，
            // 要从内置注册表取出对应的 Holder 才能写回 VillagerData（原版同样写法）。
            self.setVillagerData(data.withProfession(
                    BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE)));
        }
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void sog$nitwitGiveUpBed(ServerLevel level, CallbackInfo ci) {
        if (!SogSettings.nitwitBedDisabled) {
            return;
        }
        Villager self = (Villager) (Object) this;
        if (!self.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
            return;
        }
        if (self.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
            self.releasePoi(MemoryModuleType.HOME);
        }
    }
}
