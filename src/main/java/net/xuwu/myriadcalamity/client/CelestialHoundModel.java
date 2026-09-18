package net.xuwu.myriadcalamity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.xuwu.myriadcalamity.entity.CelestialHound;

/** An angular black celestial hunting dog: gold collar, layered bracers and an articulated jaw/tail. */
public final class CelestialHoundModel extends EntityModel<CelestialHound> {
    private final ModelPart root,body,head,jaw,tail,tailTip;
    private final ModelPart[] thighs=new ModelPart[4],shins=new ModelPart[4];

    public CelestialHoundModel() {
        MeshDefinition mesh=new MeshDefinition();
        PartDefinition r=mesh.getRoot();
        PartDefinition torso=r.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0)
            .addBox(-3.5F,-3.5F,-6.5F,7,7,13).texOffs(0,24).addBox(-3,-4,-5,6,2,9),PartPose.offset(0,12,0));
        torso.addOrReplaceChild("collar",CubeListBuilder.create().texOffs(40,0)
            .addBox(-3.8F,-3.7F,-6.8F,7.6F,1.1F,2.3F).texOffs(40,5)
            .addBox(-3.8F,2.6F,-6.8F,7.6F,1.1F,2.3F).texOffs(40,10)
            .addBox(-3.9F,-2.6F,-6.8F,1.1F,5.2F,2.3F).texOffs(40,10)
            .addBox(2.8F,-2.6F,-6.8F,1.1F,5.2F,2.3F).texOffs(40,20)
            .addBox(-1.4F,1.8F,-7.4F,2.8F,3,1.1F),PartPose.ZERO);
        PartDefinition h=torso.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,38)
            .addBox(-2.8F,-3.2F,-4.3F,5.6F,5.3F,5.6F).texOffs(20,38)
            .addBox(-2,-.9F,-7.1F,4,2.7F,3.2F).texOffs(30,49)
            .addBox(-1.8F,-.6F,-7.4F,3.6F,1.5F,.7F).texOffs(0,52)
            .addBox(-2.7F,-6.3F,-1.8F,1.8F,3.8F,2.2F).texOffs(0,52)
            .addBox(.9F,-6.3F,-1.8F,1.8F,3.8F,2.2F).texOffs(56,30)
            .addBox(-2.86F,-1.9F,-4.38F,1.25F,.9F,.25F).texOffs(56,30)
            .addBox(1.61F,-1.9F,-4.38F,1.25F,.9F,.25F),PartPose.offset(0,-1,-6));
        h.addOrReplaceChild("jaw",CubeListBuilder.create().texOffs(20,46)
            .addBox(-1.8F,0,-4.3F,3.6F,1.2F,4.5F).texOffs(55,35)
            .addBox(-1.7F,-.5F,-3.8F,.6F,.9F,.7F).texOffs(55,35)
            .addBox(1.1F,-.5F,-3.8F,.6F,.9F,.7F),PartPose.offset(0,1.5F,-2.5F));
        PartDefinition t=torso.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(24,23)
            .addBox(-1,-.7F,0,2,2,6),PartPose.offsetAndRotation(0,-1,6,.2F,0,0));
        t.addOrReplaceChild("tail_tip",CubeListBuilder.create().texOffs(25,31)
            .addBox(-.8F,-.6F,0,1.6F,1.6F,5),PartPose.offsetAndRotation(0,0,5.3F,-.3F,0,0));
        for(int i=0;i<4;i++) {
            boolean back=i>=2;
            float x=i%2==0?-2.65F:2.65F,z=back?4.6F:-4.6F;
            PartDefinition upper=r.addOrReplaceChild("leg_"+i,CubeListBuilder.create().texOffs(28,0)
                .addBox(-1.2F,-.8F,-1.4F,2.4F,5.8F,2.8F),PartPose.offset(x,14,z));
            upper.addOrReplaceChild("shin",CubeListBuilder.create().texOffs(28,10)
                .addBox(-.95F,0,-1.05F,1.9F,4.2F,2.1F).texOffs(40,25)
                .addBox(-1.2F,2.15F,-1.3F,2.4F,1.1F,2.6F).texOffs(28,17)
                .addBox(-1.3F,3.3F,-2.2F,2.6F,1.7F,3.7F),PartPose.offset(0,5,0));
        }
        root=LayerDefinition.create(mesh,64,64).bakeRoot();body=root.getChild("body");
        head=body.getChild("head");jaw=head.getChild("jaw");tail=body.getChild("tail");tailTip=tail.getChild("tail_tip");
        for(int i=0;i<4;i++) {thighs[i]=root.getChild("leg_"+i);shins[i]=thighs[i].getChild("shin");}
    }

    @Override public void setupAnim(CelestialHound dog,float swing,float amount,float age,float headYaw,float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float partial=Mth.clamp(age-dog.tickCount,0,1),elapsed=dog.actionAge(partial);
        head.yRot=Mth.clamp(headYaw,-35,35)*Mth.DEG_TO_RAD;
        head.xRot=Mth.clamp(headPitch,-20,20)*Mth.DEG_TO_RAD;
        tail.yRot=Mth.sin(age*.18F)*.11F;
        tailTip.yRot=Mth.sin(age*.18F-.5F)*.17F;
        if(dog.action()==CelestialHound.WINDUP) {
            float t=Mth.clamp(elapsed/10F,0,1);t=t*t*(3-2*t);
            body.y+=t*1.3F;body.xRot=-t*.13F;head.xRot+=t*.2F;
            jaw.xRot=t*.24F;tail.xRot-=t*.4F;
            for(int i=0;i<4;i++) {thighs[i].xRot=(i<2?-.4F:.75F)*t;shins[i].xRot=(i<2?.5F:-.95F)*t;}
        } else if(dog.action()==CelestialHound.POUNCE) {
            float t=Mth.clamp(elapsed/8F,0,1),arc=Mth.sin(t*Mth.PI);
            body.xRot=-.16F+arc*.23F;head.xRot-=.16F;jaw.xRot=.55F;
            tail.xRot+=.27F;tailTip.xRot+=.18F;
            for(int i=0;i<4;i++) {thighs[i].xRot=i<2?-1.0F+arc*.15F:1.03F-arc*.16F;shins[i].xRot=i<2?.25F:-.55F;}
        } else {
            float power=Math.min(1,amount*1.8F),clock=swing*1.15F;
            if(dog.action()==CelestialHound.RETREAT)clock=-clock;
            body.y+=Math.abs(Mth.sin(clock))*power*.7F;
            body.xRot=Mth.cos(clock*2)*power*.04F;
            for(int i=0;i<4;i++) {
                float leg=Mth.cos(clock+(i==0 || i==3?0:Mth.PI));
                thighs[i].xRot=leg*.82F*power;shins[i].xRot=Math.max(0,-leg)*-.65F*power;
            }
            head.xRot-=body.xRot*.5F;
        }
    }
    @Override public void renderToBuffer(PoseStack pose,VertexConsumer buffer,int light,int overlay,float red,float green,float blue,float alpha) {
        root.render(pose,buffer,light,overlay,red,green,blue,alpha);
    }
}
