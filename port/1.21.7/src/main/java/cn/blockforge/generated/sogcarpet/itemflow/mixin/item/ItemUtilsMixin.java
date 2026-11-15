package cn.blockforge.generated.sogcarpet.itemflow.mixin.item;

import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import cn.blockforge.generated.sogcarpet.itemflow.track.TrackMark;
import cn.blockforge.generated.sogcarpet.itemflow.track.Tracking;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;


@Mixin(ItemUtils.class)
public abstract class ItemUtilsMixin {

	@ModifyVariable(method = "onContainerDestroyed", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static Stream<ItemStack> itemflowtracker$markSpilledContents(
			Stream<ItemStack> value, ItemEntity container, Stream<ItemStack> contents) {
		TrackMark source = Tracking.get(container.getItem());

		if (source == null) {
			return value;
		}

		List<ItemStack> spilled = value.toList();
		int total = spilled.stream().mapToInt(ItemStack::getCount).sum();

		if (total <= 0) {
			return spilled.stream();
		}

		TrackMark mark = Tracking.handOver(source, total);
		spilled.forEach(stack -> Tracking.setIfAbsent(stack, mark));
		return spilled.stream();
	}
}
