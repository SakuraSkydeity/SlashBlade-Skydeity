# -*- coding: utf-8 -*-
"""OBJ 瘦身（精度 + 去重 + 索引重排）：<in> <out> [v位=3] [vt位=5] [vn位=3]

比 compactN.py 多两件事：
  1. 按「目标精度格式化后的字面值」去重 v/vt/vn（首次出现处保留，重复行删掉）；
  2. 相应重排 f 行的三元索引，并更新文件头的 `# N vertices` 之类的计数注释。

⚠ 定点格式化，禁止 str(float) / "%s" % round()（会写出 1e-05，SlashBlade 正则不认
  -> 条目被丢弃 -> 索引错位 -> 越界 -> 兜底成默认模型）。
⚠ 二进制读写，保留原 CRLF。
"""
import sys

src, dst = sys.argv[1], sys.argv[2]
NV = int(sys.argv[3]) if len(sys.argv) > 3 else 3
NT = int(sys.argv[4]) if len(sys.argv) > 4 else 5
NN = int(sys.argv[5]) if len(sys.argv) > 5 else 3


def num(x, nd):
    s = ('%.*f' % (nd, float(x)))
    if '.' in s:
        s = s.rstrip('0')
        if s.endswith('.'):
            s += '0'
    return s


fmt = {'v': NV, 'vt': NT, 'vn': NN}
raw = open(src, 'rb').read().decode('utf-8')
lines = raw.splitlines()
_crlf = raw.count('\r\n'); _lf = raw.count('\n') - _crlf
NL = '\r\n' if _crlf > _lf else '\n'          # 保持源文件的行尾风格

seen = {'v': {}, 'vt': {}, 'vn': {}}
maps = {'v': [], 'vt': [], 'vn': []}     # 原始序号(0基) -> 新序号(0基)
out = []
counts = {'v': 0, 'vt': 0, 'vn': 0, 'f': 0}
dropped = {'v': 0, 'vt': 0, 'vn': 0}

for line in lines:
    kind = line.split(' ', 1)[0] if line else ''
    if kind in fmt:
        p = line.split()
        vals = [num(t, fmt[kind]) for t in p[1:]]
        key = ' '.join(vals)
        if key in seen[kind]:
            maps[kind].append(seen[kind][key])          # 重复 -> 指回首次出现
            dropped[kind] += 1
            continue
        seen[kind][key] = counts[kind]
        maps[kind].append(counts[kind])
        counts[kind] += 1
        out.append(kind + ' ' + key)
    elif kind == 'f':
        p = line.split()
        toks = []
        for t in p[1:]:
            parts = t.split('/')
            a = maps['v'][int(parts[0]) - 1] + 1
            if len(parts) >= 2 and parts[1] != '':
                b = maps['vt'][int(parts[1]) - 1] + 1
            else:
                b = ''
            if len(parts) >= 3 and parts[2] != '':
                c = maps['vn'][int(parts[2]) - 1] + 1
            else:
                c = ''
            toks.append('%d/%d/%d' % (a, b, c) if b != '' and c != '' else
                        ('%d//%d' % (a, c) if c != '' else
                         ('%d/%d' % (a, b) if b != '' else str(a))))
        counts['f'] += 1
        out.append('f ' + ' '.join(toks))
    else:
        out.append(line)

# 修正计数注释
fixed = []
for ln in out:
    s = ln.strip()
    if s.startswith('#'):
        low = s.lower()
        if low.endswith('vertices'):
            n = counts['vn'] if 'normal' in low else (counts['vt'] if 'texture' in low else counts['v'])
            ln = '# %d %s' % (n, 'normal vertices' if 'normal' in low else
                              ('texture vertices' if 'texture' in low else 'vertices'))
        elif low.endswith('elements') or low.endswith('faces'):
            ln = '# %d elements' % counts['f']
    fixed.append(ln)

open(dst, 'wb').write((NL.join(fixed) + NL).encode('utf-8'))
print("输入 %d B -> 输出 %d B（行尾 %s）" % (len(raw.encode('utf-8')), len(open(dst, 'rb').read()),
                                        'CRLF' if NL == '\r\n' else 'LF'))
print("保留 v=%d vt=%d vn=%d f=%d ；去重删掉 v=%d vt=%d vn=%d 行（共 %d）"
      % (counts['v'], counts['vt'], counts['vn'], counts['f'],
         dropped['v'], dropped['vt'], dropped['vn'], sum(dropped.values())))
