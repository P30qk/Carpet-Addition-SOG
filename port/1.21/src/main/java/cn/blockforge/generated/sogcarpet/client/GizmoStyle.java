package cn.blockforge.generated.sogcarpet.client;

/**
 * 1.21.7~1.21.10 用的 {@code net.minecraft.gizmos.GizmoStyle} 替代：记录描边色/线宽与填充色。
 */
public final class GizmoStyle {
    private final boolean fill;
    private final int fillColor;
    private final boolean stroke;
    private final int strokeColor;
    private final float strokeWidth;

    private GizmoStyle(boolean fill, int fillColor, boolean stroke, int strokeColor,
            float strokeWidth) {
        this.fill = fill;
        this.fillColor = fillColor;
        this.stroke = stroke;
        this.strokeColor = strokeColor;
        this.strokeWidth = strokeWidth;
    }

    public static GizmoStyle fill(int color) {
        return new GizmoStyle(true, color, false, 0, 0.0F);
    }

    public static GizmoStyle stroke(int color, float width) {
        return new GizmoStyle(false, 0, true, color, width);
    }

    public static GizmoStyle strokeAndFill(int strokeColor, float width, int fillColor) {
        return new GizmoStyle(true, fillColor, true, strokeColor, width);
    }

    boolean hasFill() {
        return this.fill;
    }

    int fillColor() {
        return this.fillColor;
    }

    boolean hasStroke() {
        return this.stroke;
    }

    int strokeColor() {
        return this.strokeColor;
    }

    float strokeWidth() {
        return this.strokeWidth;
    }
}
