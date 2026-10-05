package cn.blockforge.generated.sogcarpet;

import carpet.api.settings.CarpetRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 规则切换时的视觉与音效反馈：聊天栏状态、轻柔点击音效、脚边少量主题粒子。
 *
 * <p>观察器挂在 Carpet 核心 manager 上，会收到全部规则（含原版 carpet 规则）的
 * 变更，先按 {@link SogSettings#RULE_NAMES} 过滤，只对 SOG 规则生效。
 * 只对由玩家执行的切换生效；控制台/命令方块执行时静默跳过，
 * 避免在没有玩家位置的上下文里播放音效或粒子。</p>
 */
public final class SogFeedback {
    private SogFeedback() {
    }

    public static void onRuleChanged(CommandSourceStack source, CarpetRule<?> rule, String value) {
        if (!SogSettings.RULE_NAMES.contains(rule.name())) {
            return;
        }
        if (!SogSettings.visualFeedback) {
            return;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        String name = rule.name();

        source.sendSuccess(() -> Component.translatable("sog_carpet.feedback.changed", name, value), false);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.5F);

        level.sendParticles(pickParticle(name),
                player.getX(), player.getY() + 0.2D, player.getZ(),
                10, 0.4D, 0.3D, 0.4D, 0.02D);
    }

    /** 每个主题一条粒子配色，规则名不匹配时用通用欢呼粒子。 */
    private static ParticleOptions pickParticle(String name) {
        switch (name) {
            case "copperOxidationDisabled":
                return ParticleTypes.ELECTRIC_SPARK;
            case "amethystBudGrowthLimit":
                return ParticleTypes.END_ROD;
            case "iceSnowMeltDisabled":
            case "forceThawLightLevel":
            case "iceInstantMelt":
            case "snowGenerationDisabled":
            case "snowGolemSnowDisabled":
                return ParticleTypes.SNOWFLAKE;
            case "thunderDisabled":
                return ParticleTypes.CLOUD;
            case "chickenJockeyDisabled":
                return ParticleTypes.CRIT;
            case "silkTouchReinforcedDeepslate":
            case "silkTouchSuspicious":
                return ParticleTypes.ENCHANT;
            case "sculkShriekerLevel":
            case "sculkShriekerLevelLocked":
                return ParticleTypes.SOUL;
            case "wardenSpawnDisabled":
                return ParticleTypes.ASH;
            case "nitwitSpawnDisabled":
            case "nitwitBedDisabled":
                return ParticleTypes.HAPPY_VILLAGER;
            case "tintedGlassEasyCraft":
                return ParticleTypes.END_ROD;
            case "easyElytraCraft":
                return ParticleTypes.CLOUD;
            case "beaconExtraEffects":
                return ParticleTypes.ENCHANT;
            case "sculkRangeVisualizer":
                return ParticleTypes.SCULK_SOUL;
            case "creakingHeartVisualizer":
                return ParticleTypes.CHERRY_LEAVES;
            case "enderDragonPathVisualizer":
                return ParticleTypes.END_ROD;
            case "witherBreakRangeVisualizer":
                return ParticleTypes.SMOKE;
            case "enderDragonBreakRangeVisualizer":
                return ParticleTypes.PORTAL;
            case "fakePlayerRenderSettings":
                return ParticleTypes.PORTAL;
            case "flexiblePlacementCorners":
                return ParticleTypes.END_ROD;
            default:
                return ParticleTypes.HAPPY_VILLAGER;
        }
    }
}
