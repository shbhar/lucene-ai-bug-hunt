# Bounded model check (Z3) of the IndexedDISI jump table: writer (writeBitSet / addJumps /
# flushBlockJumps) against reader (advanceBlock + readBlockHeader) for every pattern of K
# consecutive 65536-doc blocks with symbolic cardinalities, at a symbolic base block id.
#
# File:line map (lucene/core/src/java/org/apache/lucene/codecs/lucene90/IndexedDISI.java @ ea57360912ed):
#   writer loop, one iteration per non-empty block          :210-222
#   addJumps(jumps, offset, index, startBlock, endBlock)    :239-248  (entries [start, end) := (index, offset))
#   NO_MORE_DOCS block + final addJumps                      :230-233
#   flushBlockJumps (blockCount == 2 -> no table)            :253-267
#   block sizes: SPARSE 4 + 2c, DENSE 4 + rank + 8192, ALL 4 :113-136, rank length :145
#   reader advanceBlock (jump if blockIndex >= cur + 2, clamp to count - 1)   :495-510
#   readBlockHeader: block id, index = nextBlockIndex (= entry index - 1 at a jump) :519-524
#
# Model: the base block id b0 is symbolic (so the blocks may sit anywhere below 32767), K blocks
# b0 .. b0+K-1 with cardinality c_i in [0, 65536] (0 = empty, not written), every block before b0
# empty. The writer's jump-table entries are computed exactly as the Java loop does. Property for
# every target block t in [0, b0 + K + 2) with t >= 1 (the jump is taken only for blockIndex >= 1
# from an unpositioned iterator): the entry the reader uses points to the byte offset of the first
# written block whose id is >= t (or the NO_MORE_DOCS block if there is none), and its index is
# the number of docs in the blocks before that block. Entry count fits the short only when
# b0 + K <= 32766; the model takes the count as an int here (O7 in indexeddisi_bv.py covers the
# short).
import sys
import z3

K = int(sys.argv[1]) if len(sys.argv) > 1 else 6


def size_of(c, p):
    rank = z3.If(p == -1, 0, z3.If(p == 7, 1024, z3.If(p == 8, 512, z3.If(p == 9, 256, z3.If(
        p == 10, 128, z3.If(p == 11, 64, z3.If(p == 12, 32, z3.If(p == 13, 16, z3.If(p == 14, 8, 4)))))))))
    return z3.If(c == 0, 0,
                 z3.If(c <= 4095, 4 + 2 * c, z3.If(c == 65536, 4, 4 + rank + 8192)))


def run(K, mutant=None):
    b0 = z3.Int("b0")
    p = z3.Int("p")
    c = [z3.Int(f"c{i}") for i in range(K)]
    pre = [b0 >= 0, b0 + K <= 32767, z3.Or(p == -1, z3.And(p >= 7, p <= 15))]
    pre += [z3.And(ci >= 0, ci <= 65536) for ci in c]

    # writer: offsets/totals before each block, and entries for block ids [0, lastBlock]
    off = [z3.IntVal(0)]
    tot = [z3.IntVal(0)]
    for i in range(K):
        off.append(off[-1] + size_of(c[i], p))
        tot.append(tot[-1] + c[i])
    sentinel_off = off[K]
    total = tot[K]
    any_doc = z3.Or([ci > 0 for ci in c])
    # last non-empty block id + 1 (0 if none)
    last_block = z3.IntVal(0)
    for i in range(K):
        last_block = z3.If(c[i] > 0, b0 + i + 1, last_block)

    # entry(e) for a symbolic block id e: the first written block with id >= e, else the sentinel
    def entry(e):
        idx = total
        o = sentinel_off
        for i in range(K - 1, -1, -1):
            cond = z3.And(c[i] > 0, b0 + i >= e)
            idx = z3.If(cond, tot[i], idx)
            o = z3.If(cond, off[i], o)
        return idx, o

    # Java writer, transcribed: entries [lastBlock_prev, b] := (tot_before_b, off_before_b) for each
    # non-empty b; entry [lastBlock] := (total, sentinel). Evaluate entry e from the loop directly.
    def java_entry(e):
        idx = total
        o = sentinel_off
        lb = z3.IntVal(0)
        defined = z3.BoolVal(False)
        res_idx, res_off = z3.IntVal(-1), z3.IntVal(-1)
        for i in range(K):
            b = b0 + i
            hi = b if mutant == "entry-excludes-own-block" else b + 1
            hit = z3.And(c[i] > 0, e >= lb, e < hi, z3.Not(defined))
            res_idx = z3.If(hit, tot[i], res_idx)
            res_off = z3.If(hit, off[i], res_off)
            defined = z3.Or(defined, hit)
            lb = z3.If(c[i] > 0, b + 1, lb)
        hit = z3.And(e >= lb, e < lb + 1, z3.Not(defined))
        res_idx = z3.If(hit, total, res_idx)
        res_off = z3.If(hit, sentinel_off, res_off)
        defined = z3.Or(defined, hit)
        return defined, res_idx, res_off

    block_count = last_block + 1
    entry_count = z3.If(block_count == 2, 0, block_count)

    t = z3.Int("t")
    in_range = z3.If(t < entry_count, t, entry_count - 1)
    defined, j_idx, j_off = java_entry(in_range)
    want_idx, want_off = entry(t)
    s = z3.Solver()
    s.add(pre)
    s.add(t >= 1, t <= b0 + K + 2, entry_count > 0)
    s.push()
    vac = s.check()
    s.add(z3.Not(z3.And(defined, j_idx == want_idx, j_off == want_off, j_off >= 0)))
    r = s.check()
    m = s.model() if r == z3.sat else None
    return vac, r, m


for k in range(1, K + 1):
    vac, r, m = run(k)
    print(f"jump table K={k}: vacuity={vac} result={r} (unsat = every target block lands on the right block/index)")
    if m is not None:
        print("   counterexample:", m)
vac, r, m = run(K, mutant="entry-excludes-own-block")
print(f"mutant entry-excludes-own-block K={K}: result={r} (must be sat)")
if m is not None:
    print("   model:", m)
