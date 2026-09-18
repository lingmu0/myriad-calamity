package net.xuwu.myriadcalamity.entity;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.xuwu.myriadcalamity.MyriadCalamity;

/** Handles projectile impacts before vanilla arrow/trident code can pin or discard them. */
@Mod.EventBusSubscriber(modid=MyriadCalamity.ID)
public final class YangJianDefenseEvents {
    private YangJianDefenseEvents() { }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile=event.getProjectile();
        if(projectile.level().isClientSide || !(event.getRayTraceResult() instanceof EntityHitResult hit))return;
        if(hit.getEntity() instanceof YangJian boss && boss.guardProjectile(projectile))event.setCanceled(true);
    }
}
