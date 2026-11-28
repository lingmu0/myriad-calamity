package net.xuwu.myriadcalamity.world;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.DivineDragon;
import net.xuwu.myriadcalamity.entity.DivineDragonMath;

/**
 * Isolated Cloud-Sea Sakura Realm for the Divine Dragon ranged artillery encounter.
 * Builds a colossal real-block Cherry Wood tree at the center (no modeled tree) surrounded by
 * a wide open combat ring (r = 24..56) where the boss's wind exclusion barrier enforces ranged combat.
 */
@EventBusSubscriber(modid = MyriadCalamity.ID)
public final class DivineArena {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, MyriadCalamity.id("divine_realm"));
    public static final BlockPos CENTER = new BlockPos(0, 128, 0);
    public static final double EXCLUSION_RADIUS = 38.0;
    public static final double OUTER_RADIUS = 86.0;
    public static final double TREE_TOP_Y = 150.0;
    private static final int CURRENT_TREE_VERSION = 3;
    public static final BlockPos EXIT = CENTER.offset(0, 1, 80);
    private static final int PREPARATION = DivineDragonMath.INTRO_TOTAL_DURATION;
    private static final int TICKET_DISTANCE = 8;
    private static final long MAX_ENCOUNTER_TICKS = 20L * 60 * 30;
    private static final Set<UUID> TRANSFERRING = new HashSet<>();

    private DivineArena() {}

    public static boolean inDivineRealm(Level level) {
        return LEVEL.equals(level.dimension());
    }

    public static void applyWeather(Level level, CloudWeather weather) {
        if (!inDivineRealm(level)) return;
        level.setRainLevel(weather.rain());
        level.setThunderLevel(weather.thunder());
    }

    public static void useBranch(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(MyriadCalamity.EVERBLOSSOM_BRANCH.get())) return;
        player.getCooldowns().addCooldown(MyriadCalamity.EVERBLOSSOM_BRANCH.get(), 20);
        MinecraftServer server = player.server;
        DivineArenaData data = DivineArenaData.get(server);
        if (inDivineRealm(player.level())) {
            if (data.fighting && player.getUUID().equals(data.owner)) {
                message(player, "divine_battle_locked");
                return;
            }
            returnHome(player, data);
            return;
        }
        if (data.returns.containsKey(player.getUUID())) {
            returnHome(player, data);
            return;
        }
        if (data.owner != null) {
            message(player, "divine_occupied");
            return;
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            message(player, "divine_peaceful");
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isSleeping()) {
            message(player, "divine_stand_safely");
            return;
        }
        ServerLevel arena = server.getLevel(LEVEL);
        if (arena == null) {
            message(player, "divine_unavailable");
            return;
        }
        refreshTicket(arena);
        buildOnce(arena, data);
        data.returns.put(player.getUUID(), new DivineArenaData.ReturnPoint(
            player.level().dimension().location().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        data.owner = player.getUUID();
        data.boss = null;
        data.fighting = true;
        data.createdAt = server.overworld().getGameTime();
        data.startsAt = data.createdAt + PREPARATION;
        data.setDirty();

        DivineDragon boss = MyriadCalamity.DIVINE_DRAGON.get().create(arena);
        if (boss != null) {
            boss.moveTo(0.5, TREE_TOP_Y, 0.5, 0, 0);
            boss.setArena(CENTER, player.getUUID());
            boss.prepareForArena(PREPARATION);
            data.boss = boss.getUUID();
            if (!arena.addFreshEntity(boss)) boss = null;
        }
        if (boss == null) {
            release(arena, data);
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "divine_unavailable");
            return;
        }
        teleport(player, arena, new Vec3(0.5, 129.0, 68.5), 180.0F, -20.0F);
        if (!inDivineRealm(player.level())) {
            release(arena, data);
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "divine_unavailable");
            return;
        }
        applyWeather(arena, CloudWeather.phase(3));
        message(player, "divine_entered");
    }

    private static void buildOnce(ServerLevel level, DivineArenaData data) {
        if (!data.built) {
            buildCloudPlatform(level);
            buildGiantSakuraTree(level);
            level.setBlock(EXIT.below(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
            level.setBlock(EXIT, Blocks.LODESTONE.defaultBlockState(), 2);
            data.built = true;
            data.treeVersion = CURRENT_TREE_VERSION;
            data.setDirty();
            return;
        }
        upgradeTreeIfNeeded(level, data);
    }

    private static void upgradeTreeIfNeeded(ServerLevel level, DivineArenaData data) {
        if (!data.built || data.treeVersion >= CURRENT_TREE_VERSION) return;
        clearLegacyArenaStructures(level);
        buildCloudPlatform(level);
        buildGiantSakuraTree(level);
        level.setBlock(EXIT.below(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(EXIT, Blocks.LODESTONE.defaultBlockState(), 2);
        data.treeVersion = CURRENT_TREE_VERSION;
        data.setDirty();
    }

    /** Clears previous smaller-scale arena structures (old tree, old r=57 perimeter pillars, old Z=52 exit) on world upgrade. */
    private static void clearLegacyArenaStructures(ServerLevel level) {
        int clearR = 62;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -clearR; x <= clearR; x++) {
            for (int z = -clearR; z <= clearR; z++) {
                double r = Math.hypot(x, z);
                if (r > clearR) continue;
                int maxDy = r <= 36.0 ? 32 : 6;
                for (int dy = 1; dy <= maxDy; dy++) {
                    BlockPos pos = CENTER.offset(x, dy, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2);
                    }
                }
            }
        }
    }

    private static void buildCloudPlatform(ServerLevel level) {
        int outer = 88;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double r = Math.hypot(x, z);
                if (r > outer) continue;
                for (int dy = -3; dy <= 0; dy++) {
                    if (r > outer + dy * 1.4) continue;
                    BlockState state;
                    if (dy < 0) {
                        state = dy == -1 ? Blocks.CALCITE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                    } else if (r < 31.5) {
                        state = ((x * 7 + z * 13) & 3) == 0 ? Blocks.MOSS_BLOCK.defaultBlockState()
                            : Blocks.ROOTED_DIRT.defaultBlockState();
                    } else if (Math.abs(r - EXCLUSION_RADIUS) < 0.85) {
                        state = ((x + z) & 3) == 0 ? Blocks.SEA_LANTERN.defaultBlockState()
                            : Blocks.PRISMARINE_BRICKS.defaultBlockState();
                    } else if (r > 85.2 && r <= 86.4) {
                        state = Blocks.DARK_PRISMARINE.defaultBlockState();
                    } else if (r > 86.4) {
                        state = Blocks.POLISHED_DIORITE.defaultBlockState();
                    } else if (Math.abs(x) <= 1 || Math.abs(z) <= 1 || Math.abs(r - 62.0) < 0.75) {
                        state = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    } else {
                        state = ((x * 11 + z * 17) & 7) == 0 ? Blocks.CALCITE.defaultBlockState()
                            : Blocks.SNOW_BLOCK.defaultBlockState();
                    }
                    level.setBlock(CENTER.offset(x, dy, z), state, 2);
                }
                if (r > 86.5) {
                    level.setBlock(CENTER.offset(x, 1, z), Blocks.WHITE_STAINED_GLASS.defaultBlockState(), 2);
                    double angle = Math.atan2(z, x);
                    double deg = Math.toDegrees(angle);
                    double mod = ((deg % 20.0) + 20.0) % 20.0;
                    if (r > 86.8 && r < 87.9 && (mod < 0.9 || mod > 19.1)) {
                        level.setBlock(CENTER.offset(x, 1, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                        level.setBlock(CENTER.offset(x, 2, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                        level.setBlock(CENTER.offset(x, 3, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                        level.setBlock(CENTER.offset(x, 4, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * Programmatically builds a colossal 22-block-tall, wide-base tapering ancient weeping Cherry Wood mound
     * scaled proportionally for the 2.25x Sekiro Divine Dragon (sprawling root & weeping cherry cascade
     * across r=0..33 at Y=128..141, steeply tapering to a slender ancient wood pedestal at Y=145..150 with
     * ZERO leaves above Y=145 so the dragon and its 40-block Seven-Branched Sword remain 100% unobstructed).
     */
    private static void buildGiantSakuraTree(ServerLevel level) {
        BlockState cherryWood = Blocks.CHERRY_WOOD.defaultBlockState();
        BlockState strippedCherry = Blocks.STRIPPED_CHERRY_WOOD.defaultBlockState();
        BlockState darkWood = Blocks.DARK_OAK_WOOD.defaultBlockState();
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState moss = Blocks.MOSS_BLOCK.defaultBlockState();

        // 1. Tapering conical gnarled trunk (wide r~12.5 at Y=128 -> slender r~7.2 at Y=150 where 2.25x dragon anchors)
        for (int dy = 0; dy <= 22; dy++) {
            double t = dy / 22.0;
            double twistAngle = t * Math.PI * 0.85;
            double cx = Math.cos(twistAngle) * (1.2 * Math.sin(t * Math.PI));
            double cz = Math.sin(twistAngle) * (1.2 * Math.sin(t * Math.PI));
            double radius = 7.0 + 5.8 * Math.pow(1.0 - t, 1.45);
            int bound = (int) Math.ceil(radius + 2);
            for (int x = -bound; x <= bound; x++) {
                for (int z = -bound; z <= bound; z++) {
                    double dx = x - cx, dz = z - cz;
                    double ang = Math.atan2(dz, dx);
                    double flute = Math.sin(ang * 7.0 + dy * 0.22) * (1.1 * (1.0 - t * 0.65));
                    double dist = Math.hypot(dx, dz);
                    if (dist <= radius + flute) {
                        BlockState st = ((x * 5 + dy * 3 + z * 7) & 3) == 0 ? darkWood
                            : (((x + dy + z) & 7) == 0 ? strippedCherry : cherryWood);
                        level.setBlock(CENTER.offset(x, dy, z), st, 2);
                    }
                }
            }
        }

        // 2. Twelve colossal low-arching gnarled buttress roots spreading wide across the base (r = 6..32, dy = 0..10)
        for (int i = 0; i < 12; i++) {
            double baseAngle = i * (Math.PI * 2.0 / 12.0) + (i % 2) * 0.10;
            double rootLen = 23.0 + (i % 3) * 2.4;
            for (int s = 0; s <= 36; s++) {
                double u = s / 36.0;
                double ang = baseAngle + Math.sin(u * Math.PI * 1.5) * 0.20;
                double r = 6.2 + u * rootLen;
                double archY = 10.0 * Math.pow(1.0 - u, 1.55) + Math.sin(u * Math.PI) * 3.6 * (1.0 - 0.35 * u);
                double thick = 3.6 * (1.0 - 0.56 * u);
                double rx = Math.cos(ang) * r;
                double rz = Math.sin(ang) * r;
                placeWoodBlob(level, rx, archY, rz, thick, cherryWood, strippedCherry, darkWood);
                if (u > 0.22 && archY > 1.2 && s % 3 == 0) {
                    int gx = (int) Math.round(rx);
                    int gz = (int) Math.round(rz);
                    for (int fy = 1; fy < (int) archY; fy++) {
                        BlockState pillar = ((gx + fy + gz) & 1) == 0 ? darkWood : cherryWood;
                        level.setBlock(CENTER.offset(gx, fy, gz), pillar, 2);
                    }
                }
            }
        }

        // 3. Nine gnarled shoulder boughs hugging the lower-mid cone (r = 6..18, dy = 8..14 — strictly below Y=145!)
        for (int b = 0; b < 9; b++) {
            double angle = b * (Math.PI * 2.0 / 9.0) + 0.36;
            double boughLen = 11.0 + (b % 2) * 2.6;
            for (int s = 0; s <= 18; s++) {
                double u = s / 18.0;
                double a = angle + Math.sin(u * Math.PI) * 0.16;
                double r = 5.8 + u * boughLen;
                double y = 14.0 - u * 6.5 + Math.sin(u * Math.PI) * 1.4;
                double thick = 2.35 * (1.0 - 0.45 * u);
                placeWoodBlob(level, Math.cos(a) * r, y, Math.sin(a) * r, thick, cherryWood, strippedCherry, darkWood);
            }
        }

        // 4. Wide-base, steeply tapering weeping cherry blossom cascade (WIDE bottom r~32, NARROW top r~13, ZERO leaves > Y=145!)
        // Tier A (Outer broad base skirt, r = 22.0..28.5, dy = 4.2..6.8, lush weeping drapes down to Y=129)
        for (int i = 0; i < 20; i++) {
            double ang = i * (Math.PI * 2.0 / 20.0) + (i % 2) * 0.06;
            double r = 22.5 + (i % 3) * 2.1;
            double cy = 4.6 + (i % 2) * 1.4;
            placeWeepingBlossomCluster(level, Math.cos(ang) * r, cy, Math.sin(ang) * r,
                5.4 + (i % 2) * 0.8, 3.1, 6, 137, leaves);
        }
        // Tier B (Mid-lower slope stepping inward, r = 15.0..19.5, dy = 8.4..10.6, weeping curtains overlapping Tier A)
        for (int i = 0; i < 16; i++) {
            double ang = i * (Math.PI * 2.0 / 16.0) + 0.19;
            double r = 15.2 + (i % 3) * 1.6;
            double cy = 8.8 + (i % 2) * 1.3;
            placeWeepingBlossomCluster(level, Math.cos(ang) * r, cy, Math.sin(ang) * r,
                4.5 + (i % 2) * 0.6, 2.7, 5, 141, leaves);
        }
        // Tier C (Upper-mid tapering collar, r = 9.5..12.8, dy = 12.2..14.0, strictly capped at Y<=145)
        for (int i = 0; i < 10; i++) {
            double ang = i * (Math.PI * 2.0 / 10.0) + 0.31;
            double r = 9.8 + (i % 2) * 1.8;
            double cy = 12.4 + (i % 2) * 1.0;
            placeWeepingBlossomCluster(level, Math.cos(ang) * r, cy, Math.sin(ang) * r,
                3.4, 2.2, 4, 145, leaves);
        }

        // 5. Lush fallen sakura petals & mossy root drifts ringing the wide weeping skirt (r = 14..35)
        for (int x = -35; x <= 35; x++) {
            for (int z = -35; z <= 35; z++) {
                double r = Math.hypot(x, z);
                if (r < 14.0 || r > 35.0) continue;
                BlockPos ground = CENTER.offset(x, 1, z);
                BlockState below = level.getBlockState(ground.below());
                if (level.getBlockState(ground).isAir() && (below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.CHERRY_WOOD)
                        || below.is(Blocks.DARK_OAK_WOOD) || below.is(Blocks.ROOTED_DIRT)
                        || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.CALCITE))) {
                    int h = Math.abs(x * 31 + z * 17);
                    boolean nearEdge = r >= 20.0 && r <= 33.5;
                    if ((nearEdge && h % 3 == 0) || h % 6 == 0) {
                        if (below.is(Blocks.CHERRY_WOOD) || below.is(Blocks.DARK_OAK_WOOD)) {
                            level.setBlock(ground.below(), moss, 2);
                        }
                        if (below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.ROOTED_DIRT)
                                || below.is(Blocks.CHERRY_WOOD) || below.is(Blocks.DARK_OAK_WOOD)) {
                            int petals = 1 + (h % 4);
                            level.setBlock(ground, Blocks.PINK_PETALS.defaultBlockState()
                                .setValue(PinkPetalsBlock.AMOUNT, petals), 2);
                        }
                    }
                }
            }
        }
    }

    private static void placeWoodBlob(ServerLevel level, double cx, double cy, double cz, double radius,
                                      BlockState primary, BlockState stripped, BlockState dark) {
        int r = (int) Math.ceil(radius);
        int bx = (int) Math.round(cx), by = (int) Math.round(cy), bz = (int) Math.round(cz);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        int wy = by + dy;
                        if (wy < 0) continue;
                        BlockState st = ((bx + dx + wy + bz + dz) & 3) == 0 ? dark
                            : (((bx + dx + bz + dz) & 7) == 0 ? stripped : primary);
                        level.setBlock(CENTER.offset(bx + dx, wy, bz + dz), st, 2);
                    }
                }
            }
        }
    }

    /** Places a soft weeping sakura cluster with hanging vertical blossom tendrils, strictly capped below maxWorldY. */
    private static void placeWeepingBlossomCluster(ServerLevel level, double cx, double cy, double cz,
                                                   double rxRadius, double ryRadius, int maxHang,
                                                   int maxWorldY, BlockState leaves) {
        int rx = (int) Math.ceil(rxRadius);
        int ry = (int) Math.ceil(ryRadius);
        int bx = (int) Math.round(cx), by = (int) Math.round(cy), bz = (int) Math.round(cz);
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rx; dz <= rx; dz++) {
                double hNorm = (dx * dx + dz * dz) / (rxRadius * rxRadius);
                if (hNorm > 1.0) continue;
                int lowestDy = Integer.MAX_VALUE;
                for (int dy = -ry; dy <= ry; dy++) {
                    double vNorm = (dy * dy) / (ryRadius * ryRadius);
                    if (hNorm + vNorm <= 1.0) {
                        int relY = by + dy;
                        int worldY = CENTER.getY() + relY;
                        if (worldY >= 129 && worldY <= maxWorldY) {
                            BlockPos pos = CENTER.offset(bx + dx, relY, bz + dz);
                            if (level.getBlockState(pos).isAir()) {
                                level.setBlock(pos, leaves, 2);
                            }
                            if (relY < lowestDy) lowestDy = relY;
                        }
                    }
                }
                // Weeping cherry blossom drapes hanging organically down from the outer rim of each cluster
                if (lowestDy != Integer.MAX_VALUE && hNorm >= 0.28) {
                    int hash = Math.abs((bx + dx) * 37 + (bz + dz) * 19);
                    int hang = (hash % 3 == 0) ? (1 + (hash / 3) % maxHang) : ((hash % 5 == 0) ? 1 : 0);
                    for (int h = 1; h <= hang; h++) {
                        int relY = lowestDy - h;
                        int worldY = CENTER.getY() + relY;
                        if (worldY < 129 || worldY > maxWorldY) break;
                        BlockPos pos = CENTER.offset(bx + dx, relY, bz + dz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, leaves, 2);
                        } else {
                            break;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel arena = server.getLevel(LEVEL);
        if (arena == null) return;
        DivineArenaData data = DivineArenaData.get(server);
        if (data.built && data.treeVersion < CURRENT_TREE_VERSION && data.owner != null) {
            upgradeTreeIfNeeded(arena, data);
        }
        long now = server.overworld().getGameTime();
        CloudWeather weather = CloudWeather.phase(2);
        if (now % 20 == 0) rescueDroppedItems(arena);
        ServerPlayer owner = data.owner == null ? null : server.getPlayerList().getPlayer(data.owner);
        if (data.owner != null && (owner == null || !inDivineRealm(owner.level()) || now - data.createdAt > MAX_ENCOUNTER_TICKS)) {
            if (owner != null) message(owner, "divine_interrupted");
            release(arena, data);
        } else if (data.fighting && owner != null) {
            if (arena.getDifficulty() == Difficulty.PEACEFUL) {
                message(owner, "divine_peaceful");
                release(arena, data);
            } else if (data.boss != null) {
                Entity entity = arena.getEntity(data.boss);
                if (entity instanceof DivineDragon boss && boss.isDissolveComplete()) {
                    data.fighting = false;
                    data.pendingRewards.add(owner.getUUID());
                    data.setDirty();
                    boss.discard();
                    message(owner, "divine_complete");
                } else if (!(entity instanceof DivineDragon boss) || (!boss.isAlive() && !boss.isDissolving())) {
                    message(owner, "divine_interrupted");
                    release(arena, data);
                } else {
                    weather = boss.weather();
                }
            }
        }
        applyWeather(arena, weather);
        if (data.owner != null && now % 100 == 0) refreshTicket(arena);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (now % 20 == 0) deliverReward(player, data);
            boolean isOwner = player.getUUID().equals(data.owner);
            if (data.returns.containsKey(player.getUUID()) && !isOwner && player.isAlive()) {
                returnHome(player, data);
            } else if (inDivineRealm(player.level()) && !player.isSpectator()) {
                if (!isOwner) {
                    returnHome(player, data);
                    continue;
                }
                double dx = player.getX() - 0.5, dz = player.getZ() - 0.5;
                double distance = Math.hypot(dx, dz);
                if (player.getY() < 126 || player.getY() > 235 || distance > OUTER_RADIUS) {
                    double scale = Math.min(1.0, (OUTER_RADIUS - 1.5) / Math.max(1.0, distance));
                    Vec3 safe = new Vec3(0.5 + dx * scale, 129.0, 0.5 + dz * scale);
                    teleport(player, arena, safe, player.getYRot(), player.getXRot());
                    message(player, "divine_boundary");
                }
                if (!data.fighting && now % 60 == 0) message(player, "divine_return_ready");
            }
        }
    }

    private static void refreshTicket(ServerLevel arena) {
        arena.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(CENTER), TICKET_DISTANCE, CENTER, true);
    }

    private static void rescueDroppedItems(ServerLevel arena) {
        for (ItemEntity item : arena.getEntitiesOfClass(ItemEntity.class, new AABB(-140, 0, -140, 140, 260, 140))) {
            double dx = item.getX() - 0.5, dz = item.getZ() - 0.5;
            double d = Math.hypot(dx, dz);
            if (item.getY() < 126 || d > OUTER_RADIUS || d < EXCLUSION_RADIUS) {
                item.teleportTo(0.5, 129.25, 68.5);
                item.setDeltaMovement(Vec3.ZERO);
                item.fallDistance = 0;
            }
        }
    }

    private static void deliverReward(ServerPlayer player, DivineArenaData data) {
        UUID id = player.getUUID();
        if (!data.pendingRewards.contains(id)) return;
        ItemStack reward = MyriadCalamity.DIVINE_DRAGON_TEARS.get().getDefaultInstance();
        if (player.getInventory().add(reward)) {
            data.pendingRewards.remove(id);
            data.rewarded.add(id);
            data.setDirty();
            message(player, "divine_reward");
        } else if (player.server.overworld().getGameTime() % 100 == 0) {
            message(player, "divine_reward_pending");
        }
    }

    private static void release(ServerLevel arena, DivineArenaData data) {
        if (data.boss != null) {
            Entity boss = arena.getEntity(data.boss);
            if (boss instanceof DivineDragon) boss.discard();
        }
        if (data.owner != null) {
            ServerPlayer owner = arena.getServer().getPlayerList().getPlayer(data.owner);
            if (owner == null || !inDivineRealm(owner.level())) data.returns.remove(data.owner);
        }
        data.owner = null;
        data.boss = null;
        data.fighting = false;
        data.setDirty();
        arena.getChunkSource().removeRegionTicket(TicketType.PORTAL, new ChunkPos(CENTER), TICKET_DISTANCE, CENTER, true);
    }

    private static void returnHome(ServerPlayer player, DivineArenaData data) {
        DivineArenaData.ReturnPoint ticket = data.returns.get(player.getUUID());
        ServerLevel target = player.server.overworld();
        Vec3 destination = null;
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (ticket != null) {
            ResourceLocation id = ResourceLocation.tryParse(ticket.dimension());
            ServerLevel saved = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (saved != null && !inDivineRealm(saved)) {
                target = saved;
                destination = findSafe(target, player, new Vec3(ticket.x(), ticket.y(), ticket.z()));
                yaw = ticket.yaw();
                pitch = ticket.pitch();
            }
        }
        if (destination == null) {
            target = player.server.overworld();
            destination = Vec3.atBottomCenterOf(target.getSharedSpawnPos());
        }
        if (player.getUUID().equals(data.owner)) {
            ServerLevel arena = player.server.getLevel(LEVEL);
            if (arena != null) release(arena, data);
        }
        teleport(player, target, destination, yaw, pitch);
        if (!inDivineRealm(player.level())) {
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "divine_returned");
        }
    }

    private static Vec3 findSafe(ServerLevel level, ServerPlayer player, Vec3 origin) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int y = 0; y <= 12; y++) {
                for (int sign : new int[]{1, -1}) {
                    if (y == 0 && sign == -1) continue;
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            if (radius > 0 && Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                            Vec3 pos = origin.add(x, y * sign, z);
                            BlockPos block = BlockPos.containing(pos);
                            if (!level.isInWorldBounds(block) || !level.getWorldBorder().isWithinBounds(block)) continue;
                            level.getChunkAt(block);
                            if (!level.getFluidState(block).isEmpty() || !level.getFluidState(block.above()).isEmpty()) continue;
                            if (!level.getBlockState(block.below()).isFaceSturdy(level, block.below(), Direction.UP)) continue;
                            if (level.noCollision(player, player.getBoundingBox().move(pos.subtract(player.position())))) return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        TRANSFERRING.add(player.getUUID());
        try {
            player.stopRiding();
            player.setNoGravity(false);
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        } finally {
            TRANSFERRING.remove(player.getUUID());
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.myriad_calamity." + key), true);
    }

    @SubscribeEvent
    public static void exitInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!inDivineRealm(event.getLevel())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getPos().equals(EXIT)) {
            DivineArenaData data = DivineArenaData.get(player.server);
            if (data.fighting && player.getUUID().equals(data.owner)) message(player, "divine_battle_locked");
            else returnHome(player, data);
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getItemStack().is(MyriadCalamity.EVERBLOSSOM_BRANCH.get())) {
            useBranch(player);
        }
    }

    @SubscribeEvent
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && inDivineRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && inDivineRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof Level level && inDivineRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectExplosion(ExplosionEvent.Detonate event) {
        if (inDivineRealm(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TRANSFERRING.contains(player.getUUID())) return;
        DivineArenaData data = DivineArenaData.get(player.server);
        if (inDivineRealm(player.level()) && data.fighting && player.getUUID().equals(data.owner)) {
            event.setCanceled(true);
            message(player, "divine_battle_locked");
        }
    }

    @SubscribeEvent
    public static void defeat(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inDivineRealm(player.level())) return;
        DivineArenaData data = DivineArenaData.get(player.server);
        if (!data.returns.containsKey(player.getUUID())) return;
        event.setCanceled(true);
        player.setHealth(1);
        player.clearFire();
        player.invulnerableTime = 60;
        if (player.getUUID().equals(data.owner)) release(player.serverLevel(), data);
        message(player, "divine_defeated");
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DivineArenaData data = DivineArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DivineArenaData data = DivineArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DivineArenaData data = DivineArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
    }

    @SubscribeEvent
    public static void cleanOrphans(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inDivineRealm(level)) return;
        if (event.getEntity() instanceof LightningBolt bolt) bolt.setVisualOnly(true);
        if (event.getEntity() instanceof SkeletonHorse horse && horse.isTrap()) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof DivineDragon boss) || !boss.isArenaBoss()) return;
        DivineArenaData data = DivineArenaData.get(level.getServer());
        if (!data.fighting || !boss.getUUID().equals(data.boss)) event.setCanceled(true);
    }
}
