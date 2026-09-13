package net.xuwu.myriadcalamity.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.xuwu.myriadcalamity.MyriadCalamity;

public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,MyriadCalamity.ID);
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,MyriadCalamity.ID);
    public static final DeferredHolder<StructureType<?>,StructureType<CogworkTheatreStructure>> THEATRE=TYPES.register("cogwork_theatre",()->()->CogworkTheatreStructure.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> THEATRE_PIECE=PIECES.register("cogwork_theatre",()->CogworkTheatrePiece::new);
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
}
