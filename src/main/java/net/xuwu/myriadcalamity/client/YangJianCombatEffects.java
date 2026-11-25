package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.xuwu.myriadcalamity.config.MyriadConfig;
import net.xuwu.myriadcalamity.entity.YangJian;
import net.xuwu.myriadcalamity.entity.YangJianHazard;
import net.xuwu.myriadcalamity.entity.YangJianSkill;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Immediate boss-anchored VFX for all three Yang Jian phases: wards, sonic thrusts, weapon arcs, lightning whips, and third-eye divinity. */
final class YangJianCombatEffects {
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 Z_AXIS = new Vec3(0, 0, 1);

    static void render(YangJian boss,YangJianModel model,float partial,PoseStack pose,MultiBufferSource buffers) {
        if (ShaderPackSupport.pass() == ShaderPackSupport.Pass.SHADOW) return;
        int action = boss.action();
        int strokeDuration = strokeDuration(boss);
        boolean hasP1Special = action == YangJian.GUARD || action == YangJian.SUMMON_HOUND || action == YangJian.THROW
            || action == YangJian.VAULT_SLAM || action == YangJian.CLOUD_FEINT_THRUST;
        if(strokeDuration==0 && !hasP1Special && boss.phase()<2 && action!=YangJian.TRANSITION)return;
        VertexConsumer v=CombatEffectRenderLayer.buffer(buffers);
        Matrix4f m=pose.last().pose();
        float age=boss.actionAge(partial);
        float active=boss.stepAge(partial)-boss.stepWindup();

        // Phase 1 & shared defensive/summon/recall VFX
        if(action==YangJian.GUARD)guardWard(boss,partial,age,v,m);
        if(action==YangJian.SUMMON_HOUND)summonHoundAura(boss,partial,age,v,m);
        if(action==YangJian.THROW && age>=43 && age<54)throwRecallFlash(boss,model,partial,age,v,m);

        // Third-eye charge & P3 divine effects
        eyeCharge(boss,model,partial,v,m);
        if(boss.phase()==3 || action==YangJian.THIRD_EYE_OPEN)divine(boss,model,partial,age,v,m);
        if(action==YangJian.SWEEP_BEAM && boss.hasSweepClone()) {
            sweepCloneVeil(boss,partial,pose,v);
            sweepCloneModel(boss,model,partial,pose,buffers);
            v=CombatEffectRenderLayer.buffer(buffers);
        }
        if(action==YangJian.MYRIAD_SWORDS && age>=14 && age<118)myriadSwordHalo(boss,partial,age,v,m);

        // Phase 2 axe summon, orbiting aftershock charges, leaping axe aura, draw-slash glint, whip
        if(action==YangJian.AXE_SUMMON && age<32)axeSummonAura(boss,model,partial,age,v,m);
        if(boss.phase()==2 && boss.axeTrailCharges()>0)axeAftershock(boss,partial,v,m);
        if(action==YangJian.AXE_SLAM && age>=8 && age<28)axeLeapAura(boss,partial,age,v,m);
        if(action==YangJian.VAULT_SLAM && age>=4 && age<24)vaultLeapAura(boss,partial,age,v,m);
        if((action==YangJian.DRAW_SLASH || action==YangJian.AXE_DRAW_MIXUP && boss.comboStep()==1) && active<0 && active>=-8)
            drawSlashGlint(boss,model,partial,active,v,m);
        if(boss.weapon()==3 && !boss.weaponThrown())whip(boss,model,partial,v,m);
        if(action==YangJian.EYE_BLINK_CLEAVE && age>=20 && age<27) {
            float t=(age-20)/7F;double fade=1-t;
            YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,.5+t*1.6,.44*fade,.26,YangJianVisuals.RED,fade*.9);
            YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,.35+t*1.1,.26*fade,.18,YangJianVisuals.GOLD,fade*.85);
        }

        // Vanish & reappearance mist-lightning vortex for Invisible Dash
        if(action==YangJian.INVISIBLE_DASH && (age>=5 && age<12 || age>=17 && age<25)) {
            float t=age<12?(age-5)/7F:(age-17)/8F;
            double fade=1-t;
            YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,.55+t*1.35,.42*fade,.22,YangJianVisuals.SILVER,fade*.85);
            ring(v,m,Vec3.ZERO,.45+t*1.15,.045,((int)(fade*210)<<24)|0xC4F4FF);
            for(int i=0;i<8;i++) {
                double a=i*Math.PI/4+t*.65;
                Vec3 start=new Vec3(Math.cos(a)*.74,.08,Math.sin(a)*.74);
                Vec3 top=start.add(Math.cos(a+.3)*.18,3.4*fade,Math.sin(a+.3)*.18);
                beam(v,m,start,top,.026,((int)(fade*165)<<24)|0xE2FBFF);
                if(i%2==0) {
                    YangJianVisuals.bolt(v,m,start,top,.022,.16,age+i*3,boss.getId()+i*29,
                        YangJianVisuals.SILVER,fade*.72,false);
                }
            }
        }

        // Giant Thunder Axe P1 -> P2 transition weapon effects
        if(action==YangJian.TRANSITION)YangJianTransitionEffects.weapon(boss,model,partial,v,m);

        // Weapon slash ribbons (all melee swings, divine sweep, axe swings, sword cuts)
        if(strokeDuration>0 && boss.weapon()!=3 && !boss.weaponThrown() && !boss.isInvisible()
                && active>=0 && active<strokeDuration+4) {
            weaponStroke(boss,model,partial,age,active,strokeDuration,v,m);
        }

        // High-speed sonic thrust/dash lance cones
        if((action==YangJian.THRUST || action==YangJian.COUNTER || action==YangJian.LIGHTNING_THRUST
                || (action==YangJian.INVISIBLE_DASH && boss.comboStep()==1)
                || (action==YangJian.COORDINATED && boss.comboStep()==1)
                || (action==YangJian.CLOUD_FEINT_THRUST && boss.comboStep()==1)
                || (action==YangJian.WHIP_LANCE_REEL && boss.comboStep()==1))
                && active>=0 && active<7 && !boss.isInvisible()) {
            thrustSonicCone(boss,partial,active,v,m);
        }

        // P1 Melee Combo Finisher & Vault Slam volumetric golden shockwave ring
        YangJianSkill skill=YangJianSkill.forAction(action);
        if((skill!=null && skill.meleeCombo() && boss.comboStep()==skill.steps()-1 || action==YangJian.VAULT_SLAM) && active>=0 && active<9) {
            comboFinisherShock(boss,partial,active,v,m);
        }

        // P2 Axe Slam / Axe Combo Finisher thunder crater & shockwaves
        if((action==YangJian.AXE_SLAM || action==YangJian.AXE_COMBO && boss.comboStep()==3)
                && active>=0 && active<9) {
            Vec3 position=lerpPos(boss,partial);
            Vec3 center=boss.attackPoint().subtract(position);
            double radius=boss.telegraphRadius();
            double impFade=Math.max(0,1-active/9.0);
            YangJianLightningEffects.strike(v,m,center,8.5,Math.min(2.8,radius*.45),.25,active,0,
                boss.getId(),YangJianVisuals.BLUE,1);
            YangJianVisuals.impactShockRing(v,m,center,.55+active*(radius*.13),.48*impFade,.45,
                YangJianVisuals.BLUE,impFade*.95);
            if(active<5) {
                YangJianVisuals.impactShockRing(v,m,center,.35+active*(radius*.18),.28*(1-active/5.0),.24,
                    YangJianVisuals.SILVER,(1-active/5.0)*.85);
            }
            if(MyriadConfig.showYangJianAttackIndicators() && active<7) {
                ring(v,m,center,radius,.068,((int)((1-active/7)*146)<<24)|0xA8E8FF);
            }
        }

        // P3 Aerial Combo / Divine Judgement / Crimson Sky Plunge aerial dive meteor & touchdown detonation
        if((action==YangJian.AERIAL_COMBO || action==YangJian.DIVINE_JUDGEMENT || action==YangJian.CRIMSON_SKY_PLUNGE)
                && boss.comboStep()==2 && boss.telegraphShape()==2) {
            aerialDiveEffects(boss,partial,active,v,m);
        }
    }

    private static int strokeDuration(YangJian boss) {
        YangJianSkill skill = YangJianSkill.forAction(boss.action());
        if (skill == null) return 0;
        return switch (skill) {
            case COMBO, FOUR_COMBO, SIX_COMBO, COORDINATED, COUNTER, THRUST, LIGHTNING_THRUST,
                AXE_SLAM, AXE_COMBO, DRAW_SLASH, DELAYED_COMBO, DIVINE_SWEEP,
                VAULT_SLAM, CLOUD_FEINT_THRUST, AXE_DRAW_MIXUP -> skill.stepActive(boss.comboStep());
            case WHIP_LANCE_REEL -> boss.comboStep()==1 ? skill.stepActive(1) : 0;
            case EYE_BLINK_CLEAVE -> boss.comboStep()==1 ? skill.stepActive(1) : 0;
            case AERIAL_COMBO, DIVINE_JUDGEMENT, CRIMSON_SKY_PLUNGE -> boss.comboStep()==2 && boss.telegraphShape()==2 ? 6 : 0;
            case INVISIBLE_DASH -> boss.comboStep()==1 ? skill.stepActive(1) : 0;
            default -> 0;
        };
    }

    private static void guardWard(YangJian boss,float partial,float age,VertexConsumer v,Matrix4f m) {
        if(age<0 || age>18)return;
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        double rad=Math.toRadians(yaw);
        Vec3 forward=new Vec3(-Math.sin(rad),0,Math.cos(rad));
        double env=age<2?age/2.0:Math.max(0,1-(age-11)/7.0);
        double pulse=.9+.1*Math.sin(age*.9);
        Vec3 center=forward.scale(1.18).add(0,1.65,0);
        YangJianVisuals.divineWard(v,m,center,forward,1.18*pulse,age,
            boss.phase()==3?YangJianVisuals.RED:boss.phase()==2?YangJianVisuals.BLUE:YangJianVisuals.GOLD,env);
        if(age<4) {
            double flash=1-age/4.0;
            YangJianVisuals.flare(v,m,center,YangJianVisuals.side(forward),new Vec3(0,1,0),.65+age*.22,flash,YangJianVisuals.GOLD);
        }
    }

    private static void summonHoundAura(YangJian boss,float partial,float age,VertexConsumer v,Matrix4f m) {
        if(age<0 || age>32)return;
        double env=Math.sin(Mth.clamp(age/32.0,0,1)*Math.PI);
        double r=.95+age*.055;
        ring(v,m,new Vec3(0,.08,0),r,.055,YangJianVisuals.tint(YangJianVisuals.SILVER.body(),env*.72));
        ring(v,m,new Vec3(0,.10,0),r*.62,.028,YangJianVisuals.tint(YangJianVisuals.GOLD.core(),env*.85));
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2+age*.14;
            Vec3 p0=new Vec3(Math.cos(a)*r,.08,Math.sin(a)*r);
            Vec3 p1=new Vec3(Math.cos(a+.35)*r*.7,1.8*env,Math.sin(a+.35)*r*.7);
            YangJianVisuals.wake(v,m,p0,p1,.02,.07,YangJianVisuals.SILVER,0,env*.78);
        }
    }

    private static void throwRecallFlash(YangJian boss,YangJianModel model,float partial,float age,VertexConsumer v,Matrix4f m) {
        float t=Mth.clamp((age-43)/11F,0,1);
        double fade=(1-t)*(1-t);
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 grip=model.heldWeaponPoint(yaw,0,0,0);
        YangJianVisuals.flare(v,m,grip,X_AXIS,new Vec3(0,1,0),.45+t*.95,fade,YangJianVisuals.GOLD);
        YangJianVisuals.ring(v,m,grip,.3+t*1.25,.055,YangJianVisuals.tint(YangJianVisuals.GOLD.core(),fade*.9));
    }

    private static void thrustSonicCone(YangJian boss,float partial,float active,VertexConsumer v,Matrix4f m) {
        Vec3 route=boss.dashEnd().subtract(boss.dashStart());
        double len=route.horizontalDistance();
        if(len<.05)return;
        Vec3 dir=new Vec3(route.x/len,0,route.z/len);
        boolean lightning=boss.action()==YangJian.LIGHTNING_THRUST || boss.action()==YangJian.WHIP_LANCE_REEL;
        boolean silver=boss.action()==YangJian.INVISIBLE_DASH;
        YangJianVisuals.Palette pal=lightning?YangJianVisuals.BLUE:silver?YangJianVisuals.SILVER:YangJianVisuals.GOLD;
        double fade=active<5?1.0:Math.max(0,1-(active-5)/2.0);
        Vec3 tip=dir.scale(1.95).add(0,1.55,0);
        YangJianVisuals.sonicCone(v,m,tip,dir,3.6,1.18,pal,fade*.92);
        if(lightning) {
            // Twin crackling blue lightning spirals wrapping the lunge
            Vec3 side=YangJianVisuals.side(dir);
            for(int s=-1;s<=1;s+=2) {
                Vec3 back=dir.scale(-2.1).add(side.scale(s*.75)).add(0,1.45,0);
                YangJianVisuals.bolt(v,m,back,tip,.048,.26,active+s*4,boss.getId()+s*37,
                    YangJianVisuals.BLUE,fade*.9,false);
            }
        }
    }

    private static void comboFinisherShock(YangJian boss,float partial,float active,VertexConsumer v,Matrix4f m) {
        Vec3 center=boss.attackPoint().subtract(lerpPos(boss,partial));
        double radius=Math.max(2.2,boss.telegraphRadius());
        float t=active/9F;
        double fade=(1-t)*(1-t);
        YangJianVisuals.impactShockRing(v,m,center,.35+t*radius,.38*fade,.36,YangJianVisuals.GOLD,fade*.9);
        if(active<5) {
            YangJianVisuals.flare(v,m,center.add(0,.15,0),X_AXIS,Z_AXIS,.4+active*.35,(1-active/5.0)*.9,YangJianVisuals.GOLD);
        }
    }

    private static void axeSummonAura(YangJian boss,YangJianModel model,float partial,float age,VertexConsumer v,Matrix4f m) {
        float t=Mth.clamp(age/24F,0,1);
        double fade=age<=24?1.0:Math.max(0,1-(age-24)/8.0);
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 hand=model.heldWeaponPoint(yaw,0,-14F/16F,0);
        // Converging azure electric vortex rings
        double r=2.6*(1-t*.72);
        ring(v,m,new Vec3(0,.10,0),r,.065,YangJianVisuals.tint(YangJianVisuals.BLUE.body(),fade*(.35+t*.55)));
        ring(v,m,new Vec3(0,1.55,0),r*.68,.038,YangJianVisuals.tint(YangJianVisuals.BLUE.core(),fade*(.4+t*.6)));
        for(int i=0;i<3;i++) {
            double a=i*Math.PI*2/3+age*.22;
            Vec3 outer=new Vec3(Math.cos(a)*r,2.2+(i%2)*.5,Math.sin(a)*r);
            YangJianVisuals.bolt(v,m,outer,hand,.036,.24,age+i*5,boss.getId()+i*19,YangJianVisuals.BLUE,fade*.8,false);
        }
        // Thunder strike locking the 3 axe charges at tick 24
        if(age>=22) {
            YangJianLightningEffects.strike(v,m,Vec3.ZERO,10,2.2,.24,age-22,0,boss.getId()+77,
                YangJianVisuals.BLUE,fade);
        }
    }

    private static void axeLeapAura(YangJian boss,float partial,float age,VertexConsumer v,Matrix4f m) {
        double t=Mth.clamp((age-8)/20.0,0,1);
        double env=Math.sin(t*Math.PI);
        ring(v,m,new Vec3(0,1.15,0),.85+.25*env,.05,YangJianVisuals.tint(YangJianVisuals.BLUE.body(),.65));
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2+age*.26;
            Vec3 low=new Vec3(Math.cos(a)*.85,.25,Math.sin(a)*.85);
            Vec3 high=new Vec3(Math.cos(a+.7)*.55,2.95,Math.sin(a+.7)*.55);
            YangJianVisuals.bolt(v,m,low,high,.032,.22,age+i*4,boss.getId()+i*41,YangJianVisuals.BLUE,.82,false);
        }
        if(t>.55) {
            // Downward thunder plunge cone
            YangJianVisuals.sonicCone(v,m,new Vec3(0,.15,0),new Vec3(0,-1,0),3.2,1.15,YangJianVisuals.BLUE,(t-.55)/.45*.85);
        }
    }

    private static void vaultLeapAura(YangJian boss,float partial,float age,VertexConsumer v,Matrix4f m) {
        double t=Mth.clamp((age-4)/20.0,0,1);
        double env=Math.sin(t*Math.PI);
        ring(v,m,new Vec3(0,1.0,0),.72+.22*env,.045,YangJianVisuals.tint(YangJianVisuals.GOLD.body(),.62*env));
        if(t>.52) {
            YangJianVisuals.sonicCone(v,m,new Vec3(0,.15,0),new Vec3(0,-1,0),2.9,1.05,YangJianVisuals.GOLD,(t-.52)/.48*.82);
        }
    }

    private static void drawSlashGlint(YangJian boss,YangJianModel model,float partial,float active,VertexConsumer v,Matrix4f m) {
        // Iaijutsu moonlit diamond hilt-glint right before the instant draw-slash unleashes!
        double progress=Mth.clamp((active+8)/8.0,0,1);
        double env=Math.sin(progress*Math.PI);
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 hilt=model.heldWeaponPoint(yaw,0,0,0);
        YangJianVisuals.flare(v,m,hilt,X_AXIS,new Vec3(0,1,0),.35+.85*env,env,YangJianVisuals.SILVER);
        YangJianVisuals.flare(v,m,hilt,Z_AXIS,new Vec3(0,1,0),.35+.85*env,env,YangJianVisuals.SILVER);
    }

    private static void myriadSwordHalo(YangJian boss,float partial,float age,VertexConsumer v,Matrix4f m) {
        double env=age<24?(age-14)/10.0:age>104?Math.max(0,(118-age)/14.0):1.0;
        double spin=age*.075;
        Vec3 center=new Vec3(0,2.25,0);
        ring(v,m,center,2.45,.06,YangJianVisuals.tint(YangJianVisuals.RED.body(),env*.68));
        ring(v,m,center.add(0,.04,0),2.88,.032,YangJianVisuals.tint(YangJianVisuals.GOLD.core(),env*.82));
        for(int i=0;i<8;i++) {
            double a=spin+i*Math.PI/4;
            Vec3 p0=center.add(Math.cos(a)*2.1,0,Math.sin(a)*2.1);
            Vec3 p1=center.add(Math.cos(a)*3.15,0,Math.sin(a)*3.15);
            Vec3 side=new Vec3(-Math.sin(a),0,Math.cos(a));
            YangJianVisuals.strip(v,m,p0,p1,side,.055,.015,
                YangJianVisuals.tint(YangJianVisuals.RED.core(),env*.9),
                YangJianVisuals.tint(YangJianVisuals.RED.glow(),0));
        }
    }

    private static void aerialDiveEffects(YangJian boss,float partial,float active,VertexConsumer v,Matrix4f m) {
        if(active>=0 && active<6) {
            Vec3 dir=boss.attackPoint().subtract(boss.dashStart());
            if(dir.lengthSqr()>.01) {
                YangJianVisuals.sonicCone(v,m,new Vec3(0,.2,0),dir.normalize(),4.2,1.35,YangJianVisuals.RED,.92);
                YangJianVisuals.sonicCone(v,m,new Vec3(0,.4,0),dir.normalize(),3.1,.85,YangJianVisuals.GOLD,.95);
            }
        } else if(active>=6 && active<16) {
            float imp=active-6;
            double fade=Math.pow(1-imp/10.0,1.3);
            Vec3 center=boss.attackPoint().subtract(lerpPos(boss,partial));
            double rad=Math.max(3.5,boss.telegraphRadius());
            YangJianVisuals.impactShockRing(v,m,center,.6+imp*(rad*.13),.52*fade,.5,YangJianVisuals.RED,fade);
            YangJianVisuals.impactShockRing(v,m,center,.4+imp*(rad*.10),.34*fade,.3,YangJianVisuals.GOLD,fade*.9);
            YangJianLightningEffects.strike(v,m,center,11,Math.min(3.0,rad*.52),.26,imp,0,boss.getId()+131,
                YangJianVisuals.RED,fade);
        }
    }

    private static void weaponStroke(YangJian boss,YangJianModel model,float partial,float age,float active,
                                     int duration,VertexConsumer v,Matrix4f m) {
        boolean divine=boss.action()==YangJian.DIVINE_SWEEP || (boss.action()==YangJian.EYE_BLINK_CLEAVE && boss.comboStep()==1);
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        YangJianVisuals.Palette palette = divine ? YangJianVisuals.RED
            : boss.weapon()==1 || boss.action()==YangJian.LIGHTNING_THRUST || boss.action()==YangJian.WHIP_LANCE_REEL ? YangJianVisuals.BLUE
            : boss.weapon()==2 ? YangJianVisuals.SILVER : YangJianVisuals.GOLD;
        if (divine && active<duration) {
            float ext=boss.action()==YangJian.EYE_BLINK_CLEAVE
                ?YangJianSkill.eyeBlinkWeaponLength(age):YangJianSkill.divineSweepWeaponLength(age);
            Vec3 tip = model.bladeEdge(yaw,0,ext).tip();
            double tipRadius = tip.horizontalDistance();
            if (tipRadius>.1 && tipRadius<boss.telegraphRadius()) {
                double reach = boss.telegraphRadius()/tipRadius;
                Vec3 end = new Vec3(tip.x*reach,YangJianSkill.DIVINE_SWEEP_HEIGHT,tip.z*reach);
                YangJianVisuals.bolt(v,m,tip,end,.125,.38,age,boss.getId(),palette,.98,true);
                YangJianVisuals.bolt(v,m,tip,end,.058,.62,age+4,boss.getId()+51,YangJianVisuals.GOLD,.76,false);
                YangJianVisuals.flare(v,m,end,X_AXIS,new Vec3(0,1,0),.62,.92,YangJianVisuals.RED);
            }
        }

        // Sample only this stroke, including its last frames while the glow fades in recovery.
        float newest = Math.max(0,active-duration+.01F), oldest = Math.min(active+.75F,5.2F);
        if (newest>=oldest) return;
        int samples = 16;
        YangJianModel.BladeEdge previous = null;
        double previousOpacity = 0;
        for (int i=0;i<=samples;i++) {
            float back = Mth.lerp(i/(float)samples,oldest,newest);
            float sampleYaw = Mth.rotLerp(Math.max(0,partial-back),boss.yBodyRotO,boss.yBodyRot);
            float stretch = !divine ? 1F : boss.action()==YangJian.EYE_BLINK_CLEAVE
                ? YangJianSkill.eyeBlinkWeaponLength(age-back) : YangJianSkill.divineSweepWeaponLength(age-back);
            YangJianModel.BladeEdge edge = model.bladeEdge(sampleYaw,back,stretch);
            double opacity = (1-back/5.2) * (i/(double)samples);
            if (previous!=null) {
                YangJianVisuals.bladeArc(v,m,previous.heel(),previous.tip(),edge.heel(),edge.tip(),
                    palette,previousOpacity,opacity);
                // Charged lightning/divine strikes emit crisp spark filaments along the trailing arc edge
                if((divine || boss.weapon()==1) && i%5==3 && opacity>.35) {
                    YangJianVisuals.bolt(v,m,previous.tip(),edge.tip(),.026,.16,age+i,boss.getId()+i*23,
                        palette,opacity*.82,false);
                }
            }
            previous = edge;
            previousOpacity = opacity;
        }
    }

    private static void divine(YangJian boss,YangJianModel model,float partial,float age,VertexConsumer v,Matrix4f m) {
        boolean opening=boss.action()==YangJian.THIRD_EYE_OPEN;
        float glow=opening?Mth.clamp((age-10)/35,0,1):1;
        glow*=1-Mth.clamp((boss.deathTime+partial)/20,0,1);
        double time=boss.tickCount+partial;

        // Subtle pulsing passive third-eye forehead radiance + delicate ground divine ring
        float yaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 eye=model.eyePoint(yaw);
        if(!opening && glow>.05) {
            double p=.75+.25*Math.sin(time*.22);
            ring(v,m,new Vec3(0,.055,0),.95+.08*p,.028,YangJianVisuals.tint(YangJianVisuals.RED.body(),glow*.28*p));
            YangJianVisuals.flare(v,m,eye,X_AXIS,new Vec3(0,1,0),.12+.04*p,glow*.55*p,YangJianVisuals.GOLD);
        }
        // Flowing ascending crimson-gold spiritual embers
        for(int i=0;i<8;i++) {
            double angle=i*Math.PI/4+time*.018,height=.25+(time*.032+i*.39)%3.2;
            double r=.86+.12*Math.sin(time*.08+i);
            Vec3 spark=new Vec3(Math.cos(angle)*r,height,Math.sin(angle)*r);
            int rgb=(i%3==0)?0xFFE58F:0xFF7FA4;
            beam(v,m,spark,spark.add(0,.22,0),.022,((int)(glow*155)<<24)|rgb);
        }

        // Dramatic Third-Eye Opening celestial ascension spectacle!
        if(opening) {
            double openT=Mth.clamp(age/70.0,0,1);
            double openEnv=Math.sin(openT*Math.PI);
            // Forehead third-eye blossom starburst & sacred rings
            if(age>=14) {
                float eyeT=Mth.clamp((age-14)/28F,0,1);
                double eyePulse=.85+.15*Math.sin(age*.9);
                YangJianVisuals.flare(v,m,eye,X_AXIS,new Vec3(0,1,0),(.25+eyeT*.85)*eyePulse,openEnv,YangJianVisuals.GOLD);
                YangJianVisuals.flare(v,m,eye,Z_AXIS,new Vec3(0,1,0),(.25+eyeT*.85)*eyePulse,openEnv,YangJianVisuals.RED);
                double rad=Math.toRadians(yaw);
                Vec3 fwd=new Vec3(-Math.sin(rad),0,Math.cos(rad));
                YangJianVisuals.divineWard(v,m,eye.add(fwd.scale(.08)),fwd,.35+eyeT*.58,age*1.4,
                    YangJianVisuals.GOLD,openEnv*.85);
            }
            // Counter-rotating sacred ground mandala rings + orbiting crimson thunder arcs
            ring(v,m,new Vec3(0,.07,0),1.45+openT*1.6,.065,YangJianVisuals.tint(YangJianVisuals.RED.body(),openEnv*.78));
            ring(v,m,new Vec3(0,.09,0),1.05+openT*1.1,.035,YangJianVisuals.tint(YangJianVisuals.GOLD.core(),openEnv*.9));
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2+age*.16;
                Vec3 low=new Vec3(Math.cos(a)*1.35,.15,Math.sin(a)*1.35);
                Vec3 high=new Vec3(Math.cos(a+1.1)*.95,3.15,Math.sin(a+1.1)*.95);
                YangJianVisuals.bolt(v,m,low,high,.04,.26,age+i*5,boss.getId()+i*31,
                    i%2==0?YangJianVisuals.RED:YangJianVisuals.GOLD,openEnv*.88,false);
            }
            // Expanding divine release shockwaves (age 22..68)
            if(age>=22 && age<68) {
                float wt=(age-22)/46F;
                double envelope=Mth.sin(wt*Mth.PI);
                YangJianVisuals.impactShockRing(v,m,Vec3.ZERO,1.0+(age-22)*.14,.55*envelope,.48,
                    YangJianVisuals.RED,envelope*.92);
                ring(v,m,Vec3.ZERO,1.0+(age-22)*.09,.065,((int)(envelope*205)<<24)|0xFFE09A);
            }
        }
    }

    private static void eyeCharge(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        YangJianHazard hazard=boss.currentBeam();
        if(hazard==null || !hazard.warning(partial))return;
        float charge=Mth.clamp(hazard.age(partial)/Math.max(1,hazard.windup()),0,1);
        float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 eye=model.eyePoint(bodyYaw);
        Vec3 direction=hazard.visualDirection(partial).normalize();
        if (direction.lengthSqr()<1E-8) return;
        Vec3 side=YangJianVisuals.side(direction);
        Vec3 up=direction.cross(side).normalize();
        double clock=boss.tickCount+partial;
        double pulse=.84+.16*Math.sin(clock*1.35);
        double radius=.08+charge*.19;
        Vec3 center=eye.add(direction.scale(.035));
        // Intensifying divine starburst + contracting sacred focus rings
        YangJianVisuals.flare(v,m,center,side,up,radius*1.25,(.52+charge*.48)*pulse,YangJianVisuals.GOLD);
        YangJianVisuals.orientedRing(v,m,center.add(direction.scale(.04)),side,up,
            radius*(2.4-1.3*charge),.025,YangJianVisuals.tint(YangJianVisuals.RED.body(),charge*.82));
        YangJianVisuals.orientedRing(v,m,center.add(direction.scale(.06)),side,up,
            radius*(1.6-.65*charge),.018,YangJianVisuals.tint(YangJianVisuals.GOLD.core(),.55+charge*.45));
        // 6 converging celestial lightning arcs snapping into the third eye
        for (int i=0;i<6;i++) {
            double angle=clock*.19+i*Math.PI/3;
            Vec3 axis=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            YangJianVisuals.bolt(v,m,center.add(axis.scale(radius*(1.75-.72*charge))),
                center.add(axis.scale(radius*.36)),.015,.032,clock,boss.getId()+i*33,
                i%2==0?YangJianVisuals.GOLD:YangJianVisuals.RED,charge*.88,false);
        }
    }

    private static void sweepCloneModel(YangJian boss,YangJianModel model,float partial,PoseStack pose,MultiBufferSource buffers) {
        Vec3 now=lerpPos(boss,partial);
        Vec3 clone=boss.sweepClone();
        pose.pushPose();pose.translate(clone.x-now.x,clone.y-now.y,clone.z-now.z);
        pose.mulPose(new Quaternionf().rotationY((180-boss.sweepCloneYaw())*Mth.DEG_TO_RAD));
        pose.scale(-1F,-1F,1F);pose.scale(YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE,YangJianRenderer.MODEL_SCALE);
        pose.translate(0,-1.501,0);
        VertexConsumer ghost=buffers.getBuffer(RenderType.entityTranslucent(YangJianRenderer.TEXTURE));
        model.renderBeamClone(pose,ghost,15728880,OverlayTexture.NO_OVERLAY,0x42D8F2FF,
            boss.sweepCloneYaw(),boss.cloneBeam(),partial);
        pose.popPose();
    }

    private static void sweepCloneVeil(YangJian boss,float partial,PoseStack pose,VertexConsumer v) {
        Vec3 now=lerpPos(boss,partial);
        Vec3 clone=boss.sweepClone();float pulse=.72F+.28F*Mth.sin((boss.tickCount+partial)*.42F);
        pose.pushPose();pose.translate(clone.x-now.x,clone.y-now.y,clone.z-now.z);
        Matrix4f m=pose.last().pose();
        int alpha=(int)(58+38*pulse);
        ring(v,m,Vec3.ZERO,.65+.10*pulse,.045,(alpha<<24)|0xB3EFFF);
        ring(v,m,new Vec3(0,3.15,0),.42+.06*pulse,.028,((int)(alpha*.8)<<24)|0xFFE598);
        for(int i=0;i<6;i++) {
            double angle=i*Math.PI/3+(boss.tickCount+partial)*.08;
            Vec3 start=new Vec3(Math.cos(angle)*.76,.08,Math.sin(angle)*.76);
            Vec3 top=start.add(Math.cos(angle+.4)*.15,3.35*pulse,Math.sin(angle+.4)*.15);
            beam(v,m,start,top,.028,((int)(72*pulse)<<24)|0xD5F8FF);
            if(i%2==0) {
                YangJianVisuals.bolt(v,m,start,top,.02,.18,boss.tickCount+partial+i*5,
                    boss.getId()+i*59,YangJianVisuals.SILVER,pulse*.55,false);
            }
        }
        pose.popPose();
    }

    private static void axeAftershock(YangJian boss,float partial,VertexConsumer v,Matrix4f m) {
        int charges=boss.axeTrailCharges();
        double clock=boss.tickCount+partial;
        float pulse=.82F+.18F*Mth.sin((float)(clock*.30));
        ring(v,m,new Vec3(0,1.08,0),.74+.08*pulse,.032,((int)(165*pulse)<<24)|0x9CE0FF);
        double phase=clock*.14;
        Vec3 prevOrb=null;
        Vec3 firstOrb=null;
        for(int i=0;i<charges;i++) {
            double a=phase+i*Math.PI*2/charges;
            double y=1.12+.16*Math.sin(clock*.22+i*2.1);
            Vec3 orb=new Vec3(Math.cos(a)*.88,y,Math.sin(a)*.88);
            if(firstOrb==null)firstOrb=orb;
            // Crackling glowing thunder orb representing one active aftershock charge
            YangJianVisuals.flare(v,m,orb,X_AXIS,new Vec3(0,1,0),.22*pulse,.95,YangJianVisuals.BLUE);
            YangJianVisuals.flare(v,m,orb,Z_AXIS,new Vec3(0,1,0),.22*pulse,.95,YangJianVisuals.BLUE);
            // Trailing electric tail behind each orbiting charge
            Vec3 tail=new Vec3(Math.cos(a-.45)*.82,y-.06,Math.sin(a-.45)*.82);
            YangJianVisuals.wake(v,m,tail,orb,.015,.065,YangJianVisuals.BLUE,0,.85*pulse);
            if(prevOrb!=null) {
                YangJianVisuals.bolt(v,m,prevOrb,orb,.018,.11,clock+i*3,boss.getId()+i*17,
                    YangJianVisuals.BLUE,.62*pulse,false);
            }
            prevOrb=orb;
        }
        if(charges>=3 && prevOrb!=null && firstOrb!=null) {
            YangJianVisuals.bolt(v,m,prevOrb,firstOrb,.018,.11,clock+9,boss.getId()+53,
                YangJianVisuals.BLUE,.62*pulse,false);
        }
    }

    private static void whip(YangJian boss,YangJianModel model,float partial,VertexConsumer v,Matrix4f m) {
        float bodyYaw=Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot);
        Vec3 grip=model.heldWeaponPoint(bodyYaw,0,-10.8F/16F,0);
        boolean sweep=boss.action()==YangJian.WHIP_SWEEP || boss.action()==YangJian.WHIP_SPIN
            || (boss.action()==YangJian.WHIP_LANCE_REEL && boss.comboStep()==0);
        float localAge=boss.stepAge(partial),active=localAge-boss.stepWindup();
        YangJianSkill skill=YangJianSkill.forAction(boss.action());
        int duration=sweep && skill!=null?skill.stepActive(0):1;
        boolean extended=sweep && active>=0 && active<duration+6;
        Vec3 previous=grip;
        if(extended) {
            float progress=Mth.clamp(active/duration,0,1);
            double arc=Math.toRadians(boss.telegraphAngle());
            double heading=Math.atan2(boss.dashEnd().z-boss.dashStart().z,boss.dashEnd().x-boss.dashStart().x);
            double direction=YangJianSkill.sweepAngle(heading,arc,progress);
            double reach=boss.telegraphRadius();
            double tipY=1.2;
            float retract=active>duration?Mth.clamp((active-duration)/6,0,1):0;
            Vec3 end=new Vec3(Math.cos(direction)*reach,tipY,Math.sin(direction)*reach).lerp(grip.add(0,-1.4,0),retract);
            // Sweeping trailing electric plasma ribbon behind the cracking lightning whip!
            if(active<duration+3 && retract<.8) {
                double trailFade=(1-retract)*.92;
                int arcSteps=12;
                double backSpan=Math.min(progress,0.28)*arc;
                Vec3 prevInner=null, prevOuter=null;
                double prevA=0;
                for(int k=0;k<=arcSteps;k++) {
                    double f=k/(double)arcSteps;
                    double ang=direction-backSpan*(1-f);
                    Vec3 outP=new Vec3(Math.cos(ang)*reach,tipY,Math.sin(ang)*reach);
                    Vec3 inP=grip.lerp(outP,0.42);
                    double op=f*f*trailFade;
                    if(prevInner!=null) {
                        YangJianVisuals.bladeArc(v,m,prevInner,prevOuter,inP,outP,YangJianVisuals.BLUE,prevA,op);
                    }
                    prevInner=inP;prevOuter=outP;prevA=op;
                }
            }
            for(int i=1;i<=24;i++) {
                double t=i/24D;
                double bend=Math.sin(t*Math.PI)*.13*(1-progress);
                Vec3 point=grip.lerp(end,t).add(-Math.sin(direction)*bend,0,Math.cos(direction)*bend);
                chainLink(v,m,previous,point,i,active<duration);
                previous=point;
            }
            // Crackling lightning envelope + sonic whip-tip thunder flare
            if(active<duration) {
                YangJianVisuals.bolt(v,m,grip,end,.048,.34,boss.tickCount+partial,boss.getId()+61,
                    YangJianVisuals.BLUE,.92,false);
                YangJianVisuals.flare(v,m,end,X_AXIS,new Vec3(0,1,0),.58,.95,YangJianVisuals.BLUE);
                YangJianVisuals.flare(v,m,end,Z_AXIS,new Vec3(0,1,0),.58,.95,YangJianVisuals.BLUE);
            }
        } else {
            float charge=sweep?Mth.clamp(localAge/Math.max(1,boss.stepWindup()),0,1):0;
            double heading=Math.toRadians(-bodyYaw);
            for(int i=1;i<=16;i++) {
                double t=i/16D,angle=t*Math.PI*2.2;
                double radius=.27*(1-t*.45);
                Vec3 point=grip.add(Math.cos(angle+heading)*radius*t,-t*1.45,Math.sin(angle+heading)*radius*t);
                chainLink(v,m,previous,point,i,charge>.4);
                previous=point;
            }
            if(charge>.4) {
                YangJianVisuals.flare(v,m,grip,X_AXIS,new Vec3(0,1,0),.28*charge,charge*.85,YangJianVisuals.BLUE);
            }
        }
    }

    private static Vec3 lerpPos(YangJian boss,float partial) {
        return new Vec3(Mth.lerp(partial,boss.xOld,boss.getX()),
            Mth.lerp(partial,boss.yOld,boss.getY()),
            Mth.lerp(partial,boss.zOld,boss.getZ()));
    }

    private static void chainLink(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,int index,boolean charged) {
        beam(v,m,from,to,.052,index%2==0?0xFFB8A37A:0xFF586570);
        if(charged) {
            beam(v,m,from,to,.105,0x424E95FF);
            beam(v,m,from,to,.026,0xF2E6FCFF);
        }
    }
    private static void ring(VertexConsumer v,Matrix4f m,Vec3 center,double radius,double width,int color) {
        for(int i=0;i<64;i++) {
            double a=i*Math.PI/32,b=(i+1)*Math.PI/32;
            point(v,m,center.add(Math.cos(a)*(radius-width),.045,Math.sin(a)*(radius-width)),color);
            point(v,m,center.add(Math.cos(a)*(radius+width),.045,Math.sin(a)*(radius+width)),color);
            point(v,m,center.add(Math.cos(b)*(radius+width),.045,Math.sin(b)*(radius+width)),color);
            point(v,m,center.add(Math.cos(b)*(radius-width),.045,Math.sin(b)*(radius-width)),color);
        }
    }
    private static void beam(VertexConsumer v,Matrix4f m,Vec3 from,Vec3 to,double width,int color) {
        Vec3 direction=to.subtract(from).normalize();
        if(direction.lengthSqr()<1E-10)return;
        Vec3 side=direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width);
        Vec3 up=direction.cross(side).normalize().scale(width);
        for(Vec3 axis:new Vec3[]{side,up}) {
            point(v,m,from.subtract(axis),color);point(v,m,from.add(axis),color);
            point(v,m,to.add(axis),color);point(v,m,to.subtract(axis),color);
        }
    }
    private static void point(VertexConsumer v,Matrix4f m,Vec3 p,int color) {
        v.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(color).endVertex();
    }
    private YangJianCombatEffects() {}
}
