package cn.blockforge.generated.sogcarpet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * 「自定义信标效果」的黑/白名单过滤。
 *
 * <p><b>两份独立的名单</b>（各自一条 carpet 规则，随 {@code carpet.conf} 持久化、经
 * {@code CLIENT_SYNC_RULES} 同步给客户端）：</p>
 * <ul>
 *   <li>{@link SogSettings#beaconEffectBlacklist}：黑名单模式用；名单里的效果<u>不能</u>被选中；</li>
 *   <li>{@link SogSettings#beaconEffectWhitelist}：白名单模式用；只有名单里的效果<u>能</u>被选中，
 *       名单为空时<u>任何效果都不可选</u>（不静默回退），这样“界面上看到的可选项”与“存下来的名单”
 *       永远一致：右键一个效果就是把它的状态翻过来，不会出现“点一下却删掉了别的效果”。</li>
 * </ul>
 * <p>{@link SogSettings#beaconEffectFilterMode} 只决定<u>当前生效哪一份</u>，两份名单互不覆盖：
 * 在黑名单里加了一堆效果之后再切到白名单，白名单仍然是原来那一份。</p>
 *
 * <p><b>作用范围</b>：<u>只作用于信标方块里的「自定义信标效果」槽位</u>（{@link #mayPick}）。
 * 「随身信标」不再受名单影响（{@link #selectable}），它的可选范围只由
 * {@link SogSettings#customBeaconEffects} 决定：规则开启时是全部效果，关闭时是原版信标那六种。</p>
 *
 * <p>服务端与客户端共用同一份代码：服务端读自己的规则值，客户端读加入时同步下来的值
 * （{@code SogCarpetClient#applyClientSyncedRules} 写回 {@link SogSettings} 的静态字段）。
 * 名单很小，解析结果按原字符串缓存，规则一变就自动重建。</p>
 */
public final class SogBeaconFilter {
    /** 名单串长度上限：效果 id 一般 20-35 个字符，这里留足空间但挡住异常长包。 */
    public static final int MAX_LIST_LENGTH = 2048;
    /** 名单内最多保留多少条（防止误输入把同步包撑爆）。 */
    public static final int MAX_ENTRIES = 64;

    /**
     * 黑名单 / 白名单各自的「原串 → 解析结果」缓存。
     *
     * <p>字段都是 {@code volatile}：{@link #entries()} 的快路径不加锁（信标 tick 里每个信标
     * 每 tick 都会问一次名单），只有名单真的变了才进同步块重新解析。</p>
     */
    private static volatile String cachedBlackRaw = "";
    private static volatile Set<String> cachedBlack = Collections.emptySet();
    private static volatile String cachedWhiteRaw = "";
    private static volatile Set<String> cachedWhite = Collections.emptySet();

    private SogBeaconFilter() {
    }

    // ------------------------------------------------------------------ 名单读写

    /** 当前模式；规则值意外为 null 时按默认的黑名单处理。 */
    public static SogSettings.BeaconEffectFilterMode mode() {
        SogSettings.BeaconEffectFilterMode mode = SogSettings.beaconEffectFilterMode;
        return mode == null ? SogSettings.BeaconEffectFilterMode.BLACKLIST : mode;
    }

    /** 某个模式当前使用的规则原串。 */
    public static String rawFor(SogSettings.BeaconEffectFilterMode mode) {
        String raw = mode == SogSettings.BeaconEffectFilterMode.WHITELIST
                ? SogSettings.beaconEffectWhitelist
                : SogSettings.beaconEffectBlacklist;
        return raw == null ? "" : raw;
    }

    /** 某个模式对应规则名（写回 carpet 时用）。 */
    public static String ruleNameFor(SogSettings.BeaconEffectFilterMode mode) {
        return mode == SogSettings.BeaconEffectFilterMode.WHITELIST
                ? "beaconEffectWhitelist"
                : "beaconEffectBlacklist";
    }

    /** 某个模式的名单内容（小写、去重、按字典序）。 */
    public static Set<String> entriesFor(SogSettings.BeaconEffectFilterMode mode) {
        String raw = rawFor(mode);
        if (mode == SogSettings.BeaconEffectFilterMode.WHITELIST) {
            if (raw.equals(cachedWhiteRaw)) {
                return cachedWhite;
            }
            return parseAndCache(raw, true);
        }
        if (raw.equals(cachedBlackRaw)) {
            return cachedBlack;
        }
        return parseAndCache(raw, false);
    }

    /** 当前生效模式的那一份名单。 */
    public static Set<String> entries() {
        return entriesFor(mode());
    }

    /** 重新解析某一份名单并写入缓存（名单变化时才走一次）。 */
    private static synchronized Set<String> parseAndCache(String raw, boolean white) {
        if ((white ? cachedWhiteRaw : cachedBlackRaw).equals(raw)) {
            return white ? cachedWhite : cachedBlack;
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String id = canonical(part);
            if (id == null) {
                continue;
            }
            parsed.add(id);
            if (parsed.size() >= MAX_ENTRIES) {
                break;
            }
        }
        List<String> sorted = new ArrayList<>(parsed);
        Collections.sort(sorted);
        Set<String> result = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        if (white) {
            cachedWhite = result;
            cachedWhiteRaw = raw;
        } else {
            cachedBlack = result;
            cachedBlackRaw = raw;
        }
        return result;
    }

    /**
     * 把任意输入规范成最终存盘格式：去空白、小写、只留合法 id、去重、排序、逗号连接。
     * 校验器（{@link SogSettings.BeaconFilterListValidator}）也走这里，所以手工输入和指令输入的
     * 存储结果完全一致。
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String id = canonical(part);
            if (id == null) {
                continue;
            }
            parsed.add(id);
            if (parsed.size() >= MAX_ENTRIES) {
                break;
            }
        }
        List<String> sorted = new ArrayList<>(parsed);
        Collections.sort(sorted);
        String joined = String.join(",", sorted);
        return joined.length() > MAX_LIST_LENGTH ? joined.substring(0, MAX_LIST_LENGTH) : joined;
    }

    /** 当前模式的名单里加上（或去掉）一个效果 id，返回规范化后的整串。 */
    public static String withToggled(String effectId) {
        return withToggled(mode(), effectId);
    }

    /** 指定模式的名单里加上（或去掉）一个效果 id，返回规范化后的整串。 */
    public static String withToggled(SogSettings.BeaconEffectFilterMode mode, String effectId) {
        String id = canonical(effectId);
        if (id == null) {
            return normalize(String.join(",", entriesFor(mode)));
        }
        return withMember(mode, id, !entriesFor(mode).contains(id));
    }

    /** 指定模式的名单里加入（{@code member=true}）或移除一个效果 id，返回规范化后的整串。 */
    public static String withMember(SogSettings.BeaconEffectFilterMode mode, String effectId,
            boolean member) {
        Set<String> parsed = new LinkedHashSet<>(entriesFor(mode));
        String id = canonical(effectId);
        if (id == null) {
            return normalize(String.join(",", parsed));
        }
        if (member) {
            parsed.add(id);
        } else {
            parsed.remove(id);
        }
        return normalize(String.join(",", parsed));
    }

    /** 指定模式的名单里是否显式列了这个效果。 */
    public static boolean listed(SogSettings.BeaconEffectFilterMode mode, String effectId) {
        String id = canonical(effectId);
        return id != null && entriesFor(mode).contains(id);
    }

    /**
     * 把一个效果 id 规范成 {@code namespace:path}（小写）；没有命名空间时补 {@code minecraft:}。
     * 解析不出来时返回 {@code null}。
     */
    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        // 允许只写效果名（speed），自动补 minecraft: 命名空间，和原版指令的习惯一致。
        if (trimmed.indexOf(':') < 0) {
            trimmed = Identifier.DEFAULT_NAMESPACE + ":" + trimmed;
        }
        Identifier id = Identifier.tryParse(trimmed);
        return id == null ? null : id.toString();
    }

    // ------------------------------------------------------------------ 判定

    /** 效果 id；拿不到（未注册/动态效果没有 key）时返回 {@code null}。 */
    public static String idOf(Holder<MobEffect> effect) {
        if (effect == null) {
            return null;
        }
        return effect.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(null);
    }

    /**
     * 这个效果是否允许被<b>信标方块</b>里的「自定义信标效果」选中：只看当前模式对应的那一份名单，
     * 不看 {@link SogSettings#customBeaconEffects}（槽位本身只在规则开启时才出现）。
     *
     * <p>白名单为空时返回 {@code false}（全部不可选）：以前这里会静默回退到原版六种效果，于是
     * 界面上“看起来可用”的效果其实不在名单里，右键它反而会把它<u>加进</u>名单，并把原本回退出来的
     * 其余效果一次性挤掉——这就是“右键删一个效果却随机删掉别的”的根因。现在显示与存储严格一致。</p>
     */
    public static boolean mayPick(Holder<MobEffect> effect) {
        String id = idOf(effect);
        if (id == null) {
            return false;
        }
        Set<String> list = entries();
        if (mode() == SogSettings.BeaconEffectFilterMode.WHITELIST) {
            return list.contains(id);
        }
        return !list.contains(id);
    }

    /**
     * 是否可以被<b>随身信标</b>选中。
     *
     * <p>随身信标<b>不受黑/白名单影响</b>（名单只管信标方块里的自定义效果槽位），所以这里只看
     * 「自定义信标效果开启，或者本身就是原版信标能给的六种效果之一」。</p>
     */
    public static boolean selectable(Holder<MobEffect> effect) {
        return SogSettings.customBeaconEffects || SogBeaconHelper.isVanillaBeaconEffect(effect);
    }

    /** 信标方块界面上显示成“锁定”的状态：自定义槽位不可选。 */
    public static boolean locked(Holder<MobEffect> effect) {
        return !mayPick(effect);
    }

    /** 把名单拼成给人看的短串（空名单显示“-”）。 */
    public static String describe(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "-";
        }
        return String.join(", ", ids);
    }
}
