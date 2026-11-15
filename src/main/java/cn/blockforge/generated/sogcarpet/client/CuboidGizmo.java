package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A box gizmo that stays drawable from the inside, unlike vanilla's {@code CuboidGizmo}.
 *
 * <p>Vanilla emits its faces through {@code GizmoPrimitives#addQuad}, which lands in the
 * {@code debug_filled_box} render pipeline - and that pipeline keeps back face culling enabled. As
 * soon as the camera moves inside the box every face points away from it, so all six are culled at
 * once and the volume disappears.
 *
 * <p>Here each face is still emitted as a quad, but submitted twice with opposite winding. That
 * keeps the faces visible from both sides while staying on the mergeable quad render type
 * ({@code debug_filled_box} uses {@code VertexFormat.Mode.QUADS}, so the whole box is uploaded in
 * one buffer instead of one draw call per face). The stroke is copied verbatim from vanilla and is
 * unaffected, since lines are never culled.
 */
public record CuboidGizmo(AABB aabb, GizmoStyle style) implements Gizmo {
	@Override
	public void emit(final GizmoPrimitives primitives, final float alphaMultiplier) {
		double x0 = this.aabb.minX;
		double y0 = this.aabb.minY;
		double z0 = this.aabb.minZ;
		double x1 = this.aabb.maxX;
		double y1 = this.aabb.maxY;
		double z1 = this.aabb.maxZ;

		if (this.style.hasFill()) {
			int color = this.style.multipliedFill(alphaMultiplier);
			doubleSidedQuad(primitives,
				new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), color);
			doubleSidedQuad(primitives,
				new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), color);
			doubleSidedQuad(primitives,
				new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), color);
			doubleSidedQuad(primitives,
				new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), color);
			doubleSidedQuad(primitives,
				new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), color);
			doubleSidedQuad(primitives,
				new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), color);
		}

		if (this.style.hasStroke()) {
			int color = this.style.multipliedStroke(alphaMultiplier);
			float width = this.style.strokeWidth();
			primitives.addLine(new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), color, width);
			primitives.addLine(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), color, width);
			primitives.addLine(new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), color, width);
			primitives.addLine(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), color, width);
			primitives.addLine(new Vec3(x1, y1, z0), new Vec3(x0, y1, z0), color, width);
			primitives.addLine(new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), color, width);
			primitives.addLine(new Vec3(x0, y1, z1), new Vec3(x0, y0, z1), color, width);
			primitives.addLine(new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), color, width);
			primitives.addLine(new Vec3(x1, y0, z1), new Vec3(x1, y0, z0), color, width);
			primitives.addLine(new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), color, width);
			primitives.addLine(new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), color, width);
			primitives.addLine(new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), color, width);
		}
	}

	private static void doubleSidedQuad(
		final GizmoPrimitives primitives,
		final Vec3 a,
		final Vec3 b,
		final Vec3 c,
		final Vec3 d,
		final int color
	) {
		// debug_filled_box back face culls, so submit both windings. Both copies land in the same
		// mergeable vertex buffer, so the second one costs vertices but not a draw call.
		primitives.addQuad(a, b, c, d, color);
		primitives.addQuad(d, c, b, a, color);
	}
}
