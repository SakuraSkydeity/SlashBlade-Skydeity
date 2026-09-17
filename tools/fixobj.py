# -*- coding: utf-8 -*-
"""
忠实模拟 SlashBlade Resharped 的 WavefrontObject 解析器，
用来复现/验证 "OBJ 加载异常 -> 兜底默认模型" 的问题。

解析器行为（反编译自 1.9.65 的 WavefrontObject.loadObjModel / parseFace）：
  line = line.replaceAll("\\s+", " ").trim()
  依次 startsWith("v ") / "vn " / "vt " / "f " / ("g " | "o ")
  每类先过 isValidXXX 正则，不合法就 return null -> 该条不加入列表（索引整体错位）
  面里的索引 = Integer.parseInt(...) 直接查 ArrayList -> 越界即抛异常
  异常被 BladeModelManager.getModel 的 catch(Exception) 吞掉 -> 返回 defaultModel
"""
import re
import sys

RV = re.compile(r'^(v( \-?\d+(\.\d+)?){3,4} *$)|^(v( \-?\d+(\.\d+)?){3,4} *$)')
RVN = re.compile(r'^(vn( \-?\d+(\.\d+)?){3,4} *$)|^(vn( \-?\d+(\.\d+)?){3,4} *$)')
RVT = re.compile(r'^(vt( \-?\d+\.\d+){2,3} *$)|^(vt( \-?\d+(\.\d+)?){2,3} *$)')
RF = (re.compile(r'^(f( \d+/\d+/\d+){3,4} *$)'),
      re.compile(r'^(f( \d+/\d+){3,4} *$)'),
      re.compile(r'^(f( \d+//\d+){3,4} *$)'),
      re.compile(r'^(f( \d+){3,4} *$)'))
RG = re.compile(r'^([go]( [\w\d\.]+) *$)')

# 数值 token 里的科学计数法（解析器正则不支持，必须改写）
REXP = re.compile(r'-?\d+(?:\.\d+)?[eE][-+]?\d+')


def parse(path, fix=False):
    """返回 (统计, 非法行, 越界面, 修复后的文本或 None)"""
    nv = nvt = nvn = 0
    bad = []            # (行号, 类型, 内容)
    oob = []            # (行号, 面内容, 需要/现有)
    nf = 0
    out = []
    for i, ln in enumerate(open(path, encoding='utf-8', errors='replace'), 1):
        s = re.sub(r'\s+', ' ', ln).strip()
        if fix:
            # 只改写科学计数法的 token，值完全不变
            if s and not s.startswith('#'):
                def _rep(m):
                    return ('%.10f' % float(m.group(0))).rstrip('0').rstrip('.') or '0'
                s = REXP.sub(_rep, s)
        if not s:
            if fix:
                out.append('')
            continue
        if s.startswith('#'):
            if fix:
                out.append(s)
            continue
        kind = None
        if s.startswith('v '):
            kind, R = 'v', RV
        elif s.startswith('vn '):
            kind, R = 'vn', RVN
        elif s.startswith('vt '):
            kind, R = 'vt', RVT
        elif s.startswith('f '):
            kind, R = 'f', None
        elif s.startswith('g ') or s.startswith('o '):
            kind, R = 'g', RG
        if kind is None:
            if fix:
                out.append(s)
            continue
        ok = False
        if kind == 'v':
            ok = bool(RV.match(s))
            if ok:
                nv += 1
        elif kind == 'vn':
            ok = bool(RVN.match(s))
            if ok:
                nvn += 1
        elif kind == 'vt':
            ok = bool(RVT.match(s))
            if ok:
                nvt += 1
        elif kind == 'g':
            ok = bool(RG.match(s))
        else:  # f
            ok = any(r.match(s) for r in RF)
            if ok:
                nf += 1
                for tok in s[2:].split(' '):
                    p = tok.split('/')
                    try:
                        vi = int(p[0]); ti = int(p[1]) if len(p) > 1 and p[1] else 0
                        ni = int(p[2]) if len(p) > 2 and p[2] else 0
                    except ValueError:
                        oob.append((i, s[:60], 'NumberFormatException'))
                        continue
                    if vi < 1 or vi > nv or (ti and (ti < 1 or ti > nvt)) or (ni and (ni < 1 or ni > nvn)):
                        oob.append((i, s[:60], 'v=%d/%d vt=%d/%d vn=%d/%d' % (vi, nv, ti, nvt, ni, nvn)))
        if not ok:
            bad.append((i, kind, s[:70]))
        if fix:
            out.append(s)
    return dict(nv=nv, nvt=nvt, nvn=nvn, nf=nf), bad, oob, ('\n'.join(out) + '\n' if fix else None)


def report(path, fix=False, save=None):
    st, bad, oob, txt = parse(path, fix=fix)
    print('=' * 78)
    print(path)
    print('  解析通过: v=%d vt=%d vn=%d f=%d' % (st['nv'], st['nvt'], st['nvn'], st['nf']))
    if bad:
        print('  !! 非法行 %d 条（该条会被丢弃 -> 索引错位）:' % len(bad))
        for i, k, s in bad[:8]:
            print('     行%-6d [%s] %s' % (i, k, s))
    else:
        print('  非法行: 0 ✓')
    if oob:
        print('  !! 面索引越界/非法 %d 处（会抛异常 -> 游戏兜底默认模型）:' % len(oob))
        for i, s, m in oob[:8]:
            print('     行%-6d %s   %s' % (i, s, m))
    else:
        print('  面索引: 全部合法 ✓')
    if save and txt is not None:
        open(save, 'w', encoding='utf-8', newline='\n').write(txt)
        print('  已写出修复版 ->', save)
    return len(bad) == 0 and len(oob) == 0


if __name__ == '__main__':
    for a in sys.argv[1:]:
        report(a)
