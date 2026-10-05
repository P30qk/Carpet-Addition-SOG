package cn.blockforge.generated.sogcarpet.mixin;

import carpet.patches.EntityPlayerMPFake;
import cn.blockforge.generated.sogcarpet.SogFakeRender;
import cn.blockforge.generated.sogcarpet.SogFakeTools;
import cn.blockforge.generated.sogcarpet.SogSettings;
import cn.blockforge.generated.sogcarpet.SogTools;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给 carpet 假人挂上每假人独立的渲染/模拟距离设置。
 *
 * <p>渲染距离走原版按玩家生效的 requestedViewDistance（{@link ServerPlayer#updateOptions}），
 * 并额外补一张 PLAYER_LOADING 区域票把假人周围的加载半径向外扩展；模拟距离补一张
 * PLAYER_SIMULATION 区域票。原版给玩家的票据按全局半径发放、无法按玩家缩小，
 * 因此这两个滑条的语义是“在玩家默认值基础上扩展”，低于默认值时以默认值为准。</p>
 *
 * <p><b>“跟随玩家”的实现</b>：两个字段用包装类型，{@code null} 即跟随。跟随状态既
 * 不加额外票据，也会把假人自己的 {@code ClientInformation.viewDistance} 同步成服务器
 * 视距——carpet 建普通假人用的是 {@code ClientInformation.createDefault()}，视距只有 2，
 * 原版取“服务器视距与玩家请求视距的较小值”，不主动同步的话新假人就一直只加载 2 格，
 * 看起来像没跟随玩家。用包装类型也能绕开 Mixin 实例字段初值在某些构造路径下不生效、
 * 默认 0 被滑条夹到最小值的问题。</p>
 *
 * <p>票据跟随假人所在区块：tick 里检测坐标/维度变化后重建；假人死亡（kill）时移除，
 * 避免残留票据把区块永久钉住。</p>
 */
@Mixin(EntityPlayerMPFake.class)
public abstract class EntityPlayerMPFakeMixin extends ServerPlayer implements SogFakeRender, SogFakeTools {
    // 每个假人实例各存一份：null = 跟随玩家（服务器全局）设置，
    // 新召唤出来的假人因此默认跟随玩家；之后手动改过的值只写在这个实例上，
    // 与其他假人、与玩家自己的设置互不影响。
    private Integer sog$render;
    private Integer sog$simulation;
    // “跟随玩家”只在召唤时对齐一次：标记已经对齐过，之后即使服务器改视距也不再跟着变。
    // 关掉规则时不做任何回退，所以假人的渲染设置就停在关闭那一刻的值。
    private boolean sog$followApplied;
    // 可拾取的工具类别掩码：null = 全选（与“假人只拾取工具”原来的行为一致）。
    private Integer sog$toolMask;

    private ServerLevel sog$ticketLevel;
    private int sog$ticketCx;
    private int sog$ticketCz;
    private boolean sog$loadTicketOn;
    private int sog$loadTicketRadius;
    private boolean sog$simTicketOn;
    private int sog$simTicketRadius;

    // Mixin 要求混入类的构造器与目标的一个构造器签名完全一致（它不会被合并，
    // 只用于校验 super 调用链）。carpet 26.2 的假人构造器是 5 参（末尾 isAShadow）。
    private EntityPlayerMPFakeMixin(MinecraftServer server, ServerLevel level, GameProfile profile,
            ClientInformation information, boolean isAShadow) {
        super(server, level, profile, information);
        throw new IllegalStateException("carpet-SOG-addition: EntityPlayerMPFakeMixin 不应被实例化");
    }

    @Override
    public int sog$getRenderDistance() {
        return this.sog$render == null ? -1 : this.sog$render;
    }

    @Override
    public int sog$getSimulationDistance() {
        return this.sog$simulation == null ? -1 : this.sog$simulation;
    }

    @Override
    public void sog$setRenderDistance(int value) {
        this.sog$render = value < 0 ? null : value;
        if (value >= 0) {
            this.sog$applyViewDistance(value);
        } else {
            this.sog$applyFollowViewDistance();
        }
        // 手动把滑条拉回“跟随”时，允许下次 tick 再对齐一次。
        this.sog$followApplied = true;
        this.sog$refreshRenderTickets();
    }

    @Override
    public int sog$getToolMask() {
        return this.sog$toolMask == null ? SogTools.ALL : this.sog$toolMask;
    }

    @Override
    public void sog$setToolMask(int mask) {
        this.sog$toolMask = mask & SogTools.ALL;
    }

    @Override
    public void sog$setSimulationDistance(int value) {
        this.sog$simulation = value < 0 ? null : value;
        this.sog$refreshRenderTickets();
    }

    /** 原版按玩家视距：重写 ClientInformation 里的 viewDistance，其余字段原样保留。 */
    private void sog$applyViewDistance(int value) {
        ClientInformation ci = this.clientInformation();
        this.updateOptions(new ClientInformation(ci.language(), value, ci.chatVisibility(),
                ci.chatColors(), ci.modelCustomisation(), ci.mainHand(),
                ci.textFilteringEnabled(), ci.allowsListing()));
    }

    /** 尚未单独设置时把假人视距对齐到服务器视距（单机下即玩家自己的渲染距离）。 */
    private void sog$applyFollowViewDistance() {
        ServerLevel level = (ServerLevel) this.level();
        if (level == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        int serverView = server.getPlayerList().getViewDistance();
        if (this.clientInformation().viewDistance() != serverView) {
            this.sog$applyViewDistance(serverView);
        }
    }

    @Override
    public void sog$refreshRenderTickets() {
        this.sog$dropTickets();
        if (!SogSettings.fakePlayerRenderSettings) {
            return;
        }
        int render = this.sog$getRenderDistance();
        int simulation = this.sog$getSimulationDistance();
        if (render < 0 && simulation < 0) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        ChunkPos pos = this.chunkPosition();
        ServerChunkCache source = level.getChunkSource();
        if (render >= 0) {
            source.addRegionTicket(TicketType.PLAYER, pos, render, pos);
            this.sog$loadTicketOn = true;
            this.sog$loadTicketRadius = render;
        }
        if (simulation >= 0) {
            source.addRegionTicket(TicketType.PLAYER, pos, simulation, pos);
            this.sog$simTicketOn = true;
            this.sog$simTicketRadius = simulation;
        }
        this.sog$ticketLevel = level;
        this.sog$ticketCx = pos.x;
        this.sog$ticketCz = pos.z;
    }

    @Override
    public void sog$removeRenderTickets() {
        this.sog$dropTickets();
    }

    private void sog$dropTickets() {
        if (this.sog$ticketLevel == null) {
            return;
        }
        ServerChunkCache source = this.sog$ticketLevel.getChunkSource();
        ChunkPos old = new ChunkPos(this.sog$ticketCx, this.sog$ticketCz);
        if (this.sog$loadTicketOn) {
            source.removeRegionTicket(TicketType.PLAYER, old, this.sog$loadTicketRadius, old);
            this.sog$loadTicketOn = false;
        }
        if (this.sog$simTicketOn) {
            source.removeRegionTicket(TicketType.PLAYER, old, this.sog$simTicketRadius, old);
            this.sog$simTicketOn = false;
        }
        this.sog$ticketLevel = null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sog$followFakePosition(CallbackInfo ci) {
        // “跟随玩家”只剩召唤这一次：还没单独设置过视距的假人，在召唤后的第一次 tick
        // 对齐一次服务器视距，之后即使玩家改设置也不再跟着变。这样关掉规则时它的设置
        // 自然停在关闭那一刻，重新开启也不会被玩家设置覆盖。
        if (SogSettings.fakePlayerRenderSettings && this.sog$getRenderDistance() < 0
                && !this.sog$followApplied) {
            this.sog$followApplied = true;
            this.sog$applyFollowViewDistance();
        }
        boolean wanted = SogSettings.fakePlayerRenderSettings
                && (this.sog$getRenderDistance() >= 0 || this.sog$getSimulationDistance() >= 0);
        if (!wanted && this.sog$ticketLevel == null) {
            return;
        }
        ChunkPos now = this.chunkPosition();
        boolean moved = this.sog$ticketLevel == null
                || this.sog$ticketLevel != this.level()
                || this.sog$ticketCx != now.x || this.sog$ticketCz != now.z;
        if (moved) {
            this.sog$refreshRenderTickets();
        }
    }

    @Inject(method = "kill()V", at = @At("HEAD"))
    private void sog$onKill(CallbackInfo ci) {
        this.sog$removeRenderTickets();
    }

    @Inject(method = "kill(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void sog$onKillMessage(net.minecraft.network.chat.Component reason, CallbackInfo ci) {
        this.sog$removeRenderTickets();
    }
}
