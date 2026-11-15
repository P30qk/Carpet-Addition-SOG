package cn.blockforge.generated.sogcarpet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 繁体中文（zh_tw / zh_hk）规则文案的读取器。
 *
 * <p>Carpet 规则的名称与说明由 {@link SogCarpetExtension} 在代码里注册（{@code carpet.rule.*}
 * 与 {@code sog_carpet.rule.*}），并不经过 {@code assets/<namespace>/lang/<locale>.json}，所以只加
 * {@code zh_tw.json} 只能翻译界面，{@code /carpet} 里的规则说明仍是简体。这里在启用繁体语言时
 * 从随包资源 {@code /assets/sog_carpet/lang/zh_tw_rules.json} 读入已转换好的整套文案。</p>
 *
 * <p>读不到（资源缺失、解析失败、运行环境异常）时返回 {@code null}，调用方会退回简体中文，
 * 不会影响规则本身。</p>
 */
public final class SogTraditionalChinese {
    private static final String RESOURCE = "/assets/sog_carpet/lang/zh_tw_rules.json";

    /** 只读一次：语言切换时 Carpet 会重复调用，避免反复解析同一个文件。 */
    private static volatile Map<String, String> cached;
    private static volatile boolean loaded;

    private SogTraditionalChinese() {
    }

    /** 繁体规则文案；不可用时返回 {@code null}。 */
    public static Map<String, String> loadRules() {
        if (loaded) {
            return cached;
        }
        synchronized (SogTraditionalChinese.class) {
            if (loaded) {
                return cached;
            }
            try (InputStream stream = SogTraditionalChinese.class.getResourceAsStream(RESOURCE)) {
                if (stream == null) {
                    loaded = true;
                    return null;
                }
                Map<String, String> parsed = new Gson().fromJson(
                        new InputStreamReader(stream, StandardCharsets.UTF_8),
                        new TypeToken<LinkedHashMap<String, String>>() {
                        }.getType());
                if (parsed != null) {
                    Map<String, String> copy = new LinkedHashMap<>(parsed);
                    cached = copy;
                }
            } catch (Throwable ignored) {
                // 资源损坏或 Gson 不可用：退回简体中文即可，不影响规则注册。
                cached = null;
            } finally {
                loaded = true;
            }
        }
        return cached;
    }
}
