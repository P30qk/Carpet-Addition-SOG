package cn.blockforge.generated.sogcarpet.itemflow.mixin.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.blockforge.generated.sogcarpet.itemflow.track.Tracking;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDropMixin {
	/**
	 * 26.3 把“丢出物品”的第三个参数从 {@code boolean thrownFromHand} 换成了
	 * {@link Prediction}（PREDICTED / SERVER_ONLY），这里跟着目标签名走；
	 * 判定逻辑只用到物品与玩家自己，第三个参数不参与。
	 */
	@Inject(
			method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD")
	)
	private void itemflowtracker$markThrownItem(ItemStack stack, boolean randomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
		if (!(((Object) this) instanceof Player self)) {return;}

		if (self.level().isClientSide() || stack.isEmpty() || !self.isAlive()) {
			return;
		}

		ItemStack offhand = self.getOffhandItem();

		if (stack == offhand) {
			return;
		}

		DyeColor dye = Tracking.dyeOf(offhand);
		//TODO 包裹一个地毯规则来决定是否启用快捷标记
		if (dye != null && Tracking.get(stack) == null) {
			Tracking.set(stack, Tracking.newMark(dye, stack.getCount()));
		}
	}
}
