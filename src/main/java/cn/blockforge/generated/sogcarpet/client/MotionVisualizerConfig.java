package cn.blockforge.generated.sogcarpet.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * “速度/动量/加速度可视化”的显示项配置（纯客户端，保存在
 * {@code config/sog_carpet-motion.txt}）。
 *
 * <p>这些开关只影响本客户端怎么画，不影响服务端规则；所以不走逐玩家同步，直接落本地文件。
 * 每个玩家各自一份，和 Ctrl+V 总控里的“渲染开关”是两回事。</p>
 */
public final class MotionVisualizerConfig {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-motion.txt");

    /** 用动量模式（true）还是速度模式（false）。 */
    private static boolean momentum;
    /** 坐标系：true 用球极坐标（r/仰角/方位角），false 用 X/Y/Z 直角坐标轴。 */
    private static boolean spherical;
    /** 是否画三根坐标轴。 */
    private static boolean axes = true;
    /** 是否画每根轴上的矢量。 */
    private static boolean axisVectors = true;
    /** 是否画重力/阻力这两项“外力”。 */
    private static boolean forces = true;
    /** 是否画外部施加的冲量矢量。 */
    private static boolean external = true;
    /** 是否画从轴心指出的合矢量。 */
    private static boolean resultant = true;
    /** 合矢量与 X/Y/Z 轴的夹角是否分别显示。 */
    private static boolean angleX = true;
    private static boolean angleY = true;
    private static boolean angleZ = true;
    /** 仰角、方位角是否显示。 */
    private static boolean elevation = true;
    private static boolean azimuth = true;
    /** 实体自身的俯仰/偏航角是否显示。 */
    private static boolean pitchYaw = true;

    private static boolean loaded;

    private MotionVisualizerConfig() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try {
            for (String raw : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                boolean value = Boolean.parseBoolean(line.substring(eq + 1).trim());
                switch (key) {
                    case "momentum" -> momentum = value;
                    case "spherical" -> spherical = value;
                    case "axes" -> axes = value;
                    case "axisVectors" -> axisVectors = value;
                    case "forces" -> forces = value;
                    case "external" -> external = value;
                    case "resultant" -> resultant = value;
                    case "angleX" -> angleX = value;
                    case "angleY" -> angleY = value;
                    case "angleZ" -> angleZ = value;
                    case "elevation" -> elevation = value;
                    case "azimuth" -> azimuth = value;
                    case "pitchYaw" -> pitchYaw = value;
                    default -> {
                    }
                }
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
    }

    public static boolean momentum() {
        ensureLoaded();
        return momentum;
    }

    public static void setMomentum(boolean value) {
        momentum = value;
        save();
    }

    public static boolean spherical() {
        ensureLoaded();
        return spherical;
    }

    public static void setSpherical(boolean value) {
        spherical = value;
        save();
    }

    public static boolean axes() {
        ensureLoaded();
        return axes;
    }

    public static void setAxes(boolean value) {
        axes = value;
        save();
    }

    public static boolean axisVectors() {
        ensureLoaded();
        return axisVectors;
    }

    public static void setAxisVectors(boolean value) {
        axisVectors = value;
        save();
    }

    public static boolean forces() {
        ensureLoaded();
        return forces;
    }

    public static void setForces(boolean value) {
        forces = value;
        save();
    }

    public static boolean external() {
        ensureLoaded();
        return external;
    }

    public static void setExternal(boolean value) {
        external = value;
        save();
    }

    public static boolean resultant() {
        ensureLoaded();
        return resultant;
    }

    public static void setResultant(boolean value) {
        resultant = value;
        save();
    }

    public static boolean angleX() {
        ensureLoaded();
        return angleX;
    }

    public static void setAngleX(boolean value) {
        angleX = value;
        save();
    }

    public static boolean angleY() {
        ensureLoaded();
        return angleY;
    }

    public static void setAngleY(boolean value) {
        angleY = value;
        save();
    }

    public static boolean angleZ() {
        ensureLoaded();
        return angleZ;
    }

    public static void setAngleZ(boolean value) {
        angleZ = value;
        save();
    }

    public static boolean elevation() {
        ensureLoaded();
        return elevation;
    }

    public static void setElevation(boolean value) {
        elevation = value;
        save();
    }

    public static boolean azimuth() {
        ensureLoaded();
        return azimuth;
    }

    public static void setAzimuth(boolean value) {
        azimuth = value;
        save();
    }

    public static boolean pitchYaw() {
        ensureLoaded();
        return pitchYaw;
    }

    public static void setPitchYaw(boolean value) {
        pitchYaw = value;
        save();
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("# sog_carpet 速度/动量/加速度可视化显示项（可手工编辑）\n");
        sb.append("momentum=").append(momentum).append('\n');
        sb.append("spherical=").append(spherical).append('\n');
        sb.append("axes=").append(axes).append('\n');
        sb.append("axisVectors=").append(axisVectors).append('\n');
        sb.append("forces=").append(forces).append('\n');
        sb.append("external=").append(external).append('\n');
        sb.append("resultant=").append(resultant).append('\n');
        sb.append("angleX=").append(angleX).append('\n');
        sb.append("angleY=").append(angleY).append('\n');
        sb.append("angleZ=").append(angleZ).append('\n');
        sb.append("elevation=").append(elevation).append('\n');
        sb.append("azimuth=").append(azimuth).append('\n');
        sb.append("pitchYaw=").append(pitchYaw).append('\n');
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 落盘失败只影响下次启动。
        }
    }
}
