package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁止傻子村民认领床（“禁止傻子村民占用床”规则的核心）。
 *
 * <p>26.2 的村民认床走 brain 的 HOME 记忆，由 {@code AcquirePoi} 行为在
 * {@link Brain#tick(ServerLevel, LivingEntity)} 里写入。{@code AcquirePoi} 的顺序是
 * 先向 {@code PoiManager} take 一张床的 POI 票据，再写 HOME——所以不能在写入时直接
 * 取消：那样票据已经到手却永远收不回来，村子里的床会被傻子一张张漏光。</p>
 *
 * <p>这里改成写入后立刻清理：趁 HOME 还在，用 {@link Villager#releasePoi} 把票据
 * 规规矩矩还回去，再抹掉 HOME。效果和“禁止认领”一样——HOME 一没，
 * {@code SleepInBed} 和 {@code SetWalkTargetFromBlockMemory} 都不会生效，傻子既不会
 * 走到床边也不会躺下，床则回到可认领池给其他村民用。</p>
 */
@Mixin(Brain.class)
public abstract class NitwitBrainMixin {
    /** 当前正在 tick 的实体（仅 brain 自己用，用来判断写入者是不是傻子）。 */
    @Unique
    private LivingEntity sog$tickOwner;

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"))
    private void sog$captureOwner(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        this.sog$tickOwner = entity;
    }

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"))
    private void sog$clearOwner(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        this.sog$tickOwner = null;
    }

    @Inject(method = "setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;"
            + "Ljava/lang/Object;)V", at = @At("TAIL"))
    private void sog$dropHomeObject(MemoryModuleType<?> type, Object value, CallbackInfo ci) {
        sog$dropHome(type);
    }

    @Inject(method = "setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;"
            + "Ljava/lang/Object;J)V", at = @At("TAIL"))
    private void sog$dropHomeExpiry(MemoryModuleType<?> type, Object value, long expiry,
            CallbackInfo ci) {
        sog$dropHome(type);
    }

    @Inject(method = "setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;"
            + "Ljava/util/Optional;)V", at = @At("TAIL"))
    private void sog$dropHomeOptional(MemoryModuleType<?> type, Optional<?> value, CallbackInfo ci) {
        sog$dropHome(type);
    }

    /** 刚写进 HOME、且主人是傻子村民时，归还票据并抹掉记忆。 */
    @Unique
    private void sog$dropHome(MemoryModuleType<?> type) {
        if (!SogSettings.nitwitBedDisabled || type != MemoryModuleType.HOME) {
            return;
        }
        if (!(this.sog$tickOwner instanceof Villager villager)
                || !villager.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
            return;
        }
        villager.releasePoi(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
    }
}
