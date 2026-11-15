package cn.blockforge.generated.sogcarpet;

/**
 * 由 {@link cn.blockforge.generated.sogcarpet.mixin.GiantAiMixin} 实现在 {@code Giant} 上，
 * 用来把“旧版（14w50a）AI”按需装到巨人身上。
 *
 * <p>原版巨人只有属性、没有任何 AI 目标，所以站着不动。规则开启时，本接口的方法给巨人
 * 装上近战、随机游走、看向玩家、锁定玩家 / 村民 / 铁傀儡等目标，恢复 14w50a 那套“会主动
 * 追击并攻击”的行为。方法幂等：同一只巨人只会装一次。</p>
 */
public interface GiantAiAccess {
    /** 给这只巨人装上旧版 AI（若规则开启且尚未装过）。 */
    void sog$ensureOldAi();
}
