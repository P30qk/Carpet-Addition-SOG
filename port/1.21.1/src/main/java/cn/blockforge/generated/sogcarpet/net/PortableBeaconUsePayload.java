package cn.blockforge.generated.sogcarpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：随身信标里选好了效果，请给这个玩家上 buff。
 *
 * <p>最多两个效果：{@code primaryId} 必填，{@code secondaryId} 为空串表示只选了一个
 * （那时该效果是 II 级；选两个时各 I 级）。服务端不信任客户端：会重新检查
 * 「随身信标规则开着」「发包者背包里确实有信标」「两个效果都能通过黑/白名单」，
 * 任何一条不满足就丢弃并回一句提示。于是客户端界面只影响显示，改包也开不出被 Op 禁用的效果。</p>
 */
public record PortableBeaconUsePayload(String primaryId, String secondaryId)
        implements CustomPacketPayload {
    /** 效果 id 的长度上限（原版最长的 id 也远小于这个值）。 */
    private static final int MAX_ID_LENGTH = 128;

    public static final Type<PortableBeaconUsePayload> TYPE =
            new Type<>(ResourceLocation.parse("sog_carpet:portable_beacon_use"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortableBeaconUsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), PortableBeaconUsePayload::primaryId,
                    ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), PortableBeaconUsePayload::secondaryId,
                    PortableBeaconUsePayload::new);

    public PortableBeaconUsePayload {
        if (primaryId == null) {
            primaryId = "";
        }
        if (secondaryId == null) {
            secondaryId = "";
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
