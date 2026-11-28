package net.xuwu.myriadcalamity;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.xuwu.myriadcalamity.block.DanceAltarBlock;
import net.xuwu.myriadcalamity.world.ModStructures;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
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
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.SakuraWindBlade;
import net.xuwu.myriadcalamity.entity.DivineThunderHazard;
import net.xuwu.myriadcalamity.entity.SakuraRingWave;
import net.xuwu.myriadcalamity.entity.SakuraTornadoEntity;
import net.xuwu.myriadcalamity.entity.SakuraFeatherBlade;
import net.xuwu.myriadcalamity.item.CloudTalismanItem;
import net.xuwu.myriadcalamity.item.EverblossomBranchItem;
import net.xuwu.myriadcalamity.item.DivineDragonTearsItem;

@Mod(MyriadCalamity.ID)
public final class MyriadCalamity {
    public static final String ID = "myriad_calamity";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    public static final DeferredHolder<MobEffect, RoarMarkEffect> ROAR_MARK = EFFECTS.register("roar_mark", RoarMarkEffect::new);
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<DanceAltarBlock> ALTAR=BLOCKS.register("dance_altar",()->new DanceAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(-1,3600000).sound(SoundType.COPPER).lightLevel(s->9).pushReaction(PushReaction.BLOCK)));
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<CogworkDancer>> DANCER = ENTITIES.register("cogwork_dancer",
        () -> EntityType.Builder.of(CogworkDancer::new, MobCategory.MONSTER).sized(1.25F, 3.5F)
            .fireImmune().clientTrackingRange(12).updateInterval(1).build(ID + ":cogwork_dancer"));
    public static final DeferredHolder<EntityType<?>, EntityType<CogworkBlade>> BLADE=ENTITIES.register("cogwork_blade",
        () -> EntityType.Builder.<CogworkBlade>of(CogworkBlade::new,MobCategory.MISC).sized(.6F,.6F)
            .fireImmune().clientTrackingRange(10).updateInterval(1).build(ID+":cogwork_blade"));
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCER_MUSIC_PHASE1 = music("music.cogwork_dancer_phase1");
    public static final DeferredHolder<EntityType<?>, EntityType<YangJian>> YANG_JIAN = ENTITIES.register("yang_jian",
        () -> EntityType.Builder.of(YangJian::new, MobCategory.MONSTER).sized(1.4F, 3.6F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":yang_jian"));
    public static final DeferredHolder<EntityType<?>, EntityType<CelestialHound>> CELESTIAL_HOUND = ENTITIES.register("celestial_hound",
        () -> EntityType.Builder.of(CelestialHound::new, MobCategory.MONSTER).sized(.8F, 1.05F)
            .fireImmune().clientTrackingRange(12).updateInterval(1).build(ID + ":celestial_hound"));
    public static final DeferredHolder<EntityType<?>, EntityType<TriPointedBlade>> TRI_POINTED_BLADE = ENTITIES.register("tri_pointed_blade",
        () -> EntityType.Builder.<TriPointedBlade>of(TriPointedBlade::new, MobCategory.MISC).sized(.55F, .55F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":tri_pointed_blade"));
    public static final DeferredHolder<EntityType<?>, EntityType<DivineFlyingSword>> DIVINE_FLYING_SWORD = ENTITIES.register("divine_flying_sword",
        () -> EntityType.Builder.<DivineFlyingSword>of(DivineFlyingSword::new, MobCategory.MISC).sized(.45F, .45F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":divine_flying_sword"));
    public static final DeferredHolder<EntityType<?>, EntityType<LightningTrail>> LIGHTNING_TRAIL = ENTITIES.register("lightning_trail",
        () -> EntityType.Builder.<LightningTrail>of(LightningTrail::new, MobCategory.MISC).sized(.2F, .2F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":lightning_trail"));
    public static final DeferredHolder<EntityType<?>, EntityType<YangJianHazard>> YANG_JIAN_HAZARD = ENTITIES.register("yang_jian_hazard",
        () -> EntityType.Builder.<YangJianHazard>of(YangJianHazard::new, MobCategory.MISC).sized(.2F, .2F)
            .fireImmune().clientTrackingRange(16).updateInterval(1).build(ID + ":yang_jian_hazard"));
    public static final DeferredHolder<EntityType<?>, EntityType<DivineDragon>> DIVINE_DRAGON = ENTITIES.register("divine_dragon",
        () -> EntityType.Builder.of(DivineDragon::new, MobCategory.MONSTER).sized(12.0F, 26.0F)
            .fireImmune().clientTrackingRange(24).updateInterval(1).build(ID + ":divine_dragon"));
    public static final DeferredHolder<EntityType<?>, EntityType<SakuraWindBlade>> SAKURA_WIND_BLADE = ENTITIES.register("sakura_wind_blade",
        () -> EntityType.Builder.<SakuraWindBlade>of(SakuraWindBlade::new, MobCategory.MISC).sized(1.0F, 1.0F)
            .fireImmune().clientTrackingRange(20).updateInterval(1).build(ID + ":sakura_wind_blade"));
    public static final DeferredHolder<EntityType<?>, EntityType<DivineThunderHazard>> DIVINE_THUNDER_HAZARD = ENTITIES.register("divine_thunder_hazard",
        () -> EntityType.Builder.<DivineThunderHazard>of(DivineThunderHazard::new, MobCategory.MISC).sized(0.4F, 0.4F)
            .fireImmune().clientTrackingRange(24).updateInterval(1).build(ID + ":divine_thunder_hazard"));
    public static final DeferredHolder<EntityType<?>, EntityType<SakuraRingWave>> SAKURA_RING_WAVE = ENTITIES.register("sakura_ring_wave",
        () -> EntityType.Builder.<SakuraRingWave>of(SakuraRingWave::new, MobCategory.MISC).sized(1.0F, 1.0F)
            .fireImmune().clientTrackingRange(24).updateInterval(1).build(ID + ":sakura_ring_wave"));
    public static final DeferredHolder<EntityType<?>, EntityType<SakuraTornadoEntity>> SAKURA_TORNADO = ENTITIES.register("sakura_tornado",
        () -> EntityType.Builder.<SakuraTornadoEntity>of(SakuraTornadoEntity::new, MobCategory.MISC).sized(2.0F, 4.0F)
            .fireImmune().clientTrackingRange(24).updateInterval(1).build(ID + ":sakura_tornado"));
    public static final DeferredHolder<EntityType<?>, EntityType<SakuraFeatherBlade>> SAKURA_FEATHER_BLADE = ENTITIES.register("sakura_feather_blade",
        () -> EntityType.Builder.<SakuraFeatherBlade>of(SakuraFeatherBlade::new, MobCategory.MISC).sized(0.8F, 0.8F)
            .fireImmune().clientTrackingRange(24).updateInterval(1).build(ID + ":sakura_feather_blade"));
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCER_MUSIC_PHASE2 = music("music.cogwork_dancer_phase2");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCER_MUSIC_PHASE3 = music("music.cogwork_dancer_phase3");
    public static final DeferredHolder<SoundEvent, SoundEvent> DANCER_MUSIC_PHASE4 = music("music.cogwork_dancer_phase4");
    public static final DeferredHolder<SoundEvent, SoundEvent> YANG_JIAN_MUSIC_INTRO = music("music.yang_jian_bgm_intro");
    public static final DeferredHolder<SoundEvent, SoundEvent> YANG_JIAN_MUSIC_MAIN = music("music.yang_jian_bgm_main");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_MUSIC_PHASE1 = music("music.divine_dragon_phase1");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_MUSIC_PHASE2 = music("music.divine_dragon_phase2");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_AMBIENT = music("entity.divine_dragon.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_COIL = music("entity.divine_dragon.coil");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_SWORD_SWING = music("entity.divine_dragon.sword_swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_WIND_SLASH = music("entity.divine_dragon.wind_slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_GALE_ROAR = music("entity.divine_dragon.gale_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_ATTACK_ROAR = music("entity.divine_dragon.attack_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_THUNDER_ROAR = music("entity.divine_dragon.thunder_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_THUNDER_CHARGE = music("entity.divine_dragon.thunder_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_LIGHTNING_STRIKE = music("entity.divine_dragon.lightning_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_CATACLYSM = music("entity.divine_dragon.cataclysm");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_DEATH_CRY = music("entity.divine_dragon.death_cry");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVINE_DRAGON_HURT = music("entity.divine_dragon.hurt");
    public static final DeferredItem<Item> CORE = ITEMS.registerSimpleItem("cogwork_heart", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<WindingKeyItem> KEY = ITEMS.register("winding_key", () -> new WindingKeyItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final DeferredItem<DeferredSpawnEggItem> EGG = ITEMS.register("cogwork_dancer_spawn_egg",
        () -> new DeferredSpawnEggItem(DANCER, 0x263742, 0xdec68b, new Item.Properties()));
    public static final DeferredItem<CloudTalismanItem> CLOUD_TALISMAN = ITEMS.register("cloud_talisman",
        () -> new CloudTalismanItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> DIVINE_SIGIL = ITEMS.registerSimpleItem("divine_sigil", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<DeferredSpawnEggItem> YANG_JIAN_EGG = ITEMS.register("yang_jian_spawn_egg",
        () -> new DeferredSpawnEggItem(YANG_JIAN, 0x202934, 0xc3af7d, new Item.Properties()));
    public static final DeferredItem<EverblossomBranchItem> EVERBLOSSOM_BRANCH = ITEMS.register("everblossom_branch",
        () -> new EverblossomBranchItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<DivineDragonTearsItem> DIVINE_DRAGON_TEARS = ITEMS.register("divine_dragon_tears",
        () -> new DivineDragonTearsItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final DeferredItem<DeferredSpawnEggItem> DIVINE_DRAGON_EGG = ITEMS.register("divine_dragon_spawn_egg",
        () -> new DeferredSpawnEggItem(DIVINE_DRAGON, 0xE6ECEA, 0x72C6A2, new Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + ID)).icon(() -> KEY.get().getDefaultInstance())
        .displayItems((p, output) -> {
            output.accept(KEY.get()); output.accept(EGG.get()); output.accept(CORE.get());
            output.accept(CLOUD_TALISMAN.get()); output.accept(YANG_JIAN_EGG.get()); output.accept(DIVINE_SIGIL.get());
            output.accept(EVERBLOSSOM_BRANCH.get()); output.accept(DIVINE_DRAGON_EGG.get()); output.accept(DIVINE_DRAGON_TEARS.get());
        }).build());

    public MyriadCalamity(IEventBus bus, ModContainer modContainer) {
        ENTITIES.register(bus);
        SOUND_EVENTS.register(bus);
        EFFECTS.register(bus);
        modContainer.registerConfig(ModConfig.Type.COMMON, MyriadConfig.SPEC);
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
        event.put(DIVINE_DRAGON.get(), DivineDragon.attributes().build());
    }
    private static DeferredHolder<SoundEvent, SoundEvent> music(String path) {
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id(path)));
    }
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
