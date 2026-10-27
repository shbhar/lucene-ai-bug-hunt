---------------------------- MODULE PITMerge ----------------------------
(***************************************************************************)
(* Point-in-time (PIT) merges: merge-on-commit and merge-on-getReader,     *)
(* Lucene main @ ea57360912ed, with document content, deletes, doc-values  *)
(* (DV) updates and the commit's sequence-number boundary.                *)
(*                                                                         *)
(* Builds on the segment/merge/PIT skeleton of indexfiledeleter-refcount-tla's IFDRefcount.tla      *)
(* (pit/pitc actions: one IW-monitor block = one atomic action) and on     *)
(* dw-flush-control-deletequeue-tla's DWFlush.tla seqNo-replay oracle (reader/commit content = replay  *)
(* of the ops with seqNo <= the cut).  The delete queue / DWPT layer       *)
(* (dw-flush-control-deletequeue-tla's subject) is abstracted: every op is one frozen packet whose     *)
(* delGen equals its seqNo.  New: per-doc content, packet resolution       *)
(* against segments with merge retry, DV write-back and the merge          *)
(* carry-over of deletes and DV updates (incl. #16570's disk diff).        *)
(*                                                                         *)
(* Model element          -> Java (lucene/core/src/java/.../index/)        *)
(*  PIT (one action)      -> IW.prepareCommitInternal:3806-3866 /          *)
(*                           IW.getReader:583-668: flushAllThreads,        *)
(*                           applyAllDeletesAndUpdates (waitApplyAll),     *)
(*                           writeReaderPool:4117-4144, toCommit=clone     *)
(*                           :3850 / r.getSegmentInfos().clone():633,      *)
(*                           preparePointInTimeMerge:3950-4110 (register,  *)
(*                           initMergeReaders at the PIT :4081-4088)       *)
(*  base                  -> the PIT merge readers (getReaderForMerge,     *)
(*                           ReadersAndUpdates:1093-1107) = toCommit       *)
(*  MergeInit             -> IW.mergeInit:5256-5270, _mergeInit:5310       *)
(*                           writeDocValuesUpdatesForMerge (onlyOnce keeps *)
(*                           the PIT readers, IW.java:4048-4057)           *)
(*  MergeCommit (atomic)  -> IW.commitMerge:4894-5028: onMergeComplete     *)
(*                           FIRST (:4896 -> 4032-4045: origInfo=clone /   *)
(*                           getReader merged reader), carry-over          *)
(*                           commitMergedDeletesAndUpdates:4485-4557,      *)
(*                           carryOverHardDeletes, buildMappedDVUpdates-   *)
(*                           FromDisk:4571-4670 (disk diff at diskDelGen,  *)
(*                           residual at real gens, still-running skipped),*)
(*                           drop if 100% deleted :4946-4958,              *)
(*                           closeMergeReaders -> mergeFinished:3969-4029  *)
(*                           (apply to toCommit unless stop or dropped)    *)
(*  Stop                  -> MergeSpecification.await(timeout):547 then    *)
(*                           synchronized stopAddingMergedSegments:3901    *)
(*                           / finishGetReaderMerge:738-752                *)
(*  Open/Apply/Finish     -> FrozenBufferedUpdates.forceApply: open seg    *)
(*                           states under IW lock (seenSegments), apply    *)
(*                           without IW lock, finish iff mergeFinishedGen  *)
(*                           unchanged (IW.java:6543, 6632) else retry     *)
(*  WriteDV(n)            -> ReadersAndUpdates.writeFieldUpdates:859-1060  *)
(*                           (writes gens <= completedDelGen, newest wins) *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS Docs,      \* stable ids, one doc each, e.g. {1, 2}
          SegOf,     \* [Docs -> {"s1","s2"}] source segment of each doc
          Vals,      \* DV values written by updates (0 = indexed value)
          MaxOps,    \* client operations in total (before and after the PIT)
          NIDX,      \* max packets in flight (1 = sequential indexing)
          FIX,       \* TRUE = LFV-002 fix diskDelGen = min(minResidual-1, completedDelGen)
          MUT        \* "none" | "lateClone" | "lateReaders" | "noCarryDel" | "noRetry"

Src == {"s1", "s2"}
Names == Src \cup {"m"}
Ops == [k : {"del"}, d : Docs, v : {0}] \cup [k : {"dv"}, d : Docs, v : Vals]
NoView == [live |-> {}, dv |-> [x \in Docs |-> 0], ok |-> FALSE]

VARIABLES ops,      \* client ops; op i has seqNo = delGen = i
          pst,      \* packet state: "buf" | "open" | "done"
          pseen,    \* segments a packet has been applied to (seenSegments)
          ptodo,    \* segments opened in the current iteration, not yet applied
          pmg,      \* mergeFinishedGen when the iteration started
          alive,    \* segment names in IW.segmentInfos
          docsIn,   \* docs physically in each segment
          liveMem,  \* current (pending) hard live docs per segment rld
          dvDisk,   \* written DV value per segment and doc
          pend,     \* resolved, not yet written DV updates: <<gen, doc, val>>
          mgen,     \* mergeFinishedGen
          phase,    \* "pre" | "pit" | "over"
          pitSeq,   \* commit seqNo (ops <= pitSeq are in the point in time)
          base,     \* PIT content of the merge readers / toCommit
          mst,      \* merge: "none" | "reg" | "init" | "done" | "dropped"
          stop,     \* stopAddingMergedSegments / stopCollectingMergedReaders
          tc,       \* what toCommit / openingSegmentInfos holds: "src" | "merged"
          orig,     \* origInfo content captured at onMergeComplete
          result    \* the commit point / returned NRT reader content

vars == <<ops, pst, pseen, ptodo, pmg, alive, docsIn, liveMem, dvDisk, pend, mgen,
          phase, pitSeq, base, mst, stop, tc, orig, result>>

Max(S) == CHOOSE x \in S : \A y \in S : y <= x
Min(S) == CHOOSE x \in S : \A y \in S : x <= y
Pk == DOMAIN pst
Running == {i \in Pk : pst[i] # "done"}
Completed == Max({0} \cup {g \in 0..Len(ops) : \A j \in 1..g : pst[j] = "done"})

(* newest-wins DV value of doc d in segment n: highest-gen pending entry, else disk *)
Val(n, d) == LET E == {e \in pend[n] : e[2] = d} IN
             IF E = {} THEN dvDisk[n][d]
             ELSE (CHOOSE e \in E : \A f \in E : f[1] <= e[1])[3]
SegView(n) == [x \in liveMem[n] |-> Val(n, x)]
LiveView == LET L == UNION {liveMem[n] : n \in alive} IN
            [live |-> L, dv |-> [x \in Docs |-> IF x \in L
                                   THEN Val(CHOOSE n \in alive : x \in liveMem[n], x) ELSE 0],
             ok |-> TRUE]

(* seqNo replay oracle *)
RECURSIVE Rep(_)
Rep(n) == IF n = 0 THEN [live |-> Docs, dv |-> [x \in Docs |-> 0], ok |-> TRUE]
          ELSE LET p == Rep(n - 1)
                   o == ops[n] IN
               IF o.k = "del" THEN [p EXCEPT !.live = p.live \ {o.d}]
               ELSE IF o.d \in p.live THEN [p EXCEPT !.dv[o.d] = o.v] ELSE p
Content(v) == [live |-> v.live, dv |-> [x \in Docs |-> IF x \in v.live THEN v.dv[x] ELSE 0]]

Init ==
  /\ ops = <<>> /\ pst = <<>> /\ pseen = <<>> /\ ptodo = <<>> /\ pmg = <<>>
  /\ alive = Src
  /\ docsIn = [n \in Names |-> IF n = "m" THEN {} ELSE {x \in Docs : SegOf[x] = n}]
  /\ liveMem = docsIn
  /\ dvDisk = [n \in Names |-> [x \in Docs |-> 0]]
  /\ pend = [n \in Names |-> {}]
  /\ mgen = 0 /\ phase = "pre" /\ pitSeq = 0 /\ base = NoView
  /\ mst = "none" /\ stop = FALSE /\ tc = "src" /\ orig = NoView /\ result = NoView

(* ---- indexing thread: an op returns its seqNo; its packet is frozen ---- *)
Issue(o) ==
  /\ Len(ops) < MaxOps /\ Cardinality(Running) < NIDX
  /\ ops' = Append(ops, o) /\ pst' = Append(pst, "buf")
  /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<alive, docsIn, liveMem, dvDisk, pend, mgen, phase, pitSeq, base, mst, stop, tc, orig, result>>

ApplyEffect(i, n) ==
  LET o == ops[i] IN
  IF o.d \notin docsIn[n] THEN UNCHANGED <<liveMem, pend>>
  ELSE IF o.k = "del" THEN /\ liveMem' = [liveMem EXCEPT ![n] = @ \ {o.d}] /\ UNCHANGED pend
  ELSE /\ pend' = [pend EXCEPT ![n] = @ \cup {<<i, o.d, o.v>>}] /\ UNCHANGED liveMem

(* ---- FrozenBufferedUpdates.forceApply: open (IW lock), apply (no lock), finish (IW lock) ---- *)
Open(i) ==
  /\ pst[i] = "buf"
  /\ ptodo' = [ptodo EXCEPT ![i] = alive \ pseen[i]] /\ pmg' = [pmg EXCEPT ![i] = mgen]
  /\ pst' = [pst EXCEPT ![i] = "open"]
  /\ UNCHANGED <<ops, pseen, alive, docsIn, liveMem, dvDisk, pend, mgen, phase, pitSeq, base, mst, stop, tc, orig, result>>
Apply(i, n) ==
  /\ pst[i] = "open" /\ n \in ptodo[i]
  /\ ApplyEffect(i, n)      \* may hit a source rld that a merge already dropped (lost there)
  /\ ptodo' = [ptodo EXCEPT ![i] = @ \ {n}] /\ pseen' = [pseen EXCEPT ![i] = @ \cup {n}]
  /\ UNCHANGED <<ops, pst, pmg, alive, docsIn, dvDisk, mgen, phase, pitSeq, base, mst, stop, tc, orig, result>>
Finish(i) ==
  /\ pst[i] = "open" /\ ptodo[i] = {}
  /\ pst' = [pst EXCEPT ![i] = IF pmg[i] = mgen \/ MUT = "noRetry" THEN "done" ELSE "buf"]
  /\ UNCHANGED <<ops, pseen, ptodo, pmg, alive, docsIn, liveMem, dvDisk, pend, mgen, phase, pitSeq, base, mst, stop, tc, orig, result>>

(* newest-wins write of the pending updates with gen <= maxGen *)
WriteSeg(n, maxGen) ==
  LET W == {e \in pend[n] : e[1] <= maxGen} IN
  /\ dvDisk' = [dvDisk EXCEPT ![n] = [x \in Docs |->
        LET E == {e \in W : e[2] = x} IN
        IF E = {} THEN @[x] ELSE (CHOOSE e \in E : \A f \in E : f[1] <= e[1])[3]]]
  /\ pend' = [pend EXCEPT ![n] = @ \ W]
WriteDV(n) ==   \* writeSomeDocValuesUpdates / NRT refresh without PIT merges / commit
  /\ n \in alive /\ \E e \in pend[n] : e[1] <= Completed
  /\ WriteSeg(n, Completed)
  /\ UNCHANGED <<ops, pst, pseen, ptodo, pmg, alive, docsIn, liveMem, mgen, phase, pitSeq, base, mst, stop, tc, orig, result>>

(* ---- commit / getReader: the whole fullFlushLock + IW-lock block ---- *)
PIT ==
  /\ phase = "pre" /\ Running = {}       \* applyAllDeletesAndUpdates = waitApplyAll
  /\ \A n \in Src : \A e \in pend[n] : e[1] <= Completed
  /\ dvDisk' = [n \in Names |-> IF n \in Src THEN [x \in Docs |-> Val(n, x)] ELSE dvDisk[n]]
  /\ pend' = [n \in Names |-> IF n \in Src THEN {} ELSE pend[n]]   \* writeReaderPool
  /\ base' = [live |-> UNION {liveMem[n] : n \in Src},
              dv |-> [x \in Docs |-> Val(SegOf[x], x)], ok |-> TRUE]
  /\ pitSeq' = Len(ops) /\ phase' = "pit"
  /\ mst' = IF UNION {liveMem[n] : n \in Src} = {} THEN "none" ELSE "reg"
  /\ UNCHANGED <<ops, pst, pseen, ptodo, pmg, alive, docsIn, liveMem, mgen, stop, tc, orig, result>>

(* merge thread *)
MergeInit ==
  /\ mst = "reg"
  /\ dvDisk' = [n \in Names |-> IF n \in Src THEN
        [x \in Docs |-> LET E == {e \in pend[n] : e[1] <= Completed /\ e[2] = x} IN
                        IF E = {} THEN dvDisk[n][x]
                        ELSE (CHOOSE e \in E : \A f \in E : f[1] <= e[1])[3]]
        ELSE dvDisk[n]]
  /\ pend' = [n \in Names |-> IF n \in Src THEN {e \in pend[n] : e[1] > Completed} ELSE pend[n]]
  /\ mst' = "init"
  \* lateReaders mutant: merge readers pulled here (regular-merge timing) instead of at the PIT
  /\ base' = IF MUT = "lateReaders"
             THEN [live |-> UNION {liveMem[n] : n \in Src},
                   dv |-> [x \in Docs |-> dvDisk'[SegOf[x]][x]], ok |-> TRUE]
             ELSE base
  /\ UNCHANGED <<ops, pst, pseen, ptodo, pmg, alive, docsIn, liveMem, mgen, phase, pitSeq, stop, tc, orig, result>>

MergeCommit ==
  /\ mst = "init"
  /\ LET merged == base.live
         liveM == IF MUT = "noCarryDel" THEN merged
                  ELSE {x \in merged : x \in liveMem[SegOf[x]]}
         R == {e \in UNION {pend[n] : n \in Src} : e[2] \in merged /\ pst[e[1]] = "done"}
         D1 == {x \in merged : dvDisk[SegOf[x]][x] # base.dv[x]}
         raw == IF R # {} THEN Min({e[1] : e \in R}) - 1 ELSE Completed
         dg == IF FIX THEN Min({raw, Completed}) ELSE raw
         carried == {<<dg, x, dvDisk[SegOf[x]][x]>> : x \in D1} \cup R
         dropped == liveM = {}
         view == [live |-> merged, dv |-> base.dv, ok |-> TRUE]
         lateView == [live |-> liveM,
                      dv |-> [x \in Docs |-> LET E == {e \in carried : e[2] = x} IN
                              IF E = {} THEN base.dv[x]
                              ELSE (CHOOSE e \in E : \A f \in E : f[1] <= e[1])[3]], ok |-> TRUE]
     IN /\ orig' = IF MUT = "lateClone" THEN lateView ELSE view   \* onMergeComplete first
        /\ docsIn' = [docsIn EXCEPT !["m"] = merged]
        /\ liveMem' = [liveMem EXCEPT !["m"] = liveM]
        /\ dvDisk' = [dvDisk EXCEPT !["m"] = base.dv]
        /\ pend' = [pend EXCEPT !["m"] = carried]
        /\ alive' = IF dropped THEN {} ELSE {"m"}
        /\ mst' = IF dropped THEN "dropped" ELSE "done"
        /\ tc' = IF stop = FALSE /\ dropped = FALSE THEN "merged" ELSE tc
  /\ mgen' = mgen + 1
  /\ UNCHANGED <<ops, pst, pseen, ptodo, pmg, phase, pitSeq, base, stop, result>>

(* committing / refreshing thread: await (done or timeout), then stop under the IW lock *)
Stop ==
  /\ phase = "pit" /\ stop = FALSE
  /\ stop' = TRUE /\ phase' = "over"
  /\ result' = IF tc = "merged" THEN orig ELSE base
  /\ UNCHANGED <<ops, pst, pseen, ptodo, pmg, alive, docsIn, liveMem, dvDisk, pend, mgen, pitSeq, base, mst, tc, orig>>

Next ==
  \/ \E o \in Ops : Issue(o)
  \/ \E i \in Pk : Open(i) \/ Finish(i) \/ \E n \in Names : Apply(i, n)
  \/ \E n \in Names : WriteDV(n)
  \/ PIT \/ MergeInit \/ MergeCommit \/ Stop

Spec == Init /\ [][Next]_vars

(* ---------------- invariants ---------------- *)
TypeOK == /\ phase \in {"pre", "pit", "over"} /\ tc \in {"src", "merged"}
          /\ mst \in {"none", "reg", "init", "done", "dropped"}
(* CommitExact / NrtExact: the commit point (merge-on-commit) or the returned reader
   (merge-on-getReader) is exactly the replay of the ops with seqNo <= its cut *)
CommitExact == result.ok => Content(result) = Content(Rep(pitSeq))
(* the merged segment is never reported together with its sources *)
NoDoubleSegment == alive \in {Src, {"m"}, {}}
(* live writer after quiescence: every op, also those that arrived while the PIT merge ran *)
Quiet == Len(ops) = MaxOps /\ Running = {} /\ mst \notin {"reg", "init"}
NoLostOp == Quiet => Content(LiveView) = Content(Rep(Len(ops)))
(* the commit/refresh wait ends: Stop is always enabled while waiting (bounded await) *)
WaiterReleased == phase = "pit" => ENABLED Stop
(* vacuity probes: these must be violated *)
ProbeMergedApplied == ~(result.ok /\ tc = "merged" /\ Len(ops) > pitSeq)
ProbeCarry == ~(mst = "done" /\ pend["m"] # {})
=============================================================================
