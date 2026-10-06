package fr.pacdu.pacpackquests.network;

import fr.pacdu.pacpackquests.util.RegistryType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RequestRegistryPayload(RegistryType type) implements CustomPayload {

    public static final Id<RequestRegistryPayload> ID = new Id<>(Identifier.of("pacpackquests", "req_registry"));

    public static final PacketCodec<RegistryByteBuf, RequestRegistryPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeEnumConstant(value.type());
            },
            buf -> new RequestRegistryPayload(
                    buf.readEnumConstant(RegistryType.class)
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
