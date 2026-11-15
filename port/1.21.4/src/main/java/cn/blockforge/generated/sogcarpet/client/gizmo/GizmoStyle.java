package cn.blockforge.generated.sogcarpet.client.gizmo;

/** 与 1.21.11+ {@code net.minecraft.gizmos.GizmoStyle} 对齐的颜色/线宽描述。 */
public final class GizmoStyle {
    private final int stroke;
    private final float strokeWidth;
    private final int fill;
    private final boolean hasStroke;
    private final boolean hasFill;

    private GizmoStyle(int stroke, float strokeWidth, int fill, boolean hasStroke, boolean hasFill) {
        this.stroke = stroke;
        this.strokeWidth = strokeWidth;
        this.fill = fill;
        this.hasStroke = hasStroke;
        this.hasFill = hasFill;
    }

    public static GizmoStyle stroke(int color) {
        return new GizmoStyle(color, 1.0F, 0, true, false);
    }

    public static GizmoStyle stroke(int color, float width) {
        return new GizmoStyle(color, width, 0, true, false);
    }

    public static GizmoStyle fill(int color) {
        return new GizmoStyle(0, 1.0F, color, false, true);
    }

    public static GizmoStyle strokeAndFill(int strokeColor, float strokeWidth, int fillColor) {
        return new GizmoStyle(strokeColor, strokeWidth, fillColor, true, true);
    }

    public boolean hasStroke() {
        return this.hasStroke;
    }

    public boolean hasFill() {
        return this.hasFill;
    }

    public int stroke() {
        return this.stroke;
    }

    public float strokeWidth() {
        return this.strokeWidth;
    }

    public int fill() {
        return this.fill;
    }

    public int multipliedStroke(float alphaMultiplier) {
        return multiplyAlpha(this.stroke, alphaMultiplier);
    }

    public int multipliedFill(float alphaMultiplier) {
        return multiplyAlpha(this.fill, alphaMultiplier);
    }

    /** 按比例缩小 alpha（颜色其余分量不动）；1.21.10 的 ARGB 没有 multiplyAlpha。 */
    public static int multiplyAlpha(int color, float multiplier) {
        int alpha = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 0xFF) * multiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
