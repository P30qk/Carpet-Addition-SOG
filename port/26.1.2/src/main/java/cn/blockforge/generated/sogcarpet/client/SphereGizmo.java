package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * A solid sphere gizmo, modelled on Lucidity's sculk sensor range visualiser (which uses Ryan's
 * Rendering Kit's {@code SphereShape}).
 *
 * <p>Vanilla's {@code Gizmos} helper only exposes cuboids, circles, lines, arrows, rects, points and
 * billboard text - there is no sphere. {@link Gizmo} itself is a public extension point though:
 * {@link GizmoPrimitives} hands out {@code addLine} / {@code addQuad} / {@code addTriangleFan}, so a
 * mod can emit any geometry it likes while still going through the whole gizmo pipeline (per-frame
 * collection, fading, the alpha multiplier, and the optional always-on-top depth bypass).
 *
 * <p>The surface is a latitude/longitude mesh, the same construction as
 * {@code SphereShape#generateSphereShape}.
 *
 * <h2>Why quads and not triangle fans</h2>
 *
 * <p>The obvious way to emit a sphere patch is {@code addTriangleFan}, and it is what this class used
 * to do. It is also a trap. {@code MultiBufferSource.BufferSource.getBuffer} flushes the previous
 * buffer whenever the render type cannot consolidate consecutive geometry:
 *
 * <pre>{@code
 * BufferBuilder builder = this.startedBuilders.get(renderType);
 * if (builder != null && !renderType.canConsolidateConsecutiveGeometry()) {
 *     this.endBatch(renderType, builder);   // a real draw call, every single time
 *     builder = null;
 * }
 * }</pre>
 *
 * <p>And {@code canConsolidateConsecutiveGeometry()} is just {@code !mode().connectedPrimitives}, so
 * every connected primitive mode - {@code TRIANGLE_FAN} included - is unmergeable. Vanilla's own
 * {@code DrawableGizmoPrimitives$Group} then makes it worse by calling {@code getBuffer} inside the
 * per-fan loop for fans, and outside the loop for quads. Net effect: one draw call per triangle fan,
 * versus one draw call for any number of quads. At 48 x 12 patches that is 576 draw calls per sphere,
 * per frame, per target - which is exactly the frame rate cliff this class used to cause. Adding
 * ImmediatelyFast fixes it because its {@code BatchableBufferSource} defers unmergeable render types
 * and draws them in one batch; relying on another mod for that is not reasonable, so this class now
 * goes through the mergeable quad path instead.
 *
 * <p>{@code debug_filled_box} back face culls, so each patch is submitted twice with opposite winding
 * to keep the sphere visible from the inside. Both copies land in the same consolidated vertex buffer,
 * so the second one costs vertices but not a draw call. That trade is worth it: doubling 1152
 * triangles is free next to 576 draw calls.
 *
 * <p>{@link #emit} runs every frame, so the mesh is a flat table of unit-sphere corner offsets built
 * once at class load rather than a few thousand {@code sin}/{@code cos} calls per frame.
 */
public record SphereGizmo(Vec3 center, float radius, int color) implements Gizmo {
	/**
	 * Segments per full circle (longitude). Mostly drives how round the silhouette looks; the patch
	 * count is {@code SEGMENTS * PARALLEL_BANDS}, so it is also the cost lever.
	 */
	private static final int SEGMENTS = 48;
	/** Latitude bands. Drives how round the top and bottom of the silhouette look. */
	private static final int PARALLEL_BANDS = 12;

	/**
	 * Unit-sphere corners of every patch, as {@code [band * SEGMENTS + segment][corner * 3 + axis]}.
	 * Corner order is topA, topB, bottomB, bottomA.
	 */
	private static final float[][] CORNERS = corners();

	@Override
	public void emit(final GizmoPrimitives primitives, final float alphaMultiplier) {
		int tinted = ARGB.multiplyAlpha(this.color, alphaMultiplier);
		double x = this.center.x;
		double y = this.center.y;
		double z = this.center.z;
		float radius = this.radius;

		for (float[] patch : CORNERS) {
			Vec3 topA = corner(x, y, z, radius, patch, 0);
			Vec3 topB = corner(x, y, z, radius, patch, 3);
			Vec3 bottomB = corner(x, y, z, radius, patch, 6);
			Vec3 bottomA = corner(x, y, z, radius, patch, 9);

			// Outer face, then the same patch wound the other way for the inner face.
			primitives.addQuad(topA, topB, bottomB, bottomA, tinted);
			primitives.addQuad(bottomA, bottomB, topB, topA, tinted);
		}
	}

	private static Vec3 corner(
		final double x,
		final double y,
		final double z,
		final float radius,
		final float[] patch,
		final int offset
	) {
		return new Vec3(
			x + patch[offset] * radius,
			y + patch[offset + 1] * radius,
			z + patch[offset + 2] * radius
		);
	}

	private static float[][] corners() {
		float[][] table = new float[PARALLEL_BANDS * SEGMENTS][];

		for (int band = 0; band < PARALLEL_BANDS; band++) {
			double polarA = Math.PI * band / PARALLEL_BANDS;
			double polarB = Math.PI * (band + 1) / PARALLEL_BANDS;

			for (int segment = 0; segment < SEGMENTS; segment++) {
				double azimuthA = Math.TAU * segment / SEGMENTS;
				double azimuthB = Math.TAU * (segment + 1) / SEGMENTS;

				float[] patch = new float[12];
				put(patch, 0, polarA, azimuthA);
				put(patch, 3, polarA, azimuthB);
				put(patch, 6, polarB, azimuthB);
				put(patch, 9, polarB, azimuthA);
				table[band * SEGMENTS + segment] = patch;
			}
		}

		return table;
	}

	/** {@code polar} 0 is the north pole, {@code PI} the south pole. */
	private static void put(final float[] into, final int offset, final double polar, final double azimuth) {
		double sin = Math.sin(polar);
		into[offset] = (float) (sin * Math.cos(azimuth));
		into[offset + 1] = (float) Math.cos(polar);
		into[offset + 2] = (float) (sin * Math.sin(azimuth));
	}
}
