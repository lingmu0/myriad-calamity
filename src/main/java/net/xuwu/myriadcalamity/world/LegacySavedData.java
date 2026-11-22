package net.xuwu.myriadcalamity.world;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** Reads the old, flat SavedData files when no namespaced file has been saved yet. */
final class LegacySavedData {
    private LegacySavedData() {}

    static <T extends SavedData> T load(@Nullable ServerLevel level,String id,
            Function<CompoundTag,T> decode,Supplier<T> empty) {
        if(level==null)return empty.get();
        Path world=level.getServer().getWorldPath(LevelResource.ROOT);
        Path current=DimensionType.getStorageFolder(level.dimension(),world);
        Path old=level.dimension()==Level.OVERWORLD?world:level.dimension()==Level.NETHER?world.resolve("DIM-1")
            :level.dimension()==Level.END?world.resolve("DIM1"):current;
        for(Path folder:new LinkedHashSet<>(List.of(current,old))) {
            Path data=folder.resolve("data");
            for(Path legacy:List.of(data.resolve(id+".dat"),data.resolve("minecraft").resolve(id+".dat"))) {
                if(!Files.isRegularFile(legacy))continue;
                try {
                    CompoundTag root=NbtIo.readCompressed(legacy,NbtAccounter.defaultQuota());
                    return decode.apply(root.getCompoundOrEmpty("data"));
                } catch(IOException e) {
                    LogUtils.getLogger().warn("Could not read legacy encounter data at {}",legacy,e);
                }
            }
        }
        return empty.get();
    }
}
