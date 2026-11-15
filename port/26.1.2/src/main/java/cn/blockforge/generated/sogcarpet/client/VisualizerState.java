package cn.blockforge.generated.sogcarpet.client;

import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.net.AggroWantPayload;
import cn.blockforge.generated.sogcarpet.net.HealthChangeWantPayload;
import cn.blockforge.generated.sogcarpet.net.MotionWantPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnStatsConfigPayload;
import cn.blockforge.generated.sogcarpet.net.SpawnTraceWantPayload;
import cn.blockforge.generated.sogcarpet.net.VillagerBindingWantPayload;
import cn.blockforge.generated.sogcarpet.net.VisualizerPrefsSetPayload;
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
    /**
     * 规则名 -&gt; 服务端保存的<b>逐玩家</b>渲染开关。
     *
     * <p>服务端为每个玩家单独存一份清单，所以同一台服务器上玩家之间互不影响。只有当服务端
     * 明确说“这个玩家配置过”（{@link #serverPrefsConfigured}）时才以它为准；否则继续用
     * 本地文件里的设置，保证没配置过的老玩家、以及连到没装本模组的服务器时行为不变。</p>
     */
    private static final Map<String, Boolean> SERVER_PREFS = new ConcurrentHashMap<>();
    private static volatile boolean serverPrefsConfigured;

    private static boolean masterEnabled = true;
    private static boolean loaded;
    /** 服务端是否也装了本模组：收到过规则同步即为真，断线时清掉。 */
    private static volatile boolean serverHasSogRules;
    /** 服务端是否允许本玩家改规则（OP），以及哪些可视化规则已经是“永久开启”。 */
    private static volatile boolean ruleControlAvailable;
    private static final java.util.Set<String> PERSISTED_RULES = ConcurrentHashMap.newKeySet();

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
     *   <li>26.1.2 里 O 没有原版绑定（好友界面是 26.2 才有的），按 O 只切总开关；
     *       这里顺手把社交界面键的残余点击吃掉，避免以后版本换键位时冲突。</li>
     * </ul>
     */
    private static void clientTickStart(Minecraft client) {
        while (menuKey.consumeClick()) {
            // 只在游戏内、且没有别的界面打开时开设置界面，避免抢聊天框的 Ctrl+V 粘贴。
            if (!isCtrlDown(client) || client.level == null || client.screen != null) {
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
            // 吃掉同一帧里可能残留的社交界面键点击（26.1.2 的 O 本身没有绑定）。
            while (client.options.keySocialInteractions.consumeClick()) {
                // 只要这一次点击不会落到原版处理里就行。
            }
            if (client.screen != null) {
                continue;
            }
            masterEnabled = !masterEnabled;
            save();
            // 26.1.2 还没有 26.2 里那层 Gui#hud，overlayMessage 直接在 Gui 上。
            if (client.gui != null) {
                client.gui.setOverlayMessage(Component.translatable(masterEnabled
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

    /**
     * 世界是否被 <b>/tick freeze（或 /tick step）</b> 冻住了。冻结时可视化不再按 tick 淡出/移动，
     * 就把画面停在那一刻，方便用 tick 指令逐帧查看。
     */
    public static boolean tickFrozen(net.minecraft.client.Minecraft client) {
        return client != null && client.level != null && client.level.tickRateManager().isFrozen();
    }

    /** 当前连接的服务端是否也装了本模组（收到过规则同步）。纯客户端时为 false。 */
    public static boolean serverHasSogRules() {
        return serverHasSogRules;
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

    /** 本地渲染开关，默认开；服务端配置过这个玩家时以服务端清单为准。 */
    public static boolean isRenderEnabled(String rule) {
        if (serverPrefsConfigured) {
            Boolean serverValue = SERVER_PREFS.get(rule);
            return serverValue == null ? DEFAULT_RENDER : serverValue;
        }
        Boolean value = RENDER.get(rule);
        return value == null ? DEFAULT_RENDER : value;
    }

    public static void setRenderEnabled(String rule, boolean value) {
        RENDER.put(rule, value);
        if (serverPrefsConfigured) {
            SERVER_PREFS.put(rule, value);
        }
        save();
        syncSpawnTraceWant();
        // 改动同步到服务端，让服务端也知道这个玩家还要不要收相关数据（逐玩家、互不影响）。
        pushPrefsToServer();
    }

    /** 总控界面里显示的规则顺序。 */
    public static List<String> rules() {
        return SogSettings.VISUALIZER_RULES;
    }

    /** 翻转总开关（Ctrl+O 与总控界面顶部按钮共用）。 */
    public static void toggleMaster() {
        masterEnabled = !masterEnabled;
        save();
        syncSpawnTraceWant();
    }

    /**
     * 把“还要不要收刷怪轨迹”告诉服务端：总开关、规则开关、这条可视化的渲染开关三者都开
     * 才是要。服务端据此不再向已经把可视化关掉的客户端每 tick 发轨迹，减小专用服务器上行。
     * 加入时、收到服务端规则同步后、以及每次改动本地开关后都会调一次。
     *
     * <p>只有收到过服务端的规则同步（说明这一侧也装了本模组）才发；连到没装本模组的
     * 服务器时保持沉默，不会往一个不认识这条通道的服务端发包。</p>
     */
    private static void syncSpawnTraceWant() {
        Minecraft client = Minecraft.getInstance();
        if (!serverHasSogRules || client.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new SpawnTraceWantPayload(
                isActive("mobSpawnVisualizer") || isActive("spawnStatsVisualizer")));
        // 同理：服务端只在玩家明确要收时才每几 tick 扫生物、发仇恨快照。
        ClientPlayNetworking.send(new AggroWantPayload(isActive("aggroVisualizer")));
        // 速度/动量可视化的“外力冲量”：玩家关掉后服务端就别再扫、别再发。
        // 投掷物轨迹可视化的预览要用同一份冲量（悬停远传珍珠的待施加速度 → 落点），
        // 所以只要两条里还有一条开着，就继续要。
        ClientPlayNetworking.send(new MotionWantPayload(
                isActive("motionVisualizer") || isActive("projectileTrajectoryVisualizer")));
        // 村民功能方块绑定：玩家关掉后服务端就别再扫村民、别再发绑定快照。
        ClientPlayNetworking.send(new VillagerBindingWantPayload(
                isActive("villagerBindingVisualizer")));
        // 生命数值变化：玩家关掉后服务端就别再采集伤害/治疗、别再发。
        ClientPlayNetworking.send(new HealthChangeWantPayload(isActive("healthChangeVisualizer")));
        // 怪物生成可视化：把统计面板配置（整体 + 每个分类开关）一并上报。
        pushSpawnStatsConfig();
    }

    /** 把“怪物生成可视化”统计面板的配置发给服务端（面板由服务端写进 Tab 列表页脚）。 */
    public static void pushSpawnStatsConfig() {
        Minecraft client = Minecraft.getInstance();
        if (!serverHasSogRules || client.getConnection() == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(128);
        for (VisualizerConfig.Option option : VisualizerConfig.options("spawnStatsVisualizer")) {
            sb.append(option.key()).append('=')
                    .append(VisualizerConfig.option("spawnStatsVisualizer", option.key(),
                            option.defaultValue()) ? '1' : '0')
                    .append(';');
        }
        ClientPlayNetworking.send(new SpawnStatsConfigPayload(sb.toString()));
    }

    /**
     * 把整份逐玩家渲染清单上报给服务端。
     *
     * <p>只在确认对面也装了本模组（收到过规则同步）后才发，避免往不认识这条通道的服务端
     * 发包。服务端按发包者自己的 UUID 保存，所以客户端改不到别人的清单；改别人只能走
     * {@code /sogcarpet visualizer}。每次改动发整份而不是单条，服务端存的就是当前完整状态，
     * 不会因为丢包留下半套值。</p>
     */
    private static void pushPrefsToServer() {
        Minecraft client = Minecraft.getInstance();
        if (!serverHasSogRules || client.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(new VisualizerPrefsSetPayload(encodePrefs()));
    }

    /** 把当前这份清单编成 {@code 规则名=0/1;} 串。 */
    private static String encodePrefs() {
        StringBuilder sb = new StringBuilder(256);
        for (String rule : SogSettings.VISUALIZER_RULES) {
            sb.append(rule).append('=').append(isRenderEnabled(rule) ? '1' : '0').append(';');
        }
        return sb.toString();
    }

    /** 每条可视化都有独立配置界面（颜色，部分规则另有选项）。 */
    public static boolean hasConfigScreen(String rule) {
        return SogSettings.VISUALIZER_RULES.contains(rule);
    }

    /** 这条规则对应的独立配置界面（无上级，关闭后回到游戏）。 */
    public static net.minecraft.client.gui.screens.Screen configScreen(String rule) {
        return configScreen(rule, null);
    }

    /** 这条规则对应的独立配置界面；parent 非空时 Done/Esc 返回该上级面板。 */
    public static net.minecraft.client.gui.screens.Screen configScreen(String rule,
            net.minecraft.client.gui.screens.Screen parent) {
        if ("worldEaterHelper".equals(rule)) {
            return new WorldEaterConfigScreen(parent);
        }
        if ("motionVisualizer".equals(rule)) {
            return new MotionVisualizerConfigScreen(parent);
        }
        if ("explosionVisualizer".equals(rule)) {
            return new ExplosionVisualizerConfigScreen(parent);
        }
        if ("aggroRangeVisualizer".equals(rule)) {
            return new AggroRangeConfigScreen(parent);
        }
        if ("itemFlowTracker".equals(rule)) {
            return new ItemFlowConfigScreen(parent);
        }
        return new VisualizerConfigScreen(rule, parent);
    }

    /** 打开某条可视化的颜色/选项配置界面（无上级）。 */
    public static void openColorScreen(String rule) {
        openColorScreen(rule, null);
    }

    /** 打开颜色/选项配置界面；关闭后返回 parent（通常是打开它的子面板）。 */
    public static void openColorScreen(String rule, net.minecraft.client.gui.screens.Screen parent) {
        Minecraft client = Minecraft.getInstance();
        client.setScreenAndShow(new VisualizerConfigScreen(rule, parent));
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
        SERVER_PREFS.clear();
        serverPrefsConfigured = false;
        serverHasSogRules = false;
        ruleControlAvailable = false;
        PERSISTED_RULES.clear();
    }

    // ------------------------------------------------------------------ 面板直接开关规则

    /**
     * 服务端下发的「能不能改规则 + 哪些是永久开启」。
     *
     * <p>解析 {@code op=1;perm:<规则>=1;} 形式的串。没有 OP 的玩家面板保持只读，避免他们点了
     * 却什么都没发生；真正能不能改最终还是由服务端再校验一次权限。</p>
     */
    public static void applyRuleStateData(String data) {
        PERSISTED_RULES.clear();
        boolean op = false;
        if (data != null) {
            for (String part : data.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = part.substring(0, eq).trim();
                boolean value = "1".equals(part.substring(eq + 1).trim());
                if ("op".equals(key)) {
                    op = value;
                } else if (key.startsWith("perm:") && value) {
                    PERSISTED_RULES.add(key.substring("perm:".length()));
                }
            }
        }
        ruleControlAvailable = op;
        refreshOpenScreen();
    }

    /** 面板要不要把规则状态做成可点的三态按钮（对面装了本模组 + 自己是 OP）。 */
    public static boolean canControlRules() {
        return serverHasSogRules && ruleControlAvailable;
    }

    /** 这条规则是不是「永久开启」（carpet.conf 里有它的 true）。 */
    public static boolean isPermanentlyEnabled(String rule) {
        return PERSISTED_RULES.contains(rule);
    }

    /**
     * 点一下按钮：按 关闭 → 暂时开启 → 永久开启 → 关闭 的顺序循环。
     *
     * <p>顺序是“先开、再永久、再关”：第一次点就能看到画面变化，第二次再落盘，第三次彻底关掉。</p>
     */
    public static void cycleRuleMode(String rule) {
        int mode;
        if (isPermanentlyEnabled(rule)) {
            mode = cn.blockforge.generated.sogcarpet.net.RuleSetPayload.MODE_OFF;
        } else if (isRuleEnabled(rule)) {
            mode = cn.blockforge.generated.sogcarpet.net.RuleSetPayload.MODE_PERMANENT_ON;
        } else {
            mode = cn.blockforge.generated.sogcarpet.net.RuleSetPayload.MODE_TEMPORARY_ON;
        }
        Minecraft client = Minecraft.getInstance();
        if (!serverHasSogRules || client.getConnection() == null) {
            return;
        }
        ClientPlayNetworking.send(
                new cn.blockforge.generated.sogcarpet.net.RuleSetPayload(rule, mode));
    }

    /** 三态按钮上显示的文字。 */
    public static Component ruleModeName(String rule) {
        String suffix;
        if (isPermanentlyEnabled(rule)) {
            suffix = "permanent";
        } else if (isRuleEnabled(rule)) {
            suffix = "temporary";
        } else {
            suffix = "off";
        }
        return Component.translatable("sog_carpet.ui.visualizer.rule_mode." + suffix);
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
        // 收到同步说明对面也装了本模组，之后才允许往它发“要不要收刷怪轨迹”。
        serverHasSogRules = true;
        // 服务端规则开关刚同步过来，顺便把“要不要收刷怪轨迹”回报过去。
        syncSpawnTraceWant();
    }

    /**
     * 解析服务端下发的逐玩家渲染清单。
     *
     * <p>{@code configured=false} 表示服务端没有这个玩家的记录（比如连到没装本模组的服务器，
     * 或这个玩家还没配置过），此时继续用本地文件；{@code true} 则以服务端值为准，
     * 这样管理员用 {@code /sogcarpet visualizer} 给没装客户端模组的玩家配好的清单，
     * 等玩家装上模组再加入就能直接生效。每个玩家的清单服务端各存一份，互不影响。</p>
     */
    public static void applyPrefsData(boolean configured, String data) {
        SERVER_PREFS.clear();
        serverPrefsConfigured = configured;
        if (data != null) {
            for (String part : data.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = part.substring(0, eq).trim();
                if (SogSettings.VISUALIZER_RULES.contains(key)) {
                    SERVER_PREFS.put(key, "1".equals(part.substring(eq + 1).trim()));
                }
            }
        }
        syncSpawnTraceWant();
        // 管理员在服务端改了清单时，正开着的 Ctrl+V 界面要立刻显示新状态。
        refreshOpenScreen();
    }

    /** 重新构建正开着的渲染总控界面，让服务端推来的新开关立刻反映到按钮文字上。 */
    private static void refreshOpenScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null && client.screen instanceof VisualizerScreen) {
            client.setScreen(client.screen);
        }
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
