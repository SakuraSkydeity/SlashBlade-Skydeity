package com.example.skydeityslash.client.objopt;

/**
 * 让烘焙结果直接挂在原版模型对象上。
 *
 * <p>用接口而不是另开一张静态表，是为了让烘焙网格的生命周期跟着
 * {@code WavefrontObject} 走：模型被缓存淘汰时烘焙数据一起回收，不会留下悬空引用。
 */
public interface BakedMeshHolder {

    ObjBakedMesh objopt$getBakedMesh();
}
