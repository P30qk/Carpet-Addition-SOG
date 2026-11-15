package cn.blockforge.generated.sogcarpet.mixin;

import cn.blockforge.generated.sogcarpet.SogSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 巨人 AI 的补充：巨人打死村民时，像普通僵尸一样把村民感染成僵尸村民。
 *
 * <p>{@code Giant} 自己没有任何击杀后处理，而 {@link Entity#killedEntity} 是原版在
 * {@code LivingEntity#die} 里回调给“击杀者”的唯一收口点（僵尸的感染逻辑也挂在它自己的同名
 * 重写上），所以这里在 {@code Entity} 上挂钩、只对巨人生效，等价于给巨人补了一份
 * {@code Zombie#killedEntity} 的实现。</p>
 *
 * <p>机制逐条对齐原版普通僵尸：</p>
 * <ul>
 *   <li>只有难度为 {@code NORMAL} 或 {@code HARD} 才会感染（和平 / 简单不感染）；</li>
 *   <li>{@code NORMAL} 下再掷一次 50% 概率，掷不中就只是普通击杀；{@code HARD} 必定感染；</li>
 *   <li>转换走原版 {@code Villager#convertTo}，并把村民的职业数据、交易、经验、声望一并带过去，
 *       播放原版的 1026 音效（转换成功时 {@code killedEntity} 返回 false，与僵尸一致）。</li>
 * </ul>
 *
 * <p>只在“巨人旧版 AI”规则开启时生效；规则关闭时与原版完全一致。</p>
 */
@Mixin(Entity.class)
public abstract class GiantVillagerInfectionMixin {
    @Inject(method = "killedEntity(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("RETURN"), cancellable = true)
    private void sog$giantInfectsVillagers(ServerLevel level, LivingEntity victim,
            DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!SogSettings.giantOldAi || !cir.getReturnValueZ()) {
            return;
        }
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Giant giant) || !(victim instanceof Villager villager)) {
            return;
        }
        // 与普通僵尸完全相同的难度门槛：只有 NORMAL / HARD 会感染。
        Difficulty difficulty = level.getDifficulty();
        if (difficulty != Difficulty.NORMAL && difficulty != Difficulty.HARD) {
            return;
        }
        // NORMAL 下 50% 概率放弃感染；HARD 必定感染。
        if (difficulty != Difficulty.HARD && giant.getRandom().nextBoolean()) {
            return;
        }
        ZombieVillager converted = villager.convertTo(EntityTypes.ZOMBIE_VILLAGER,
                ConversionParams.single(villager, true, true),
                zombieVillager -> sog$finalizeConversion(giant, villager, level, zombieVillager));
        if (converted != null) {
            cir.setReturnValue(false);
        }
    }

    /** 原版 {@code Zombie#convertVillagerToZombieVillager} 里的收尾步骤，逐项对齐。 */
    private static void sog$finalizeConversion(Giant giant, Villager villager, ServerLevel level,
            ZombieVillager zombieVillager) {
        zombieVillager.setVillagerDataFinalized(villager.getVillagerDataFinalized());
        zombieVillager.finalizeSpawn(level,
                level.getCurrentDifficultyAt(zombieVillager.blockPosition()),
                EntitySpawnReason.CONVERSION, new Zombie.ZombieGroupData(false, true));
        zombieVillager.setVillagerData(villager.getVillagerData());
        zombieVillager.setGossips(villager.getGossips().copy());
        zombieVillager.setTradeOffers(villager.getOffers().copy());
        zombieVillager.setVillagerXp(villager.getVillagerXp());
        if (!giant.isSilent()) {
            level.levelEvent(null, 1026, giant.blockPosition(), 0);
        }
    }
}
