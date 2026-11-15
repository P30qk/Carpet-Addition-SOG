package cn.blockforge.generated.sogcarpet.itemflow.mixin.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.blockforge.generated.sogcarpet.itemflow.track.Tracking;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

@Mixin(Player.class)
public abstract class LivingEntityDropMixin {
	// 1.21.3 没有 LivingEntity#drop(ItemStack, boolean, boolean)，玩家丢弃走的是
	// Player#drop(ItemStack, boolean, boolean)，所以这里把目标换成 Player 的同名方法。
	@Inject(
			method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD")
	)
	private void itemflowtracker$markThrownItem(ItemStack stack, boolean randomly, boolean thrownFromHand, CallbackInfoReturnable<ItemEntity> cir) {
		Player self = (Player) (Object) this;

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
