--------------------------- MODULE PsdpRetention ---------------------------
(***************************************************************************)
(* PersistentSnapshotDeletionPolicy (PSDP) over SnapshotDeletionPolicy     *)
(* (SDP): reference counts of snapshotted commits, the gen -> IndexCommit   *)
(* map, the save files snapshots_N, one injected I/O fault per run, and a   *)
(* policy restart. The policy methods are synchronized, so one call is one  *)
(* atomic step here; the fault decides how persist() ends.                  *)
(*                                                                         *)
(* Model element -> Java (lucene/core/src/java/org/apache/lucene/index/,   *)
(* main a9779ef3c19; PR = open PR #16482 head 67ced02):                    *)
(*   held[g]          SDP.refCounts                       SDP.java:44      *)
(*   map[g]           generation of SDP.indexCommits[g]   SDP.java:47      *)
(*                    incRef stores lastCommit            SDP.java:120     *)
(*                    (PR: stores ic)                                      *)
(*   latest           SDP.lastCommit                      SDP.java:66,79   *)
(*   disk             IndexFileDeleter.commits after      IFD.java:237,571 *)
(*                    KeepOnlyLastCommit + SnapshotCommitPoint.delete      *)
(*                                                        SDP.java:206-213 *)
(*   files, nwg       snapshots_N files, nextWriteGen     PSDP.java:161-184*)
(*   Snapshot         PSDP.snapshot (rollback release)    PSDP.java:113-126*)
(*   ReleaseIC        PSDP.release(IndexCommit)           PSDP.java:135-147*)
(*                    (rollback incRef(commit))                            *)
(*   ReleaseGen       PSDP.release(long), no rollback     PSDP.java:156-159*)
(*                    (PR: rollback incRef(ic) if ic != null)              *)
(*   persist fault    createOutput / writeVInt / close    PSDP.java:163-173*)
(*                    caught: delete snapshots_N, rethrow                  *)
(*                    sync: not caught, file stays        PSDP.java:175    *)
(*                    delete old file: ignored            PSDP.java:177-181*)
(*                    createOutput on an existing name throws              *)
(*                    FileAlreadyExistsException, caught, file deleted     *)
(*   Restart          new PSDP(CREATE_OR_APPEND) + IndexWriter onInit      *)
(*                    loadPriorSnapshots (newest file)    PSDP.java:211-264*)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS MaxGen, MaxRef, MaxSteps, PR, FaultKinds

Gens == 1..MaxGen
NoFault == "none"
Zero == [g \in Gens |-> 0]

VARIABLES latest, disk, held, map, files, nwg, faultsLeft, steps,
          outcome, lastFault, preHeld, preDurable, lastOp

vars == <<latest, disk, held, map, files, nwg, faultsLeft, steps,
          outcome, lastFault, preHeld, preDurable, lastOp>>

(* files: set of [n |-> save-file generation, c |-> refcount function, ok |-> complete] *)
FileGens == {f.n : f \in files}
Newest == IF files = {} THEN [n |-> 0, c |-> Zero, ok |-> TRUE]
          ELSE CHOOSE f \in files : \A h \in files : h.n <= f.n
Durable == Newest.c

Inc(h, g) == [h EXCEPT ![g] = @ + 1]
Dec(h, g) == [h EXCEPT ![g] = @ - 1]

(* persist(newHeld) under fault k: returns [ok, files, nwg] *)
Persist(newHeld, k) ==
  LET name == nwg
      exists == name \in FileGens
      newFile == [n |-> name, c |-> newHeld, ok |-> TRUE]
      oldGone == {f \in files : f.n # name - 1}
  IN  IF exists
        THEN [ok |-> FALSE, fs |-> {f \in files : f.n # name}, w |-> nwg]   \* FileAlreadyExists, caught, deleted
      ELSE IF k \in {"create", "write", "close"}
        THEN [ok |-> FALSE, fs |-> files, w |-> nwg]                       \* caught, partial file deleted
      ELSE IF k = "sync"
        THEN [ok |-> FALSE, fs |-> files \cup {newFile}, w |-> nwg]        \* file stays, nwg not advanced
      ELSE IF k = "delold"
        THEN [ok |-> TRUE, fs |-> files \cup {newFile}, w |-> nwg + 1]     \* old file stays
      ELSE [ok |-> TRUE, fs |-> oldGone \cup {newFile}, w |-> nwg + 1]

Init == /\ latest = 1 /\ disk = {1}
        /\ held = Zero /\ map = Zero
        /\ files = {} /\ nwg = 0
        /\ faultsLeft = 1 /\ steps = 0
        /\ outcome = "ok" /\ lastFault = NoFault
        /\ preHeld = Zero /\ preDurable = Zero /\ lastOp = "init"

Faults == IF faultsLeft > 0 THEN FaultKinds \cup {NoFault} ELSE {NoFault}

Ghost(op, k) == /\ preHeld' = held /\ preDurable' = Durable
                /\ lastOp' = op /\ lastFault' = k
                /\ faultsLeft' = IF k = NoFault THEN faultsLeft ELSE faultsLeft - 1
                /\ steps' = steps + 1

Commit == /\ latest < MaxGen
          /\ latest' = latest + 1
          /\ disk' = {latest + 1} \cup {g \in disk : held[g] > 0}
          /\ outcome' = "ok"
          /\ UNCHANGED <<held, map, files, nwg>>
          /\ Ghost("commit", NoFault)

DeleteUnused == /\ disk' = {latest} \cup {g \in disk : held[g] > 0}
                /\ outcome' = "ok"
                /\ UNCHANGED <<latest, held, map, files, nwg>>
                /\ Ghost("deleteUnused", NoFault)

Snapshot(k) ==
  LET g == latest
      h1 == Inc(held, g)
      m1 == IF held[g] = 0 THEN [map EXCEPT ![g] = latest] ELSE map
      r == Persist(h1, k)
  IN /\ held[g] < MaxRef
     /\ files' = r.fs /\ nwg' = r.w
     /\ IF r.ok
          THEN /\ held' = h1 /\ map' = m1 /\ outcome' = "ok"
          ELSE \* rollback: super.release(ic) -> releaseGen
               /\ held' = held
               /\ map' = IF h1[g] = 1 THEN [m1 EXCEPT ![g] = 0] ELSE m1
               /\ outcome' = "failed"
     /\ UNCHANGED <<latest, disk>>
     /\ Ghost("snapshot", k)

ReleaseIC(g, k) ==
  LET h1 == Dec(held, g)
      m1 == IF held[g] = 1 THEN [map EXCEPT ![g] = 0] ELSE map
      r == Persist(h1, k)
  IN /\ held[g] > 0
     /\ files' = r.fs /\ nwg' = r.w
     /\ IF r.ok
          THEN /\ held' = h1 /\ map' = m1 /\ outcome' = "ok"
          ELSE \* rollback: incRef(commit); main stores lastCommit, PR stores the commit
               /\ held' = held
               /\ map' = IF h1[g] = 0 THEN [m1 EXCEPT ![g] = IF PR THEN g ELSE latest] ELSE m1
               /\ outcome' = "failed"
     /\ UNCHANGED <<latest, disk>>
     /\ Ghost("releaseIC", k)

ReleaseGen(g, k) ==
  LET h1 == Dec(held, g)
      m1 == IF held[g] = 1 THEN [map EXCEPT ![g] = 0] ELSE map
      r == Persist(h1, k)
  IN /\ held[g] > 0
     /\ files' = r.fs /\ nwg' = r.w
     /\ IF r.ok
          THEN /\ held' = h1 /\ map' = m1 /\ outcome' = "ok"
          ELSE IF PR /\ map[g] # 0
                 THEN /\ held' = held                     \* PR: incRef(ic) with ic = getIndexCommit(gen)
                      /\ map' = IF h1[g] = 0 THEN [m1 EXCEPT ![g] = map[g]] ELSE m1
                      /\ outcome' = "failed"
                 ELSE /\ held' = h1 /\ map' = m1 /\ outcome' = "failed"   \* main: no rollback
     /\ UNCHANGED <<latest, disk>>
     /\ Ghost("releaseGen", k)

(* close the writer, construct a new PSDP on the same save directory, open a new writer *)
Restart ==
  LET loaded == IF Newest.ok THEN Durable ELSE Zero
  IN /\ files # {}
     /\ held' = loaded
     /\ map' = [g \in Gens |-> IF loaded[g] > 0 /\ g \in disk THEN g ELSE 0]
     /\ disk' = {latest} \cup {g \in disk : loaded[g] > 0}
     /\ nwg' = Newest.n + 1
     /\ files' = {Newest}
     /\ outcome' = "ok"
     /\ UNCHANGED <<latest>>
     /\ Ghost("restart", NoFault)

Next == /\ steps < MaxSteps
        /\ \/ Commit
           \/ DeleteUnused
           \/ \E k \in Faults : Snapshot(k)
           \/ \E g \in Gens, k \in Faults : ReleaseIC(g, k)
           \/ \E g \in Gens, k \in Faults : ReleaseGen(g, k)
           \/ Restart

Spec == Init /\ [][Next]_vars

(***************************** properties **********************************)
(* every held generation maps to the commit of that generation *)
MapOK == \A g \in Gens : held[g] > 0 => map[g] = g
(* a call that throws leaves the reference counts as they were *)
RollbackOK == outcome = "failed" => held = preHeld
(* the newest save file (what a restart loads) equals memory after every call *)
DurableOK == Durable = held
(* a commit that the newest save file lists is still on disk *)
RetentionOK == \A g \in Gens : Durable[g] > 0 => g \in disk
(* a held count always has its commit (no count without commits) *)
HeldOnDisk == \A g \in Gens : held[g] > 0 => g \in disk
(* a call without an injected fault does not fail *)
NoSpuriousFailure == outcome = "failed" => lastFault # NoFault
(* vacuity probes: these must be violated (reachable) *)
VacuityNoFailure == outcome # "failed"
VacuityNoDoubleRef == \A g \in Gens : held[g] < 2
VacuityNoRestartWithHeld == ~(lastOp = "restart" /\ \E g \in Gens : held[g] > 0 /\ g # latest)
=============================================================================
