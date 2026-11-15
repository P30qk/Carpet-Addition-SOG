package cn.blockforge.generated.sogcarpet;

import cn.blockforge.generated.sogcarpet.net.PortableBeaconUsePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;

/**
 * 「随身信标」的服务端实现：客户端选好效果后，由这里校验、发放并维持效果。
 *
 * <p><b>规则（本次改动）</b></p>
 * <ul>
 *   <li>最多可选<b>两个</b>效果：选两个时各 <b>I 级</b>；只选一个时该效果 <b>II 级</b>；</li>
 *   <li><b>时长</b>：不再是固定 90 秒。只要信标还在玩家背包里（含快捷栏、副手、装备槽），
 *       效果就一直续期，等于永久；信标一旦离开背包（丢弃、放进箱子、被拿走、死亡掉落），
 *       效果立刻被移除。规则关闭或玩家退出时同样收尾。</li>
 * </ul>
 *
 * <p><b>怎么做到“一直存在”</b>：不用无限时长（那会在服务器崩溃后留下永久 buff），而是把每次
 * 施加的时长设成 {@value #REFRESH_DURATION} tick，并在 {@value #REFRESH_INTERVAL} tick
 * 后重新施加一次更大的时长。这样：</p>
 * <ul>
 *   <li>正常携带时效果的时间条始终是满的，界面上看不出倒计时；</li>
 *   <li>信标丢失时最多 {@value #REFRESH_INTERVAL} tick 内就停了，并且会把已经施加的那一条
 *       主动移除（不是等它自然走完 12 秒）；</li>
 *   <li>服务器异常退出时，残留的 buff 最多 {@value #REFRESH_DURATION} tick 自动消失，
 *       不会变成永久效果。</li>
 * </ul>
 *
 * <p><b>服务端校验</b>（不信任客户端）：规则开着、背包里真的有信标、玩家活着且不是旁观者、
 * 两个效果都存在且都通过黑/白名单（{@link SogBeaconFilter#selectable}）、两次使用间隔超过
 * {@value #COOLDOWN_TICKS} tick。</p>
 */
public final class SogPortableBeacon {
    /** 每次施加的时长（tick）：比刷新间隔长，保证两次刷新之间不会中断。 */
    public static final int REFRESH_DURATION = 240;
    /** 刷新间隔（tick）：4 秒。 */
    public static final int REFRESH_INTERVAL = 80;
    /** 两次“选择效果”之间的最短间隔（tick），挡住连点与重发包。 */
    public static final int COOLDOWN_TICKS = 20;
    /** 一个效果（选中两个时各 I 级）。 */
    private static final int AMPLIFIER_TWO = 0;
    /** 只选一个时给 II 级。 */
    private static final int AMPLIFIER_ONE = 1;

    /** 玩家 UUID → 已选的两个效果（顺序即选择顺序，第二个可能为空串）。 */
    private static final Map<UUID, Choice> CHOICES = new ConcurrentHashMap<>();
    /** 玩家 UUID → 可以再次“选择效果”的服务器游戏刻。 */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    /** 距离下一次续期还有多少 tick。 */
    private static int refreshCountdown;

    /** 一个玩家选中的效果 id 组合（{@code secondaryId} 为空串表示只选了一个）。 */
    public record Choice(String primaryId, String secondaryId) {
        public boolean isSingle() {
            return secondaryId == null || secondaryId.isEmpty();
        }
    }

    private SogPortableBeacon() {
    }

    /** 玩家背包里是否有信标（主背包 + 快捷栏 + 副手 + 装备，原版 {@code Inventory#contains} 全覆盖）。 */
    public static boolean carriesBeacon(ServerPlayer player) {
        return player != null && player.getInventory().contains(stack -> stack.is(Items.BEACON));
    }

    /** 客户端 C2S：选好了效果，请给我上 buff。 */
    public static void handleUse(PortableBeaconUsePayload payload,
            ServerPlayNetworking.Context context) {
        if (payload == null) {
            return;
        }
        apply(context.player(), payload.primaryId(), payload.secondaryId(), true);
    }

    /**
     * 真正的“选择效果”流程（网络包与 {@code /sogcarpet portablebeacon} 共用，方便原版客户端
     * 与管理员在服务端操作，也让这条路径在服务端测试里可验证）。
     *
     * @param primaryId   主效果 id（必须能解析）
     * @param secondaryId 第二个效果 id，可为 {@code null}/空串
     * @param notify      是否给玩家发动作栏提示
     * @return 是否成功生效
     */
    public static boolean apply(ServerPlayer player, String primaryId, String secondaryId,
            boolean notify) {
        if (player == null) {
            return false;
        }
        if (!SogSettings.portableBeacon) {
            if (notify) {
                player.sendOverlayMessage(
                        Component.translatable("sog_carpet.portable.rule_off"));
            }
            return false;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }
        // 空 primary 表示“关掉随身信标”（界面里的“关闭效果”按钮走这一条）。
        if (SogBeaconFilter.canonical(primaryId) == null) {
            clearEffects(player, CHOICES.remove(player.getUUID()));
            if (notify) {
                player.sendOverlayMessage(Component.translatable("sog_carpet.portable.cleared"));
            }
            return true;
        }
        if (!carriesBeacon(player)) {
            if (notify) {
                player.sendOverlayMessage(Component.translatable("sog_carpet.portable.no_beacon"));
            }
            return false;
        }
        Holder<MobEffect> primary = resolve(primaryId);
        Holder<MobEffect> secondary = resolve(secondaryId);
        if (primary == null) {
            return false;
        }
        if (!SogBeaconFilter.selectable(primary)
                || (secondary != null && !SogBeaconFilter.selectable(secondary))) {
            if (notify) {
                player.sendOverlayMessage(
                        Component.translatable("sog_carpet.portable.not_allowed"));
            }
            return false;
        }
        // 同一个效果被选两次没有意义，直接当单选处理（于是会是 II 级）。
        if (secondary != null && secondary.equals(primary)) {
            secondary = null;
        }
        long now = player.level().getGameTime();
        Long readyAt = COOLDOWN.get(player.getUUID());
        if (readyAt != null && now < readyAt) {
            return false;
        }
        COOLDOWN.put(player.getUUID(), now + COOLDOWN_TICKS);
        if (COOLDOWN.size() > 512) {
            COOLDOWN.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        Choice choice = new Choice(SogBeaconFilter.idOf(primary),
                secondary == null ? "" : SogBeaconFilter.idOf(secondary));
        // 换效果时先撤掉旧的那两条，避免叠加出三层 buff。
        Choice previous = CHOICES.put(player.getUUID(), choice);
        clearEffects(player, previous);
        maintain(player, choice);
        if (notify) {
            player.sendOverlayMessage(message(choice));
        }
        return true;
    }

    /** 每 {@value #REFRESH_INTERVAL} tick 续期一次；信标没了就撤掉效果。 */
    public static void onEndServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (--refreshCountdown > 0) {
            return;
        }
        refreshCountdown = REFRESH_INTERVAL;
        if (!SogSettings.portableBeacon) {
            // 规则关掉：所有人停止，并把已经发出去的效果收回来。
            if (!CHOICES.isEmpty()) {
                for (UUID uuid : new ArrayList<>(CHOICES.keySet())) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    clearEffects(player, CHOICES.remove(uuid));
                }
            }
            return;
        }
        if (CHOICES.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Choice> entry : new ArrayList<>(CHOICES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                CHOICES.remove(entry.getKey());
                continue;
            }
            if (!carriesBeacon(player)) {
                // 信标离开背包（丢弃 / 存进箱子 / 被别的玩家拿走 / 死亡掉落）→ 效果立刻消失，
                // 但“选择”记住：信标再回到背包时效果会自动恢复。
                clearEffects(player, entry.getValue());
                continue;
            }
            maintain(player, entry.getValue());
        }
    }

    /** 按选择施加/续期效果（每次都用更长的时长，客户端的时间条因此始终是满的）。 */
    private static void maintain(ServerPlayer player, Choice choice) {
        List<Holder<MobEffect>> effects = resolveAll(choice);
        if (effects.isEmpty()) {
            return;
        }
        int amplifier = effects.size() == 1 ? AMPLIFIER_ONE : AMPLIFIER_TWO;
        for (Holder<MobEffect> effect : effects) {
            MobEffectInstance current = player.getEffect(effect);
            // 已经有更高等级（例如信标本体给的 II 级）时不去压低它。
            int level = current == null ? amplifier : Math.max(amplifier, current.getAmplifier());
            player.addEffect(new MobEffectInstance(effect, REFRESH_DURATION, level, true, true, true));
        }
    }

    /** 撤掉这条选择发放的效果（只撤等级对得上的那一条，别误删别的来源的 buff）。 */
    private static void clearEffects(ServerPlayer player, Choice choice) {
        if (player == null || choice == null) {
            return;
        }
        List<Holder<MobEffect>> effects = resolveAll(choice);
        int amplifier = effects.size() == 1 ? AMPLIFIER_ONE : AMPLIFIER_TWO;
        for (Holder<MobEffect> effect : effects) {
            MobEffectInstance current = player.getEffect(effect);
            if (current != null && current.getAmplifier() <= amplifier) {
                player.removeEffect(effect);
            }
        }
    }

    private static Component message(Choice choice) {
        List<Holder<MobEffect>> effects = resolveAll(choice);
        if (effects.isEmpty()) {
            return Component.translatable("sog_carpet.portable.cleared");
        }
        if (effects.size() == 1) {
            return Component.translatable("sog_carpet.portable.applied_one",
                    effects.get(0).value().getDisplayName());
        }
        return Component.translatable("sog_carpet.portable.applied_two",
                effects.get(0).value().getDisplayName(), effects.get(1).value().getDisplayName());
    }

    /** 解析一个效果 id；空串/非法/不存在都返回 null。 */
    private static Holder<MobEffect> resolve(String effectId) {
        String canonical = SogBeaconFilter.canonical(effectId);
        if (canonical == null) {
            return null;
        }
        Identifier id = Identifier.tryParse(canonical);
        return id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
    }

    /** 当前选择对应的已注册效果（过滤掉解析不出来的）。 */
    public static List<Holder<MobEffect>> resolveAll(Choice choice) {
        List<Holder<MobEffect>> effects = new ArrayList<>(2);
        if (choice == null) {
            return effects;
        }
        Holder<MobEffect> primary = resolve(choice.primaryId());
        if (primary != null) {
            effects.add(primary);
        }
        Holder<MobEffect> secondary = resolve(choice.secondaryId());
        if (secondary != null && !secondary.equals(primary)) {
            effects.add(secondary);
        }
        return effects;
    }

    /** 当前玩家已选的效果 id（调试/指令显示用，没有则返回 null）。 */
    public static Choice choiceOf(UUID uuid) {
        return uuid == null ? null : CHOICES.get(uuid);
    }

    /** 玩家退出：停止续期（他身上的 buff 会在下一次刷新时自然过期，不需要也不应该在这里改）。 */
    public static void onDisconnect(UUID uuid) {
        if (uuid != null) {
            CHOICES.remove(uuid);
            COOLDOWN.remove(uuid);
        }
    }

    /** 服务器停下：清空全部状态，避免单机切换世界时残留。 */
    public static void clear() {
        CHOICES.clear();
        COOLDOWN.clear();
        refreshCountdown = 0;
    }

    /** 效果名（指令里显示用）。 */
    public static String describe(Holder<MobEffect> effect) {
        return effect == null ? "-" : effect.value().getDisplayName().getString().toLowerCase(Locale.ROOT);
    }
}
