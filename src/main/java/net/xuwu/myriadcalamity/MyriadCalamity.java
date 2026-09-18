package net.xuwu.myriadcalamity;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.xuwu.myriadcalamity.block.DanceAltarBlock;
import net.xuwu.myriadcalamity.world.ModStructures;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.xuwu.myriadcalamity.entity.CogworkDancer;
import net.xuwu.myriadcalamity.entity.CogworkBlade;
import net.xuwu.myriadcalamity.item.WindingKeyItem;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.minecraft.world.effect.MobEffect;
import net.xuwu.myriadcalamity.effect.RoarMarkEffect;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.CelestialHound;
import net.xuwu.myriadcalamity.entity.TriPointedBlade;
import net.xuwu.myriadcalamity.entity.DivineFlyingSword;
import net.xuwu.myriadcalamity.entity.LightningTrail;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.item.CloudTalismanItem;

@Mod(MyriadCalamity.ID)
public final class MyriadCalamity {
    public static final String ID = "myriad_calamity";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    public static final RegistryObject<RoarMarkEffect> ROAR_MARK = EFFECTS.register("roar_mark", RoarMarkEffect::new);
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final RegistryObject<DanceAltarBlock> ALTAR=BLOCKS.register("dance_altar",()->new DanceAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1,3600000).sound(SoundType.COPPER).lightLevel(s->9).pushReaction(PushReaction.BLOCK)));
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<EntityType<CogworkDancer>> DANCER = ENTITIES.register("cogwork_dancer",
        () -> EntityType.Builder.of(CogworkDancer::new, MobCategory.MONSTER).sized(1.25F, 3.5F)
            .fireImmune().clientTrackingRange(12).updateInterval(1).build(ID + ":cogwork_dancer"));
    public static final RegistryObject<EntityType<CogworkBlade>> BLADE=ENTITIES.register("cogwork_blade",
        () -> EntityType.Builder.<CogworkBlade>of(CogworkBlade::new,MobCategory.MISC).sized(.6F,.6F)
            .fireImmune().clientTrackingRange(10).updateInterval(1).build(ID+":cogwork_blade"));
    public static final RegistryObject<SoundEvent> DANCER_MUSIC_PHASE1 = music("music.cogwork_dancer_phase1");
    public static final RegistryObject<EntityType<YangJian>> YANG_JIAN = ENTITIES.register("yang_jian",
        () -> EntityType.Builder.of(YangJian::new, MobCategory.MONSTER).sized(1.25F, 3.35F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":yang_jian"));
    public static final RegistryObject<EntityType<CelestialHound>> CELESTIAL_HOUND = ENTITIES.register("celestial_hound",
        () -> EntityType.Builder.of(CelestialHound::new, MobCategory.MONSTER).sized(.8F, 1.05F)
            .fireImmune().clientTrackingRange(12).updateInterval(1).build(ID + ":celestial_hound"));
    public static final RegistryObject<EntityType<TriPointedBlade>> TRI_POINTED_BLADE = ENTITIES.register("tri_pointed_blade",
        () -> EntityType.Builder.<TriPointedBlade>of(TriPointedBlade::new, MobCategory.MISC).sized(.55F, .55F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":tri_pointed_blade"));
    public static final RegistryObject<EntityType<DivineFlyingSword>> DIVINE_FLYING_SWORD = ENTITIES.register("divine_flying_sword",
        () -> EntityType.Builder.<DivineFlyingSword>of(DivineFlyingSword::new, MobCategory.MISC).sized(.45F, .45F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":divine_flying_sword"));
    public static final RegistryObject<EntityType<LightningTrail>> LIGHTNING_TRAIL = ENTITIES.register("lightning_trail",
        () -> EntityType.Builder.<LightningTrail>of(LightningTrail::new, MobCategory.MISC).sized(.2F, .2F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":lightning_trail"));
    public static final RegistryObject<EntityType<YangJianHazard>> YANG_JIAN_HAZARD = ENTITIES.register("yang_jian_hazard",
        () -> EntityType.Builder.<YangJianHazard>of(YangJianHazard::new, MobCategory.MISC).sized(.2F, .2F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":yang_jian_hazard"));
    public static final RegistryObject<SoundEvent> DANCER_MUSIC_PHASE2 = music("music.cogwork_dancer_phase2");
    public static final RegistryObject<SoundEvent> DANCER_MUSIC_PHASE3 = music("music.cogwork_dancer_phase3");
    public static final RegistryObject<SoundEvent> DANCER_MUSIC_PHASE4 = music("music.cogwork_dancer_phase4");
    public static final RegistryObject<SoundEvent> YANG_JIAN_MUSIC_INTRO = music("music.yang_jian_bgm_intro");
    public static final RegistryObject<SoundEvent> YANG_JIAN_MUSIC_MAIN = music("music.yang_jian_bgm_main");
    public static final RegistryObject<Item> CORE = ITEMS.register("cogwork_heart", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<WindingKeyItem> KEY = ITEMS.register("winding_key", () -> new WindingKeyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<ForgeSpawnEggItem> EGG = ITEMS.register("cogwork_dancer_spawn_egg",
        () -> new ForgeSpawnEggItem(DANCER, 0x263742, 0xdec68b, new Item.Properties()));
    public static final RegistryObject<CloudTalismanItem> CLOUD_TALISMAN = ITEMS.register("cloud_talisman",
        () -> new CloudTalismanItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DIVINE_SIGIL = ITEMS.register("divine_sigil", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<ForgeSpawnEggItem> YANG_JIAN_EGG = ITEMS.register("yang_jian_spawn_egg",
        () -> new ForgeSpawnEggItem(YANG_JIAN, 0x202934, 0xc3af7d, new Item.Properties()));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + ID)).icon(() -> KEY.get().getDefaultInstance())
        .displayItems((p, output) -> {
            output.accept(KEY.get()); output.accept(EGG.get()); output.accept(CORE.get());
            output.accept(CLOUD_TALISMAN.get()); output.accept(YANG_JIAN_EGG.get()); output.accept(DIVINE_SIGIL.get());
        }).build());

    // Forge 1.20.1 loads mods through a no-argument constructor and hands out the bus here.
    public MyriadCalamity() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        SOUND_EVENTS.register(bus);
        EFFECTS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MyriadConfig.SPEC);
        BLOCKS.register(bus);
        ModStructures.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
        bus.addListener(MyriadCalamity::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DANCER.get(), CogworkDancer.attributes().build());
        event.put(YANG_JIAN.get(), YangJian.attributes().build());
        event.put(CELESTIAL_HOUND.get(), CelestialHound.attributes().build());
    }
    private static RegistryObject<SoundEvent> music(String path) {
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id(path)));
    }
    public static ResourceLocation id(String path) { return new ResourceLocation(ID, path); }
}
