package cn.blockforge.generated.sogcarpet.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品流向面板的最新会话列表文本（服务端 {@code ItemFlowStatusPayload} 送来）。
 * 面板打开时读取，改动后刷新。
 */
public final class ItemFlowPanelState {
    private static volatile String data = "";

    private ItemFlowPanelState() {
    }

    public static void set(String value) {
        data = value == null ? "" : value;
    }

    /** 会话列表按行拆分；规则关闭时返回单行标记。 */
    public static List<String> lines() {
        List<String> result = new ArrayList<>();
        for (String part : data.split("\n")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }
}
