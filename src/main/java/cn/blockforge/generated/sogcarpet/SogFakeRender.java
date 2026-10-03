package cn.blockforge.generated.sogcarpet;

/**
 * 注入到 carpet 假人实体（{@code carpet.patches.EntityPlayerMPFake}）上的
 * 每假人独立渲染设置。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>渲染距离：设置假人自身的视距（原版按玩家生效的 requestedViewDistance），
 *       并在假人所在区块补一张 PLAYER_LOADING 票据，把加载范围向外扩展到该半径；
 *       低于服务器全局值时以全局值为准（原版票据只增不减）。</li>
 *   <li>模拟距离：在假人所在区块补一张 PLAYER_SIMULATION 票据，把实体/方块实体
 *       的模拟范围向外扩展到该半径；同样不低于全局值。</li>
 *   <li>-1 表示这个假人还没单独设置过，按服务器全局（玩家）默认值处理；新假人只在
 *       召唤时对齐一次玩家设置，之后不再跟随。</li>
 * </ul>
 */
public interface SogFakeRender {
    /*
     * 滑条的最大/最小值跟随原版游戏设置（视频选项）：
     * 「渲染距离」选项的可选范围是 2-32，「模拟距离」选项是 5-32。
     * 客户端画滑条、服务端夹取收到的值，两边都用同一组常量，避免各写一套。
     * 常量属于编译期常量，会被内联，客户端加载它们不会连带加载 mixin 目标类。
     */
    /** 原版「渲染距离」最小值。 */
    int SOG_MIN_RENDER_DISTANCE = 2;
    /** 原版「渲染距离」最大值。 */
    int SOG_MAX_RENDER_DISTANCE = 32;
    /** 原版「模拟距离」最小值。 */
    int SOG_MIN_SIMULATION_DISTANCE = 5;
    /** 原版「模拟距离」最大值。 */
    int SOG_MAX_SIMULATION_DISTANCE = 32;

    int sog$getRenderDistance();

    int sog$getSimulationDistance();

    void sog$setRenderDistance(int value);

    void sog$setSimulationDistance(int value);

    /** 按当前坐标重建两张票据（假人移动、换维度或数值变化后调用）。 */
    void sog$refreshRenderTickets();

    /** 移除本假人加过的票据（假人死亡/移除时调用）。 */
    void sog$removeRenderTickets();
}
