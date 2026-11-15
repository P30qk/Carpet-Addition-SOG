package cn.blockforge.generated.sogcarpet.client.gizmo;

import net.minecraft.world.phys.Vec3;

/** 与 1.21.11+ {@code net.minecraft.gizmos.TextGizmo} 对齐的公告板文字。 */
public final class TextGizmo implements Gizmo {
    private final Vec3 pos;
    private final String text;
    private final Style style;

    public TextGizmo(Vec3 pos, String text, Style style) {
        this.pos = pos;
        this.text = text;
        this.style = style;
    }

    public Vec3 pos() {
        return this.pos;
    }

    public String text() {
        return this.text;
    }

    public Style style() {
        return this.style;
    }

    @Override
    public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
        primitives.addText(this.pos, this.text, this.style);
    }

    /** 文字颜色与缩放。1.21.10 只需要这两项，左对齐参数保留为兼容占位。 */
    public static final class Style {
        public static final float DEFAULT_SCALE = 1.0F;

        private final int color;
        private final float scale;
        private final float adjustLeft;
        private final boolean leftAligned;

        private Style(int color, float scale, float adjustLeft, boolean leftAligned) {
            this.color = color;
            this.scale = scale;
            this.adjustLeft = adjustLeft;
            this.leftAligned = leftAligned;
        }

        public static Style whiteAndCentered() {
            return new Style(0xFFFFFFFF, DEFAULT_SCALE, 0.0F, false);
        }

        public static Style forColorAndCentered(int color) {
            return new Style(color, DEFAULT_SCALE, 0.0F, false);
        }

        public static Style forColor(int color) {
            return new Style(color, DEFAULT_SCALE, 0.0F, false);
        }

        public Style withScale(float scale) {
            return new Style(this.color, scale, this.adjustLeft, this.leftAligned);
        }

        public Style withLeftAlignment(float adjustLeft) {
            return new Style(this.color, this.scale, adjustLeft, true);
        }

        public int color() {
            return this.color;
        }

        public float scale() {
            return this.scale;
        }

        public float adjustLeft() {
            return this.adjustLeft;
        }

        public boolean leftAligned() {
            return this.leftAligned;
        }
    }
}
