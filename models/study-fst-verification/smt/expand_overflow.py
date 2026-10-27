"""
Z3 (QF_BVFP) model of the fixed-length-arc EXPANSION arithmetic in FSTCompiler.addNode /
writeNodeForBinarySearch / writeNodeForDirectAddressingOrContinuous (main 7cecf79523), all int32:
  sizeForBinarySearch      = maxBytesPerArc * numArcs
  sizeForDirectAddressing  = presence + firstLabelBytes + maxBytesPerArcWithoutLabel * numArcs
  allowedOversize          = (int)(sizeForBinarySearch * factor)
  expansionCost            = sizeForDirectAddressing - allowedOversize
  BS:  destPos = headerLen + numArcs * maxBytesPerArc ; if (destPos > srcPos) expand else skip
  DA/CONT: totalArcBytes = firstLabelBytes + numArcs * maxBytesPerArcWithoutLabel
           bufferOffset  = 11 + presence + totalArcBytes  (array offset)
srcPos = size of the variable-length (first pass) encoding = sum of arc sizes, which is what
actually has to fit in memory.

Q5: factor = -1 (javadoc: "effectively disables direct addressing, only binary search nodes will
    be created") and DA is chosen anyway.
Q6: silent corruption search: BS chosen, -da semantics, destPos <= srcPos so the arcs are NOT
    expanded although the header written claims numArcs fixed-size slots of maxBytesPerArc.
Q7: assurance: if numArcs*maxBytesPerArc + 11 + presence < 2^31 (expanded node fits in an int)
    then none of the size/offset computations wraps (any factor in [-1, 100]).
"""
import time
from z3 import *

F32 = Float32()
def i2f(x): return fpSignedToFP(RNE(), x, F32)
def f2i(f):
    return If(fpIsNaN(f), BitVecVal(0, 32),
           If(fpGEQ(f, FPVal(2147483647.0, F32)), BitVecVal(0x7FFFFFFF, 32),
           If(fpLEQ(f, FPVal(-2147483648.0, F32)), BitVecVal(-2**31, 32),
              fpToSBV(RTZ(), f, BitVecSort(32)))))
def vint_len(x):  # bytes of a vInt for 0 <= x
    return If(ULT(x, 1 << 7), BitVecVal(1, 32), If(ULT(x, 1 << 14), BitVecVal(2, 32),
           If(ULT(x, 1 << 21), BitVecVal(3, 32), If(ULT(x, 1 << 28), BitVecVal(4, 32), BitVecVal(5, 32)))))
Z = lambda x: ZeroExt(32, x)

NUMARCS = [None]
MAXSRC = [1 << 29]
def model(s, byte1=True):
    maxB, labelB, rangeV, srcPos = BitVecs('maxBytesPerArc labelBytes labelRange srcPos', 32)
    numArcs = BitVecVal(NUMARCS[0], 32)
    factor = FP('factor', F32); credit = BitVec('credit', 64)
    s.add(numArcs >= 5, rangeV >= numArcs)
    if byte1:
        s.add(labelB == 1, rangeV <= 256, numArcs <= 255)
    else:
        s.add(labelB >= 1, labelB <= 5, rangeV <= 1 << 20)
    maxNoLabel = maxB - labelB
    s.add(maxNoLabel >= 2, maxB <= 1 << 30)
    # the variable-length node holds one arc of maxB bytes and numArcs-1 arcs of >= 2 bytes
    s.add(srcPos >= 0, maxB >= 0, Z(srcPos) >= Z(maxB) + Z(numArcs - 1) * 2, srcPos <= MAXSRC[0])  # first-pass size bound
    s.add(Z(srcPos) <= Z(numArcs) * Z(maxB))  # every arc is at most maxBytesPerArc
    presence = (rangeV + 7) >> 3
    sizeBS = maxB * numArcs
    sizeDA = presence + labelB + maxNoLabel * numArcs
    allowed = f2i(fpMul(RNE(), i2f(sizeBS), factor))
    cost = sizeDA - allowed
    da = Or(cost <= 0, And(credit >= SignExt(32, cost),
                           fpLEQ(i2f(sizeDA), fpMul(RNE(), i2f(allowed), FPVal(1.66, F32)))))
    cont = rangeV == numArcs
    headerLen = 1 + vint_len(numArcs) + vint_len(maxB)
    destPos = headerLen + numArcs * maxB
    total = labelB + numArcs * maxNoLabel
    bufferOffset = 11 + presence + total
    return dict(numArcs=numArcs, maxB=maxB, rangeV=rangeV, srcPos=srcPos, factor=factor,
                credit=credit, da=da, cont=cont, destPos=destPos, sizeBS=sizeBS, sizeDA=sizeDA,
                bufferOffset=bufferOffset, cost=cost, allowed=allowed, presence=presence, total=total,
                expanded_math=Z(numArcs) * Z(maxB) + 11 + Z(presence))

def show(s, m, keys):
    md = s.model()
    out = {}
    for k in keys:
        v = md.eval(m[k], model_completion=True)
        out[k] = v.as_signed_long() if is_bv_value(v) else v
    print("   cex:", out)

def run(name, f, expect):
    s = Solver(); t = time.time(); m = f(s); r = s.check()
    print(f"{name}: {r} (expected {expect}) {time.time()-t:.2f}s")
    if r == sat: show(s, m, ['numArcs', 'maxB', 'rangeV', 'srcPos', 'factor', 'sizeBS', 'sizeDA', 'allowed', 'cost', 'destPos', 'bufferOffset'])

def q5(s):
    m = model(s); s.add(m['factor'] == FPVal(-1.0, F32), m['credit'] >= 0, Not(m['cont']), m['da']); return m
def q5b(s):  # first-pass node <= 64 MB
    MAXSRC[0] = 1 << 26
    try:
        m = q5(s)
    finally:
        MAXSRC[0] = 1 << 29
    return m
def q6(s):
    m = model(s); s.add(m['credit'] >= 0, Not(fpIsNaN(m['factor'])))
    s.add(Not(m['cont']), Not(m['da']), m['destPos'] <= m['srcPos']); return m
def q6default(s):
    m = q6(s); s.add(m['factor'] == FPVal(1.0, F32), m['credit'] == 0); return m
def q7(s):
    m = model(s, byte1=False)
    s.add(fpGEQ(m['factor'], FPVal(-1.0, F32)), fpLEQ(m['factor'], FPVal(100.0, F32)), m['credit'] >= 0)
    s.add(m['expanded_math'] < 2**31 - 1)
    s.add(Or(SignExt(32, m['destPos']) != Z(m['numArcs']) * Z(m['maxB']) + ZeroExt(32, 1 + vint_len(m['numArcs']) + vint_len(m['maxB'])),
             m['bufferOffset'] < 0, m['sizeBS'] < 0, m['sizeDA'] < 0))
    return m

if __name__ == '__main__':
    import sys
    for n in [5, 10, 64, 200, 255]:
        NUMARCS[0] = n
        print(f"--- numArcs = {n} (BYTE1 labels)")
        run("Q5  factor=-1 yet DIRECT_ADDRESSING chosen (non-contiguous)", q5, "sat")
        run("Q5b same with first-pass node <= 64 MB", q5b, "?")
        run("Q6  BS chosen and arcs silently not expanded (factor in any non-NaN, credit>=0)", q6, "?")
        run("Q6d same with default factor 1, credit 0", q6default, "?")
    for n in [10, 1000, 65536]:
        NUMARCS[0] = n
        print(f"--- numArcs = {n} (BYTE4 labels, range <= 2^20)")
        run("Q7  expanded node < 2^31 => no wrap anywhere (negation)", q7, "unsat")
