package cn.blockforge.generated.sogcarpet;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * “发射器可除锈/除蜡”的发射器行为——让发射器里的斧头做玩家手里斧头能做的事。
 *
 * <p>规则开启且发射器取到的物品是斧头时，由 {@code DispenserBlockMixin} 在本方块
 * 原版没有任何发射器行为（NOOP）的前提下把 {@link #INSTANCE} 换进去。每次发射：</p>
 * <ol>
 *   <li>取发射器正前方（{@link DispenserBlock#FACING}）那一格；</li>
 *   <li>按与原版 {@code AxeItem} 完全一样的顺序求“处理结果”：先查
 *       {@link WeatheringCopper#getPrevious} 把氧化等级降一级（氧化→风化→斑驳），
 *       再查 {@link HoneycombItem#WAX_OFF_BY_BLOCK} 把涂蜡去掉；两者都没有就什么都不做；</li>
 *   <li>处理成功后：方块按原版旗标 11 写回、发 {@code BLOCK_CHANGE} 游戏事件、
 *       播放原版对应的音效（{@code AXE_SCRAPE}/{@code AXE_WAX_OFF}）与粒子事件
 *       （{@code PARTICLES_SCRAPE}/{@code PARTICLES_WAX_OFF}）；双联铜箱的另一半
 *       与原版一样补发事件（另半边会随方块更新一起换材质）；</li>
 *   <li>斧头调用 {@code hurtAndBreak(1, ...)}：耐久 -1，耐久耗尽时照常损坏消失
 *       （与玩家用斧头的处理路径一致）。</li>
 * </ol>
 *
 * <p>无论成功还是失败都留在发射器里（不弹出物品）：失败时沿用
 * {@link OptionalDispenseItemBehavior} 的“失败咔哒声”，不消耗耐久、不碰方块。
 * 所有逻辑都发生在服务端（{@link BlockSource} 只由服务端构建），纯客户端/单机/
 * 专用服务器下行为一致，且不需要任何客户端同步。</p>
 */
public final class SogCopperScrape extends OptionalDispenseItemBehavior {
    /** 行为唯一实例，由 mixin 在“斧头 + 原版无行为 + 规则开启”时替换返回。 */
    public static final SogCopperScrape INSTANCE = new SogCopperScrape();

    /** 与原版斧头除蜡/去锈一致的写回旗标（1|2|8：更新邻居、同步客户端、立即更新）。 */
    private static final int SET_BLOCK_FLAGS = 11;

    private SogCopperScrape() {
    }

    @Override
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        this.setSuccess(false);
        ServerLevel level = source.level();
        BlockPos targetPos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
        BlockState targetState = level.getBlockState(targetPos);
        ScrapeResult scrape = scrapeResult(targetState);
        if (scrape == null) {
            return stack;
        }
        level.playSound(null, targetPos, scrape.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.levelEvent(null, scrape.particleEvent(), targetPos, 0);
        if (scrape.state().getBlock() instanceof ChestBlock
                && scrape.state().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connectedPos = ChestBlock.getConnectedBlockPos(targetPos, scrape.state());
            level.gameEvent(GameEvent.BLOCK_CHANGE, connectedPos,
                    GameEvent.Context.of(level.getBlockState(connectedPos)));
            level.levelEvent(null, scrape.particleEvent(), connectedPos, 0);
        }
        level.setBlock(targetPos, scrape.state(), SET_BLOCK_FLAGS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, targetPos, GameEvent.Context.of(scrape.state()));
        stack.hurtAndBreak(1, level, null, item -> { });
        this.setSuccess(true);
        return stack;
    }

    /**
     * 与原版 {@code AxeItem} 相同的顺序求铜类方块的处理结果：先降一级氧化，再除蜡；
     * 不可处理时返回 {@code null}。
     */
    private static @Nullable ScrapeResult scrapeResult(BlockState state) {
        Optional<BlockState> deoxidized = WeatheringCopper.getPrevious(state);
        if (deoxidized.isPresent()) {
            return new ScrapeResult(deoxidized.get(), SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE);
        }
        Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
        if (unwaxed != null) {
            return new ScrapeResult(unwaxed.withPropertiesOf(state), SoundEvents.AXE_WAX_OFF,
                    LevelEvent.PARTICLES_WAX_OFF);
        }
        return null;
    }

    /** 一次成功处理的目标状态及配套的音效/粒子事件号。 */
    private record ScrapeResult(BlockState state, SoundEvent sound, int particleEvent) {
    }
}
