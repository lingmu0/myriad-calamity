package net.xuwu.myriadcalamity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.xuwu.myriadcalamity.MyriadCalamity;

public final class CogworkTheatrePiece extends StructurePiece {
    private final BlockPos centre;
    public CogworkTheatrePiece(BlockPos centre) {
        super(ModStructures.THEATRE_PIECE.get(),0,new BoundingBox(centre.getX()-20,centre.getY()-8,centre.getZ()-20,centre.getX()+20,centre.getY()+13,centre.getZ()+20));
        this.centre=centre;
    }
    public CogworkTheatrePiece(StructurePieceSerializationContext context,CompoundTag tag) {
        super(ModStructures.THEATRE_PIECE.get(),tag); centre=BlockPos.of(tag.getLong("TheatreCentre"));
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) { tag.putLong("TheatreCentre",centre.asLong()); }
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox chunkBounds,ChunkPos chunk,BlockPos origin) {
        for(int x=-20;x<=20;x++) for(int z=-20;z<=20;z++) {
            if(!TheatreLayout.floor(x,z)) continue;
            for(int y=-8;y<0;y++) set(level,chunkBounds,x,y,z,Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            BlockState tile=switch(TheatreLayout.material(x,z)) {
                case 1 -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                case 2 -> Blocks.WAXED_CUT_COPPER.defaultBlockState();
                case 3 -> Blocks.LODESTONE.defaultBlockState();
                case 4 -> MyriadCalamity.ALTAR.get().defaultBlockState();
                default -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            };
            set(level,chunkBounds,x,0,z,tile);
            for(int y=1;y<=12;y++) set(level,chunkBounds,x,y,z,Blocks.AIR.defaultBlockState());
            if(TheatreLayout.pillar(x,z)) {
                for(int y=1;y<=9;y++) set(level,chunkBounds,x,y,z,(y==1||y==8?Blocks.WAXED_CUT_COPPER:Blocks.POLISHED_DEEPSLATE).defaultBlockState());
                set(level,chunkBounds,x,10,z,Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
                set(level,chunkBounds,x,11,z,Blocks.OCHRE_FROGLIGHT.defaultBlockState());
                set(level,chunkBounds,x,12,z,Blocks.WAXED_CUT_COPPER_SLAB.defaultBlockState());
            } else if(Math.hypot(x,z)>19 && Math.abs(x)>3 && Math.abs(z)>3) {
                set(level,chunkBounds,x,1,z,Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
            }
            if(Math.abs(Math.hypot(x,z)-16)<.45 && (Math.abs(x)==Math.abs(z) || x==0 || z==0)) {
                set(level,chunkBounds,x,0,z,Blocks.OCHRE_FROGLIGHT.defaultBlockState());
            }
        }
        // Four entrances descend to the surrounding terrain without obstructing the fighting floor.
        for(int side=-1;side<=1;side+=2) for(int width=-2;width<=2;width++) {
            for(int step=0;step<3;step++) {
                set(level,chunkBounds,side*(18+step),-step,width,Blocks.WAXED_CUT_COPPER.defaultBlockState());
                set(level,chunkBounds,width,-step,side*(18+step),Blocks.WAXED_CUT_COPPER.defaultBlockState());
            }
        }
    }
    private void set(WorldGenLevel level,BoundingBox bounds,int x,int y,int z,BlockState state) {
        BlockPos at=centre.offset(x,y,z);
        if(bounds.isInside(at)) level.setBlock(at,state,2);
    }
}
