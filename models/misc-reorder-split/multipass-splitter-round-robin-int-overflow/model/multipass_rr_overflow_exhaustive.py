"""
Exhaustive check of the round-robin part test of MultiPassIndexSplitter.split (MultiPassIndexSplitter.java:103-107)
in exact Java int arithmetic, over every doc id where j + numParts - i can overflow.
For j + numParts <= Integer.MAX_VALUE the expression equals the mathematical one and j lands in part j % numParts
only (identity), so only j > Integer.MAX_VALUE - numParts needs checking; j < maxDoc <= MAX_DOCS = 2^31 - 129.
Reports, per numParts, the docs that land in 0 or >= 2 parts.
"""
import sys
INT_MAX = 2**31 - 1
MAX_DOCS = 2**31 - 129

def wrap(x):
    x &= 0xFFFFFFFF
    return x - 2**32 if x >= 2**31 else x

def jrem(a, b):  # Java % (truncated, sign of dividend)
    r = abs(a) % abs(b)
    return -r if a < 0 else r

first_bad = None
summary = []
for n in range(2, int(sys.argv[1]) if len(sys.argv) > 1 else 1025):
    lost = dup = 0
    example = None
    lo = max(0, INT_MAX - n + 1)
    for j in range(lo, MAX_DOCS):  # j <= MAX_DOCS - 1
        parts = [i for i in range(n) if jrem(wrap(j + n - i), n) == 0]
        if len(parts) != 1:
            if len(parts) == 0: lost += 1
            else: dup += 1
            if example is None: example = (j, parts[:5])
    if lost or dup:
        if first_bad is None: first_bad = n
        summary.append(f"numParts={n}: lost={lost} dup={dup} first=(doc {example[0]}, parts {example[1]})")
print(f"checked numParts 2..{(int(sys.argv[1]) if len(sys.argv) > 1 else 1025) - 1}; smallest numParts with a lost/duplicated doc: {first_bad}")
for s in summary[:5]: print("  " + s)
print(f"  ... {len(summary)} numParts values affected in total")
