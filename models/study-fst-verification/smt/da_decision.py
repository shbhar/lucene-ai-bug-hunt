"""
SMT (Z3, QF_BVFP) model of FSTCompiler's fixed-length-arc encoding decision, transcribed
from lucene/core/src/java/org/apache/lucene/util/fst/FSTCompiler.java @ 7cecf79523:

  addNode():                       lines ~519-537
    int labelRange = arcs[n-1].label - arcs[0].label + 1;          // int32
    boolean continuousLabel = labelRange == numArcs;
    if (continuousLabel && version >= VERSION_CONTINUOUS_ARCS) -> CONTINUOUS
    else if (shouldExpandNodeWithDirectAddressing(...))           -> DIRECT_ADDRESSING
    else                                                         -> BINARY_SEARCH
  shouldExpandNodeWithDirectAddressing():  lines ~603-633
    int sizeForBinarySearch = numBytesPerArc * numArcs;
    int sizeForDirectAddressing = getNumPresenceBytes(labelRange) + numLabelBytesPerArc[0]
                                  + maxBytesPerArcWithoutLabel * numArcs;
    int allowedOversize = (int) (sizeForBinarySearch * factor);        // float multiply, f2i cast
    int expansionCost = sizeForDirectAddressing - allowedOversize;
    DA iff expansionCost <= 0 ||
           (credit >= expansionCost && sizeForDirectAddressing <= allowedOversize * 1.66f)
  FST.getNumPresenceBytes(int labelRange) = (labelRange + 7) >> 3        // FST.java ~609
  writeNodeForDirectAddressingOrContinuous(): numPresenceBytes = getNumPresenceBytes(labelRange)
     used as an array offset (bufferOffset = 11 + numPresenceBytes + totalArcBytes).

Java semantics encoded exactly: 32-bit two's complement wrap-around for + - *, arithmetic >>,
IEEE-754 binary32 multiply (RNE) for int*float (int converted to float with RNE), and the JLS
5.1.3 narrowing f2i (NaN -> 0, saturate at Integer.MIN/MAX, else round toward zero).

Queries:
  Q1  (bug search)  exists a legal node (labels are non-negative ints, BYTE4 input) such that the
      writer selects DIRECT_ADDRESSING while the computed presence-byte count is not the
      mathematical ceil(range/8) (i.e. a 32-bit overflow corrupted the size computation).
  Q2  (characterisation) the overflow happens  <=>  last - first >= 2^31 - 8.
  Q3  (assurance) for BYTE1/BYTE2 label domains (labels <= 65535) the int arithmetic never
      overflows: sizeForDirectAddressing equals its mathematical (unbounded) value, for all
      numArcs <= 65536 and bytes-per-arc <= 2^12, and any float factor.
  Q4  (assurance) for every legal BYTE4 node with last - first < 2^31 - 8, when DA is chosen the
      32-bit sizes equal the mathematical ones (no wrap-around anywhere in the decision).
"""
import sys
import time
from z3 import (BitVec, BitVecVal, BitVecSort, FP, Float32, RNE, RTZ, fpToSBV, fpSignedToFP,
                fpIsNaN, fpGEQ, fpLEQ, fpLT, fpMul, FPVal, If, And, Or, Not, Solver, Int, BV2Int,
                ULT, sat, unsat, Extract, SignExt, ZeroExt, BoolVal, fpToReal, ToInt)

BV32 = BitVecSort(32)
F32 = Float32()


def i2f(x):
    """Java int -> float conversion (JLS 5.1.2, round to nearest)."""
    return fpSignedToFP(RNE(), x, F32)


def f2i(f):
    """Java float -> int narrowing (JLS 5.1.3)."""
    maxf = FPVal(2147483647.0, F32)  # rounds to 2^31
    minf = FPVal(-2147483648.0, F32)
    return If(fpIsNaN(f), BitVecVal(0, 32),
              If(fpGEQ(f, maxf), BitVecVal(0x7FFFFFFF, 32),
                 If(fpLEQ(f, minf), BitVecVal(-2**31, 32),
                    fpToSBV(RTZ(), f, BV32))))


def model(s, wide_labels=True):
    first = BitVec('first', 32)
    last = BitVec('last', 32)
    numArcs = BitVec('numArcs', 32)
    maxBytesPerArc = BitVec('maxBytesPerArc', 32)
    maxBytesNoLabel = BitVec('maxBytesPerArcWithoutLabel', 32)
    firstLabelBytes = BitVec('numLabelBytesPerArc0', 32)
    credit = BitVec('credit', 64)
    factor = FP('factor', F32)
    labelMax = 2**31 - 1 if wide_labels else 65535
    # writer-side invariants (FSTCompiler.addNode): labels sorted, non-negative (writeLabel assert),
    # numArcs >= 2 distinct labels in [first, last], first-pass arc sizes positive and bounded
    s.add(first >= 0, last > first, last <= labelMax)
    s.add(numArcs >= 2, numArcs <= 65536)
    # numArcs distinct labels must fit in the range (mathematical, no overflow: 64-bit)
    s.add(ZeroExt(32, numArcs) <= ZeroExt(32, last) - ZeroExt(32, first) + 1)
    s.add(firstLabelBytes >= 1, firstLabelBytes <= 5)  # vInt label (BYTE4) or 1/2 bytes
    s.add(maxBytesNoLabel >= 1, maxBytesNoLabel <= 4096)
    s.add(maxBytesPerArc > maxBytesNoLabel, maxBytesPerArc <= maxBytesNoLabel + 5)
    labelRange = last - first + 1  # int32 wrap
    presence = (labelRange + 7) >> 3  # arithmetic shift
    sizeBS = maxBytesPerArc * numArcs
    sizeDA = presence + firstLabelBytes + maxBytesNoLabel * numArcs
    allowed = f2i(fpMul(RNE(), i2f(sizeBS), factor))
    cost = sizeDA - allowed
    allowed166 = fpMul(RNE(), i2f(allowed), FPVal(1.66, F32))
    da = Or(cost <= 0, And(credit >= SignExt(32, cost), fpLEQ(i2f(sizeDA), allowed166)))
    continuous = labelRange == numArcs
    choose_da = And(Not(continuous), da)
    # mathematical values (64-bit, no wrap for these bounds)
    R64 = ZeroExt(32, last) - ZeroExt(32, first) + 1
    presence_math = (R64 + 7) / 8  # unsigned div on non-negative
    from z3 import UDiv
    presence_math = UDiv(R64 + 7, 8)
    sizeDA_math = presence_math + ZeroExt(32, firstLabelBytes) + ZeroExt(32, maxBytesNoLabel) * ZeroExt(32, numArcs)
    return dict(first=first, last=last, numArcs=numArcs, factor=factor, credit=credit,
                labelRange=labelRange, presence=presence, sizeDA=sizeDA, sizeBS=sizeBS,
                choose_da=choose_da, presence_math=presence_math, sizeDA_math=sizeDA_math,
                R64=R64, maxBytesPerArc=maxBytesPerArc, maxBytesNoLabel=maxBytesNoLabel,
                firstLabelBytes=firstLabelBytes)


def run(name, build, expect):
    s = Solver()
    m = build(s)
    t = time.time()
    r = s.check()
    dt = time.time() - t
    print(f"{name}: {r} (expected {expect}) in {dt:.2f}s")
    if r == sat:
        mdl = s.model()
        def v(x):
            val = mdl.eval(x, model_completion=True)
            try:
                return val.as_signed_long()
            except Exception:
                return val
        print("   cex:", {k: v(m[k]) for k in ['first', 'last', 'numArcs', 'labelRange', 'presence',
                                                'presence_math', 'sizeDA', 'maxBytesPerArc',
                                                'maxBytesNoLabel', 'firstLabelBytes', 'factor']})
    return r


def q1(s):
    m = model(s)
    # default factor 1.0f, credit 0 (first node)
    s.add(m['factor'] == FPVal(1.0, F32), m['credit'] == 0)
    s.add(m['choose_da'])
    s.add(SignExt(32, m['presence']) != m['presence_math'])
    # smallest realistic node: 5 arcs (root fan-out threshold), 1-byte payloads
    s.add(m['numArcs'] == 5)
    return m


def q2(s):
    # overflow of the presence computation  <=>  last - first >= 2^31 - 8  (any factor/credit)
    m = model(s)
    overflow = SignExt(32, m['presence']) != m['presence_math']
    cond = ZeroExt(32, m['last']) - ZeroExt(32, m['first']) >= 2**31 - 8
    s.add(overflow != cond)
    return m


def q2b(s):
    # and whenever it overflows, DA is chosen for every factor in [-inf, +inf] that is not NaN and
    # credit >= 0 (i.e. the writer always takes the corrupt branch)
    m = model(s)
    overflow = SignExt(32, m['presence']) != m['presence_math']
    s.add(overflow, Not(fpIsNaN(m['factor'])), m['credit'] >= 0)
    # realistic node sizes; factor anywhere in [-1, 100] (the javadoc's "-1 disables DA" included)
    s.add(m['numArcs'] <= 4096, m['maxBytesNoLabel'] <= 64)
    s.add(fpGEQ(m['factor'], FPVal(-1.0, F32)), fpLEQ(m["factor"], FPVal(100.0, F32)))
    s.add(Not(m['choose_da']))
    return m


def q3(s):
    m = model(s, wide_labels=False)
    s.add(m['choose_da'])
    s.add(Or(SignExt(32, m['presence']) != m['presence_math'],
             SignExt(32, m['sizeDA']) != m['sizeDA_math']))
    return m


def q4(s):
    m = model(s)
    s.add(ZeroExt(32, m['last']) - ZeroExt(32, m['first']) < 2**31 - 8)
    s.add(m['choose_da'])
    s.add(Or(SignExt(32, m['presence']) != m['presence_math'],
             SignExt(32, m['sizeDA']) != m['sizeDA_math']))
    return m


if __name__ == '__main__':
    t0 = time.time()
    run("Q1 DA chosen with corrupt presence-byte count (factor=1, credit=0, 5 arcs)", q1, "sat")
    run("Q2 overflow <=> last-first >= 2^31-8 (negation)", q2, "unsat")
    run("Q2b overflow => DA chosen (negation; numArcs<=4096, bytes/arc<=64, factor in [-1,100], credit>=0)", q2b, "unsat")
    run("Q3 BYTE1/BYTE2 labels: DA size arithmetic exact (negation)", q3, "unsat")
    run("Q4 BYTE4, range < 2^31-8: DA size arithmetic exact (negation)", q4, "unsat")
    print(f"total {time.time()-t0:.1f}s")
