------------------------- MODULE FailedCommitDurability -------------------------
(***************************************************************************)
(* One failed commit, then the application's recovery, then a crash.       *)
(* Name-level model in the style of commit-crash-recovery's CommitDurability.tla: file bytes *)
(* are always fsynced before the rename (IndexWriter.startCommit), so only *)
(* directory entries are modelled.                                         *)
(*                                                                         *)
(* State at Init: commit 1 (segments_1 -> File(1)) acknowledged, durable.  *)
(* Commit 2 references File(1), File(2) (a newly flushed segment). With    *)
(* Merge, File(1)+File(2) are merged into File(9) after the commit point   *)
(* was taken (prepareCommit then forceMerge, or a merge that finishes      *)
(* during startCommit's fsync), so only the pending commit and commit 1    *)
(* still reference File(2) / File(1).                                      *)
(*                                                                         *)
(* Model element -> Java (main @ a9779ef3c193)                             *)
(*  "w2"      flush of segment 2: createOutput (DocumentsWriter flush)     *)
(*  "prep2"   SegmentInfos.prepareCommit: dir.syncMetaData() (:970)        *)
(*  "pend2"   SegmentInfos.write: create pending_segments_2 (:605-628)     *)
(*  "merge"   merge commits File(9) (IndexWriter.commitMerge)              *)
(*  "rename2" SegmentInfos.finishCommit: dir.rename (:1005); the fault:    *)
(*            rename_noeffect = throws, no effect; rename_effect = takes   *)
(*            effect, then throws                                          *)
(*  "finsync2" finishCommit: dir.syncMetaData() (:1007); fault finsync     *)
(*  "deldest" finishCommit catch: deleteFilesSuppressingExceptions(dest)   *)
(*            (:1008-1012, LUCENE-9477); on main only for finsync; with    *)
(*            FixRename also for a rename failure                          *)
(*  "rbcommit" finishCommit outer catch -> rollbackCommit: delete          *)
(*            pending_segments_2 (:942-957, :1013-1017)                    *)
(*  "decref"  IndexWriter.finishCommit finalizer deleter.decRef(commitFiles)*)
(*            (:4288); deletes File(2) when Merge (refcount 0)             *)
(*  rollback: IndexWriter.rollbackInternalNoCommit: checkpoint(rollback    *)
(*            infos) deletes File(2), File(9) ("rb1","rb2"), then          *)
(*            deleter.refresh() deletes the unknown segments_2 ("rb3")     *)
(*            (:2655-2668); no syncMetaData                                *)
(*  retry:    a new commit 3 (prepareCommit syncMetaData, pending_3,       *)
(*            rename, syncMetaData), then the deletion policy removes      *)
(*            segments_1 and the files only it used (FileDeleter: segments *)
(*            first); the orphan segments_2 is unknown to IndexFileDeleter *)
(* Crash: any state; durable namespace = Apply(durable, chosen pending)    *)
(*  (Ordered: a prefix in program order; else any subset).                 *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANTS Ordered, Fault, Recovery, Merge, FixRename

Seg(g) == <<"seg", g>>
Pend(g) == <<"pend", g>>
File(x) == <<"file", x>>

Refs(g) == CASE g = 1 -> {File(1)}
             [] g = 2 -> {File(1), File(2)}
             [] g = 3 -> IF Merge THEN {File(9)} ELSE {File(1), File(2)}

VARIABLES live, durable, pending, pc, acked
vars == <<live, durable, pending, pc, acked>>

Op(t, a, b) == [t |-> t, a |-> a, b |-> b]
Do(op) ==
  /\ pending' = Append(pending, op)
  /\ live' = CASE op.t = "create" -> live \cup {op.a}
             [] op.t = "delete" -> live \ {op.a}
             [] op.t = "rename" -> (live \ {op.a}) \cup {op.b}
  /\ UNCHANGED durable
DelIfLive(x) == IF x \in live THEN Do(Op("delete", x, "-")) ELSE UNCHANGED <<live, durable, pending>>
DirSync == /\ durable' = live /\ pending' = <<>> /\ UNCHANGED live
Skip == UNCHANGED <<live, durable, pending>>
Go(p) == pc' = p

Init == /\ live = {File(1), Seg(1)} /\ durable = live /\ pending = <<>> /\ pc = "w2" /\ acked = 1

Next ==
  \/ /\ pc = "w2" /\ Go("prep2") /\ Do(Op("create", File(2), "-")) /\ UNCHANGED acked
  \/ /\ pc = "prep2" /\ Go("pend2") /\ DirSync /\ UNCHANGED acked
  \/ /\ pc = "pend2" /\ Go("merge") /\ Do(Op("create", Pend(2), "-")) /\ UNCHANGED acked
  \/ /\ pc = "merge" /\ Go("rename2") /\ UNCHANGED acked
     /\ IF Merge THEN Do(Op("create", File(9), "-")) ELSE Skip
  \/ /\ pc = "rename2" /\ UNCHANGED acked
     /\ CASE Fault = "rename_noeffect" -> /\ Skip /\ Go(IF FixRename THEN "deldest" ELSE "rbcommit")
          [] Fault = "rename_effect" -> /\ Do(Op("rename", Pend(2), Seg(2)))
                                        /\ Go(IF FixRename THEN "deldest" ELSE "rbcommit")
          [] OTHER -> /\ Do(Op("rename", Pend(2), Seg(2))) /\ Go("finsync2")
  \/ /\ pc = "finsync2"
     /\ IF Fault = "finsync" THEN /\ Skip /\ Go("deldest") /\ UNCHANGED acked
                             ELSE /\ DirSync /\ Go("ok2") /\ acked' = 2
  \/ /\ pc = "deldest" /\ Go("rbcommit") /\ DelIfLive(Seg(2)) /\ UNCHANGED acked
  \/ /\ pc = "rbcommit" /\ Go("decref") /\ DelIfLive(Pend(2)) /\ UNCHANGED acked
  \/ /\ pc = "decref" /\ UNCHANGED acked
     /\ Go(IF Recovery = "rollback" THEN "rb1" ELSE "rt1")
     /\ IF Merge THEN DelIfLive(File(2)) ELSE Skip
  \* rollback(): checkpoint(rollback infos) then refresh()
  \/ /\ pc = "rb1" /\ Go("rb2") /\ DelIfLive(File(2)) /\ UNCHANGED acked
  \/ /\ pc = "rb2" /\ Go("rb3") /\ DelIfLive(File(9)) /\ UNCHANGED acked
  \/ /\ pc = "rb3" /\ Go("done") /\ DelIfLive(Seg(2)) /\ UNCHANGED acked
  \* retry: commit 3, then the deletion policy removes commit 1 (segments first)
  \/ /\ pc = "rt1" /\ Go("rt2") /\ DirSync /\ UNCHANGED acked
  \/ /\ pc = "rt2" /\ Go("rt3") /\ Do(Op("create", Pend(3), "-")) /\ UNCHANGED acked
  \/ /\ pc = "rt3" /\ Go("rt4") /\ Do(Op("rename", Pend(3), Seg(3))) /\ UNCHANGED acked
  \/ /\ pc = "rt4" /\ Go("rt5") /\ DirSync /\ acked' = 3
  \/ /\ pc = "rt5" /\ Go("rt6") /\ DelIfLive(Seg(1)) /\ UNCHANGED acked
  \/ /\ pc = "rt6" /\ Go("done") /\ UNCHANGED acked
     /\ IF Merge THEN DelIfLive(File(1)) ELSE Skip
  \/ /\ pc \in {"ok2", "done"} /\ UNCHANGED vars

Spec == Init /\ [][Next]_vars

RECURSIVE ApplyR(_, _, _)
ApplyR(r, chosen, i) ==
  IF i > Len(pending) THEN r
  ELSE IF r.ok = FALSE \/ i \notin chosen THEN ApplyR(r, chosen, i + 1)
  ELSE LET op == pending[i] IN
       CASE op.t = "create" -> ApplyR([r EXCEPT !.ns = @ \cup {op.a}], chosen, i + 1)
         [] op.t = "delete" -> ApplyR([r EXCEPT !.ns = @ \ {op.a}], chosen, i + 1)
         [] op.t = "rename" -> IF op.a \in r.ns
                                THEN ApplyR([r EXCEPT !.ns = (@ \ {op.a}) \cup {op.b}], chosen, i + 1)
                                ELSE ApplyR([r EXCEPT !.ok = FALSE], chosen, i + 1)

Choices == IF Ordered THEN {1..k : k \in 0..Len(pending)} ELSE SUBSET (1..Len(pending))
CrashStates == { r.ns : r \in { x \in {ApplyR([ok |-> TRUE, ns |-> durable], c, 1) : c \in Choices} : x.ok } }

Gens(ns) == {g \in 1..3 : Seg(g) \in ns}
Max(S) == CHOOSE m \in S : \A x \in S : x <= m

(* no crash: the newest commit in the live directory references only existing files *)
LiveOK == Gens(live) # {} => Refs(Max(Gens(live))) \subseteq live
(* crash: DirectoryReader.open of the newest durable commit finds every referenced file *)
ReaderOK == \A ns \in CrashStates : Gens(ns) # {} => Refs(Max(Gens(ns))) \subseteq ns
(* crash: the newest durable commit is not older than the last acknowledged one *)
NoAckLoss == \A ns \in CrashStates : Gens(ns) # {} /\ Max(Gens(ns)) >= acked
(* vacuity probe: the failure cleanup runs (must be violated whenever Fault # "none") *)
NoCleanup == pc # "decref"
=============================================================================
