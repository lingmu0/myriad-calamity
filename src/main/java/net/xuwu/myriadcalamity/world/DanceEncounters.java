package net.xuwu.myriadcalamity.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-level lock survives altar/boss chunk unloading and prevents duplicate paid summons. */
public final class DanceEncounters extends SavedData {
    private static final SavedDataType<DanceEncounters> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath(MyriadCalamity.ID,"dances"),
        level -> LegacySavedData.load(level,"myriad_calamity_dances",DanceEncounters::load,DanceEncounters::new),
        level -> CompoundTag.CODEC.xmap(DanceEncounters::load,DanceEncounters::save));
    private final Map<Long,Set<UUID>> active=new HashMap<>();
    public static DanceEncounters get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
    public boolean active(BlockPos altar) { return active.containsKey(altar.asLong()); }
    public void begin(BlockPos altar,UUID first,UUID second) {
        active.put(altar.asLong(),new HashSet<>(Set.of(first,second)));setDirty();
    }
    public void release(BlockPos altar,UUID dancer) {
        Set<UUID> ids=active.get(altar.asLong());
        if(ids!=null && ids.remove(dancer)) { if(ids.isEmpty()) active.remove(altar.asLong());setDirty(); }
    }
    public static DanceEncounters load(CompoundTag tag) {
        DanceEncounters result=new DanceEncounters();
        for(Tag entry:tag.getListOrEmpty("Encounters")) {
            if(!(entry instanceof CompoundTag dance))continue;Set<UUID> ids=new HashSet<>();
            for(Tag item:dance.getListOrEmpty("Dancers")) {
                if(!(item instanceof CompoundTag dancer))continue;dancer.read("UUID",UUIDUtil.CODEC).ifPresent(ids::add);
            }
            if(!ids.isEmpty())result.active.put(dance.getLongOr("Altar",0L),ids);
        }
        return result;
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();
        ListTag list=new ListTag();
        active.forEach((pos,ids)->{CompoundTag dance=new CompoundTag();dance.putLong("Altar",pos);ListTag dancers=new ListTag();
            for(UUID id:ids){CompoundTag item=new CompoundTag();item.store("UUID",UUIDUtil.CODEC,id);dancers.add(item);}dance.put("Dancers",dancers);list.add(dance);});
        tag.put("Encounters",list);return tag;
    }
}
