package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：切换某个假人“可拾取工具类别”的位掩码。 */
public record FakeToolsSetPayload(String fake, int mask) implements CustomPacketPayload {
    public static final Type<FakeToolsSetPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:fake_tools_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FakeToolsSetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FakeToolsSetPayload::fake,
                    ByteBufCodecs.VAR_INT, FakeToolsSetPayload::mask,
                    FakeToolsSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
