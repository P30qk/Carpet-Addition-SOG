package cn.blockforge.generated.sogcarpet.itemflow.track;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import cn.blockforge.generated.sogcarpet.itemflow.mixin.accessors.CompoundContainerAccessor;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;

public class Containers {
	public static void forEachLeaf(@Nullable Container container, Consumer<Container> sink) {
		if (container == null) {
			return;
		}

		if (container instanceof CompoundContainer compound) {
			CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
			forEachLeaf(accessor.itemflowtracker$container1(), sink);
			forEachLeaf(accessor.itemflowtracker$container2(), sink);
			return;
		}

		sink.accept(container);
	}
}
