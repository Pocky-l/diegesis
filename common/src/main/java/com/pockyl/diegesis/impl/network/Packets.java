package com.pockyl.diegesis.impl.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import com.pockyl.diegesis.impl.action.StationAction;

import java.util.Objects;
import java.util.UUID;

/**
 * Every message of the library. All travel in two payloads, one per direction ({@link DiegesisNetwork.ToServer},
 * {@link DiegesisNetwork.ToClient}); the first byte names the message. Optional on both sides: a client without the library never gets them, a server without it never answers, and
 * then blocks keep their vanilla behaviour.
 */
public final class Packets {
    private Packets() {
    }

    public sealed interface Packet permits ClientPacket, ServerPacket {
        int id();

        void writeBody(RegistryFriendlyByteBuf buf);

        default void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(id());
            writeBody(buf);
        }
    }

    /** Client to server. */
    public sealed interface ClientPacket extends Packet permits Open, Close, Action, PresenceUpdate {
    }

    /** Server to client. */
    public sealed interface ServerPacket extends Packet permits Opened, Denied, Closed, StateSync, PresenceRelay, Effect {
    }

    public static ClientPacket readClient(RegistryFriendlyByteBuf buf) {
        int id = buf.readByte();
        return switch (id) {
            case 0 -> new Open(buf.readBlockPos());
            case 1 -> new Close();
            case 2 -> new Action(buf.readBlockPos(), StationAction.read(buf));
            case 3 -> new PresenceUpdate(buf.readBlockPos(), Presence.read(buf));
            default -> throw new IllegalArgumentException("Unknown client message " + id);
        };
    }

    public static ServerPacket readServer(RegistryFriendlyByteBuf buf) {
        int id = buf.readByte();
        return switch (id) {
            case 10 -> new Opened(buf.readBlockPos(), buf.readResourceLocation(), buf.readEnum(Direction.class), buf.readByteArray(),
                    buf.readUtf());
            case 11 -> new Denied(buf.readBlockPos());
            case 12 -> new Closed(buf.readBlockPos());
            case 13 -> new StateSync(buf.readBlockPos(), buf.readResourceLocation(), buf.readEnum(Direction.class), buf.readBoolean(),
                    buf.readByteArray());
            case 14 -> new PresenceRelay(buf.readBlockPos(), buf.readUUID(), Presence.read(buf));
            case 15 -> new Effect(buf.readBlockPos(), buf.readResourceLocation(), buf.readUtf(64),
                    Objects.requireNonNullElseGet(buf.readNbt(), CompoundTag::new));
            default -> throw new IllegalArgumentException("Unknown server message " + id);
        };
    }

    /** The player used a station block and wants to work at it. */
    public record Open(BlockPos pos) implements ClientPacket {
        @Override
        public int id() {
            return 0;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }
    }

    /** The player left the station. */
    public record Close() implements ClientPacket {
        @Override
        public int id() {
            return 1;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
        }
    }

    public record Action(BlockPos pos, StationAction action) implements ClientPacket {
        @Override
        public int id() {
            return 2;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            action.write(buf);
        }
    }

    public record PresenceUpdate(BlockPos pos, Presence presence) implements ClientPacket {
        @Override
        public int id() {
            return 3;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            presence.write(buf);
        }
    }

    /** The server accepted the player at the station; {@code state} is the full state. */
    public record Opened(BlockPos pos, ResourceLocation type, Direction facing, byte[] state, String seat) implements ServerPacket {
        @Override
        public int id() {
            return 10;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeResourceLocation(type);
            buf.writeEnum(facing);
            buf.writeByteArray(state);
            buf.writeUtf(seat);
        }
    }

    /** The server refused (too far, busy, block changed); the client flies the camera back. */
    public record Denied(BlockPos pos) implements ServerPacket {
        @Override
        public int id() {
            return 11;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }
    }

    /** The server ended the player's use (walked away, block broken, closed by code). */
    public record Closed(BlockPos pos) implements ServerPacket {
        @Override
        public int id() {
            return 12;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }
    }

    /** State of a station in use, to its users and the players around it; {@code full} replaces, otherwise changes. */
    public record StateSync(BlockPos pos, ResourceLocation type, Direction facing, boolean full, byte[] data) implements ServerPacket {
        @Override
        public int id() {
            return 13;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeResourceLocation(type);
            buf.writeEnum(facing);
            buf.writeBoolean(full);
            buf.writeByteArray(data);
        }
    }

    /** What another player at the station holds. */
    public record PresenceRelay(BlockPos pos, UUID player, Presence presence) implements ServerPacket {
        @Override
        public int id() {
            return 14;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(player);
            presence.write(buf);
        }
    }

    public record Effect(BlockPos pos, ResourceLocation effect, String node, CompoundTag data) implements ServerPacket {
        @Override
        public int id() {
            return 15;
        }

        @Override
        public void writeBody(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeResourceLocation(effect);
            buf.writeUtf(node, 64);
            buf.writeNbt(data);
        }
    }
}
