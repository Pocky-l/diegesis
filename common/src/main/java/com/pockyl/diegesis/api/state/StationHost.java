package com.pockyl.diegesis.api.state;

/**
 * Implemented by a block entity whose station keeps its state between uses (items left on the table, a knob's position).
 * The block entity creates the state once with its station's schema and saves it:
 *
 * <pre>{@code
 * private final StationState station = new StationState(MyStations.LAB_TABLE.schema());
 *
 * public StationState stationState() { return station; }
 * protected void saveAdditional(CompoundTag tag) { tag.put("station", station.save()); }
 * public void load(CompoundTag tag) { station.load(tag.getCompound("station")); }
 * public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }   // clients see the items while idle
 * public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
 * }</pre>
 *
 * The library marks the block entity changed and sends the block update whenever a player changes the state.
 */
public interface StationHost {
    StationState stationState();
}
