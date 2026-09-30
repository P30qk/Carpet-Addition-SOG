package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 可视化渲染的<b>客户端总控</b>。
 *
 * <p>以前每条可视化规则各自注册了一个默认绑定为 <b>O</b> 的按键，七条规则共用同一个键，
 * 在控制设置里就是七条互相冲突的绑定（改一个会把其余的解绑），Ctrl+O 时开时不开。
 * 现在只保留<b>一个</b> Ctrl+O 总开关命中所有可视化，另外用 Ctrl+V 打开总控界面。</p>
 *
 * <p>两层开关：</p>
 * <ul>
 *   <li><b>规则开关</b>（carpet 规则，服务端）：决定这条可视化有没有数据。单机 / 集成服务端
 *       直接读 {@link SogSettings} 的静态字段；专用服务器上客户端读不到，于是服务端会在玩家
 *       加入与规则变更时把开关状态同步过来（{@link #applySync(Map)}）。</li>
 *   <li><b>渲染开关</b>（本界面，纯客户端）：每条规则是否真的画出来，默认开，保存在
 *       {@code config/sog_carpet-visualizers.txt}，每个玩家各自独立。</li>
 * </ul>
 *
 * <p>渲染判定统一走 {@link #isActive(String)}：总开关 &amp;&amp; 规则开 &amp;&amp; 渲染开。</p>
 */
public final class VisualizerState {
    /** 总开关默认值：以前每条规则自带 visible=true，这里保持一致。 */
    private static final boolean DEFAULT_RENDER = true;

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sog_carpet-visualizers.txt");

    /** 规则名 -&gt; 本地渲染开关（只存被关掉过的，缺省即开）。 */
    private static final Map<String, Boolean> RENDER = new ConcurrentHashMap<>();
    /** 规则名 -&gt; 服务端同步过来的规则开关。 */
    private static final Map<String, Boolean> SYNCED = new ConcurrentHashMap<>();

    private static boolean masterEnabled = true;
    private static boolean loaded;

    private static KeyMapping toggleKey;
    private static KeyMapping menuKey;

    private VisualizerState() {
    }

    /** 客户端入口调用一次：注册 Ctrl+O / Ctrl+V，读取本地开关。 */
    public static void init() {
        load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sog_carpet.visualizer_toggle", GLFW.GLFW_KEY_O, KeyMapping.Category.MISC));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.sog_carpet.visualizer_menu", GLFW.GLFW_KEY_V, KeyMapping.Category.MISC));
        ClientTickEvents.START_CLIENT_TICK.register(VisualizerState::clientTickStart);
    }

    /**
     * Ctrl+V 开界面、Ctrl+O 切总开关都必须赶在 {@code handleKeybinds()} 之前处理，
     * 否则会被原版按键抢先：
     * <ul>
     *   <li>V 本身没有原版绑定，这里只需在 START 里开界面；</li>
     *   <li>O 在 26.2 里是原版<b>好友界面</b>的默认键，Ctrl+O 会连带把好友界面也打开，
     *       所以这里在切换总开关的同时把那次 {@code keyFriends} 点击一并吃掉。</li>
     * </ul>
     */
    private static void clientTickStart(Minecraft client) {
        while (menuKey.consumeClick()) {
            // 只在游戏内、且没有别的界面打开时开设置界面，避免抢聊天框的 Ctrl+V 粘贴。
            if (!isCtrlDown(client) || client.level == null || client.gui.screen() != null) {
                continue;
            }
            client.setScreenAndShow(new VisualizerScreen());
            return;
        }
        while (toggleKey.consumeClick()) {
            if (!isCtrlDown(client)) {
                // 单独按 O 是原版好友界面，不归我们管。
                continue;
            }
            // 吃掉同一个 Ctrl+O 触发的原版好友界面。
            while (client.options.keyFriends.consumeClick()) {
                // 只要这一次点击不会落到原版处理里就行。
            }
            if (client.gui.screen() != null) {
                continue;
            }
            masterEnabled = !masterEnabled;
            save();
            if (client.gui != null && client.gui.hud != null) {
                client.gui.hud.setOverlayMessage(Component.translatable(masterEnabled
                        ? "sog_carpet.ui.visualizer.master_on"
                        : "sog_carpet.ui.visualizer.master_off"), true);
            }
        }
    }

    private static boolean isCtrlDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    // ------------------------------------------------------------------ 查询

    /** 总开关。 */
    public static boolean masterEnabled() {
        return masterEnabled;
    }

    /** 这条规则当前是否真的画出来：总开关 &amp;&amp; 规则开 &amp;&amp; 渲染开关。 */
    public static boolean isActive(String rule) {
        return masterEnabled && isRuleEnabled(rule) && isRenderEnabled(rule);
    }

    /** 规则本身开没开（服务端同步值优先，没有同步值时退回本地静态字段）。 */
    public static boolean isRuleEnabled(String rule) {
        Boolean synced = SYNCED.get(rule);
        if (synced != null) {
            return synced;
        }
        return SogSettings.visualizerRuleValue(rule);
    }

    /** 本地渲染开关，默认开。 */
    public static boolean isRenderEnabled(String rule) {
        Boolean value = RENDER.get(rule);
        return value == null ? DEFAULT_RENDER : value;
    }

    public static void setRenderEnabled(String rule, boolean value) {
        RENDER.put(rule, value);
        save();
    }

    /** 总控界面里显示的规则顺序。 */
    public static List<String> rules() {
        return SogSettings.VISUALIZER_RULES;
    }

    /** 翻转总开关（Ctrl+O 与总控界面顶部按钮共用）。 */
    public static void toggleMaster() {
        masterEnabled = !masterEnabled;
        save();
    }

    /** 是否有独立配置界面（世吞小助手有）。 */
    public static boolean hasConfigScreen(String rule) {
        return "worldEaterHelper".equals(rule);
    }

    /** 规则在按钮上显示用的短名字。 */
    public static Component ruleName(String rule) {
        return Component.translatable("sog_carpet.ui.visualizer.rule." + rule);
    }

    /** 服务端同步过来的规则开关。 */
    public static void applySync(Map<String, Boolean> values) {
        SYNCED.clear();
        SYNCED.putAll(values);
    }

    /** 断开连接时清掉同步值，免得把上一个服务器的规则状态带到下一个服务器。 */
    public static void clearSync() {
        SYNCED.clear();
    }

    /** 解析服务端发来的 {@code 规则名=0/1;} 串并写入同步表。 */
    public static void applySyncData(String data) {
        Map<String, Boolean> values = new HashMap<>();
        if (data != null) {
            for (String part : data.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = part.substring(0, eq).trim();
                if (SogSettings.VISUALIZER_RULES.contains(key)) {
                    values.put(key, "1".equals(part.substring(eq + 1).trim()));
                }
            }
        }
        applySync(values);
    }

    // ------------------------------------------------------------------ 持久化

    private static void load() {
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
                if ("master".equals(key)) {
                    masterEnabled = value;
                } else if (SogSettings.VISUALIZER_RULES.contains(key)) {
                    RENDER.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // 读不出来就用默认值。
        }
    }

    private static void save() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# sog_carpet 可视化渲染开关（可手工编辑）\n");
        sb.append("master=").append(masterEnabled).append('\n');
        List<String> rules = new ArrayList<>(SogSettings.VISUALIZER_RULES);
        for (String rule : rules) {
            sb.append(rule).append('=').append(isRenderEnabled(rule)).append('\n');
        }
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
