import struct, zlib, gzip, traceback
import prune_empty_chunks as P

raw = open(r'..\src\main\resources\mandarava\region\r.0.0.mca', 'rb').read()
print('file len', len(raw))

# slot 0
b = raw[0:4]
off = (b[0] << 16) | (b[1] << 8) | b[2]
cnt = b[3]
print('slot0 off', off, 'cnt', cnt)
start = off * P.SECTOR
ln = struct.unpack_from('>i', raw, start)[0]
print('chunk len', ln, 'comp', raw[start+4])
payload = bytes(raw[start+5: start+5+ln])
print('payload bytes len', len(payload))

# decompress
comp = payload[0]
data = payload[1:]
if comp == 2:
    dec = zlib.decompress(data)
elif comp == 1:
    dec = gzip.decompress(data)
else:
    dec = data
print('decompressed len', len(dec))

# manual low-level walk with per-tag print, catching errors
class Walk:
    def __init__(self, d):
        self.d = d; self.o = 0
    def u8(self): v = self.d[self.o]; self.o += 1; return v
    def u16(self): v = struct.unpack_from('>H', self.d, self.o)[0]; self.o += 2; return v
    def i32(self): v = struct.unpack_from('>i', self.d, self.o)[0]; self.o += 4; return v
    def i64(self): v = struct.unpack_from('>q', self.d, self.o)[0]; self.o += 8; return v

w = Walk(dec)
try:
    t0 = w.u8(); name0 = dec[w.o: w.o + w.u16()].decode('utf-8', 'replace'); print('root type', t0, 'name', repr(name0))
    for n in range(10):
        t = w.u8()
        if t == 0:
            print('END at', w.o); break
        ln = w.u16(); name = dec[w.o: w.o + ln].decode('utf-8', 'replace'); w.o += ln
        print(' tag', t, repr(name), 'at', w.o)
        if t == 8:  # string
            sl = w.u16(); sv = dec[w.o: w.o + sl]; w.o += sl
            print('   string len', sl, repr(sv[:40]))
        elif t == 3:
            print('   int', i32(w))
        elif t == 4:
            print('   long', i64(w))
        elif t == 9:  # list
            et = w.u8(); cl = w.i32(); print('   list et', et, 'count', cl, 'list-start', w.o)
            if not (et == 10) and n < 3:
                pass
except Exception as e:
    traceback.print_exc()
print('final offset', w.o)