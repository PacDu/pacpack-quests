package fr.pacdu.pacpackquests.network;

import fr.pacdu.pacpackquests.util.RegistryType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SyncRegistryPayload(RegistryType type, List<String> entries) implements CustomPayload {

    public static final Id<SyncRegistryPayload> ID = new Id<>(Identifier.of("pacpackquests", "sync_registry"));

    public static final PacketCodec<RegistryByteBuf, SyncRegistryPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeEnumConstant(value.type());
                buf.writeCollection(value.entries(), PacketByteBuf::writeString);
            },
            buf -> new SyncRegistryPayload(
                    buf.readEnumConstant(RegistryType.class),
                    buf.readCollection(ArrayList::new, PacketByteBuf::readString)
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
