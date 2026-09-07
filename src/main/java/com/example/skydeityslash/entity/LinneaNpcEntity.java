package com.example.skydeityslash.entity;

import com.example.skydeityslash.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 莉奈娅人形 NPC：丢下 linnea 刀时生成，与芙宁娜 NPC 完全相同的坐姿/隐藏名字/重力下落逻辑。
 * 仅实体类型与渲染贴图不同（linnea_npc.png），逻辑全部继承自 FurinaNpcEntity。
 */
public class LinneaNpcEntity extends FurinaNpcEntity {

    public LinneaNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static LinneaNpcEntity spawn(Level level, UUID ownerId, ItemStack blade, Vec3 pos) {
        LinneaNpcEntity npc = new LinneaNpcEntity(ModEntities.LINNEA_NPC.get(), level);
        npc.initNpc(ownerId, blade, pos);
        level.addFreshEntity(npc);
        return npc;
    }
}
