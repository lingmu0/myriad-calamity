package net.xuwu.myriadcalamity.world;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class CogworkTheatreStructure extends Structure {
    public static final Codec<CogworkTheatreStructure> CODEC=simpleCodec(CogworkTheatreStructure::new);
    public CogworkTheatreStructure(StructureSettings settings) { super(settings); }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int dx:new int[]{-16,0,16}) for(int dz:new int[]{-16,0,16}) {
            int y=context.chunkGenerator().getFirstOccupiedHeight(x+dx,z+dz,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            low=Math.min(low,y); high=Math.max(high,y);
        }
        if(high-low>7 || low<context.chunkGenerator().getSeaLevel() || high+14>=context.heightAccessor().getMaxBuildHeight()) return Optional.empty();
        BlockPos centre=new BlockPos(x,high,z);
        return Optional.of(new GenerationStub(centre,builder->builder.addPiece(new CogworkTheatrePiece(centre))));
    }
    @Override public StructureType<?> type() { return ModStructures.THEATRE.get(); }
}
