-------------------------------- MODULE MABC --------------------------------
(***************************************************************************)
(* MemoryAccountingBitsetCollector + ...CollectorManager driven by          *)
(* IndexSearcher.search(Query, CollectorManager) with arbitrary (custom)    *)
(* slices, including intra-segment partitions, sharing one tracker.         *)
(*                                                                          *)
(* Mapping, model element -> Java (lucene main ea57360912ed):               *)
(*  CreateCollector  IndexSearcher.java:750-772 (firstCollector, then one   *)
(*                   newCollector() per further slice, before any task)     *)
(*                   + MemoryAccountingBitsetCollector.java:44-47           *)
(*                   (ctor: tracker.updateBytes(new FixedBitSet(0).ram))    *)
(*  Visit(s)         IndexSearcher.java:808-815 (partitions of one slice,   *)
(*                   in array order) + searchLeaf :829-865; array order is  *)
(*                   fixed by LeafSlice COMPARATOR :1004-1006, sort :1031   *)
(*                   (Sorted = TRUE). Sorted = FALSE models a driver that   *)
(*                   visits a slice's partitions in another order.          *)
(*     SetNext part  MemoryAccountingBitsetCollector.java:49-62             *)
(*     Grow          FixedBitSet.java:62-83 ensureCapacityInternal and      *)
(*                   ArrayUtil.java oversize (8-byte elements, 64-bit JVM)  *)
(*     Track         CollectorMemoryTracker.java:38-48 (addAndGet, then     *)
(*                   throw if > limit; the allocation already happened)     *)
(*     Collect part  MemoryAccountingBitsetCollector.java:64-74 (assert     *)
(*                   local > highestSetBit; set(local); hi := local)        *)
(*                   collect() touches only per-collector state, so all     *)
(*                   collects of one partition are folded into the Visit    *)
(*                   step (they commute with every other action).           *)
(*  Reduce           MemoryAccountingBitsetCollectorManager.java:57-85      *)
(*     orRange guard FixedBitSet.orRange Objects.checkFromIndexSize         *)
(*  Ram(w)           FixedBitSet.ramBytesUsed() = BASE + sizeOf(long[w]);   *)
(*                   symbolic constants, exact values are irrelevant here.  *)
(*  Bits are a set of local indices (no word model: word-level alignment of *)
(*  orRange is checked exhaustively on the real jar, harness 'kernel').     *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS LeafSizes,  \* maxDoc of each leaf, in docBase order (a sequence)
          K,          \* number of slices returned by slices() (>= 1)
          Limits,     \* tracker limits explored
          Sorted,     \* TRUE: LeafSlice sorts partitions by docBase (stock)
          EA          \* TRUE: -ea (Java assertions enabled)

NL == Len(LeafSizes)
Leaves == 1..NL
RECURSIVE DB(_)
DB(l) == IF l = 1 THEN 0 ELSE DB(l - 1) + LeafSizes[l - 1]
NDocs == IF NL = 0 THEN 0 ELSE DB(NL) + LeafSizes[NL]
Docs == 0..(NDocs - 1)
LeafOf(d) == CHOOSE l \in Leaves : DB(l) <= d /\ d < DB(l) + LeafSizes[l]
Slices == 1..K
MAXINT == 100000          \* stands for Integer.MAX_VALUE (initial minDocBase)
BASE == 24                \* shallow size of FixedBitSet (symbolic)
AHDR == 16                \* long[] header (symbolic)
Ram(w) == BASE + AHDR + 8 * w
Max(a, b) == IF a > b THEN a ELSE b
Min(a, b) == IF a < b THEN a ELSE b
MaxSet(S) == CHOOSE x \in S : \A y \in S : y <= x
MinSet(S) == CHOOSE x \in S : \A y \in S : y >= x
Bits2Words(n) == IF n = 0 THEN 0 ELSE ((n - 1) \div 64) + 1
Oversize(m) == IF m = 0 THEN 0 ELSE m + Max(3, m \div 8)
RECURSIVE SumF(_, _)
SumF(f, S) == IF S = {} THEN 0 ELSE LET x == CHOOSE y \in S : TRUE IN f[x] + SumF(f, S \ {x})

\* A slicing assigns every doc to one slice; per (leaf, slice) the docs form one
\* interval (one partition of that leaf in that slice: IndexSearcher.java:544-575).
Contig(D) == \A x, y \in D : \A z \in x..y : z \in D
ValidSliceOf(f) ==
  \A l \in Leaves, s \in Slices : Contig({d \in Docs : LeafOf(d) = l /\ f[d] = s})
SliceOfs == {f \in [Docs -> Slices] : ValidSliceOf(f)}
VisitedLeaves(f, s) == {l \in Leaves : \E d \in Docs : LeafOf(d) = l /\ f[d] = s}
Perms(S) == {p \in [1..Cardinality(S) -> S] : \A i, j \in DOMAIN p : i # j => p[i] # p[j]}
SortedSeq(S) == CHOOSE p \in Perms(S) : \A i \in 1..(Len(p) - 1) : p[i] < p[i + 1]
OrderChoices(f) ==
  IF Sorted THEN {[s \in Slices |-> SortedSeq(VisitedLeaves(f, s))]}
  ELSE {o \in [Slices -> UNION {Perms(VisitedLeaves(f, t)) : t \in Slices}] :
          \A s \in Slices : o[s] \in Perms(VisitedLeaves(f, s))}

\* Growth of one collector's bitset on SetNext (FixedBitSet.ensureCapacity(bits, len)).
GrowArr(nbits, arr, len) ==
  IF len < nbits THEN <<nbits, arr>>
  ELSE LET nw == Bits2Words(len)
           na == IF nw >= arr THEN Oversize(nw + 1) ELSE arr
       IN <<na * 64, na>>

VARIABLES sliceOf, matches, order, limit,          \* chosen in Init, then fixed
          phase, created, mem, failReason,
          pc, minB, maxE, nbits, arr, bits, hi,
          resBits, resLen, resArr
vars == <<sliceOf, matches, order, limit, phase, created, mem, failReason,
          pc, minB, maxE, nbits, arr, bits, hi, resBits, resLen, resArr>>

\* Interleaving-independent expected total (fold of the growth over each slice's
\* visit sequence + the result bitset), used to check "fails iff over the limit".
RECURSIVE FoldArr(_, _, _, _, _, _)
FoldArr(seq, i, mb, me, nb, ar) ==
  IF i > Len(seq) THEN ar
  ELSE LET l == seq[i]
           nmb == Min(mb, DB(l))
           nme == Max(me, DB(l) + LeafSizes[l])
           g == GrowArr(nb, ar, nme - nmb)
       IN FoldArr(seq, i + 1, nmb, nme, g[1], g[2])
ExpectedLen == IF matches = {} THEN 1 ELSE MaxSet(matches) + 1
ExpectedTotal ==
  SumF([s \in Slices |-> Ram(FoldArr(order[s], 1, MAXINT, 0, 0, 0))], Slices)
    + Ram(Bits2Words(ExpectedLen))

Init ==
  /\ sliceOf \in SliceOfs
  /\ matches \in SUBSET Docs
  /\ order \in OrderChoices(sliceOf)
  /\ limit \in Limits
  /\ phase = "create" /\ created = 0 /\ mem = 0 /\ failReason = "none"
  /\ pc = [s \in Slices |-> 0]
  /\ minB = [s \in Slices |-> MAXINT] /\ maxE = [s \in Slices |-> 0]
  /\ nbits = [s \in Slices |-> 0] /\ arr = [s \in Slices |-> 0]
  /\ bits = [s \in Slices |-> {}] /\ hi = [s \in Slices |-> -1]
  /\ resBits = {} /\ resLen = 0 /\ resArr = 0

Fail(r) == phase' = "failed" /\ failReason' = r

CreateCollector ==
  /\ phase = "create" /\ created < K
  /\ created' = created + 1
  /\ mem' = mem + Ram(0)
  /\ IF mem + Ram(0) > limit THEN Fail("limit")
     ELSE /\ phase' = IF created + 1 = K THEN "run" ELSE "create"
          /\ UNCHANGED failReason
  /\ UNCHANGED <<sliceOf, matches, order, limit, pc, minB, maxE, nbits, arr, bits, hi,
                 resBits, resLen, resArr>>

Visit(s) ==
  /\ phase = "run" /\ pc[s] < Len(order[s])
  /\ LET l == order[s][pc[s] + 1]
         nmb == Min(minB[s], DB(l))
         nme == Max(maxE[s], DB(l) + LeafSizes[l])
         g == GrowArr(nbits[s], arr[s], nme - nmb)
         delta == Ram(g[2]) - Ram(arr[s])
         locs == {d - nmb : d \in {x \in matches : LeafOf(x) = l /\ sliceOf[x] = s}}
         assertFail == locs # {} /\ MinSet(locs) <= hi[s]
         setFail == \E x \in locs : x >= g[1]
     IN /\ mem' = mem + delta
        /\ pc' = [pc EXCEPT ![s] = pc[s] + 1]
        /\ IF mem + delta > limit
             THEN /\ Fail("limit")     \* bitSet not replaced; nothing collected
                  /\ UNCHANGED <<minB, maxE, nbits, arr, bits, hi>>
           ELSE /\ minB' = [minB EXCEPT ![s] = nmb]
                /\ maxE' = [maxE EXCEPT ![s] = nme]
                /\ nbits' = [nbits EXCEPT ![s] = g[1]]
                /\ arr' = [arr EXCEPT ![s] = g[2]]
                /\ bits' = [bits EXCEPT ![s] = bits[s] \cup locs]
                /\ hi' = [hi EXCEPT ![s] = IF locs = {} THEN hi[s] ELSE MaxSet(locs)]
                /\ IF EA /\ assertFail THEN Fail("assert")
                   ELSE IF setFail THEN Fail("oob")
                   ELSE UNCHANGED <<phase, failReason>>
  /\ UNCHANGED <<sliceOf, matches, order, limit, created, resBits, resLen, resArr>>

Reduce ==
  /\ phase = "run" /\ \A s \in Slices : pc[s] = Len(order[s])
  /\ LET Hit == {s \in Slices : hi[s] >= 0}
         size == IF Hit = {} THEN 0
                 ELSE MaxSet({minB[s] + hi[s] + 1 : s \in Hit})
         rl == Max(1, size)
         ra == Bits2Words(rl)
         inBounds == \A s \in Hit : hi[s] + 1 <= nbits[s] /\ minB[s] + hi[s] + 1 <= rl
     IN /\ mem' = mem + Ram(ra)
        /\ resLen' = rl /\ resArr' = ra
        /\ resBits' = UNION {{minB[s] + i : i \in {j \in bits[s] : j <= hi[s]}} : s \in Hit}
        /\ IF mem + Ram(ra) > limit THEN Fail("limit")
           ELSE IF inBounds = FALSE THEN Fail("ioobe")
           ELSE /\ phase' = "done" /\ UNCHANGED failReason
  /\ UNCHANGED <<sliceOf, matches, order, limit, created, pc, minB, maxE, nbits, arr, bits, hi>>

Terminated == phase \in {"done", "failed"} /\ UNCHANGED vars

Next == CreateCollector \/ (\E s \in Slices : Visit(s)) \/ Reduce \/ Terminated
Spec == Init /\ [][Next]_vars

\* ---------------------------------------------------------------- properties
NotStuck == phase \notin {"done", "failed"} =>
              (ENABLED CreateCollector \/ (\E s \in Slices : ENABLED Visit(s)) \/ ENABLED Reduce)
ResultIsUnion   == phase = "done" => resBits = matches
ResultLength    == phase = "done" => resLen = ExpectedLen
AccountingExact == phase = "done" => mem = SumF([s \in Slices |-> Ram(arr[s])], Slices) + Ram(resArr)
WithinLimit     == phase = "done" => mem <= limit
OnlyLimitFails  == phase = "failed" => failReason = "limit"
FailIffOver     == /\ (phase = "failed" /\ failReason = "limit") => ExpectedTotal > limit
                   /\ phase = "done" => ExpectedTotal <= limit
=============================================================================
