# Z3 (bit-vector) check of HnswLock's stripe index: lockid = (level*31 + node) % 512 in Java int
# arithmetic (HnswLock.java:39,46,52-54).  Java '%' is srem; a negative lockid would throw
# ArrayIndexOutOfBoundsException at locks[lockid].
from z3 import BitVec, BitVecVal, Solver, And, ULT, SRem, sat
MAX_DOCS = 2147483647 - 128            # IndexWriter.MAX_DOCS
lvl, node = BitVec('level', 32), BitVec('node', 32)
lockid = SRem(lvl * 31 + node, BitVecVal(512, 32))
def check(maxlevel, maxnode, label):
    s = Solver()
    s.add(lvl >= 0, lvl <= maxlevel, node >= 0, node < maxnode, lockid < 0)
    r = s.check()
    print(label, r, s.model() if r == sat else '')
# levels: getRandomGraphLevel = (int)(-ln(u) * 1/ln(M)), u >= 2^-53 -> level <= 36.7/ln(M) (<= 52 for M=2)
check(52, MAX_DOCS, 'any ordinal < MAX_DOCS, level <= 52 : negative stripe index possible?')
check(52, MAX_DOCS - 31 * 52, 'ordinal < MAX_DOCS - 31*52, level <= 52 : negative stripe index possible?')
check(4, MAX_DOCS, 'any ordinal < MAX_DOCS, level <= 4  : negative stripe index possible?')
