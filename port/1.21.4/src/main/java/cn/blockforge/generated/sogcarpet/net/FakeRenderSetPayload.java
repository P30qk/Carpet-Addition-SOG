package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：调整某个假人的渲染距离 / 模拟距离。
 *
 * <p>取值约定：-1 = 跟随玩家（服务器全局）默认值；2-32 = 独立设定。</p>
 */
public record FakeRenderSetPayload(String fake, int render, int simulation) implements CustomPacketPayload {
    public static final Type<FakeRenderSetPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:fake_render_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FakeRenderSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FakeRenderSetPayload::fake,
                    ByteBufCodecs.VAR_INT, FakeRenderSetPayload::render,
                    ByteBufCodecs.VAR_INT, FakeRenderSetPayload::simulation,
                    FakeRenderSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
