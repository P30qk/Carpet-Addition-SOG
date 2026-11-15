package cn.blockforge.generated.sogcarpet.client;

import net.minecraft.client.input.MouseButtonEvent;

/**
 * 26.3 的鼠标键编号适配。
 *
 * <p>26.3 把底层输入从 GLFW 换成了 SDL，{@link MouseButtonEvent#button()} 的取值也因此从
 * “0 起编号”（0=左键、1=右键、2=中键）变成 SDL 的“1 起编号”（1=左键、2=中键、3=右键）。
 * 原版自己完全按新编号判定，可以直接在字节码里看到：</p>
 * <ul>
 *   <li>{@code AbstractWidget#isValidClickButton} 只在 {@code button == 1} 时返回 true；</li>
 *   <li>{@code AbstractContainerScreen#getContainerClickButton} 把 1 映射成左键（PICKUP）、
 *       3 映射成右键（QUICK_MOVE）；</li>
 *   <li>{@code ContainerEventHandler} 的点击/拖动分发同样只认 {@code button == 1}。</li>
 * </ul>
 *
 * <p>所以控件里不能再写 GLFW 时代的 {@code event.button() == 0}——那样左键永远匹配不上，
 * 表现为“能点但点不动、拖也拖不动”。这里把两个常量集中定义一次，界面代码不再出现裸数字。</p>
 */
final class SogMouseButtons {
    /** 左键（SDL 编号）。 */
    static final int LEFT = 1;
    /** 右键（SDL 编号）。 */
    static final int RIGHT = 3;

    private SogMouseButtons() {
    }

    static boolean isLeft(MouseButtonEvent event) {
        return event.button() == LEFT;
    }

    static boolean isRight(MouseButtonEvent event) {
        return event.button() == RIGHT;
    }
}
