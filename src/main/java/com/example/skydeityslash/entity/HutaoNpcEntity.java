package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 胡桃人形 NPC：丢下 hutao 刀时生成，与芙宁娜 NPC 完全相同的坐姿/隐藏名字/重力下落逻辑。
 * 仅实体类型与渲染贴图不同（hutao_npc.png），逻辑全部继承自 FurinaNpcEntity。
 */
public class HutaoNpcEntity extends FurinaNpcEntity {

    public HutaoNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static HutaoNpcEntity spawn(Level level, UUID ownerId, ItemStack blade, Vec3 pos) {
        HutaoNpcEntity npc = new HutaoNpcEntity(ModEntities.HUTAO_NPC.get(), level);
        npc.initNpc(ownerId, blade, pos);
        level.addFreshEntity(npc);
        return npc;
    }
}
