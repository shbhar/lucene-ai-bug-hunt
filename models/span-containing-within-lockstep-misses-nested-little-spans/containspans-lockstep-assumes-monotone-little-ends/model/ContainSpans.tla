---------------------------- MODULE ContainSpans ----------------------------
(***************************************************************************)
(* Model of the per-document lockstep of SpanContainingQuery and           *)
(* SpanWithinQuery (Lucene main a9779ef3c193) over two abstract Spans:     *)
(* "big" and "little", each a list of [start, end) spans sorted by start   *)
(* and then by end, as the Spans class javadoc requires                    *)
(* (lucene/queries/.../spans/Spans.java:25-28). End positions need NOT be  *)
(* monotone in start order (SpanOr of a long and a short clause).          *)
(*                                                                         *)
(* Mapping model element -> Java (lucene/queries/src/java/org/apache/      *)
(* lucene/queries/spans/):                                                 *)
(*   St(seq,i), En(seq,i)   Spans.startPosition()/endPosition(): -1 before *)
(*                          the first nextStartPosition (Spans.java:42-54),*)
(*                          NoMore after the last                          *)
(*   bi / li                position of bigSpans / littleSpans             *)
(*   Mode = "containing"    SpanContainingQuery.java:94-132                *)
(*     pc "outer"           :97 / :119 while (bigSpans.nextStartPosition() *)
(*                          != NO_MORE_POSITIONS)                          *)
(*     pc "inner"           :98-103 / :120-125 while (littleSpans.start <  *)
(*                          bigSpans.start) littleSpans.nextStartPosition()*)
(*     pc "check"           :104 / :126 if (big.end >= little.end) match   *)
(*   Mode = "within"        SpanWithinQuery.java:93-131                    *)
(*     pc "outer"           :96 / :118 while (littleSpans.nextStartPosition*)
(*                          () != NO_MORE_POSITIONS)                       *)
(*     pc "inner"           :97-102 / :119-124 while (big.end < little.end)*)
(*                          bigSpans.nextStartPosition()                   *)
(*     pc "check"           :103 / :125 if (big.start <= little.start)     *)
(*   emitted                the spans the ContainSpans returns for the doc *)
(*                          (twoPhaseCurrentDocMatches + nextStartPosition *)
(*                          share one loop body; the first emitted span is *)
(*                          the doc-level match, atFirstInCurrentDoc)      *)
(*   Fixed = TRUE           the fix sketch: containing scans every buffered*)
(*                          little span that starts at or after big.start; *)
(*                          within keeps maxEnd = max end of the big spans *)
(*                          that start at or before little.start           *)
(* Reference (javadoc SpanContainingQuery.java:33, SpanWithinQuery.java:32)*)
(*   RefContaining = {b in big : E l in little: b.s <= l.s /\ l.e <= b.e}  *)
(*   RefWithin     = {l in little : E b in big:  b.s <= l.s /\ l.e <= b.e} *)
(***************************************************************************)
EXTENDS Naturals, Integers, Sequences, FiniteSets

CONSTANTS N,        \* positions 0..N-1 in the document
          MaxLen,   \* at most MaxLen spans per Spans
          Mode,     \* "containing" or "within"
          Fixed,    \* FALSE = code under test, TRUE = fix sketch
          FixMutant \* TRUE = vacuity mutant of the fix sketch (strict comparisons)

NoMore == 1000     \* Spans.NO_MORE_POSITIONS (Integer.MAX_VALUE)

Span == {sp \in [s : 0..(N-1), e : 1..N] : sp.s < sp.e}
Less(x, y) == x.s < y.s \/ (x.s = y.s /\ x.e < y.e)
\* strictly sorted (start, end) lists of 1..MaxLen spans
SortedSeqs == UNION { {sq \in [1..k -> Span] :
                         \A i \in 1..(k-1) : Less(sq[i], sq[i+1])} : k \in 1..MaxLen }

St(sq, i) == IF i = 0 THEN -1 ELSE IF i > Len(sq) THEN NoMore ELSE sq[i].s
En(sq, i) == IF i = 0 THEN -1 ELSE IF i > Len(sq) THEN NoMore ELSE sq[i].e

VARIABLES big, little, bi, li, pc, emitted, maxEnd

vars == <<big, little, bi, li, pc, emitted, maxEnd>>

Contains(b, l) == b.s <= l.s /\ l.e <= b.e
RefContaining == {big[i] : i \in {i \in 1..Len(big) : \E j \in 1..Len(little) : Contains(big[i], little[j])}}
RefWithin     == {little[j] : j \in {j \in 1..Len(little) : \E i \in 1..Len(big) : Contains(big[i], little[j])}}
Ref == IF Mode = "containing" THEN RefContaining ELSE RefWithin

Init == /\ big \in SortedSeqs
        /\ little \in SortedSeqs
        /\ bi = 0 /\ li = 0
        /\ pc = "outer"
        /\ emitted = {}
        /\ maxEnd = -1

Done == pc = "done" /\ UNCHANGED vars

(* ---------------- code under test: SpanContainingQuery ---------------- *)
COuter == /\ pc = "outer"
          /\ bi' = bi + 1
          /\ pc' = IF bi + 1 > Len(big) THEN "done" ELSE "inner"
          /\ UNCHANGED <<big, little, li, emitted, maxEnd>>
CInner == /\ pc = "inner"
          /\ IF St(little, li) < St(big, bi)
               THEN /\ li' = li + 1
                    /\ pc' = IF li + 1 > Len(little) THEN "done" ELSE "inner"
               ELSE /\ pc' = "check" /\ UNCHANGED li
          /\ UNCHANGED <<big, little, bi, emitted, maxEnd>>
CCheck == /\ pc = "check"
          /\ IF En(big, bi) >= En(little, li)
               THEN emitted' = emitted \cup {big[bi]}
               ELSE UNCHANGED emitted
          /\ pc' = "outer"
          /\ UNCHANGED <<big, little, bi, li, maxEnd>>

(* ---------------- code under test: SpanWithinQuery ---------------- *)
WOuter == /\ pc = "outer"
          /\ li' = li + 1
          /\ pc' = IF li + 1 > Len(little) THEN "done" ELSE "inner"
          /\ UNCHANGED <<big, little, bi, emitted, maxEnd>>
WInner == /\ pc = "inner"
          /\ IF En(big, bi) < En(little, li)
               THEN /\ bi' = bi + 1
                    /\ pc' = IF bi + 1 > Len(big) THEN "done" ELSE "inner"
               ELSE /\ pc' = "check" /\ UNCHANGED bi
          /\ UNCHANGED <<big, little, li, emitted, maxEnd>>
WCheck == /\ pc = "check"
          /\ IF St(big, bi) <= St(little, li)
               THEN emitted' = emitted \cup {little[li]}
               ELSE UNCHANGED emitted
          /\ pc' = "outer"
          /\ UNCHANGED <<big, little, bi, li, maxEnd>>

(* ---------------- fix sketch ---------------- *)
\* containing: little spans of the doc are buffered (random access); li is the
\* first buffered little span with start >= big.start (monotone, never rewinds)
FCOuter == /\ pc = "outer"
           /\ bi' = bi + 1
           /\ pc' = IF bi + 1 > Len(big) THEN "done" ELSE "inner"
           /\ UNCHANGED <<big, little, li, emitted, maxEnd>>
FCInner == /\ pc = "inner"
           /\ IF li = 0 \/ (li <= Len(little) /\ St(little, li) < St(big, bi))
                THEN /\ li' = li + 1 /\ pc' = "inner"
                ELSE /\ pc' = "check" /\ UNCHANGED li
           /\ UNCHANGED <<big, little, bi, emitted, maxEnd>>
FCCheck == /\ pc = "check"
           /\ IF \E j \in li..Len(little) : IF FixMutant THEN little[j].e < En(big, bi)
                                              ELSE little[j].e <= En(big, bi)   \* suffix-min of ends
                THEN emitted' = emitted \cup {big[bi]}
                ELSE UNCHANGED emitted
           /\ pc' = "outer"
           /\ UNCHANGED <<big, little, bi, li, maxEnd>>
\* within: streaming, no buffer: maxEnd over big spans with start <= little.start
FWOuter == /\ pc = "outer"
           /\ li' = li + 1
           /\ pc' = IF li + 1 > Len(little) THEN "done" ELSE "inner"
           /\ UNCHANGED <<big, little, bi, emitted, maxEnd>>
FWInner == /\ pc = "inner"
           /\ IF St(big, bi) <= St(little, li)
                THEN /\ maxEnd' = IF bi = 0 THEN maxEnd
                                  ELSE IF En(big, bi) > maxEnd THEN En(big, bi) ELSE maxEnd
                     /\ bi' = bi + 1      \* bigSpans.nextStartPosition(); NoMore start ends the loop
                     /\ pc' = "inner"
                ELSE /\ pc' = "check" /\ UNCHANGED <<bi, maxEnd>>
           /\ UNCHANGED <<big, little, li, emitted>>
FWCheck == /\ pc = "check"
           /\ IF (IF FixMutant THEN maxEnd > En(little, li) ELSE maxEnd >= En(little, li))
                THEN emitted' = emitted \cup {little[li]}
                ELSE UNCHANGED emitted
           /\ pc' = "outer"
           /\ UNCHANGED <<big, little, bi, li, maxEnd>>

Next == \/ Done
        \/ (~Fixed /\ Mode = "containing" /\ (COuter \/ CInner \/ CCheck))
        \/ (~Fixed /\ Mode = "within"     /\ (WOuter \/ WInner \/ WCheck))
        \/ (Fixed  /\ Mode = "containing" /\ (FCOuter \/ FCInner \/ FCCheck))
        \/ (Fixed  /\ Mode = "within"     /\ (FWOuter \/ FWInner \/ FWCheck))

Spec == Init /\ [][Next]_vars /\ WF_vars(Next)

(* ---------------- properties ---------------- *)
Sound            == emitted \subseteq Ref                               \* no false positive (invariant)
DocComplete      == pc = "done" => ((emitted = {}) = (Ref = {}))       \* hit set (invariant)
SpanComplete     == pc = "done" => emitted = Ref                        \* span set (invariant)
Terminates       == <>(pc = "done")

\* Characterisation: little span ends are monotone in start order
LittleEndsMonotone == \A i, j \in 1..Len(little) : i < j => little[i].e <= little[j].e
\* "complete whenever the little ends are monotone" (no counterexample expected for the code under test)
SpanCompleteIfMonotone == (pc = "done" /\ LittleEndsMonotone) => emitted = Ref
=============================================================================
