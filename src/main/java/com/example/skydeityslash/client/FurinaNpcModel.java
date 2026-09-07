package com.example.skydeityslash.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * 芙宁娜/胡桃人形 NPC 坐姿模型：逐字节复刻参考 mod（FriendlyNpcModel）的 setupAnim。
 * 不调用 super.setupAnim；躯干直立、双臂前倾撑地、双腿盘起，
 * 配合渲染器 -0.65 下移形成坐姿。外层部件（hat/jacket/sleeve/pants）跟随对应内层部件。
 * 泛型 T 由渲染器指定（FurinaNpcEntity / HutaoNpcEntity），两者共用同一坐姿。
 */
public class FurinaNpcModel<T extends LivingEntity> extends PlayerModel<T> {

    public FurinaNpcModel(ModelPart root) {
        super(root, true);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.xRot = headPitch * 0.017453292F - 0.096F;
        this.head.yRot = netHeadYaw * 0.017453292F;
        this.head.zRot = 0.0F;

        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        this.rightArm.xRot = -0.6283F;
        this.rightArm.yRot = -0.2618F;
        this.rightArm.zRot = 0.0F;

        this.leftArm.xRot = -0.6283F;
        this.leftArm.yRot = 0.2618F;
        this.leftArm.zRot = 0.0F;

        this.rightLeg.xRot = -1.3886F;
        this.rightLeg.yRot = 0.1806F;
        this.rightLeg.zRot = -0.041F;

        this.leftLeg.xRot = -1.3886F;
        this.leftLeg.yRot = -0.1806F;
        this.leftLeg.zRot = 0.041F;

        this.hat.copyFrom(this.head);
        this.jacket.copyFrom(this.body);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }
}
