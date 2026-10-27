# Concrete re-execution of the int32 decision arithmetic (Java semantics) to pick a JUnit-sized
# witness from the Z3 regime: one big leaf arc (flags,label,vint(len),bytes) + (n-1) small arcs.
import struct, sys
def i32(x): x &= 0xFFFFFFFF; return x - (1 << 32) if x & 0x80000000 else x
def f32(x): return struct.unpack('f', struct.pack('f', float(x)))[0]
def f2i(f):
    if f != f: return 0
    if f >= 2147483647.0: return 2147483647
    if f <= -2147483648.0: return -2147483648
    return int(f)
def vlen(x): return 1 if x < 1<<7 else 2 if x < 1<<14 else 3 if x < 1<<21 else 4 if x < 1<<28 else 5
def decide(n, rng, maxB, labelB=1, factor=1.0, credit=0):
    maxNo = maxB - labelB
    presence = i32(rng + 7) >> 3
    sizeBS = i32(maxB * n); sizeDA = i32(presence + labelB + i32(maxNo * n))
    allowed = f2i(f32(f32(sizeBS) * f32(factor))); cost = i32(sizeDA - allowed)
    da = cost <= 0 or (credit >= cost and f32(sizeDA) <= f32(f32(allowed) * f32(1.66)))
    cont = rng == n
    hdr = 1 + vlen(n) + vlen(maxB); destPos = i32(hdr + i32(n * maxB))
    return ('CONT' if cont else 'DA' if da else 'BS'), destPos, sizeBS, sizeDA, cost
n = int(sys.argv[1]); rng = int(sys.argv[2]); factor = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0
small = 4  # small arc: flags,label,vint(2),2 output bytes  (+ 0 target: leaf)
for outLen in range((1 << 31) // n - 64, (1 << 31) // n + 64):
    maxB = 1 + 1 + vlen(outLen) + outLen
    enc, destPos, sbs, sda, cost = decide(n, rng, maxB, factor=factor)
    srcPos = maxB + (n - 1) * (1 + 1 + 1 + 2)
    if enc == 'BS' and destPos <= srcPos:
        print(f"n={n} range={rng} outLen={outLen} maxBytesPerArc={maxB} enc={enc} destPos={destPos} srcPos={srcPos} sizeBS={sbs} sizeDA={sda} cost={cost}  -> SKIPPED EXPANSION")
        break
else:
    print("no silent-skip witness in window")
