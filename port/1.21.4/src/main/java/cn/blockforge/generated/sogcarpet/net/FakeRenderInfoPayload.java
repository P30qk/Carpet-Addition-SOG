package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：打开 GCA 假人背包时下发的假人渲染设置。
 *
 * <p>render / simulation 为 -1 表示跟随默认；defaultRender / defaultSimulation
 * 携带服务器当前的玩家视距与模拟距离，用于“默认跟随玩家设置”的滑条初值。
 * syncId 是该背包菜单的 containerId，客户端据此把设置与具体界面对齐，
 * 避免上一个假人的缓存画到新界面上。</p>
 */
public record FakeRenderInfoPayload(int syncId, String fake, int render, int simulation,
        int defaultRender, int defaultSimulation) implements CustomPacketPayload {

    public static final Type<FakeRenderInfoPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:fake_render_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FakeRenderInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FakeRenderInfoPayload::syncId,
                    ByteBufCodecs.STRING_UTF8, FakeRenderInfoPayload::fake,
                    ByteBufCodecs.VAR_INT, FakeRenderInfoPayload::render,
                    ByteBufCodecs.VAR_INT, FakeRenderInfoPayload::simulation,
                    ByteBufCodecs.VAR_INT, FakeRenderInfoPayload::defaultRender,
                    ByteBufCodecs.VAR_INT, FakeRenderInfoPayload::defaultSimulation,
                    FakeRenderInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
