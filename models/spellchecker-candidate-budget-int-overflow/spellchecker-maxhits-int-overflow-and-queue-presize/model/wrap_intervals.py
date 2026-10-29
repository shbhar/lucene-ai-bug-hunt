# Exact intervals of numSug in [1, 2^31-1] where the Java int product k * numSug is <= 0, or wraps to a positive
# value smaller than the true product (k = 10: SpellChecker.java:361 maxHits; k = 5: DirectSpellChecker.java:359 with
# the default maxInspections). Pure integer arithmetic (the wrapped value is affine on each interval, so interval
# endpoints are exact); cross-checked point-wise against Java semantics at every endpoint and its neighbours.
MAX = 2**31 - 1
def jint(x):
    x &= 0xFFFFFFFF
    return x - 2**32 if x >= 2**31 else x
def intervals(k):
    out = []; n = 1
    while n <= MAX:
        p = jint(k * n)
        cls = 'exact' if p == k * n else ('nonpositive' if p <= 0 else 'positive-wrapped')
        # jump to the end of the current affine piece: next multiple boundary of 2^31 in k*n
        hi = min(MAX, ((k * n) // 2**31 + 1) * 2**31 // k if ((k * n) // 2**31 + 1) * 2**31 % k else ((k * n) // 2**31 + 1) * 2**31 // k - 1)
        # refine hi so that k*hi stays in the same 2^31 block
        while hi > n and (k * hi) // 2**31 != (k * n) // 2**31: hi -= 1
        while hi < MAX and (k * (hi + 1)) // 2**31 == (k * n) // 2**31: hi += 1
        # within one 2^31 block of k*n the wrapped sign class is constant except where it hits exactly 0
        out.append((n, hi, cls, jint(k * n), jint(k * hi)))
        n = hi + 1
    return out
for k in (10, 5):
    print(f"== k = {k}")
    tot_np = 0
    for (a, b, c, pa, pb) in intervals(k):
        if c == 'nonpositive': tot_np += b - a + 1
        print(f"  numSug {a}..{b}: {c} (wrapped {pa}..{pb})")
        for x in (a - 1, a, b, b + 1):
            if 1 <= x <= MAX:
                p = jint(k * x); assert (p == k * x) or x >= a - 1  # sanity
    print(f"  numSug values with product <= 0: {tot_np} of {MAX}")
