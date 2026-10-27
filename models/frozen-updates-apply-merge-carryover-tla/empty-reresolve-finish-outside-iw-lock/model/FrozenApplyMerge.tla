------------------------- MODULE FrozenApplyMerge -------------------------
(***************************************************************************)
(* Frozen delete/update packets applied while regular (background) merges *)
(* run, and the merge carry-over of late changes onto the merged segment.  *)
(* Lucene main @ a9779ef3c193 (the index/ files below are unchanged since  *)
(* ea57360912ed).                                                          *)
(*                                                                         *)
(* Delta over merge-on-commit-refresh-tla's PITMerge.tla (point-in-time merges, no try* ops, two  *)
(* segments): regular merge timing (waitApplyForMerge + _mergeInit, then   *)
(* getReaderForMerge per source, interleavable), a third, non-merging      *)
(* segment that packets also resolve against, soft deletes (a doc-values   *)
(* field whose written values clear live docs), tryUpdateDocValue (fresh   *)
(* gen from the same counter, finished at once) and tryDeleteDocument,     *)
(* keys shared by documents of different segments, dropped merges (maxDoc  *)
(* 0: no carry-over and no mergeFinishedGen bump; 100% deleted after the   *)
(* carry-over), a cascading second merge of the merged segment while its   *)
(* carried updates are still pending, 100%-deleted segment drops, and a    *)
(* failure (exception) in the middle of a packet apply followed by a fresh *)
(* forceApply.                                                             *)
(*                                                                         *)
(* Model element -> Java (lucene/core/src/java/org/apache/lucene/index/)   *)
(*  Issue(o)      -> IW.deleteDocuments/updateDocValues/softUpdate*: the   *)
(*                   op is frozen at once (DW delete queue abstracted, as  *)
(*                   in merge-on-commit-refresh-tla; that layer is dw-flush-control-deletequeue-tla's), BufferedUpdatesStream *)
(*                   .push:67-94 (gen = nextGen++), IW.publishFrozenUpdates*)
(*                   :2928-2947                                            *)
(*  TryUpd(x,v)   -> IW.tryUpdateDocValue:1721-1784 (getNextGen 1729,      *)
(*                   addDVUpdate in place, finishedSegment 1778) via       *)
(*                   tryModifyDocument:1791-1829 (only if segmentInfos     *)
(*                   contains the reader's segment and it is pooled)       *)
(*  TryDel(x)     -> IW.tryDeleteDocument:1688-1705 (drop if 100% deleted) *)
(*  PStart(i)     -> IW.forceApply:6533-6586: mergeGenStart 6543,          *)
(*                   getInfosToApply 6690-6705, openSegmentStates          *)
(*                   6778-6801 (bufferedDeletesGen <= delGen, not seen);   *)
(*                   no segment -> break 6567 -> finished 6661-6664        *)
(*  PApply(i,n)   -> FrozenBufferedUpdates.apply:156-176; term deletes     *)
(*                   444-505; DV updates 178-360 (delGen < segState.delGen *)
(*                   skip 191, refCount()==1 skip 196-201 = model: n not   *)
(*                   pooled, acceptDocs = rld.getLiveDocs() 321, addDVUpdate*)
(*                   355 -> ReadersAndUpdates.addDVUpdate:153-164,         *)
(*                   assertNoDupGen 137-146 = model flag dupGen)           *)
(*  PFail(i)      -> IOException inside updates.apply: IW.forceApply       *)
(*                   6593-6596 (finishApply(false), rethrow); packet stays *)
(*                   in BufferedUpdatesStream.updates, the next            *)
(*                   waitApplyAll/forceApply starts with a new             *)
(*                   seenSegments (non-tragic caller, e.g. IW.getReader    *)
(*                   596 / 638-646)                                        *)
(*  PClose(i)     -> IW.finishApply:6707-6736 (closeSegmentStates, drop    *)
(*                   100% deleted via dropDeletedSegment 1832-1850, which  *)
(*                   keeps merging segments)                               *)
(*  PEmptyFinish  -> IW.forceApply:6661-6664 (finished outside the IW lock after the     *)
(*                   empty-iteration break at 6562-6568)                   *)
(*  PFinish(i)    -> IW.forceApply:6628-6659 (mergeFinishedGen unchanged   *)
(*                   -> BufferedUpdatesStream.finished 152-167, else next  *)
(*                   iteration with the same seenSegments)                 *)
(*  Completed     -> BufferedUpdatesStream.FinishedSegments:310-356        *)
(*                   (contiguous completedDelGen; stillRunning 332-334)    *)
(*  Write(n)      -> ReadersAndUpdates.writeFieldUpdates:859-1043 with     *)
(*                   maxDelGen = completedDelGen (writeSomeDocValuesUpdates*)
(*                   807-870, ReaderPool.release 187-230, writeAllDocValues*)
(*                   Updates 244-258, commit 348-375); newest gen wins per *)
(*                   doc (DocValuesFieldUpdates.mergedIterator 157-168);   *)
(*                   written soft deletes clear live docs                  *)
(*                   (PendingSoftDeletes.onDocValuesUpdate); dvGen of each *)
(*                   written field advances (handleDVUpdates 408)          *)
(*  MReg          -> IW.registerMerge (mergingSegments)                    *)
(*  MInit         -> IW.mergeInit:5256-5270: waitApplyForMerge             *)
(*                   (BufferedUpdatesStream:178-207: packets with delGen <=*)
(*                   max source bufferedDeletesGen) then _mergeInit        *)
(*                   5272-5349: writeDocValuesUpdatesForMerge 5310         *)
(*                   (ReaderPool:265-276)                                  *)
(*  MRead(n)      -> IW.mergeMiddle:5484-5491 -> ReadersAndUpdates         *)
(*                   .getReaderForMerge:1093-1107 (latest live docs incl.  *)
(*                   written soft deletes, on-disk DV, hardLiveDocs)       *)
(*  MCommit       -> IW.commitMerge:4894-5028 (one IW-monitor block):      *)
(*                   maxDoc==0 -> no commitMergedDeletesAndUpdates (4937), *)
(*                   else mergeFinishedGen++ 4488, carryOverHardDeletes    *)
(*                   4518-4523/4834-4891, buildMappedDVUpdatesFromDisk     *)
(*                   4571-4674 (residual = pending not stillRunning        *)
(*                   4585-4596; field updated if dvGen moved or residual   *)
(*                   4597-4612; diskDelGen 4620-4623; disk diff 4721-4751; *)
(*                   residual at real gens 4640-4662), bufferedDeletesGen  *)
(*                   = min source gen 4509/4554, 100% deleted -> drop      *)
(*                   4946-4958; sources leave the pool (closeMergeReaders  *)
(*                   5394-5425: dropChanges + readerPool.drop)             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, Sequences, TLC

CONSTANTS Docs,      \* stable ids, one doc each
          SegOf,     \* [Docs -> {"s1","s2","s3"}] initial segment of each doc
          KeyOf,     \* [Docs -> Keys] the term (id field) of each doc
          Keys,      \* terms used by term-based ops
          Vals,      \* DV values written by updates (0 = indexed value)
          MaxOps,    \* acknowledged client operations in total
          Kinds,     \* subset of {"del","upd","sdel","tupd","tdel"}
          NIDX,      \* max packets in flight (not finished)
          \* (merges run in PlanC order)
          MaxFail,   \* max injected packet-apply failures
          FIX,       \* TRUE = LFV-002 fix: diskDelGen = min(minResidual-1, completedDelGen)
          PlanC,     \* sequence of [src |-> SUBSET Names, dst |-> Names] merges, run in order
          EMPTYATOMIC, \* TRUE = v1 abstraction (empty open finishes atomically) = the fix shape
          MUT        \* mutation for vacuity checks: "none" | "noRetry" | "noCarryDel" |
                     \* "residualKeepsRunning" | "noWaitForMerge"

Init3 == {"s1", "s2", "s3"}
Names == Init3 \cup {"s4", "m", "m2"}     \* s4: flushed during the run (Add / SoftUpd)
Fields == {"v", "s"}             \* "v" = numeric DV field, "s" = soft-deletes field
Plan == PlanC
AddDocs == {x \in Docs : SegOf[x] = "s4"}

OpSet == [k : {"del", "sdel"}, key : Keys, d : {0}, v : {0}]
         \cup [k : {"upd"}, key : Keys, d : {0}, v : Vals]
         \cup [k : {"tupd"}, key : {"-"}, d : Docs, v : Vals]
         \cup [k : {"tdel"}, key : {"-"}, d : Docs, v : {0}]
         \cup [k : {"add", "supd"}, key : {"-"}, d : AddDocs, v : {0}]
PacketKinds == {"del", "upd", "sdel"}

VARIABLES ops,      \* acknowledged client ops, in seqNo order
          ogen,     \* gen of op i (packet delGen, or tryUpdateDocValue gen; 0 for tdel)
          pst,      \* packet state: "na" (try op) | "buf" | "open" | "closed" | "done"
          pseen,    \* seenSegments of the current forceApply call
          ptodo,    \* segments opened in this iteration, not yet applied
          pcur,     \* segments opened in this iteration (for finishApply)
          pmg,      \* mergeFinishedGen when the iteration started
          ng,       \* BufferedUpdatesStream.nextGen
          fin,      \* finished gens (FinishedSegments)
          alive,    \* IW.segmentInfos
          pool,     \* segments with a pooled ReadersAndUpdates
          segGen,   \* SegmentCommitInfo.bufferedDeletesGen
          docsIn,   \* docs physically in each segment
          hl,       \* current hard live docs (PendingDeletes)
          sd,       \* written soft deletes (on-disk soft field present)
          dv,       \* written DV value
          wv,       \* per segment and field: number of DV writes (dvGen)
          pend,     \* resolved, not yet written: <<gen, field, doc, val>>
          mgen,     \* mergeFinishedGen
          mi,       \* index of the current planned merge
          mst,      \* "none" | "reg" | "init"
          mread,    \* sources whose merge reader was pulled
          base,     \* merge-reader snapshot per source
          nfail,    \* injected apply failures so far
          dupGen    \* assertNoDupGen would fire (AssertionError with -ea)

vars == <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, alive, pool, segGen, docsIn,
          hl, sd, dv, wv, pend, mgen, mi, mst, mread, base, nfail, dupGen>>

Max(S) == CHOOSE x \in S : \A y \in S : y <= x
Min(S) == CHOOSE x \in S : \A y \in S : x <= y
Pk == {i \in DOMAIN pst : pst[i] # "na"}
Running == {i \in Pk : pst[i] # "done"}
Completed == Max({c \in 0..(ng - 1) : \A j \in 1..c : j \in fin})
StillRunning(g) == g > Completed /\ g \notin fin
Merging == IF mst = "none" THEN {} ELSE Plan[mi].src
NoBase == [hl |-> {}, sd |-> {}, dv |-> [x \in Docs |-> 0], wv |-> [f \in Fields |-> 0]]
ZeroWv == [f \in Fields |-> 0]

(* visible value / liveness once the pending updates <= Completed are written *)
W(n) == {e \in pend[n] : e[1] <= Completed}
Newest(E) == CHOOSE e \in E : \A f \in E : f[1] <= e[1]
Val(n, x) == LET E == {e \in W(n) : e[2] = "v" /\ e[3] = x} IN
             IF E = {} THEN dv[n][x] ELSE Newest(E)[4]
SoftDel(n, x) == x \in sd[n] \/ \E e \in W(n) : e[2] = "s" /\ e[3] = x
Live(n) == {x \in docsIn[n] : x \in hl[n] /\ ~SoftDel(n, x)}
LiveNow(n) == {x \in docsIn[n] : x \in hl[n] /\ x \notin sd[n]}   \* rld.getLiveDocs()
LiveView == LET L == UNION {Live(n) : n \in alive} IN
            [live |-> L, dv |-> [x \in Docs |-> IF x \in L
                         THEN Val(CHOOSE n \in alive : x \in Live(n), x) ELSE 0]]

(* seqNo replay oracle *)
RECURSIVE Rep(_)
Rep(n) == IF n = 0 THEN [live |-> Docs \ AddDocs, dv |-> [x \in Docs |-> 0]]
          ELSE LET p == Rep(n - 1)
                   o == ops[n]
                   K == {x \in Docs : o.k \in PacketKinds /\ KeyOf[x] = o.key} IN
               CASE o.k \in {"del", "sdel"} -> [p EXCEPT !.live = p.live \ K]
                 [] o.k = "upd" -> [p EXCEPT !.dv = [x \in Docs |->
                                      IF x \in K /\ x \in p.live THEN o.v ELSE p.dv[x]]]
                 [] o.k = "tupd" -> [p EXCEPT !.dv[o.d] = o.v]
                 [] o.k = "tdel" -> [p EXCEPT !.live = p.live \ {o.d}]
                 [] o.k = "add" -> [p EXCEPT !.live = p.live \cup {o.d}, !.dv[o.d] = 0]
                 [] o.k = "supd" -> \* softUpdateDocument(key(d), d): soft-delete, then add
                      [p EXCEPT !.live = (p.live \ {x \in Docs : KeyOf[x] = KeyOf[o.d]})
                                          \cup {o.d}, !.dv[o.d] = 0]
Content(v) == [live |-> v.live, dv |-> [x \in Docs |-> IF x \in v.live THEN v.dv[x] ELSE 0]]

Init ==
  /\ ops = <<>> /\ ogen = <<>> /\ pst = <<>> /\ pseen = <<>> /\ ptodo = <<>>
  /\ pcur = <<>> /\ pmg = <<>>
  /\ ng = 4 /\ fin = {1, 2, 3}
  /\ alive = Init3 /\ pool = Init3
  /\ segGen = [n \in Names |-> CASE n = "s1" -> 1 [] n = "s2" -> 2 [] n = "s3" -> 3
                                    [] OTHER -> 0]
  /\ docsIn = [n \in Names |-> {x \in Docs : SegOf[x] = n}]
  /\ hl = docsIn
  /\ sd = [n \in Names |-> {}]
  /\ dv = [n \in Names |-> [x \in Docs |-> 0]]
  /\ wv = [n \in Names |-> ZeroWv]
  /\ pend = [n \in Names |-> {}]
  /\ mgen = 0 /\ mi = 1 /\ mst = "none" /\ mread = {}
  /\ base = [n \in Names |-> NoBase]
  /\ nfail = 0 /\ dupGen = FALSE

PNoChange == <<pseen, ptodo, pcur, pmg>>

(* drop a 100% deleted segment unless a merge registered it (dropDeletedSegment) *)
DropIfFull(S, hl2) ==
  LET D == {n \in S \cap alive : n \notin Merging
                 /\ {x \in docsIn[n] : x \in hl2[n] /\ x \notin sd[n]} = {}} IN
  /\ alive' = alive \ D
  /\ pool' = pool \ D

(* ---- client threads ---- *)
Issue(o) ==
  /\ Len(ops) < MaxOps /\ o.k \in Kinds /\ o.k \in PacketKinds
  /\ Cardinality(Running) < NIDX
  /\ ops' = Append(ops, o) /\ ogen' = Append(ogen, ng) /\ ng' = ng + 1
  /\ pst' = Append(pst, "buf") /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {})
  /\ pcur' = Append(pcur, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<fin, alive, pool, segGen, docsIn, hl, sd, dv, wv, pend, mgen, mi, mst,
                 mread, base, nfail, dupGen>>

\* the segment holding x in segmentInfos (the NRT reader that the caller uses saw it)
HolderOf(x) == {n \in alive \cap pool : x \in docsIn[n]}

TryUpd(o) ==
  /\ Len(ops) < MaxOps /\ o.k = "tupd" /\ "tupd" \in Kinds
  /\ HolderOf(o.d) # {}
  /\ LET n == CHOOSE m \in HolderOf(o.d) : TRUE IN
     /\ pend' = [pend EXCEPT ![n] = @ \cup {<<ng, "v", o.d, o.v>>}]
  /\ ops' = Append(ops, o) /\ ogen' = Append(ogen, ng) /\ ng' = ng + 1
  /\ fin' = fin \cup {ng}
  /\ pst' = Append(pst, "na") /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {})
  /\ pcur' = Append(pcur, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<alive, pool, segGen, docsIn, hl, sd, dv, wv, mgen, mi, mst, mread, base,
                 nfail, dupGen>>

TryDel(o) ==
  /\ Len(ops) < MaxOps /\ o.k = "tdel" /\ "tdel" \in Kinds
  /\ HolderOf(o.d) # {}
  /\ LET n == CHOOSE m \in HolderOf(o.d) : TRUE
         hl2 == [hl EXCEPT ![n] = @ \ {o.d}] IN
     /\ hl' = hl2
     /\ DropIfFull({n}, hl2)
  /\ ops' = Append(ops, o) /\ ogen' = Append(ogen, 0)
  /\ pst' = Append(pst, "na") /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {})
  /\ pcur' = Append(pcur, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<ng, fin, segGen, docsIn, sd, dv, wv, pend, mgen, mi, mst, mread, base,
                 nfail, dupGen>>

(* addDocument / softUpdateDocument of doc x, then a flush that publishes segment s4 with
   bufferedDeletesGen = a fresh gen (IW.publishFlushedSegment:2953-2992); for supd the
   soft-delete packet of x's key is pushed first (lower gen), so it applies to the older
   segments only (the DWPT already resolved it for its own docs). *)
Add(o) ==
  /\ Len(ops) < MaxOps /\ o.k = "add" /\ "add" \in Kinds /\ "s4" \notin alive
  /\ ~(\E j \in 1..Len(ops) : ops[j].k \in {"add", "supd"})
  /\ ops' = Append(ops, o) /\ ogen' = Append(ogen, ng) /\ ng' = ng + 1
  /\ fin' = fin \cup {ng}
  /\ segGen' = [segGen EXCEPT !["s4"] = ng]
  /\ alive' = alive \cup {"s4"} /\ pool' = pool \cup {"s4"}
  /\ pst' = Append(pst, "na") /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {})
  /\ pcur' = Append(pcur, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<docsIn, hl, sd, dv, wv, pend, mgen, mi, mst, mread, base, nfail, dupGen>>

SoftUpd(o) ==
  /\ Len(ops) < MaxOps /\ o.k = "supd" /\ "supd" \in Kinds /\ "s4" \notin alive
  /\ ~(\E j \in 1..Len(ops) : ops[j].k \in {"add", "supd"})
  /\ Cardinality(Running) < NIDX
  /\ ops' = Append(ops, o) /\ ogen' = Append(ogen, ng) /\ ng' = ng + 2
  /\ fin' = fin \cup {ng + 1}
  /\ segGen' = [segGen EXCEPT !["s4"] = ng + 1]
  /\ alive' = alive \cup {"s4"} /\ pool' = pool \cup {"s4"}
  /\ pst' = Append(pst, "buf") /\ pseen' = Append(pseen, {}) /\ ptodo' = Append(ptodo, {})
  /\ pcur' = Append(pcur, {}) /\ pmg' = Append(pmg, 0)
  /\ UNCHANGED <<docsIn, hl, sd, dv, wv, pend, mgen, mi, mst, mread, base, nfail, dupGen>>

(* ---- packet resolution: IW.forceApply ---- *)
PStart(i) ==
  /\ pst[i] = "buf"
  /\ LET g == ogen[i]
         T == {n \in alive : segGen[n] <= g /\ n \notin pseen[i]} IN
     IF T = {}
     THEN \* "packet matches no segments": break out of the IW-locked block (6562-6568); the
          \* packet is marked finished later, outside the IW lock and without the mergeGen check
          \* (6661-6664) -> PEmptyFinish. (Model fix: v1 made this one atomic step.)
          /\ pst' = [pst EXCEPT ![i] = IF EMPTYATOMIC THEN "done" ELSE "empty"]
          /\ fin' = IF EMPTYATOMIC THEN fin \cup {g} ELSE fin
          /\ UNCHANGED PNoChange
     ELSE /\ pst' = [pst EXCEPT ![i] = "open"]
          /\ ptodo' = [ptodo EXCEPT ![i] = T] /\ pcur' = [pcur EXCEPT ![i] = T]
          /\ pseen' = [pseen EXCEPT ![i] = @ \cup T] /\ pmg' = [pmg EXCEPT ![i] = mgen]
          /\ UNCHANGED fin
  /\ UNCHANGED <<ops, ogen, ng, alive, pool, segGen, docsIn, hl, sd, dv, wv, pend, mgen, mi,
                 mst, mread, base, nfail, dupGen>>

PApply(i, n) ==
  /\ pst[i] = "open" /\ n \in ptodo[i]
  /\ ptodo' = [ptodo EXCEPT ![i] = @ \ {n}]
  /\ LET o == ops[i]
         g == ogen[i]
         key == IF o.k = "supd" THEN KeyOf[o.d] ELSE o.key
         K == {x \in docsIn[n] : KeyOf[x] = key}
         A == K \cap LiveNow(n)
         f == IF o.k = "upd" THEN "v" ELSE "s"
         val == IF o.k = "upd" THEN o.v ELSE 1 IN
     IF n \notin pool THEN UNCHANGED <<hl, pend, dupGen>>      \* refCount()==1: merged away
     ELSE IF o.k = "del"
          THEN /\ hl' = [hl EXCEPT ![n] = @ \ K] /\ UNCHANGED <<pend, dupGen>>
          ELSE IF A = {} THEN UNCHANGED <<hl, pend, dupGen>>
          ELSE /\ dupGen' = (dupGen \/ \E e \in pend[n] : e[1] = g /\ e[2] = f)
               /\ pend' = [pend EXCEPT ![n] = @ \cup {<<g, f, x, val>> : x \in A}]
               /\ UNCHANGED hl
  /\ UNCHANGED <<ops, ogen, pst, pseen, pcur, pmg, ng, fin, alive, pool, segGen, docsIn, sd,
                 dv, wv, mgen, mi, mst, mread, base, nfail>>

PEmptyFinish(i) ==
  /\ pst[i] = "empty"
  /\ pst' = [pst EXCEPT ![i] = "done"] /\ fin' = fin \cup {ogen[i]}
  /\ UNCHANGED <<ops, ogen, pseen, ptodo, pcur, pmg, ng, alive, pool, segGen, docsIn, hl, sd,
                 dv, wv, pend, mgen, mi, mst, mread, base, nfail, dupGen>>

PFail(i) ==
  /\ pst[i] = "open" /\ nfail < MaxFail
  /\ pst' = [pst EXCEPT ![i] = "buf"] /\ pseen' = [pseen EXCEPT ![i] = {}]
  /\ ptodo' = [ptodo EXCEPT ![i] = {}] /\ pcur' = [pcur EXCEPT ![i] = {}]
  /\ nfail' = nfail + 1
  /\ UNCHANGED <<ops, ogen, pmg, ng, fin, alive, pool, segGen, docsIn, hl, sd, dv, wv, pend,
                 mgen, mi, mst, mread, base, dupGen>>

PClose(i) ==
  /\ pst[i] = "open" /\ ptodo[i] = {}
  /\ pst' = [pst EXCEPT ![i] = "closed"]
  /\ DropIfFull(pcur[i], hl)
  /\ UNCHANGED <<ops, ogen, pseen, ptodo, pcur, pmg, ng, fin, segGen, docsIn, hl, sd, dv, wv,
                 pend, mgen, mi, mst, mread, base, nfail, dupGen>>

PFinish(i) ==
  /\ pst[i] = "closed"
  /\ IF pmg[i] = mgen \/ MUT = "noRetry"
     THEN /\ pst' = [pst EXCEPT ![i] = "done"] /\ fin' = fin \cup {ogen[i]}
     ELSE /\ pst' = [pst EXCEPT ![i] = "buf"] /\ UNCHANGED fin
  /\ UNCHANGED <<ops, ogen, pseen, ptodo, pcur, pmg, ng, alive, pool, segGen, docsIn, hl, sd,
                 dv, wv, pend, mgen, mi, mst, mread, base, nfail, dupGen>>

(* ---- DV write-back (newest gen wins; written soft deletes clear live docs) ---- *)
WriteVals(n, Wn) ==
  [x \in Docs |-> LET E == {e \in Wn : e[2] = "v" /\ e[3] = x} IN
                  IF E = {} THEN dv[n][x] ELSE Newest(E)[4]]
WriteSoft(n, Wn) == sd[n] \cup {e[3] : e \in {e2 \in Wn : e2[2] = "s"}}
WriteWv(n, Wn) == [f \in Fields |-> IF \E e \in Wn : e[2] = f THEN wv[n][f] + 1 ELSE wv[n][f]]

Write(n) ==
  /\ n \in pool /\ W(n) # {}
  /\ dv' = [dv EXCEPT ![n] = WriteVals(n, W(n))]
  /\ sd' = [sd EXCEPT ![n] = WriteSoft(n, W(n))]
  /\ wv' = [wv EXCEPT ![n] = WriteWv(n, W(n))]
  /\ pend' = [pend EXCEPT ![n] = @ \ W(n)]
  /\ UNCHANGED <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, alive, pool, segGen, docsIn,
                 hl, mgen, mi, mst, mread, base, nfail, dupGen>>

(* ---- merge thread ---- *)
MReg ==
  /\ mst = "none" /\ mi <= Len(Plan) /\ Plan[mi].src \subseteq alive
  /\ mst' = "reg"
  /\ UNCHANGED <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, alive, pool, segGen, docsIn,
                 hl, sd, dv, wv, pend, mgen, mi, mread, base, nfail, dupGen>>

MInit ==
  /\ mst = "reg"
  /\ LET S == Plan[mi].src
         maxG == Max({segGen[n] : n \in S}) IN
     /\ (MUT = "noWaitForMerge" \/ \A i \in Pk : ogen[i] <= maxG => pst[i] = "done")
     /\ dv' = [n \in Names |-> IF n \in S \cap pool THEN WriteVals(n, W(n)) ELSE dv[n]]
     /\ sd' = [n \in Names |-> IF n \in S \cap pool THEN WriteSoft(n, W(n)) ELSE sd[n]]
     /\ wv' = [n \in Names |-> IF n \in S \cap pool THEN WriteWv(n, W(n)) ELSE wv[n]]
     /\ pend' = [n \in Names |-> IF n \in S \cap pool THEN pend[n] \ W(n) ELSE pend[n]]
  /\ mst' = "init" /\ mread' = {}
  /\ UNCHANGED <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, alive, pool, segGen, docsIn,
                 hl, mgen, mi, base, nfail, dupGen>>

MRead(n) ==
  /\ mst = "init" /\ n \in Plan[mi].src \ mread
  /\ base' = [base EXCEPT ![n] = [hl |-> hl[n], sd |-> sd[n], dv |-> dv[n], wv |-> wv[n]]]
  /\ mread' = mread \cup {n}
  /\ UNCHANGED <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, alive, pool, segGen, docsIn,
                 hl, sd, dv, wv, pend, mgen, mi, mst, nfail, dupGen>>

\* doc-values carry-over from source n for field f onto the merged docs MD
Carry(n, f, MD) ==
  LET R == {e \in pend[n] : e[2] = f /\ (~StillRunning(e[1]) \/ MUT = "residualKeepsRunning")}
      upd == R # {} \/ wv[n][f] # base[n].wv[f]
      raw == IF R # {} THEN Min({e[1] : e \in R}) - 1 ELSE Completed
      dg == IF FIX THEN Min({raw, Completed}) ELSE raw
      D == IF f = "v"
           THEN {<<dg, "v", x, dv[n][x]>> : x \in {y \in MD \cap docsIn[n] : dv[n][y] # base[n].dv[y]}}
           ELSE {<<dg, "s", x, 1>> : x \in MD \cap docsIn[n] \cap sd[n]}
  IN IF upd THEN D \cup {e \in R : e[3] \in MD} ELSE {}

MCommit ==
  /\ mst = "init" /\ mread = Plan[mi].src
  /\ LET S == Plan[mi].src
         dst == Plan[mi].dst
         MD == UNION {{x \in docsIn[n] : x \in base[n].hl /\ x \notin base[n].sd} : n \in S}
         SrcOfX(x) == CHOOSE n \in S : x \in docsIn[n]
         hlM == IF MUT = "noCarryDel" THEN MD ELSE {x \in MD : x \in hl[SrcOfX(x)]}
         carried == UNION {Carry(n, f, MD) : n \in S, f \in Fields}
         allDel == hlM = {}
     IN IF MD = {}
        THEN /\ alive' = alive \ S /\ pool' = pool \ S          \* maxDoc 0: no carry-over
             /\ UNCHANGED <<mgen, segGen, docsIn, hl, dv, pend>>
        ELSE /\ mgen' = mgen + 1
             /\ segGen' = [segGen EXCEPT ![dst] = Min({segGen[n] : n \in S})]
             /\ docsIn' = [docsIn EXCEPT ![dst] = MD]
             /\ hl' = [hl EXCEPT ![dst] = hlM]
             /\ dv' = [dv EXCEPT ![dst] = [x \in Docs |->
                          IF x \in MD THEN base[SrcOfX(x)].dv[x] ELSE 0]]
             /\ pend' = [pend EXCEPT ![dst] = IF allDel THEN {} ELSE carried]
             /\ alive' = (alive \ S) \cup (IF allDel THEN {} ELSE {dst})
             /\ pool' = (pool \ S) \cup (IF allDel THEN {} ELSE {dst})
  /\ mst' = "none" /\ mi' = mi + 1 /\ mread' = {}
  /\ UNCHANGED <<ops, ogen, pst, pseen, ptodo, pcur, pmg, ng, fin, sd, wv, base, nfail, dupGen>>

Next ==
  \/ \E o \in OpSet : Issue(o) \/ TryUpd(o) \/ TryDel(o) \/ Add(o) \/ SoftUpd(o)
  \/ \E i \in Pk : PStart(i) \/ PEmptyFinish(i) \/ PFail(i) \/ PClose(i) \/ PFinish(i)
                   \/ \E n \in Names : PApply(i, n)
  \/ \E n \in Names : Write(n)
  \/ MReg \/ MInit \/ MCommit \/ \E n \in Names : MRead(n)

Spec == Init /\ [][Next]_vars
FairSpec == Spec /\ WF_vars(\E i \in Pk : PStart(i) \/ PEmptyFinish(i) \/ PClose(i) \/ PFinish(i)
                              \/ \E n \in Names : PApply(i, n))

(* ---------------- properties ---------------- *)
TypeOK == /\ mst \in {"none", "reg", "init"} /\ alive \subseteq pool
          /\ \A i \in DOMAIN pst : pst[i] \in {"na", "buf", "open", "closed", "empty", "done"}
          /\ \A n \in Names : \A e \in pend[n] : e[2] \in Fields
(* I2 (exactly once, -ea): no packet gen is added twice to a segment's field list *)
NoDupGen == dupGen = FALSE
(* I1/I3: once every acknowledged packet is resolved (what waitApplyAll gives every NRT
   reader and commit), the visible content equals the seqNo replay of all ops, also while a
   merge runs and after it committed *)
Quiet == Running = {}
NrtExact == Quiet => Content(LiveView) = Content(Rep(Len(ops)))
(* a document is live in at most one segment *)
NoDoubleDoc == \A x \in Docs : Cardinality({n \in alive : x \in Live(n)}) <= 1
(* I4: every packet eventually finishes (under weak fairness of the packet threads) *)
PacketsFinish == <>[](Running = {})
(* vacuity probes: these must be violated *)
ProbeCarryResidual == ~(\E n \in {"m", "m2"} : n \in alive /\ \E e \in pend[n] : e[1] > Completed)
ProbeMerge2 == ~("m2" \in alive)
ProbeDroppedThenMerged == ~("m2" \in alive /\ "m" \notin alive /\ mgen >= 1 /\ docsIn["m"] # {})
ProbeRetry == ~(\E i \in Pk : pst[i] = "buf" /\ pseen[i] # {})
ProbeTryDuringMerge == ~(mst = "init" /\ \E j \in 1..Len(ops) : ops[j].k = "tupd"
                          /\ \E n \in Plan[mi].src : \E e \in pend[n] : e[1] = ogen[j])
=============================================================================
