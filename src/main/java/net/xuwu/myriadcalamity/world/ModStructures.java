package net.xuwu.myriadcalamity.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.xuwu.myriadcalamity.MyriadCalamity;

public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,MyriadCalamity.ID);
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,MyriadCalamity.ID);
    public static final RegistryObject<StructureType<CogworkTheatreStructure>> THEATRE=TYPES.register("cogwork_theatre",()->()->CogworkTheatreStructure.CODEC);
    public static final RegistryObject<StructurePieceType> THEATRE_PIECE=PIECES.register("cogwork_theatre",()->CogworkTheatrePiece::new);
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); }
}
