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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.TickEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;
import net.xuwu.myriadcalamity.entity.YangJian;

/** One reusable, isolated challenge arena. No overworld blocks or inventories are replaced. */
@Mod.EventBusSubscriber(modid = MyriadCalamity.ID)
public final class CloudArena {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, MyriadCalamity.id("cloud_realm"));
    public static final BlockPos CENTER = new BlockPos(0, 128, 0);
    public static final double RADIUS = 22.0;
    public static final BlockPos EXIT = CENTER.offset(0, 1, 20);
    private static final int PREPARATION = 80;
    // Region distance 4 => ticket level 29: entity-ticking level <= 31 reaches two chunks
    // from (0, 0), covering every platform chunk (-2..1 on both axes).
    private static final int TICKET_DISTANCE = 4;
    private static final long MAX_ENCOUNTER_TICKS = 20L * 60 * 30;
    private static final Set<UUID> TRANSFERRING = new HashSet<>();

    private CloudArena() {}

    public static boolean inCloudRealm(Level level) { return LEVEL.equals(level.dimension()); }

    public static void useTalisman(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(MyriadCalamity.CLOUD_TALISMAN.get())) return;
        player.getCooldowns().addCooldown(MyriadCalamity.CLOUD_TALISMAN.get(), 20);
        MinecraftServer server = player.server;
        CloudArenaData data = CloudArenaData.get(server);
        if (inCloudRealm(player.level())) {
            if (data.fighting && player.getUUID().equals(data.owner)) {
                message(player, "cloud_battle_locked");
                return;
            }
            returnHome(player, data);
            return;
        }
        // A previous interrupted challenge must return before a fresh ticket can be made.
        if (data.returns.containsKey(player.getUUID())) {
            returnHome(player, data);
            return;
        }
        if (data.owner != null) {
            message(player, "cloud_occupied");
            return;
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            message(player, "cloud_peaceful");
            return;
        }
        if (!player.onGround() || player.isPassenger() || player.isSleeping()) {
            message(player, "cloud_stand_safely");
            return;
        }
        ServerLevel arena = server.getLevel(LEVEL);
        if (arena == null) {
            message(player, "cloud_unavailable");
            return;
        }
        refreshTicket(arena);
        buildOnce(arena, data);
        data.returns.put(player.getUUID(), new CloudArenaData.ReturnPoint(player.level().dimension().location().toString(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        data.owner = player.getUUID();
        data.boss = null;
        data.fighting = true;
        data.createdAt = server.overworld().getGameTime();
        data.startsAt = data.createdAt + PREPARATION;
        data.setDirty();
        YangJian boss = MyriadCalamity.YANG_JIAN.get().create(arena);
        if (boss != null) {
            boss.moveTo(.5, 129, -8.5, 0, 0);
            boss.setArena(CENTER, player.getUUID());
            boss.prepareForArena(PREPARATION);
            data.boss = boss.getUUID();
            if (!arena.addFreshEntity(boss)) boss = null;
        }
        if (boss == null) {
            release(arena, data);
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "cloud_unavailable");
            return;
        }
        teleport(player, arena, new Vec3(.5, 129, 15.5), 180, 0);
        if (!inCloudRealm(player.level())) {
            release(arena, data);
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "cloud_unavailable");
            return;
        }
        message(player, "cloud_entered");
    }

    private static void buildOnce(ServerLevel level, CloudArenaData data) {
        if (data.built) return;
        // Only our void dimension is ever built, once per save. The arena is a stepped jade/quartz disc.
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            double radius = Math.sqrt(x * x + z * z);
            if (radius > 24) continue;
            for (int dy = -4; dy <= 0; dy++) {
                if (radius > 24 + dy * 1.25) continue;
                BlockState state = dy < 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
                if (dy == -1) state = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                if (dy == 0) {
                    if (radius > 21.5 && radius < 22.5 || radius < 3.2 && radius > 2.1)
                        state = Blocks.CUT_COPPER.defaultBlockState();
                    else if (Math.abs(x) <= 1 || Math.abs(z) <= 1 || (x + z) % 8 == 0)
                        state = Blocks.DARK_PRISMARINE.defaultBlockState();
                    else if (radius > 22.5) state = Blocks.POLISHED_DIORITE.defaultBlockState();
                }
                level.setBlock(CENTER.offset(x, dy, z), state, 2);
            }
            if (radius > 22.6) {
                level.setBlock(CENTER.offset(x, 1, z), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
                level.setBlock(CENTER.offset(x, 2, z), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), 2);
                if (Math.abs(x) % 8 == 0 || Math.abs(z) % 8 == 0) {
                    level.setBlock(CENTER.offset(x, 1, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                    level.setBlock(CENTER.offset(x, 2, z), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
                    level.setBlock(CENTER.offset(x, 3, z), Blocks.SEA_LANTERN.defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(EXIT.below(), Blocks.SEA_LANTERN.defaultBlockState(), 2);
        level.setBlock(EXIT, Blocks.LODESTONE.defaultBlockState(), 2);
        data.built = true;
        data.setDirty();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerLevel arena = server.getLevel(LEVEL);
        if (arena == null) return;
        CloudArenaData data = CloudArenaData.get(server);
        long now = server.overworld().getGameTime();
        if (now % 20 == 0) rescueDroppedItems(arena);
        ServerPlayer owner = data.owner == null ? null : server.getPlayerList().getPlayer(data.owner);
        if (data.owner != null && (owner == null || !inCloudRealm(owner.level()) || now - data.createdAt > MAX_ENCOUNTER_TICKS)) {
            if (owner != null) message(owner, "cloud_interrupted");
            release(arena, data);
        } else if (data.fighting && owner != null) {
            if (arena.getDifficulty() == Difficulty.PEACEFUL) {
                message(owner, "cloud_peaceful");
                release(arena, data);
            } else if (data.boss != null) {
                Entity entity = arena.getEntity(data.boss);
                if (entity instanceof YangJian boss && boss.isTrialComplete()) {
                    data.fighting = false;
                    if (!data.rewarded.contains(owner.getUUID())) data.pendingRewards.add(owner.getUUID());
                    data.setDirty();
                    // P3 is a real death: let its final animation finish while return becomes available.
                    if (boss.phase() < 3) boss.discard();
                    message(owner, boss.phase() >= 3 ? "cloud_p3_complete" : boss.phase() >= 2 ? "cloud_p2_complete" : "cloud_p1_complete");
                } else if (!(entity instanceof YangJian) || !entity.isAlive()) {
                    message(owner, "cloud_interrupted");
                    release(arena, data);
                } else if (now < data.startsAt && now % 20 == 0) {
                    owner.displayClientMessage(Component.translatable("message.myriad_calamity.cloud_countdown", (data.startsAt - now + 19) / 20), true);
                }
            }
        }
        if (data.owner != null && now % 100 == 0) refreshTicket(arena);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (now % 20 == 0) deliverReward(player, data);
            boolean isOwner = player.getUUID().equals(data.owner);
            if (data.returns.containsKey(player.getUUID()) && !isOwner && player.isAlive()) {
                returnHome(player, data);
            } else if (inCloudRealm(player.level()) && !player.isSpectator()) {
                if (!isOwner) {
                    returnHome(player, data);
                    continue;
                }
                double dx = player.getX() - .5, dz = player.getZ() - .5;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (player.getY() < 126 || player.getY() > 148 || distance > RADIUS) {
                    double scale = Math.min(1, (RADIUS - 1.25) / Math.max(1, distance));
                    Vec3 safe = new Vec3(.5 + dx * scale, 129, .5 + dz * scale);
                    teleport(player, arena, safe, player.getYRot(), player.getXRot());
                    message(player, "cloud_boundary");
                }
                if (!data.fighting && now % 60 == 0) message(player, "cloud_return_ready");
            }
        }
    }

    private static void refreshTicket(ServerLevel arena) {
        // PORTAL expires after 300 ticks even on an interrupted shutdown; no persistent forceload.
        arena.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(CENTER), TICKET_DISTANCE, CENTER, true);
    }

    private static void rescueDroppedItems(ServerLevel arena) {
        for (ItemEntity item : arena.getEntitiesOfClass(ItemEntity.class, new AABB(-96, 0, -96, 96, 160, 96))) {
            double dx = item.getX() - .5, dz = item.getZ() - .5;
            if (item.getY() < 126 || dx * dx + dz * dz > RADIUS * RADIUS) {
                item.teleportTo(.5, 129.25, 17.5);
                item.setDeltaMovement(Vec3.ZERO);
                item.fallDistance = 0;
            }
        }
    }

    private static void deliverReward(ServerPlayer player, CloudArenaData data) {
        UUID id = player.getUUID();
        if (!data.pendingRewards.contains(id)) return;
        ItemStack reward = MyriadCalamity.DIVINE_SIGIL.get().getDefaultInstance();
        // Retain the pending award if the inventory is full; never drop it into the void.
        if (player.getInventory().add(reward)) {
            data.pendingRewards.remove(id);
            data.rewarded.add(id);
            data.setDirty();
            message(player, "cloud_reward");
        } else if (player.server.overworld().getGameTime() % 100 == 0) message(player, "cloud_reward_pending");
    }

    private static void release(ServerLevel arena, CloudArenaData data) {
        if (data.boss != null) {
            Entity boss = arena.getEntity(data.boss);
            if (boss instanceof YangJian) boss.discard();
        }
        // An encounter that ends while its owner is already elsewhere (they died and respawned, or
        // logged out away from the realm) leaves nothing to travel back to. Keeping that stale
        // ticket would swallow their next talisman use as a no-op return, so the fight could not be
        // started again. The ticket lives on only while the owner still stands in the realm.
        if (data.owner != null) {
            ServerPlayer owner = arena.getServer().getPlayerList().getPlayer(data.owner);
            if (owner == null || !inCloudRealm(owner.level())) data.returns.remove(data.owner);
        }
        data.owner = null;
        data.boss = null;
        data.fighting = false;
        data.setDirty();
        arena.getChunkSource().removeRegionTicket(TicketType.PORTAL, new ChunkPos(CENTER), TICKET_DISTANCE, CENTER, true);
    }

    private static void returnHome(ServerPlayer player, CloudArenaData data) {
        CloudArenaData.ReturnPoint ticket = data.returns.get(player.getUUID());
        ServerLevel target = player.server.overworld();
        Vec3 destination = null;
        float yaw = player.getYRot(), pitch = player.getXRot();
        if (ticket != null) {
            ResourceLocation id = ResourceLocation.tryParse(ticket.dimension());
            ServerLevel saved = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (saved != null && !inCloudRealm(saved)) {
                target = saved;
                destination = findSafe(target, player, new Vec3(ticket.x(), ticket.y(), ticket.z()));
                yaw = ticket.yaw();
                pitch = ticket.pitch();
            }
        }
        if (destination == null) {
            target = player.server.overworld();
            // Vanilla's adjusted spawn search is the fallback if the original dimension was removed
            // or another player built over the ticket. This neither builds blocks nor consumes anchors.
            destination = Vec3.atBottomCenterOf(target.getSharedSpawnPos());
        }
        if (player.getUUID().equals(data.owner)) {
            ServerLevel arena = player.server.getLevel(LEVEL);
            if (arena != null) release(arena, data);
        }
        teleport(player, target, destination, yaw, pitch);
        if (!inCloudRealm(player.level())) {
            data.returns.remove(player.getUUID());
            data.setDirty();
            message(player, "cloud_returned");
        }
    }

    private static Vec3 findSafe(ServerLevel level, ServerPlayer player, Vec3 origin) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int y = 0; y <= 12; y++) for (int sign : new int[] {1, -1}) {
                if (y == 0 && sign == -1) continue;
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
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
        return null;
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        TRANSFERRING.add(player.getUUID());
        try {
            player.stopRiding();
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
        } finally { TRANSFERRING.remove(player.getUUID()); }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.myriad_calamity." + key), true);
    }

    @SubscribeEvent
    public static void exitInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!inCloudRealm(event.getLevel())) return;
        // Block interactions (beds, fluids, containers) cannot mutate the dedicated arena.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getPos().equals(EXIT)) {
            CloudArenaData data = CloudArenaData.get(player.server);
            if (data.fighting && player.getUUID().equals(data.owner)) message(player, "cloud_battle_locked");
            else returnHome(player, data);
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getItemStack().is(MyriadCalamity.CLOUD_TALISMAN.get())) useTalisman(player);
    }

    @SubscribeEvent
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && inCloudRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && inCloudRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof Level level && inCloudRealm(level)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void protectExplosion(ExplosionEvent.Detonate event) {
        if (inCloudRealm(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TRANSFERRING.contains(player.getUUID())) return;
        CloudArenaData data = CloudArenaData.get(player.server);
        if (inCloudRealm(player.level()) && data.fighting && player.getUUID().equals(data.owner)) {
            event.setCanceled(true);
            message(player, "cloud_battle_locked");
        }
    }

    @SubscribeEvent
    public static void defeat(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inCloudRealm(player.level())) return;
        CloudArenaData data = CloudArenaData.get(player.server);
        if (!data.returns.containsKey(player.getUUID())) return;
        event.setCanceled(true);
        player.setHealth(1);
        player.clearFire();
        player.invulnerableTime = 60;
        if (player.getUUID().equals(data.owner)) release(player.serverLevel(), data);
        message(player, "cloud_defeated");
        // The post-tick return avoids moving dimensions inside LivingEntity.die(). Inventory stays intact.
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CloudArenaData data = CloudArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CloudArenaData data = CloudArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
        // A persistent return ticket is consumed by tick after login/respawn is fully initialized.
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CloudArenaData data = CloudArenaData.get(player.server);
        ServerLevel arena = player.server.getLevel(LEVEL);
        if (arena != null && player.getUUID().equals(data.owner)) release(arena, data);
    }

    @SubscribeEvent
    public static void cleanOrphans(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inCloudRealm(level)
                || !(event.getEntity() instanceof YangJian boss) || !boss.isArenaBoss()) return;
        CloudArenaData data = CloudArenaData.get(level.getServer());
        if (!data.fighting || !boss.getUUID().equals(data.boss)) event.setCanceled(true);
    }
}
