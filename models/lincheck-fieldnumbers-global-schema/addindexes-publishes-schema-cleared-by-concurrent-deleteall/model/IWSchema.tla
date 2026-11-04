------------------------------- MODULE IWSchema -------------------------------
(***************************************************************************)
(* lincheck-fieldnumbers-global-schema: the writer-wide field schema (FieldInfos.FieldNumbers) as it is    *)
(* reached by concurrent IndexWriter operations, and its reset by deleteAll *)
(*                                                                          *)
(* Model element -> Java (Lucene main a9779ef3c193, lucene/core/src/java/    *)
(* org/apache/lucene/index/):                                               *)
(*   global            FieldNumbers.fieldProperties (FieldInfos.java:371)   *)
(*   GlobalAccepts     FieldNumbers.addOrGet -> verifySameSchema            *)
(*                     (FieldInfos.java:411-455, 509-551)                   *)
(*   segs              IndexWriter.segmentInfos (published segments)        *)
(*   buf               DWPT in-memory docs (IndexingChain + FieldInfos.     *)
(*                     Builder bound to globalFieldNumberMap, DW.java:124)  *)
(*   DocAdd            IndexingChain.processDocument/initializeFieldInfo     *)
(*                     (IndexingChain.java:583-698, 1372-1413), atomic:     *)
(*                     runs under the DWPT lock that deleteAll's            *)
(*                     lockAndAbortAll takes (DocumentsWriter.java:266-312) *)
(*   Flush             DWPT flush + publish, atomic w.r.t. deleteAll        *)
(*                     (the in-flight flush window of LFV-474 is NOT        *)
(*                     modelled, i.e. assumed fixed)                         *)
(*   AIReg / AIPub     addIndexes(Directory...): addOrGet loop outside any  *)
(*                     IW lock (IndexWriter.java:3178-3183), publication    *)
(*                     under synchronized(this) (IndexWriter.java:3194-3211)*)
(*                     addIndexes(CodecReader...): registration inside the  *)
(*                     merge (SegmentMerger -> FieldInfos.Builder), the     *)
(*                     merge leaves runningMerges, publication under        *)
(*                     synchronized(this) (IndexWriter.java:3305-3360): the *)
(*                     same two-step shape                                   *)
(*   UVerify/UEnqueue  updateDocValues: buildDocValuesUpdate ->             *)
(*                     verifyOrCreateDvOnlyField (IndexWriter.java:2097),   *)
(*                     then docWriter.updateDocValues (IndexWriter.java:    *)
(*                     2073), no lock in between                             *)
(*   Apply             ReadersAndUpdates.writeFieldUpdates: a field missing *)
(*                     from the segment is cloned from the global map,      *)
(*                     "which is guaranteed to exist" (RAU.java:916-929);   *)
(*                     an existing field must have the update's DV type     *)
(*                     (assert, RAU.java:920)                                *)
(*   DeleteAll         IndexWriter.deleteAll (IndexWriter.java:2750-2812):  *)
(*                     fullFlushLock + lockAndAbortAll + synchronized(this):*)
(*                     drops buffered docs, queued updates, segments, and   *)
(*                     globalFieldNumberMap.clear()                         *)
(*                                                                          *)
(* FIX_AI: addIndexes re-registers the incoming schema at publication,     *)
(*         inside synchronized(this) (fails on conflict).                   *)
(* FIX_U : updateDocValues verifies/creates and enqueues atomically w.r.t. *)
(*         deleteAll.                                                       *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets, TLC

CONSTANTS FIX_AI, FIX_U, WITH_AI, WITH_U, WITH_DEL, NODOCS, BG

None == "none"
Fields == {"f", "g"}
Schemas == {"NUM", "SORTED", "STR"}

\* addIndexes brings a segment with f as NUMERIC doc values
IncomingSeg == [f |-> "NUM", g |-> None]
\* documents a thread may add (field -> schema, None = absent)
DocA == [f |-> "SORTED", g |-> None]   \* conflicts with the incoming f
\* the document matched by the update's term; BG = its own g ("none" or "SORTED",
\* the latter conflicting with the update's NUMERIC g)
DocB == [f |-> None, g |-> BG]
Docs == IF NODOCS THEN {} ELSE {"A", "B"}
Sch(d) == IF d = "A" THEN DocA ELSE DocB
\* updateDocValues(term, NumericDocValuesField("g")): a NUMERIC dv-only g
UpdField == "g"

VARIABLES global, segs, buf, pending, pcAI, pcU, pcDel, docsLeft, crashed

vars == <<global, segs, buf, pending, pcAI, pcU, pcDel, docsLeft, crashed>>

Empty == [x \in Fields |-> None]

GlobalAccepts(doc) == \A x \in Fields : doc[x] = None \/ global[x] \in {None, doc[x]}
Register(doc) == [x \in Fields |-> IF doc[x] = None THEN global[x] ELSE doc[x]]

\* schema of a set of documents (all accepted against one global schema)
SchOf(ds) == [x \in Fields |-> IF \E d \in ds : Sch(d)[x] # None
                                 THEN CHOOSE v \in Schemas : \E d \in ds : Sch(d)[x] = v
                                 ELSE None]

\* a published segment: the docs it holds ("inc" = the addIndexes doc) + its FieldInfos
Seg(ds, sch) == [docs |-> ds, sch |-> sch]

Init ==
  /\ global = Empty
  /\ segs = {}
  /\ buf = {}
  /\ pending = FALSE      \* a queued DV update for DocB's term not yet applied to a buffered DocB
  /\ pcAI = IF WITH_AI THEN "start" ELSE "done"
  /\ pcU = IF WITH_U THEN "start" ELSE "done"
  /\ pcDel = IF WITH_DEL THEN "start" ELSE "done"
  /\ docsLeft = Docs
  /\ crashed = FALSE

\* ---- addDocument (atomic under the DWPT lock) ----
DocAdd(d) ==
  /\ d \in docsLeft
  /\ docsLeft' = docsLeft \ {d}
  /\ IF GlobalAccepts(Sch(d))
       THEN /\ global' = Register(Sch(d))
            /\ buf' = buf \cup {d}
       ELSE UNCHANGED <<global, buf>>
  /\ UNCHANGED <<segs, pending, pcAI, pcU, pcDel, crashed>>

\* writeFieldUpdates on a segment with FieldInfos sch: the field is cloned
\* from the global map if the segment lacks it ("guaranteed to exist"), else
\* the segment's field must have the update's DV type.
ApplyOK(sch) ==
  IF sch[UpdField] = None THEN global[UpdField] = "NUM"
  ELSE sch[UpdField] = "NUM"

\* ---- flush of the buffered docs (atomic w.r.t. deleteAll; LFV-474 not modelled) ----
Flush ==
  /\ buf # {}
  /\ LET sch == SchOf(buf) IN
       /\ segs' = segs \cup {Seg(buf, sch)}
       /\ crashed' = (crashed \/ (pending /\ "B" \in buf /\ ~ApplyOK(sch)))
  /\ buf' = {}
  /\ pending' = FALSE
  /\ UNCHANGED <<global, pcAI, pcU, pcDel, docsLeft>>

\* ---- addIndexes: register, then publish ----
AIReg ==
  /\ pcAI = "start"
  /\ IF GlobalAccepts(IncomingSeg)
       THEN /\ global' = Register(IncomingSeg)
            /\ pcAI' = "registered"
       ELSE /\ pcAI' = "failed"
            /\ UNCHANGED global
  /\ UNCHANGED <<segs, buf, pending, pcU, pcDel, docsLeft, crashed>>

AIPub ==
  /\ pcAI = "registered"
  /\ IF FIX_AI
       THEN IF GlobalAccepts(IncomingSeg)
              THEN /\ global' = Register(IncomingSeg)
                   /\ segs' = segs \cup {Seg({"inc"}, IncomingSeg)}
                   /\ pcAI' = "done"
              ELSE /\ pcAI' = "failed"
                   /\ UNCHANGED <<global, segs>>
       ELSE /\ segs' = segs \cup {Seg({"inc"}, IncomingSeg)}
            /\ pcAI' = "done"
            /\ UNCHANGED global
  /\ UNCHANGED <<buf, pending, pcU, pcDel, docsLeft, crashed>>

\* ---- updateDocValues(term matching DocB, g NUMERIC) ----
VerifyOrCreate == global[UpdField] \in {None, "NUM"}

\* the update applies to docs with a smaller seqNo: a buffered DocB gets it at
\* flush; a published DocB segment gets it now (frozen packet)
UVerify ==
  /\ pcU = "start"
  /\ IF VerifyOrCreate
       THEN /\ global' = [global EXCEPT ![UpdField] = "NUM"]
            /\ IF FIX_U
                 THEN /\ pending' = (pending \/ "B" \in buf)
                      /\ crashed' = (crashed \/ \E s \in segs : "B" \in s.docs /\ ~(IF s.sch[UpdField] = None THEN TRUE ELSE s.sch[UpdField] = "NUM"))
                      /\ pcU' = "done"
                 ELSE /\ pcU' = "verified"
                      /\ UNCHANGED <<pending, crashed>>
       ELSE /\ pcU' = "failed"
            /\ UNCHANGED <<global, pending, crashed>>
  /\ UNCHANGED <<segs, buf, pcAI, pcDel, docsLeft>>

UEnqueue ==
  /\ pcU = "verified"
  /\ pending' = (pending \/ "B" \in buf)
  /\ crashed' = (crashed \/ \E s \in segs : "B" \in s.docs /\ ~ApplyOK(s.sch))
  /\ pcU' = "done"
  /\ UNCHANGED <<global, segs, buf, pcAI, pcDel, docsLeft>>

\* ---- deleteAll ----
DeleteAll ==
  /\ pcDel = "start"
  /\ global' = Empty
  /\ segs' = {}
  /\ buf' = {}
  /\ pending' = FALSE
  /\ pcDel' = "done"
  /\ UNCHANGED <<pcAI, pcU, docsLeft, crashed>>

Done == /\ pcAI \in {"done", "failed"} /\ pcU \in {"done", "failed"} /\ pcDel = "done"
        /\ docsLeft = {} /\ buf = {}

\* explicit terminal stutter so deadlock checking stays on and meaningful
Terminated == Done /\ UNCHANGED vars

Next ==
  \/ \E d \in Docs : DocAdd(d)
  \/ Flush
  \/ AIReg \/ AIPub
  \/ UVerify \/ UEnqueue
  \/ DeleteAll
  \/ Terminated

Spec == Init /\ [][Next]_vars

\* ---- properties ----
\* every field of every published segment is registered in the writer-wide
\* schema with the same schema (getFieldNames lists it; later documents are
\* checked against it)
SchemaCovers == \A s \in segs : \A x \in Fields : s.sch[x] # None => global[x] = s.sch[x]
\* no two published segments disagree on a field's schema (else merges fail
\* and a new IndexWriter refuses the index)
SegmentsConsistent == \A s1, s2 \in segs : \A x \in Fields :
                        (s1.sch[x] # None /\ s2.sch[x] # None) => s1.sch[x] = s2.sch[x]
\* applying a queued DV update never meets a missing global field or a field
\* of another DV type
NoApplyFailure == ~crashed

TypeOK == /\ global \in [Fields -> Schemas \cup {None}]
          /\ pcAI \in {"start", "registered", "done", "failed"}
          /\ pcU \in {"start", "verified", "done", "failed"}
          /\ pcDel \in {"start", "done"}
          /\ pending \in BOOLEAN
\* reachability probe (must be violated = reachable): all three operations completed with segments left
ProbeAllDone == ~(pcAI = "done" /\ pcU = "done" /\ pcDel = "done" /\ segs # {})
=============================================================================
