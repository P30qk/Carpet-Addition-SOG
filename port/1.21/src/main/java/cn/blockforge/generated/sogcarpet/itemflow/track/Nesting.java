package cn.blockforge.generated.sogcarpet.itemflow.track;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;


public final class Nesting {
	private static final int MAX_DEPTH = 4;

	private Nesting() {
	}

	@Nullable
	public static TrackMark inStack(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		TrackMark mark = Tracking.get(stack);

		if (mark != null) {
			return mark;
		}

		return inContents(
				stack.get(DataComponents.CONTAINER),
				stack.get(DataComponents.BUNDLE_CONTENTS),
				0);
	}

	public static boolean carriesMark(@Nullable ItemStack stack) {
		return inStack(stack) != null;
	}

	@Nullable
	public static TrackMark inContainer(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			TrackMark mark = inStack(container.getItem(slot));

			if (mark != null) {
				return mark;
			}
		}

		return null;
	}

	@Nullable
	public static TrackMark inEntity(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return inStack(item.getItem());
		}

		return entity instanceof Container container ? inContainer(container) : null;
	}


	/**
	 * 递归查一层嵌套物品里的标记。
	 *
	 * <p>26.x 把容器/收纳袋的内容换成了 {@code ItemStackTemplate}（轻量模板），1.21.11 里还是
	 * 完整的 {@link ItemStack}，所以这里直接用 {@link #inStack(ItemStack)} 继续往下钻；
	 * 深度上限照旧防止循环嵌套。</p>
	 */
	@Nullable
	private static TrackMark inNested(ItemStack nested, int depth) {
		if (depth >= MAX_DEPTH) {
			return null;
		}

		return inStack(nested);
	}

	@Nullable
	private static TrackMark inContents(
			@Nullable ItemContainerContents container,
			@Nullable BundleContents bundle,
			int depth) {
		if (container != null) {
			for (ItemStack nested : container.nonEmptyItems()) {
				TrackMark mark = inNested(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		if (bundle != null) {
			for (ItemStack nested : bundle.items()) {
				TrackMark mark = inNested(nested, depth + 1);

				if (mark != null) {
					return mark;
				}
			}
		}

		return null;
	}
}
