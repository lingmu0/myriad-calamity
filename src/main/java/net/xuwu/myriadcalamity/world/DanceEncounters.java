package net.xuwu.myriadcalamity.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-level lock survives altar/boss chunk unloading and prevents duplicate paid summons. */
public final class DanceEncounters extends SavedData {
    private final Map<Long,Set<UUID>> active=new HashMap<>();
    public static DanceEncounters get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(DanceEncounters::new,DanceEncounters::load),"myriad_calamity_dances");
    }
    public boolean active(BlockPos altar) { return active.containsKey(altar.asLong()); }
    public void begin(BlockPos altar,UUID first,UUID second) {
        active.put(altar.asLong(),new HashSet<>(Set.of(first,second)));setDirty();
    }
    public void release(BlockPos altar,UUID dancer) {
        Set<UUID> ids=active.get(altar.asLong());
        if(ids!=null && ids.remove(dancer)) { if(ids.isEmpty()) active.remove(altar.asLong());setDirty(); }
    }
    public static DanceEncounters load(CompoundTag tag,HolderLookup.Provider registries) {
        DanceEncounters result=new DanceEncounters();
        for(Tag entry:tag.getList("Encounters",Tag.TAG_COMPOUND)) {
            CompoundTag dance=(CompoundTag)entry;Set<UUID> ids=new HashSet<>();
            for(Tag item:dance.getList("Dancers",Tag.TAG_COMPOUND)) {
                CompoundTag dancer=(CompoundTag)item;if(dancer.hasUUID("UUID"))ids.add(dancer.getUUID("UUID"));
            }
            if(!ids.isEmpty())result.active.put(dance.getLong("Altar"),ids);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        ListTag list=new ListTag();
        active.forEach((pos,ids)->{CompoundTag dance=new CompoundTag();dance.putLong("Altar",pos);ListTag dancers=new ListTag();
            for(UUID id:ids){CompoundTag item=new CompoundTag();item.putUUID("UUID",id);dancers.add(item);}dance.put("Dancers",dancers);list.add(dance);});
        tag.put("Encounters",list);return tag;
    }
}
