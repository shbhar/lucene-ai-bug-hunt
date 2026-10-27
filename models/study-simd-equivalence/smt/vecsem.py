# Lane-precise semantics of the jdk.incubator.vector operations used by Lucene's
# PanamaVectorUtilSupport / MemorySegmentPostingDecodingUtil / PanamaDocValues*Support,
# written as z3 bit-vector terms.  A "vector" is a Python list of z3 BitVec terms,
# lane 0 first.  Each helper documents the Vector API rule it implements.
#
# Sources for the semantics (JDK 25 jdk.incubator.vector javadoc):
#  - VectorOperators.LSHR: "a>>>(n&(ESIZE*8-1))"; for byte/short lanes the lane value is
#    treated as unsigned before the shift (ByteVector uses (a & 0xFF) >>> n).
#  - lanewise ADD/SUB/MUL: wrap modulo 2^ESIZE (Java integral arithmetic narrowed to lane type).
#  - ABS on byte: Math.abs then narrowed, so abs(-128) == -128.
#  - convert/convertShape(conv, rsp, part): the logical result of converting every input lane
#    is split into blocks of rsp.length() lanes; `part` selects the block (expanding case).
#  - reinterpretShape/reinterpretAsX: "as if" stored in little-endian byte order and reloaded.
#  - reduceLanes(ADD) for integral lanes: sum modulo 2^ESIZE (order irrelevant).
#  - BIT_COUNT: Integer/Long.bitCount per lane.
#  - compare(GE/LE/LT): signed comparison for integral lanes.
#  - compress(mask): selected lanes packed to the low lanes in order, remaining lanes zero.
#  - VectorMask.toLong(): bit i set iff lane i set; trueCount(): popcount.
import os
import z3


def bvv(v, w):
    return z3.BitVecVal(v, w)


def sym(name, n, w):
    return [z3.BitVec(f"{name}{i}", w) for i in range(n)]


def sext(x, w):
    return z3.SignExt(w - x.size(), x) if w > x.size() else x


def zext(x, w):
    return z3.ZeroExt(w - x.size(), x) if w > x.size() else x


def trunc(x, w):
    return z3.Extract(w - 1, 0, x) if x.size() > w else x


def lshr(x, n):
    # LSHR on a lane of width w: unsigned shift by (n & (w-1))
    w = x.size()
    return z3.LShR(x, bvv(n & (w - 1), w))


def lanes_map(f, *vs):
    return [f(*xs) for xs in zip(*vs)]


def vadd(a, b):
    return lanes_map(lambda x, y: x + y, a, b)


def vsub(a, b):
    return lanes_map(lambda x, y: x - y, a, b)


def vmul(a, b):
    return lanes_map(lambda x, y: x * y, a, b)


def vand_const(a, c):
    return [x & bvv(c, x.size()) for x in a]


def vlshr(a, n):
    return [lshr(x, n) for x in a]


def vabs(a):
    return [z3.If(x < 0, -x, x) for x in a]  # -(-128) wraps to -128 like Java narrowing


def convert_part(a, conv, out_lanes, part):
    """convertShape/convert with an expanding conversion: convert every lane, then select block
    `part` of size out_lanes.  conv is a function lane -> lane."""
    full = [conv(x) for x in a]
    assert len(full) >= out_lanes * (part + 1), (len(full), out_lanes, part)
    return full[part * out_lanes:(part + 1) * out_lanes]


def B2S(x):
    return sext(x, 16)


def ZB2S(x):
    return zext(x, 16)


def B2I(x):
    return sext(x, 32)


def ZB2I(x):
    return zext(x, 32)


def S2I(x):
    return sext(x, 32)


def ZS2I(x):
    return zext(x, 32)


def reinterpret(a, out_w):
    """Little-endian reinterpretation of a list of lanes into lanes of width out_w."""
    in_w = a[0].size()
    if out_w == in_w:
        return a
    if out_w > in_w:
        k = out_w // in_w
        assert len(a) % k == 0
        res = []
        for j in range(len(a) // k):
            parts = a[j * k:(j + 1) * k]
            res.append(z3.Concat(*reversed(parts)))  # lane j*k is least significant
        return res
    k = in_w // out_w
    res = []
    for x in a:
        for t in range(k):
            res.append(z3.Extract((t + 1) * out_w - 1, t * out_w, x))
    return res


def reduce_add(a):
    s = a[0]
    for x in a[1:]:
        s = s + x
    return s


def popcount(x):
    w = x.size()
    out_w = max(8, w.bit_length() + 1)
    bits = [zext(z3.Extract(i, i, x), 32) for i in range(w)]
    s = bits[0]
    for b in bits[1:]:
        s = s + b
    return s  # 32-bit count


def popcount_w(x):
    """BIT_COUNT lanewise: result in the same lane width."""
    return trunc(popcount(x), x.size()) if x.size() < 32 else zext(popcount(x), x.size())


def loop_bound(n, lanes):
    """VectorSpecies.loopBound(n) = n & -lanes (lanes is a power of two)."""
    return n - (n % lanes) if n >= 0 else -((-n + lanes - 1) // lanes) * lanes


def java_loop_bound(n, lanes):
    # VectorSpecies.loopBound(length) == length & ~(lanes-1) in two's complement
    return n & ~(lanes - 1)


CVC5 = os.environ.get('CVC5', 'cvc5')
OBL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "obligations")


def cvc5_check(assertions, tag, opts=("--solve-bv-as-int=sum",), timeout_s=600, keep=False):
    """Dump the conjunction of z3 assertions as SMT-LIB2 (QF_BV) and decide it with cvc5.
    Returns 'unsat' / 'sat' / 'unknown' / 'timeout'."""
    import subprocess
    os.makedirs(OBL_DIR, exist_ok=True)
    s = z3.Solver()
    s.add(*assertions)
    path = os.path.join(OBL_DIR, tag + ".smt2")
    with open(path, "w") as f:
        f.write("(set-logic QF_BV)\n" + s.to_smt2())
    try:
        out = subprocess.run([CVC5, *opts, path], capture_output=True, text=True, timeout=timeout_s)
        res = out.stdout.strip().splitlines()[-1] if out.stdout.strip() else ("error:" + out.stderr[:200])
    except subprocess.TimeoutExpired:
        res = "timeout"
    if not keep and res == "unsat":
        os.remove(path)
    return res
