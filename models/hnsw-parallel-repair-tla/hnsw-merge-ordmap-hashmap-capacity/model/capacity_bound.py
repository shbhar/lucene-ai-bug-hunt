# Mirrors org.apache.lucene.internal.hppc.HashContainers.minBufferSize (Lucene main @ ea57360912ed,
# HashContainers.java:81-99) with IEEE double arithmetic (Python float == Java double):
#   long length = (long) Math.ceil(elements / loadFactor); if (length == elements) length++;
#   length = max(MIN_HASH_ARRAY_LENGTH, nextHighestPowerOfTwo(length));
#   if (length > MAX_HASH_ARRAY_LENGTH /* 0x80000000 >>> 1 = 2^30 */) throw BufferAllocationException
# with DEFAULT_LOAD_FACTOR = 0.75f (HashContainers.java:33), i.e. (double) 0.75f = 0.75 exactly.
# Finds the smallest expectedElements that throws, by binary search over [0, 2^31 - 1].
import math

MAX_HASH_ARRAY_LENGTH = 0x80000000 >> 1
MIN_HASH_ARRAY_LENGTH = 4
LOAD = 0.75

def next_pow2(x):
    return 1 if x <= 1 else 1 << (x - 1).bit_length()

def throws(elements):
    length = math.ceil(elements / LOAD)
    if length == elements:
        length += 1
    length = max(MIN_HASH_ARRAY_LENGTH, next_pow2(length))
    return length > MAX_HASH_ARRAY_LENGTH

lo, hi = 0, 2**31 - 1
assert not throws(lo) and throws(hi)
while lo + 1 < hi:
    mid = (lo + hi) // 2
    if throws(mid):
        hi = mid
    else:
        lo = mid
print(f"largest expectedElements that fits: {lo:,}")
print(f"smallest expectedElements that throws: {hi:,}  (= 0.75 * 2^30 + 1: {hi == 3 * 2**28 + 1})")
print(f"IndexWriter.MAX_DOCS = {2**31 - 129:,}; fraction of MAX_DOCS = {hi / (2**31 - 129):.3f}")
