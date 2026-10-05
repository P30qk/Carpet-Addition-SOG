package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：打开 GCA 假人背包时下发的“可拾取工具类别”掩码。
 *
 * <p>只有 {@code fakePlayerToolsOnly} 规则开着时才会发；客户端收到后就在背包右侧
 * 画那一列开关。syncId 用来和具体界面（containerId）对齐。</p>
 */
public record FakeToolsInfoPayload(int syncId, String fake, int mask) implements CustomPacketPayload {
    public static final Type<FakeToolsInfoPayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:fake_tools_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FakeToolsInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FakeToolsInfoPayload::syncId,
                    ByteBufCodecs.STRING_UTF8, FakeToolsInfoPayload::fake,
                    ByteBufCodecs.VAR_INT, FakeToolsInfoPayload::mask,
                    FakeToolsInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
