package cn.blockforge.generated.sogcarpet;

/**
 * 注入到 carpet 假人实体（{@code carpet.patches.EntityPlayerMPFake}）上的
 * “可拾取工具类别”开关。
 *
 * <p>一个位掩码，第 i 位对应 {@link SogTools} 里的第 i 类工具。默认
 * {@link SogTools#ALL}（全都捡），与“假人只拾取工具”原来的行为一致；玩家在 GCA
 * 假人背包界面右侧那一列开关里逐类切换后，只有掩码里开着的类别会被捡起。</p>
 */
public interface SogFakeTools {
    int sog$getToolMask();

    void sog$setToolMask(int mask);
}
