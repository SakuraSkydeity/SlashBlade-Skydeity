#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
扫描 Minecraft Anvil (.mca) region 文件，识别「显式区块全为 air」的纯空区块，
并重写 region 剔除这些区块（只保留含非空气方块的区块），以降低 mod 维度资源的
内存/体积占用。

- 判定：区块存在且其所有 section 的 block_states.palette 只含 minecraft:air
  （不含非空气方块、无多态 data、且无 block_entities / block_ticks / fluid_ticks）。
- 对无法解析的区块一律保留，任何解析/重写异常都不破坏原文件（先备份）。
- 重写时原样拷贝被保留区块的已压缩字节，不重新压缩，保证数据无损。

用法：
  python prune_empty_chunks.py --analyze            # 只分析报告（默认）
  python prune_empty_chunks.py --apply              # 备份原文件后执行剔除重写
  python prune_empty_chunks.py --region r.0.0.mca   # 只处理单个文件（可用 * 通配）
"""
import os
import sys
import gzip
import zlib
import shutil
import struct
import glob
from collections import OrderedDict

REGION_DIR = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "mandarava", "region"))
BACKUP_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mandarava_region_backup")
SECTOR = 4096
SLOTS = 1024
AIR = "minecraft:air"


# ---------- 极简自包含 NBT 解析（Big-Endian） ----------
TAG_END = 0; TAG_BYTE = 1; TAG_SHORT = 2; TAG_INT = 3; TAG_LONG = 4
TAG_FLOAT = 5; TAG_DOUBLE = 6; TAG_BYTE_ARR = 7; TAG_STR = 8
TAG_LIST = 9; TAG_COMPOUND = 10; TAG_INT_ARR = 11; TAG_LONG_ARR = 12


class NBTReader:
    def __init__(self, data):
        self.d = bytearray(data)
        self.o = 0

    def need(self, n):
        if self.o + n > len(self.d):
            raise ValueError("NBT 越界读取")
        return self.o

    def u8(self):
        i = self.need(1); v = self.d[i]; self.o += 1; return v

    def i16(self):
        i = self.need(2); v = struct.unpack_from(">h", self.d, i)[0]; self.o += 2; return v

    def u16(self):
        i = self.need(2); v = struct.unpack_from(">H", self.d, i)[0]; self.o += 2; return v

    def i32(self):
        i = self.need(4); v = struct.unpack_from(">i", self.d, i)[0]; self.o += 4; return v

    def i64(self):
        i = self.need(8); v = struct.unpack_from(">q", self.d, i)[0]; self.o += 8; return v

    def f32(self):
        i = self.need(4); v = struct.unpack_from(">f", self.d, i)[0]; self.o += 4; return v

    def f64(self):
        i = self.need(8); v = struct.unpack_from(">d", self.d, i)[0]; self.o += 8; return v

    @staticmethod
    def dec_utf16be(b):
        # 该维度导出的 NBT 字符串按「字节长度」紧凑编码（非标准 UTF-16 双字节），
        # 方块名为 ASCII，直接按 UTF-8 解码。
        try:
            return b.decode("utf-8", errors="replace")
        except Exception:
            return b.decode("utf-16-be", errors="replace")

    def tag_str(self):
        n = self.u16()
        raw = self.d[self.need(n): self.o + n]
        self.o += n
        return self.dec_utf16be(bytes(raw))

    def read_payload(self, t):
        if t == TAG_END:
            return None
        if t == TAG_BYTE:
            return self.u8()
        if t == TAG_SHORT:
            return self.i16()
        if t == TAG_INT:
            return self.i32()
        if t == TAG_LONG:
            return self.i64()
        if t == TAG_FLOAT:
            return self.f32()
        if t == TAG_DOUBLE:
            return self.f64()
        if t == TAG_BYTE_ARR:
            n = self.i32(); raw = self.d[self.need(n): self.o + n]; self.o += n; return list(bytes(raw))
        if t == TAG_STR:
            return self.tag_str()
        if t == TAG_INT_ARR:
            n = self.i32(); return [self.i32() for _ in range(n)]
        if t == TAG_LONG_ARR:
            n = self.i32(); return [self.i64() for _ in range(n)]
        if t == TAG_LIST:
            et = self.u8(); n = self.i32(); return [self.read_payload(et) for _ in range(n)]
        if t == TAG_COMPOUND:
            out = OrderedDict()
            while True:
                t2 = self.u8()
                if t2 == TAG_END:
                    break
                name = self.tag_str()
                out[name] = self.read_payload(t2)
            return out
        raise ValueError("未知 TAG 类型 %d" % t)

    def root_compound(self):
        t = self.u8()
        if t != TAG_COMPOUND:
            raise ValueError("根不是 CompoundTag (%d)" % t)
        self.tag_str()  # 根名称
        # 先探测是否有嵌套 name-payload：直接按复合后接数据解析
        out = OrderedDict()
        while True:
            t2 = self.u8()
            if t2 == TAG_END:
                break
            name = self.tag_str()
            out[name] = self.read_payload(t2)
        return out


def parse_chunk(chunk_payload_raw: bytes):
    """chunk_payload_raw = region 中 chunk 段（不包含前面 4 字节 length），首字节为压缩类型。"""
    if not chunk_payload_raw:
        return None
    comp = chunk_payload_raw[0]
    data = chunk_payload_raw[1:]
    try:
        if comp == 1:
            raw = gzip.decompress(data)
        elif comp == 2:
            raw = zlib.decompress(data)
        elif comp == 3:
            raw = data
        else:
            return None
    except Exception:
        return None
    try:
        r = NBTReader(raw)
        return r.root_compound()
    except Exception:
        return None


class ChunkInfo:
    __slots__ = ("exists", "nbt", "nonair", "reason")

    def __init__(self):
        self.exists = False     # region 是否含该区块
        self.nbt = None         # 解析出的 NBT（None=解析失败）
        self.nonair = False     # 判定为非纯空 / 需保留
        self.reason = ""


def base_name(name: str) -> str:
    """剥掉命名空间前缀：'minecraft:air' -> 'air'；无前缀原样返回。"""
    if not name:
        return ""
    if ":" in name:
        return name.split(":", 1)[1]
    return name


def chunk_is_pure_air(nbt) -> str:
    """返回 '' 表示纯空；否则返回一条需保留的原因。"""
    if not isinstance(nbt, dict):
        return "解析失败/无法判定"
    sections = nbt.get("sections")
    if sections is None:
        return "无 section 数据"
    for sec in sections:
        if not isinstance(sec, dict):
            continue
        bs = sec.get("block_states")
        if bs is None:
            continue  # 无 block_states = 该层全空气
        if not isinstance(bs, dict):
            return "block_states 结构异常"
        pal = bs.get("palette") or []
        for p in pal:
            if not isinstance(p, dict):
                return "palette 结构异常"
            # 只要调色板含任意非 air 方块，该 section 就有内容（即便带 data 数组也算，须保留）
            if base_name(str(p.get("Name", ""))) != "air":
                return "section 含非空气方块(%s)" % p.get("Name")
    # 空方块但可能有方块实体/待处理数据
    for flag in ("block_entities", "block_ticks", "fluid_ticks"):
        val = nbt.get(flag)
        if val:
            return "含 %s" % flag
    return ""


def analyze_region(path):
    with open(path, "rb") as f:
        raw = f.read()
    loc = []
    for i in range(SLOTS):
        b = raw[4 * i: 4 * i + 4]
        off = (b[0] << 16) | (b[1] << 8) | b[2]
        cnt = b[3]
        loc.append((off, cnt))
    ts = [struct.unpack_from(">i", raw, 4096 + 4 * i)[0] for i in range(SLOTS)]

    infos = []
    total_existing = 0
    kept = []
    for i in range(SLOTS):
        off, cnt = loc[i]
        ci = ChunkInfo()
        if off > 0 and cnt > 0:
            ci.exists = True
            total_existing += 1
            start = off * SECTOR
            if start + 4 < len(raw):
                ln = struct.unpack_from(">i", raw, start)[0]
                # chunk 段 = 长度(4) + 压缩类型(1) + 压缩数据(ln-1)，共 4+ln 字节
                if 0 < ln <= (cnt * SECTOR - 4) and start + 4 + ln <= len(raw):
                    # 从 start+4 开始（含压缩类型字节），parse_chunk 内部按 [0]=压缩类型处理
                    chunk_payload = raw[start + 4: start + 4 + ln]
                    ci.nbt = parse_chunk(chunk_payload)
                    if ci.nbt is None:
                        ci.nonair = True
                        ci.reason = "解析失败(保留)"
                    else:
                        rc = chunk_is_pure_air(ci.nbt)
                        if rc:
                            # 含非空气方块 → 保留
                            ci.nonair = True
                            ci.reason = rc
                        else:
                            # 显式全 air → 纯空剔除
                            ci.nonair = False
                            ci.reason = "纯空"
                else:
                    ci.nonair = True
                    ci.reason = "长度异常(保留)"
            else:
                ci.nonair = True
                ci.reason = "越界(保留)"
        if ci.nonair:
            kept.append(i)
        infos.append(ci)
    empty_count = total_existing - len(kept)
    return infos, total_existing, len(kept), empty_count


def main():
    mode = "analyze"
    only = None
    for a in sys.argv[1:]:
        if a in ("--apply", "-a"):
            mode = "apply"
        elif a in ("--analyze",):
            mode = "analyze"
        elif a.startswith("--region="):
            only = a.split("=", 1)[1]
        elif not a.startswith("-"):
            only = a

    if not os.path.isdir(REGION_DIR):
        print("region 目录不存在：%s" % REGION_DIR)
        sys.exit(1)

    pattern = os.path.join(REGION_DIR, only if only else "*.mca")
    files = sorted(glob.glob(pattern))
    if not files:
        print("未找到匹配文件：%s" % pattern)
        sys.exit(1)

    total_kept_byts = 0
    rows = []
    grand_existing = grand_kept = grand_empty = 0

    for path in files:
        infos, existing, kept, empty = analyze_region(path)
        sz = os.path.getsize(path)
        # 估算剔除后的体积：头部 + 保留区块字节量（压缩后原样）
        kept_bytes = 0
        with open(path, "rb") as f:
            raw = f.read()
        loc = []
        for i in range(SLOTS):
            b = raw[4 * i: 4 * i + 4]
            loc.append(((b[0] << 16) | (b[1] << 8) | b[2], b[3]))
        est = SECTOR + SECTOR  # 头部 + 第一块对齐余量估算
        body = 0
        for i in range(SLOTS):
            if i >= len(infos) or not infos[i].nonair:
                continue
            off, cnt = loc[i]
            if off <= 0 or cnt <= 0:
                continue
            start = off * SECTOR
            if start + 4 > len(raw):
                continue
            ln = struct.unpack_from(">i", raw, start)[0]
            if 0 < ln <= cnt * SECTOR - 4:
                body += ((4 + ln + SECTOR - 1) // SECTOR) * SECTOR
        kept_bytes = SECTOR + body  # 头部8192
        grand_existing += existing
        grand_kept += kept
        grand_empty += empty
        total_kept_byts += kept_bytes
        rows.append((os.path.basename(path), sz, kept_bytes, existing, kept, empty))

    print("=" * 76)
    print("region 目录：%s  (%d 个文件)" % (REGION_DIR, len(files)))
    if mode == "analyze":
        print("模式：仅分析（不修改文件）")
    else:
        print("模式：备份原文件后剔除纯空区块")
    print("-" * 76)
    hdr = "%-18s %9s %9s %6s %6s %6s" % ("文件", "原大小", "预估新", "总区块", "保留", "纯空")
    print(hdr)
    print("-" * 76)
    org_total = 0
    for name, sz, kb, ex, kp, em in rows:
        org_total += sz
        print("%-18s %7.1fM %7.1fM %6d %6d %6d" % (
            name[:18], sz / 1048576.0, kb / 1048576.0, ex, kp, em))
    print("-" * 76)
    print("总计: 现 %6.1f MB  ->  剔除后约 %6.1f MB  (省 %6.1f MB)" % (
        org_total / 1048576.0, total_kept_byts / 1048576.0,
        (org_total - total_kept_byts) / 1048576.0))
    print("区块: 显式存在 %d ，其中保留(非空) %d ，纯空剔除 %d" % (grand_existing, grand_kept, grand_empty))

    if mode == "apply":
        os.makedirs(BACKUP_DIR, exist_ok=True)
        for path in files:
            shutil.copy2(path, os.path.join(BACKUP_DIR, os.path.basename(path)))
            _rewrite(path)
        print("-" * 76)
        print("已备份原文件到：%s" % BACKUP_DIR)
        print("已剔除纯空区块并重写全部 region。")


def _rewrite(path):
    with open(path, "rb") as f:
        raw = f.read()

    # 读取原始 location / timestamp
    loc = []
    ts = []
    for i in range(SLOTS):
        b = raw[4 * i: 4 * i + 4]
        loc.append(((b[0] << 16) | (b[1] << 8) | b[2], b[3]))
        ts.append(struct.unpack_from(">i", raw, 4096 + 4 * i)[0])

    # 逐个保留区块并记录原始 chunk 字节
    kept_payloads = []  # (slot, chunk_bytes_raw 含 5 字节头)
    for i in range(SLOTS):
        off, cnt = loc[i]
        if off <= 0 or cnt <= 0:
            continue
        start = off * SECTOR
        if start + 4 > len(raw):
            continue
        ln = struct.unpack_from(">i", raw, start)[0]
        if not (0 < ln <= cnt * SECTOR - 4):
            continue
        chunk_raw = raw[start: start + 4 + ln]
        # 是否纯空：与 analyze 同逻辑
        nbt = None
        try:
            nbt = parse_chunk(bytes(chunk_raw[4:4 + ln]))
        except Exception:
            nbt = None
        if nbt is None:
            kept_payloads.append((i, bytes(chunk_raw)))  # 解析失败保留
            continue
        # 保留非空区块（chunk_is_pure_air 返回非空字符串=含非空气方块；返回''=纯空）
        if not chunk_is_pure_air(nbt):
            continue  # 纯空 → 剔除，不写入新区块
        kept_payloads.append((i, bytes(chunk_raw)))

    # 重建新 region
    new_header = bytearray(SECTOR + SECTOR)
    offset_sector = 2  # 头部占 0-1 扇区
    body = bytearray()
    for slot, chunk_raw in kept_payloads:
        n = len(chunk_raw)
        secs = (n + SECTOR - 1) // SECTOR
        # 写入 location
        new_header[4 * slot] = (offset_sector >> 16) & 0xFF
        new_header[4 * slot + 1] = (offset_sector >> 8) & 0xFF
        new_header[4 * slot + 2] = offset_sector & 0xFF
        new_header[4 * slot + 3] = secs
        # 时间戳沿用原值
        struct.pack_into(">i", new_header, SECTOR + 4 * slot, ts[slot])
        body += chunk_raw
        if n % SECTOR:
            body += b"\x00" * (SECTOR - n % SECTOR)
        offset_sector += secs

    with open(path, "wb") as f:
        f.write(bytes(new_header))
        f.write(bytes(body))


if __name__ == "__main__":
    main()