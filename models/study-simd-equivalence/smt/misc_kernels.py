# Z3 translation validation of the remaining integer SIMD kernels (@7cecf79523):
#  findNextGEQ (PanamaVectorUtilSupport ~L1296), expand8 (~L1660), splitInts
#  (MemorySegmentPostingDecodingUtil), filterByScore (~L1560, compress), int4BitDotProduct /
#  int4DibitDotProduct (~L1330-1450), DocValuesRangeSupport.rangeIntoBitSet /
#  sortedNumericRangeIntoBitSet (PanamaDocValuesRangeSupport + FixedBitSet.orMask).
import os
import sys
import time
import json

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import z3
from vecsem import *  # noqa

RESULTS = []


def prove(name, cond, assumptions=(), timeout=300000, expect="unsat"):
    s = z3.Solver()
    s.set("timeout", timeout)
    s.add(*assumptions)
    s.add(z3.Not(cond))
    r = s.check()
    return r


def log(line):
    print(line, flush=True)
    RESULTS.append(line)


# ---------------- findNextGEQ ----------------

def p_find_next_geq(buf, target, frm, to, L):
    """Returns a z3 Int-valued (BV32) term.  frm/to are Python ints (enumerated)."""
    # for (; from + L < to; from += L + 1) { if (buffer[from + L] >= target) { ... return from + count } }
    res = None
    conds = []
    f = frm
    branches = []
    while f + L < to:
        hit = buf[f + L] >= target
        cnt = bvv(0, 32)
        for i in range(L):
            cnt = cnt + z3.If(buf[f + i] < target, bvv(1, 32), bvv(0, 32))  # compare(LT).trueCount()
        branches.append((hit, bvv(f, 32) + cnt))
        f += L + 1
    # scalar tail from f
    tail = bvv(to, 32)
    for i in reversed(range(f, to)):
        tail = z3.If(buf[i] >= target, bvv(i, 32), tail)
    out = tail
    for hit, val in reversed(branches):
        out = z3.If(hit, val, out)
    return out


def s_find_next_geq(buf, target, frm, to):
    out = bvv(to, 32)
    for i in reversed(range(frm, to)):
        out = z3.If(buf[i] >= target, bvv(i, 32), out)
    return out


VW = int(os.environ.get("VALUE_WIDTH", "32"))


def check_find_next_geq(Ls=(4, 8, 16), nmax_extra=3):
    for L in Ls:
        t0 = time.time()
        nmax = 3 * L + nmax_extra
        ok, cnt = True, 0
        for to in range(0, nmax + 1):
            buf = sym("x", to, VW)
            target = z3.BitVec("t", VW)
            sorted_ = [buf[i] <= buf[i + 1] for i in range(to - 1)]  # sorted on [0, to) (signed)
            for frm in range(0, to + 1):
                cnt += 1
                r = prove("geq", p_find_next_geq(buf, target, frm, to, L) == s_find_next_geq(buf, target, frm, to), sorted_)
                if r != z3.unsat:
                    ok = False
                    log(f"FAIL findNextGEQ L={L} to={to} from={frm}: {r}")
                    break
            if not ok:
                break
        if ok:
            log(f"PROVED findNextGEQ INT lanes={L:2d}: all sorted arrays, all targets, every 0<=from<=to<={nmax} ({cnt} obligations, value width {VW} bits, {time.time() - t0:.1f}s)")
        # the precondition is needed: unsorted counterexample
        to, frm = L + 2, 0
        buf = sym("x", to, 32)
        target = z3.BitVec("t", 32)
        s = z3.Solver()
        s.add(p_find_next_geq(buf, target, frm, to, L) != s_find_next_geq(buf, target, frm, to))
        r = s.check()
        if r == z3.sat:
            m = s.model()
            log(f"  precondition needed (L={L}): unsorted counterexample buffer={[m.eval(b).as_signed_long() for b in buf]} target={m.eval(target).as_signed_long()}")


# ---------------- expand8 ----------------

def check_expand8():
    for L in (4, 8, 16):
        arr = sym("e", 64, 32)
        # scalar
        sc = [None] * 256
        for i in range(64):
            l = arr[i]
            sc[i] = lshr(l, 24) & bvv(0xFF, 32)
            sc[64 + i] = lshr(l, 16) & bvv(0xFF, 32)
            sc[128 + i] = lshr(l, 8) & bvv(0xFF, 32)
            sc[192 + i] = l & bvv(0xFF, 32)
        # vector: reads arr[i..i+L) before writing the same lanes (intoArray(arr, i) after fromArray)
        out = list(arr) + [None] * 192
        for i in range(0, 64, L):
            v = out[i:i + L]
            for k in range(L):
                out[i + k] = lshr(v[k], 24)
                out[64 + i + k] = lshr(v[k], 16) & bvv(0xFF, 32)
                out[128 + i + k] = lshr(v[k], 8) & bvv(0xFF, 32)
                out[192 + i + k] = v[k] & bvv(0xFF, 32)
        r = prove("expand8", z3.And(*[a == b for a, b in zip(out, sc)]))
        log(f"{'PROVED' if r == z3.unsat else 'FAIL ' + str(r)} expand8 INT lanes={L:2d}: all 256 outputs equal for all 64 input ints")


# ---------------- splitInts ----------------

def scalar_split(mem, count, b, bShift, dec, bMask, c, cIndex, cMask, alias):
    # in.readInts(c, cIndex, count)
    c = list(c)
    for i in range(count):
        c[cIndex + i] = mem[i]
    if alias:
        b = c
    maxIter = (bShift - 1) // dec
    for j in range(maxIter + 1):
        shift = bShift - j * dec
        for i in range(count):
            b[count * j + i] = lshr(c[cIndex + i], shift) & bvv(bMask, 32)  # Java >>> masks shift by 31
            if alias:
                c = b
    for i in range(count):
        c[cIndex + i] = c[cIndex + i] & bvv(cMask, 32)
    return (c, c) if alias else (b, c)


def vector_split(mem, count, b, bShift, dec, bMask, c, cIndex, cMask, L, alias):
    if count < L:
        return scalar_split(mem, count, list(b), bShift, dec, bMask, list(c), cIndex, cMask, alias)
    b = list(b)
    c = b if alias else list(c)
    maxIter = (bShift - 1) // dec

    def shift(vec, i):
        for j in range(maxIter + 1):
            for k in range(L):
                b[count * j + i + k] = lshr(vec[k], bShift - j * dec) & bvv(bMask, 32)

    loopBound = java_loop_bound(count - 1, L)
    i = 0
    while i < loopBound:
        vec = mem[i:i + L]  # IntVector.fromMemorySegment(..., offset) : reads memory, not c
        shift(vec, i)
        for k in range(L):
            c[cIndex + i + k] = vec[k] & bvv(cMask, 32)
        i += L
    i = count - L
    vec = mem[i:i + L]
    shift(vec, i)
    for k in range(L):
        c[cIndex + i + k] = vec[k] & bvv(cMask, 32)
    return (b, b) if alias else (b, c)


FORUTIL_CFGS = [  # (count, bShift, dec, bMask, cMask, cIndex-if-c-aliases-b-else-None)  ForUtil.java L167-L506
    (8, 7, 1, 0x01010101, 0x01010101, 56), (16, 6, 2, 0x03030303, 0x03030303, 48), (24, 5, 3, 0x07070707, 0x03030303, None),
    (32, 4, 4, 0x0F0F0F0F, 0x0F0F0F0F, 32), (40, 3, 5, 0x1F1F1F1F, 0x07070707, None), (48, 2, 6, 0x3F3F3F3F, 0x03030303, None),
    (56, 1, 7, 0x7F7F7F7F, 0x01010101, None), (72, 7, 9, 0x01FF01FF, 0x007F007F, None), (80, 6, 10, 0x03FF03FF, 0x003F003F, None),
    (88, 5, 11, 0x07FF07FF, 0x001F001F, None), (96, 4, 12, 0x0FFF0FFF, 0x000F000F, None), (104, 3, 13, 0x1FFF1FFF, 0x00070007, None),
    (112, 2, 14, 0x3FFF3FFF, 0x00030003, None), (120, 1, 15, 0x7FFF7FFF, 0x00010001, None),
] + [(bpv * 8, 32 - bpv, 32, (1 << bpv) - 1, 0xFFFFFFFF, None) for bpv in range(17, 32)]  # decodeSlow


def check_split():
    for L in (4, 8, 16):
        t0 = time.time()
        ok = True
        cfgs = list(FORUTIL_CFGS) + [(cnt, bs, dc, 0x5A5A5A5A, 0x3C3C3C3C, None)
                                    for cnt in (L, L + 1, 2 * L - 1, 2 * L, 2 * L + 1, 3 * L + 3)
                                    for bs, dc in ((31, 7), (9, 4), (1, 1), (20, 32))]
        for (count, bShift, dec, bMask, cMask, alias_idx) in cfgs:
            alias = alias_idx is not None
            mem = sym("m", count, 32)
            maxIter = (bShift - 1) // dec
            size = max(256, count * (maxIter + 1) + 8, (alias_idx or 0) + count + 8)
            b0 = sym("b", size, 32)  # initial (garbage) array contents are symbolic too
            c0 = b0 if alias else sym("c", count + 8, 32)
            cIndex = alias_idx if alias else 3
            sb, scc = scalar_split(mem, count, list(b0), bShift, dec, bMask, list(c0), cIndex, cMask, alias)
            vb, vc = vector_split(mem, count, b0, bShift, dec, bMask, c0, cIndex, cMask, L, alias)
            cond = z3.And(*([x == y for x, y in zip(sb, vb)] + [x == y for x, y in zip(scc, vc)]))
            r = prove("split", cond)
            if r != z3.unsat:
                ok = False
                log(f"FAIL splitInts L={L} cfg={(count, bShift, dec, alias)} {r}")
        if ok:
            log(f"PROVED splitInts INT lanes={L:2d}: b[] and c[] identical (all memory ints, all initial array contents) for {len(cfgs)} configs incl. all lucene104.ForUtil calls and the c-aliases-b ones ({time.time() - t0:.1f}s)")


# ---------------- filterByScore ----------------

def check_filter():
    for DL in (2, 4, 8):
        t0 = time.time()
        ok = True
        for upTo in range(0, 3 * DL + 3):
            n = upTo + 2
            docs = sym("d", n, 32)
            scores = sym("s", n, 64)  # opaque: only moved, never inspected except via keep[]
            keep = [z3.Bool(f"k{i}") for i in range(n)]  # keep[i] <=> scores[i] >= minScoreInclusive
            # scalar
            sd, ss, sz = list(docs), list(scores), bvv(0, 32)
            for i in range(upTo):
                d, sc = sd[i], ss[i]
                sd = [z3.If(sz == bvv(j, 32), d, sd[j]) for j in range(n)]
                ss = [z3.If(sz == bvv(j, 32), sc, ss[j]) for j in range(n)]
                sz = z3.If(keep[i], sz + 1, sz)
            # vector (HAS_FAST_COMPRESS_MASK_CAST): compress(mask).intoArray(buf, newUpto)
            vd, vs, vz = list(docs), list(scores), bvv(0, 32)
            i = 0
            bound = java_loop_bound(upTo, DL)
            while i < bound:
                sv, dv, mk = vs[i:i + DL], vd[i:i + DL], keep[i:i + DL]
                # compressed lane k = k-th selected lane, else 0
                def compress(vals, zero):
                    out = []
                    for k in range(DL):
                        e = zero
                        for src in reversed(range(DL)):
                            rank = sum([z3.If(mk[t], 1, 0) for t in range(src)]) if src else z3.IntVal(0)
                            e = z3.If(z3.And(mk[src], rank == k), vals[src], e)
                        out.append(e)
                    return out
                cs, cd = compress(sv, bvv(0, 64)), compress(dv, bvv(0, 32))
                for k in range(DL):
                    vs = [z3.If(vz + bvv(k, 32) == bvv(j, 32), cs[k], vs[j]) for j in range(n)]
                    vd = [z3.If(vz + bvv(k, 32) == bvv(j, 32), cd[k], vd[j]) for j in range(n)]
                cntk = bvv(0, 32)
                for k in range(DL):
                    cntk = cntk + z3.If(mk[k], bvv(1, 32), bvv(0, 32))
                vz = vz + cntk
                i += DL
            for ii in range(i, upTo):
                d, sc = vd[ii], vs[ii]
                vd = [z3.If(vz == bvv(j, 32), d, vd[j]) for j in range(n)]
                vs = [z3.If(vz == bvv(j, 32), sc, vs[j]) for j in range(n)]
                vz = z3.If(keep[ii], vz + 1, vz)
            prefix = [z3.Implies(bvv(j, 32) < sz, z3.And(sd[j] == vd[j], ss[j] == vs[j])) for j in range(n)]
            untouched = [z3.And(sd[j] == vd[j], ss[j] == vs[j]) for j in range(upTo, n)]
            r = prove("filter", z3.And(sz == vz, *prefix, *untouched))
            if r != z3.unsat:
                ok = False
                log(f"FAIL filterByScore DOUBLE lanes={DL} upTo={upTo}: {r}")
                break
        if ok:
            log(f"PROVED filterByScore DOUBLE lanes={DL}: same count, same first newUpto (doc,score) pairs, entries >= upTo untouched, every upTo in [0,{3 * DL + 2}] ({time.time() - t0:.1f}s)")


# ---------------- int4BitDotProduct / int4DibitDotProduct ----------------

def bitcount_bytes(xs):
    return reduce_add([popcount(x) for x in xs]) if xs else bvv(0, 32)


def scalar_int4bit(q, d, dOff, stripe):
    """DefaultVectorUtilSupport.int4BitDotProductImpl(q, d, dOffset, stripeSize) -> 64-bit"""
    ret = bvv(0, 64)
    for i in range(4):
        sub = bvv(0, 64)
        r = 0
        ub = stripe & -4
        while r < ub:
            qi = z3.Concat(*reversed(q[i * stripe + r:i * stripe + r + 4]))  # VH_NATIVE_INT (LE host)
            di = z3.Concat(*reversed(d[dOff + r:dOff + r + 4]))
            sub = sub + zext(popcount(qi & di), 64)
            r += 4
        while r < stripe:
            sub = sub + zext(popcount(q[i * stripe + r] & d[dOff + r]), 64)
            r += 1
        ret = ret + (sub << i)
    return ret


def vector_int4bit_256(q, d, dOff, stripe):
    sub = [bvv(0, 64)] * 4
    i = 0
    if stripe >= 64:
        limit = java_loop_bound(stripe, 32)
        acc = [[bvv(0, 64)] * 4 for _ in range(4)]
        while i < limit:
            vd = reinterpret(d[dOff + i:dOff + i + 32], 64)
            for s in range(4):
                vq = reinterpret(q[i + s * stripe:i + s * stripe + 32], 64)
                acc[s] = [a + zext(popcount(x & y), 64) for a, x, y in zip(acc[s], vq, vd)]
            i += 32
        sub = [sub[s] + reduce_add(acc[s]) for s in range(4)]
    if stripe - i >= 16:
        limit = java_loop_bound(stripe, 16)
        acc = [[bvv(0, 64)] * 2 for _ in range(4)]
        while i < limit:
            vd = reinterpret(d[dOff + i:dOff + i + 16], 64)
            for s in range(4):
                vq = reinterpret(q[i + s * stripe:i + s * stripe + 16], 64)
                acc[s] = [a + zext(popcount(x & y), 64) for a, x, y in zip(acc[s], vq, vd)]
            i += 16
        sub = [sub[s] + reduce_add(acc[s]) for s in range(4)]
    while i < stripe:
        for s in range(4):
            sub[s] = sub[s] + zext(popcount(q[i + s * stripe] & d[dOff + i]), 64)
        i += 1
    return sub[0] + (sub[1] << 1) + (sub[2] << 2) + (sub[3] << 3)


def vector_int4bit_128(q, d, dOff, stripe):
    acc = [[bvv(0, 32)] * 4 for _ in range(4)]
    i = 0
    limit = java_loop_bound(stripe, 16)
    while i < limit:
        vd = reinterpret(d[dOff + i:dOff + i + 16], 32)
        for s in range(4):
            vq = reinterpret(q[i + s * stripe:i + s * stripe + 16], 32)
            acc[s] = [a + popcount(x & y) for a, x, y in zip(acc[s], vd, vq)]
        i += 16
    sub = [sext(reduce_add(acc[s]), 64) for s in range(4)]  # IntVector reduce -> int, then long += (sign-extends)
    while i < stripe:
        for s in range(4):
            sub[s] = sub[s] + zext(popcount(d[dOff + i] & q[i + s * stripe]), 64)
        i += 1
    return sub[0] + (sub[1] << 1) + (sub[2] << 2) + (sub[3] << 3)


def check_int4bit():
    for VB in (128, 256):
        t0 = time.time()
        ok = True
        lens = list(range(0, 70)) + [95, 96, 97, 127, 128, 129, 143]
        for dl in lens:
            q = sym("q", 4 * dl, 8)
            d = sym("d", dl, 8)
            sc = scalar_int4bit(q, d, 0, dl)
            if dl >= 16:
                vc = vector_int4bit_256(q, d, 0, dl) if VB >= 256 else vector_int4bit_128(q, d, 0, dl)
            else:
                vc = sc
            r = cvc5_check([vc != sc], f"int4bit_VB{VB}_d{dl}")
            if r != "unsat":
                ok = False
                log(f"FAIL int4BitDotProduct VB={VB} d.length={dl}: {r}")
                break
        if ok:
            log(f"PROVED int4BitDotProduct VB={VB}: all byte contents, every d.length in {lens[0]}..{lens[-1]} ({len(lens)} sizes; cvc5 int-blasting) ({time.time() - t0:.1f}s)")
        # dibit: two stripes of d, same helper
        ok = True
        for dl in [2 * k for k in range(0, 40)] + [64, 65, 66, 96, 97, 130]:
            q = sym("q", 2 * dl, 8)
            d = sym("d", dl, 8)
            st = dl // 2
            sc = scalar_int4bit(q, d, 0, st) + (scalar_int4bit(q, d, st, st) << 1)
            if st >= 16:
                f = vector_int4bit_256 if VB >= 256 else vector_int4bit_128
                vc = f(q, d, 0, st) + (f(q, d, st, st) << 1)
            else:
                vc = sc
            r = cvc5_check([vc != sc], f"int4dibit_VB{VB}_d{dl}")
            if r != "unsat":
                ok = False
                log(f"FAIL int4DibitDotProduct VB={VB} d.length={dl}: {r}")
                break
        if ok:
            log(f"PROVED int4DibitDotProduct VB={VB}: all byte contents, d.length in 0..130 (even sizes and odd 65/97) ({time.time() - t0:.1f}s)")


# ---------------- DocValuesRangeSupport ----------------

def or_mask(words, startBit, mask, maskLen):
    """FixedBitSet.orMask on a list of BV64 words (startBit concrete)."""
    w = list(words)
    wi, bo = startBit >> 6, startBit & 63
    w[wi] = w[wi] | (mask << bo) if bo else w[wi] | mask
    if bo + maskLen > 64:
        w[wi + 1] = w[wi + 1] | z3.LShR(mask, 64 - bo)
    return w


def set_bit(words, idx, cond):
    w = list(words)
    w[idx >> 6] = z3.If(cond, w[idx >> 6] | bvv(1 << (idx & 63), 64), w[idx >> 6])
    return w


def check_dv_range():
    for LL in [int(x) for x in os.environ.get("DV_LANES", "2,4,8").split(",")]:
        t0 = time.time()
        ok, cnt = True, 0
        for (frm, to, off) in [(f, t, o) for f in range(0, 5) for t in range(f, f + 3 * LL + 3) for o in (0, f, max(0, f - 1))]:
            vals = sym("v", to, 64)
            lo, hi = z3.BitVecs("lo hi", 64)
            nwords = (to - off + 64) // 64 + 1
            words0 = sym("w", nwords, 64)
            sw = list(words0)
            for d in range(frm, to):
                sw = set_bit(sw, d - off, z3.And(vals[d] >= lo, vals[d] <= hi))
            vw = list(words0)
            loopBound = frm + java_loop_bound(to - frm, LL)
            d = frm
            while d < loopBound:
                bits = bvv(0, 64)
                for i in range(LL):
                    bits = bits | z3.If(z3.And(vals[d + i] >= lo, vals[d + i] <= hi), bvv(1 << i, 64), bvv(0, 64))
                vw = [z3.If(bits != 0, x, y) for x, y in zip(or_mask(vw, d - off, bits, LL), vw)]
                d += LL
            for d in range(loopBound, to):
                vw = set_bit(vw, d - off, z3.And(vals[d] >= lo, vals[d] <= hi))
            cnt += 1
            r = prove("dvrange", z3.And(*[x == y for x, y in zip(sw, vw)]))
            if r != z3.unsat:
                ok = False
                log(f"FAIL rangeIntoBitSet LONG lanes={LL} from={frm} to={to} off={off}: {r}")
                break
        if ok:
            log(f"PROVED DocValuesRangeSupport.rangeIntoBitSet LONG lanes={LL}: identical bitset words (any initial bits, any values/bounds) for {cnt} (from,to,offset) shapes ({time.time() - t0:.1f}s)")
        # sorted numeric, fixed cardinality
        ok, cnt = True, 0
        for card in range(1, 10):
            dpv = LL // card
            for (frm, to, off) in [(f, t, o) for f in (0, 1, 3) for t in range(f, f + 2 * max(dpv, 1) + 3) for o in (0, f)]:
                vals = sym("v", to * card, VW)
                lo, hi = z3.BitVecs("lo hi", VW)
                sortedness = [vals[d * card + i] <= vals[d * card + i + 1] for d in range(to) for i in range(card - 1)]
                nwords = (to - off + 64) // 64 + 1
                words0 = sym("w", nwords, 64)

                def scalar_from(words, start):
                    w = list(words)
                    for doc in range(start, to):
                        # break at the first value >= lo; set iff that value <= hi
                        cond = z3.BoolVal(False)
                        for i in reversed(range(card)):
                            v = vals[doc * card + i]
                            cond = z3.If(v >= lo, v <= hi, cond)
                        w = set_bit(w, doc - off, cond)
                    return w
                sw = scalar_from(words0, frm)
                if dpv == 0 or LL % card != 0:
                    vw = scalar_from(words0, frm)
                else:
                    vw = list(words0)
                    end = frm + (to - frm) // dpv * dpv
                    doc = frm
                    while doc < end:
                        lane_in = [z3.And(vals[doc * card + l] >= lo, vals[doc * card + l] <= hi) for l in range(LL)]
                        docmask = bvv(0, 64)
                        for dd in range(dpv):
                            anyv = z3.Or(*lane_in[dd * card:(dd + 1) * card])
                            docmask = docmask | z3.If(anyv, bvv(1 << dd, 64), bvv(0, 64))
                        vw = [z3.If(docmask != 0, x, y) for x, y in zip(or_mask(vw, doc - off, docmask, dpv), vw)]
                        doc += dpv
                    vw = scalar_from(vw, doc) if doc < to else vw
                    # scalar_from restarted at `doc` must only touch docs >= doc: rebuild properly
                    w = list(vw)
                cnt += 1
                r = prove("dvsorted", z3.And(*[x == y for x, y in zip(sw, vw)]), sortedness)
                if r != z3.unsat:
                    ok = False
                    log(f"FAIL sortedNumericRangeIntoBitSet LONG lanes={LL} card={card} from={frm} to={to} off={off}: {r}")
                    break
            if not ok:
                break
        if ok:
            log(f"PROVED DocValuesRangeSupport.sortedNumericRangeIntoBitSet LONG lanes={LL}: cardinality 1..9, {cnt} shapes, under per-doc sortedness (value width {VW} bits) ({time.time() - t0:.1f}s)")
        # precondition: per-doc sortedness is required (e.g. encoded 64-bit values are NOT signed-sorted)
        card = LL if LL >= 2 else 2
        to, frm, off = 1, 0, 0
        vals = sym("v", to * card, 64)
        lo, hi = z3.BitVecs("lo hi", 64)
        s = z3.Solver()
        cond = z3.BoolVal(False)
        for i in reversed(range(card)):
            v = vals[i]
            cond = z3.If(v >= lo, v <= hi, cond)
        anyv = z3.Or(*[z3.And(v >= lo, v <= hi) for v in vals])
        s.add(cond != anyv)
        if s.check() == z3.sat:
            m = s.model()
            log(f"  precondition needed (lanes={LL}, card={card}): unsorted doc values {[m.eval(v).as_signed_long() for v in vals]} "
                f"lo={m.eval(lo).as_signed_long()} hi={m.eval(hi).as_signed_long()}: scalar early-break != SIMD any-in-range")


if __name__ == "__main__":
    which = sys.argv[1].split(",") if len(sys.argv) > 1 else ["geq", "expand8", "split", "filter", "bit", "dv"]
    if "geq" in which: check_find_next_geq()
    if "expand8" in which: check_expand8()
    if "split" in which: check_split()
    if "filter" in which: check_filter()
    if "bit" in which: check_int4bit()
    if "dv" in which: check_dv_range()
    if "geq8" in which: check_find_next_geq(Ls=(8, 16))
