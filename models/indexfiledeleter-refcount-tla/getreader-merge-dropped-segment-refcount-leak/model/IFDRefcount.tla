---------------------------- MODULE IFDRefcount ----------------------------
(***************************************************************************)
(* Reference-counting protocol of IndexFileDeleter (IFD) and its holders  *)
(* in IndexWriter (IW), Lucene main @ ea57360912ed.                       *)
(*                                                                         *)
(* Files are tokens: <<"s", n, 0>> = the base files of segment n,          *)
(* <<"s", n, g>> (g >= 1) = its live-docs file of delete generation g,     *)
(* <<"c", k, 0>> = segments_k.  Each IW monitor block is ONE atomic action *)
(* (every incRef/decRef runs under the IW lock, IFD.locked()).  Steps that *)
(* run without the lock (merge middle, startCommit fsync, the getReader    *)
(* wait for point-in-time merges) are separate actions, so TLC explores    *)
(* every interleaving of writer, merge, reader and getReader threads.      *)
(*                                                                         *)
(* Model element            -> Java (lucene/core/src/java/.../index/)      *)
(*  rc                      -> FileDeleter.refCounts (util/FileDeleter:41) *)
(*  Inc / DecDel            -> FileDeleter.incRef:83 / decRef:95-130       *)
(*  DeleteNew               -> IFD.deleteNewFiles:634, FileDeleter:173-191 *)
(*  Checkpoint              -> IFD.checkpoint(sis,false):544-585           *)
(*  lastFiles               -> IFD.lastFiles:76                            *)
(*  commits / CommitFinish  -> IFD.checkpoint(sis,true):565-574,           *)
(*                             deleteCommits:401-448, IW.finishCommit      *)
(*                             IW.java:4288-4318                           *)
(*  KeepLast + snaps        -> KeepOnlyLastCommitDeletionPolicy wrapped by *)
(*                             SnapshotDeletionPolicy (SDP:66-70,206-214)  *)
(*  pc.ftc / CommitPrepare  -> IW.filesToCommit, IW.java:3851-3857, 3910   *)
(*  readers / NRTOpen       -> SDR.open(writer..):150 incRefDeleter        *)
(*  NRTClose                -> SDR.doClose:540-552 (ACE swallowed when the *)
(*                             writer is closed)                          *)
(*  merges.rfiles           -> getReaderForMerge consumer incRef,          *)
(*                             IW.java:5486-5490 / 4083-4087;              *)
(*                             closeMergeReaders decRef IW.java:5418       *)
(*  MergeCommit             -> IW.commitMerge:4894-5000                    *)
(*  pit (getReader PIT)     -> IW.getReader:536-700, preparePointInTimeMerge*)
(*                             3940-4100 (onMergeComplete accept 4041-4053,*)
(*                             mergeFinished apply 3974-4024),             *)
(*                             finishGetReaderMerge:706-727,               *)
(*                             maybeReopenMergedNRTReader:729-768,         *)
(*                             onGetReaderMergeResources:651-667           *)
(*  CommitPreparePIT/WaitDone -> merge-on-commit, IW.java:3857-3910,       *)
(*                             mergeFinished COMMIT branch 3974-4024       *)
(*  Rollback                -> IW.rollbackInternalNoCommit:2600-2690       *)
(*  Refresh                 -> IFD.refresh:455-486                         *)
(*  DeleteUnused            -> IW.deleteUnusedFiles:6187 / revisitPolicy   *)
(***************************************************************************)
EXTENDS Naturals, Integers, FiniteSets, Sequences

CONSTANTS MaxSeg,      \* number of segment names the writer may allocate
          MaxDelGen,   \* delete generations per segment (0 = no deletes)
          MaxCommits,  \* number of commits
          NReaders,    \* NRT reader slots held by the application
          PIT,         \* TRUE = getReader point-in-time merges (default on main)
          FIX,         \* TRUE = candidate fix: decRef leftover merged readers
          SNAP,        \* TRUE = SnapshotDeletionPolicy snapshot/release
          CONC,        \* FALSE = one thread: other actions wait while a merge runs
          MUT,         \* mutant id (vacuity): "none", "noMergeDecRef", "noNrtIncRef", "staleFtc"
          LOCKCHECK,   \* TRUE = deletes after close are refused by LockValidatingDirectoryWrapper (real code)
          PITC         \* TRUE = commit point-in-time merges (merge-on-commit, default on main)

NoPC == [on |-> FALSE, segs |-> {}, ftc |-> {}, wait |-> FALSE, applied |-> FALSE]
NoPIT == [on |-> FALSE, slot |-> 1, r |-> {}, opening |-> {}, stop |-> TRUE, merged |-> {}]
NoRd == [on |-> FALSE, files |-> {}]
Slots == 1..NReaders
SegFiles(s) == {<<"s", s.n, 0>>} \cup (IF s.g > 0 THEN {<<"s", s.n, s.g>>} ELSE {})
SisFiles(S) == UNION {SegFiles(s) : s \in S}
CFile(k) == <<"c", k, 0>>
CommitFiles(c) == SisFiles(c.segs) \cup {CFile(c.gen)}
Names(S) == {s.n : s \in S}
AllFiles == {<<"s", n, g>> : n \in 1..MaxSeg, g \in 0..MaxDelGen}
            \cup {CFile(k) : k \in 0..MaxCommits}

VARIABLES disk,        \* files that exist in the Directory
          rc,          \* IFD refcounts
          sis,         \* IW.segmentInfos (set of [n, g])
          counter,     \* next segment name
          commits,     \* IFD.commits (set of [gen, segs]); deleted ones removed
          lastFiles,   \* IFD.lastFiles
          rbSegs,      \* IW.rollbackSegments
          gen,         \* next commit generation
          pc,          \* pending commit: NONE or [segs, ftc]
          readers,     \* [Slots -> NONE or set of files incRef'd by that reader]
          merges,      \* set of running merges
          pit,         \* getReader point-in-time state, NONE or a record
          wopen,       \* writer open?
          orphan,      \* files of NRT readers still open when the writer closed
          snaps,       \* snapshotted commit generations
          delAfterClose, \* history: a closed writer deleted a file
          mid          \* merge id counter

vars == <<disk, rc, sis, counter, commits, lastFiles, rbSegs, gen, pc, readers,
          merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

Inc(r, F) == [f \in DOMAIN r |-> IF f \in F THEN r[f] + 1 ELSE r[f]]
Dec(r, F) == [f \in DOMAIN r |-> IF f \in F THEN r[f] - 1 ELSE r[f]]
Zero(r, F) == {f \in F : r[f] = 1}          \* files a Dec(r, F) deletes
DeleteNew(r, F) == {f \in F : r[f] <= 0}    \* deleteNewFiles: !exists(f)

MergingNames == UNION {Names(m.src) : m \in merges}
Busy == CONC = FALSE /\ merges # {}

Init ==
  /\ disk = {CFile(0)}               \* initial empty commit segments_0 (gen 0)
  /\ rc = [f \in AllFiles |-> IF f = CFile(0) THEN 1 ELSE 0]
  /\ sis = {}
  /\ counter = 1
  /\ commits = {[gen |-> 0, segs |-> {}, pa |-> FALSE]}
  /\ lastFiles = {}
  /\ rbSegs = {}
  /\ gen = 1
  /\ pc = NoPC
  /\ readers = [i \in Slots |-> NoRd]
  /\ merges = {}
  /\ pit = NoPIT
  /\ wopen = TRUE
  /\ orphan = {}
  /\ snaps = {}
  /\ delAfterClose = FALSE
  /\ mid = 0

\* IFD.checkpoint(S, false) applied to refcount map r; returns <<r', deleted>>
CkRc(r, S) == Dec(Inc(r, SisFiles(S)), lastFiles)
CkDel(r, S) == Zero(Inc(r, SisFiles(S)), lastFiles)

(* ---- writer thread: flush a new segment and publish it (checkpoint) ---- *)
Flush ==
  /\ wopen /\ ~Busy /\ counter <= MaxSeg
  /\ LET s == [n |-> counter, g |-> 0] IN
     LET S2 == sis \cup {s} IN
     /\ sis' = S2
     /\ rc' = CkRc(rc, S2)
     /\ disk' = (disk \cup SegFiles(s)) \ CkDel(rc, S2)
     /\ lastFiles' = SisFiles(S2)
  /\ counter' = counter + 1
  /\ UNCHANGED <<commits, rbSegs, gen, pc, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- writer thread: deletes written as a new live-docs gen (checkpointNoSIS) ---- *)
WriteDeletes(s) ==
  /\ wopen /\ ~Busy /\ s \in sis /\ s.g < MaxDelGen
  /\ LET t == [n |-> s.n, g |-> s.g + 1] IN
     LET S2 == (sis \ {s}) \cup {t} IN
     /\ sis' = S2
     /\ rc' = CkRc(rc, S2)
     /\ disk' = (disk \cup SegFiles(t)) \ CkDel(rc, S2)
     /\ lastFiles' = SisFiles(S2)
  /\ UNCHANGED <<counter, commits, rbSegs, gen, pc, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- commit phase 1 (prepareCommitInternal, IW lock + fullFlushLock) ---- *)
CommitPrepare ==
  /\ wopen /\ ~Busy /\ ~pc.on /\ gen <= MaxCommits
  /\ pc' = [on |-> TRUE, segs |-> sis, ftc |-> SisFiles(sis), wait |-> FALSE, applied |-> FALSE]
  /\ rc' = Inc(rc, SisFiles(sis))
  /\ UNCHANGED <<disk, sis, counter, commits, lastFiles, rbSegs, gen, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- commit phase 1 with merge-on-commit: also registers a full-flush merge whose readers are
        pulled under the lock (preparePointInTimeMerge, IW.java:3857-3864, 4077-4090) ---- *)
CommitPreparePIT ==
  /\ PITC /\ wopen /\ ~Busy /\ ~pc.on /\ gen <= MaxCommits /\ merges = {} /\ counter <= MaxSeg
  /\ \E src \in SUBSET sis :
       /\ Cardinality(src) = 2
       /\ merges' = {[id |-> mid, src |-> src, out |-> counter, phase |-> "running",
                      dropped |-> FALSE, pit |-> FALSE, pitc |-> TRUE, rfiles |-> SisFiles(src)]}
       /\ rc' = Inc(Inc(rc, SisFiles(sis)), SisFiles(src))
  /\ pc' = [on |-> TRUE, segs |-> sis, ftc |-> SisFiles(sis), wait |-> TRUE, applied |-> FALSE]
  /\ counter' = counter + 1
  /\ mid' = mid + 1
  /\ UNCHANGED <<disk, sis, commits, lastFiles, rbSegs, gen, readers, pit, wopen, orphan, snaps, delAfterClose>>

(* ---- end of the merge-on-commit wait (merges done or timeout): stopAddingMergedSegments=true
        under the lock, then filesToCommit = toCommit.files(false) (IW.java:3896-3910) ---- *)
CommitWaitDone ==
  /\ wopen /\ pc.on /\ pc.wait
  /\ pc' = [pc EXCEPT !.wait = FALSE, !.ftc = IF MUT = "staleFtc" THEN @ ELSE SisFiles(pc.segs)]
  /\ UNCHANGED <<disk, rc, sis, counter, commits, lastFiles, rbSegs, gen, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* KeepOnlyLastCommitDeletionPolicy (optionally behind SnapshotDeletionPolicy) *)
Doomed(C) == {c \in C : (\A d \in C : d.gen <= c.gen) = FALSE /\ c.gen \notin snaps}
DecCommits(r, D) == [f \in DOMAIN r |-> r[f] - Cardinality({c \in D : f \in CommitFiles(c)})]
ZeroCommits(r, D) == {f \in UNION {CommitFiles(c) : c \in D} :
                        r[f] - Cardinality({c \in D : f \in CommitFiles(c)}) = 0}

(* ---- commit phase 2 (finishCommit: checkpoint(isCommit) then decRef(filesToCommit)) ---- *)
CommitFinish ==
  /\ wopen /\ pc.on /\ ~pc.wait
  /\ LET c   == [gen |-> gen, segs |-> pc.segs, pa |-> pc.applied]
         r1  == Inc(rc, CommitFiles(c))
         C2  == commits \cup {c}
         D   == Doomed(C2)
         r2  == DecCommits(r1, D)
         z2  == ZeroCommits(r1, D)
         r3  == Dec(r2, pc.ftc)
         z3  == Zero(r2, pc.ftc)
     IN /\ rc' = r3
        /\ disk' = ((disk \cup {CFile(gen)}) \ z2) \ z3
        /\ commits' = C2 \ D
        /\ rbSegs' = pc.segs
  /\ gen' = gen + 1
  /\ pc' = NoPC
  /\ UNCHANGED <<sis, counter, lastFiles, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- application: NRT reader open (getReader without PIT merges) / close ---- *)
NRTOpen(i) ==
  /\ wopen /\ ~Busy /\ ~readers[i].on /\ ~pit.on
  /\ readers' = [readers EXCEPT ![i] = [on |-> TRUE, files |-> SisFiles(sis)]]
  /\ rc' = IF MUT = "noNrtIncRef" THEN rc ELSE Inc(rc, SisFiles(sis))
  /\ UNCHANGED <<disk, sis, counter, commits, lastFiles, rbSegs, gen, pc, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

NRTClose(i) ==
  /\ readers[i].on /\ ~(pit.on /\ pit.slot = i)
  /\ readers' = [readers EXCEPT ![i] = NoRd]
  /\ IF wopen
       THEN /\ rc' = Dec(rc, readers[i].files)
            /\ disk' = disk \ Zero(rc, readers[i].files)
       ELSE UNCHANGED <<rc, disk>>          \* decRefDeleter -> AlreadyClosedException, swallowed
  /\ UNCHANGED <<sis, counter, commits, lastFiles, rbSegs, gen, pc, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- merge thread: register a natural merge (merge readers incRef their files) ---- *)
MergeRegister ==
  /\ wopen /\ merges = {} /\ counter <= MaxSeg
  /\ \E src \in SUBSET sis :
       /\ Cardinality(src) = 2 /\ Names(src) \cap MergingNames = {}
       /\ merges' = {[id |-> mid, src |-> src, out |-> counter, phase |-> "running",
                      dropped |-> FALSE, pit |-> FALSE, pitc |-> FALSE, rfiles |-> SisFiles(src)]}
       /\ rc' = Inc(rc, SisFiles(src))
  /\ counter' = counter + 1
  /\ mid' = mid + 1
  /\ UNCHANGED <<disk, sis, commits, lastFiles, rbSegs, gen, pc, readers, pit, wopen, orphan, snaps, delAfterClose>>

(* ---- merge thread: mergeMiddle writes the merged files, not refcounted yet ---- *)
MergeWrite(m) ==
  /\ m \in merges /\ m.phase = "running"
  /\ disk' = disk \cup {<<"s", m.out, 0>>}
  /\ merges' = (merges \ {m}) \cup {[m EXCEPT !.phase = "written"]}
  /\ UNCHANGED <<rc, sis, counter, commits, lastFiles, rbSegs, gen, pc, readers, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- another thread deletes every document of the merging segments while the merge runs
        (e.g. tryDeleteDocument / applied deleteDocuments / updateDocument); the deletes are
        carried over in commitMerge, so the merged segment is 100% deleted and dropped ---- *)
DeleteAllDuringMerge(m) ==
  /\ wopen /\ CONC /\ m \in merges /\ m.phase \in {"running", "written"} /\ ~m.dropped
  /\ merges' = (merges \ {m}) \cup {[m EXCEPT !.dropped = TRUE]}
  /\ UNCHANGED <<disk, rc, sis, counter, commits, lastFiles, rbSegs, gen, pc, readers, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- merge thread: commitMerge (one IW monitor block) ---- *)
MergeCommit(m) ==
  /\ wopen /\ m \in merges /\ m.phase = "written"
  /\ LET out    == [n |-> m.out, g |-> 0]
         accept == m.pit /\ pit.on /\ ~pit.stop          \* onMergeComplete -> mergeFinished.accept
         r1     == IF accept THEN Inc(rc, SegFiles(out)) ELSE rc
         S2     == IF m.dropped THEN {s \in sis : s.n \notin Names(m.src)}
                   ELSE {s \in sis : s.n \notin Names(m.src)} \cup {out}
         dnew   == IF m.dropped THEN DeleteNew(r1, SegFiles(out)) ELSE {}
         \* commit PIT merge, mergeFinished (IW.java:3978-4024): incRef(origInfo.files()), decRef the
         \* merged-away segments of toCommit, apply the merge to toCommit; runs before the reader decRefs
         applyC == m.pitc /\ pc.on /\ pc.wait /\ ~m.dropped
         goneC  == {s \in pc.segs : s.n \in Names(m.src)}
         r1c    == IF applyC THEN Dec(Inc(r1, SegFiles(out)), SisFiles(goneC)) ELSE r1
         z1c    == IF applyC THEN Zero(Inc(r1, SegFiles(out)), SisFiles(goneC)) ELSE {}
         dfiles == IF MUT = "noMergeDecRef" THEN {} ELSE m.rfiles
         r2     == Dec(r1c, dfiles)                           \* closeMergeReaders
         z2     == Zero(r1c, dfiles) \cup z1c
         r3     == CkRc(r2, S2)                               \* checkpoint()
         z3     == CkDel(r2, S2)
     IN /\ sis' = S2
        /\ rc' = r3
        /\ disk' = ((disk \ dnew) \ z2) \ z3
        /\ lastFiles' = SisFiles(S2)
        /\ pit' = IF ~accept THEN pit
                  ELSE [pit EXCEPT !.merged = @ \cup {out},
                                   !.opening = IF m.dropped THEN @
                                               ELSE {s \in @ : s.n \notin Names(m.src)} \cup {out}]
        /\ pc' = IF applyC THEN [pc EXCEPT !.segs = (@ \ goneC) \cup {out}, !.applied = TRUE] ELSE pc
  /\ merges' = merges \ {m}
  /\ UNCHANGED <<counter, commits, rbSegs, gen, readers, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- getReader with point-in-time merges, part 1 (fullFlushLock + IW lock):
        open r (incRefDeleter), clone its SIS, register a full-flush merge ---- *)
GetReaderStart(i) ==
  /\ PIT /\ wopen /\ ~Busy /\ ~pit.on /\ ~readers[i].on /\ merges = {} /\ counter <= MaxSeg
  /\ \E src \in SUBSET sis :
       /\ Cardinality(src) = 2
       /\ merges' = {[id |-> mid, src |-> src, out |-> counter, phase |-> "running",
                      dropped |-> FALSE, pit |-> TRUE, pitc |-> FALSE, rfiles |-> SisFiles(src)]}
       /\ rc' = Inc(Inc(rc, SisFiles(sis)), SisFiles(src))
       /\ pit' = [on |-> TRUE, slot |-> i, r |-> SisFiles(sis), opening |-> sis, stop |-> FALSE, merged |-> {}]
  /\ counter' = counter + 1
  /\ mid' = mid + 1
  /\ UNCHANGED <<disk, sis, commits, lastFiles, rbSegs, gen, pc, readers, wopen, orphan, snaps, delAfterClose>>

(* ---- getReader part 2, after the wait (merge done or timed out), IW lock:
        stopCollecting=true; maybeReopenMergedNRTReader; close leftovers; r.close() ---- *)
GetReaderFinish ==
  /\ pit.on
  /\ LET applied  == {s \in pit.merged : s.n \in Names(pit.opening)}
         leftover == pit.merged \ applied
     IN IF pit.merged = {}
        THEN \* reader r is returned as is
             /\ readers' = [readers EXCEPT ![pit.slot] = [on |-> TRUE, files |-> pit.r]]
             /\ UNCHANGED <<rc, disk, delAfterClose>>
        ELSE IF wopen
        THEN LET r1 == Inc(rc, SisFiles(pit.opening))      \* SDR.open -> incRefDeleter
                 r2 == Dec(r1, SisFiles(applied))           \* finally: deleter.decRef(files)
                 z2 == Zero(r1, SisFiles(applied))
                 lf == IF FIX THEN SisFiles(leftover) ELSE {}
                 r3 == Dec(r2, lf)                          \* IOUtils.close(mergedReaders.values())
                 z3 == Zero(r2, lf)
                 r4 == Dec(r3, pit.r)                       \* r.close() -> decRefDeleter
                 z4 == Zero(r3, pit.r)
             IN /\ rc' = r4
                /\ disk' = ((disk \ z2) \ z3) \ z4
                /\ readers' = [readers EXCEPT ![pit.slot] = [on |-> TRUE, files |-> SisFiles(pit.opening)]]
                /\ UNCHANGED delAfterClose
        ELSE \* straggler: writer was rolled back meanwhile; incRefDeleter throws ACE,
             \* the finally block and onGetReaderMergeResources still decRef, but every delete goes
             \* through IW's LockValidatingDirectoryWrapper (IndexWriter.java: directory =
             \* new LockValidatingDirectoryWrapper(d, writeLock)), whose deleteFile throws
             \* AlreadyClosedException once the write lock is released: nothing is deleted.
             \* (model fix 1: the first version deleted here; the real-code replay showed the
             \* lock check refuses it, see logs/replay-straggler.txt)
             LET r2 == Dec(rc, SisFiles(applied))
                 z2 == Zero(rc, SisFiles(applied))
                 r3 == Dec(r2, SisFiles(leftover))
                 z3 == Zero(r2, SisFiles(leftover))
             IN /\ rc' = r3
                /\ disk' = IF LOCKCHECK THEN disk ELSE (disk \ z2) \ z3
                /\ readers' = readers
                /\ delAfterClose' = (delAfterClose \/ (~LOCKCHECK /\ (z2 \cup z3) # {}))
  /\ pit' = NoPIT
  /\ UNCHANGED <<sis, counter, commits, lastFiles, rbSegs, gen, pc, merges, wopen, orphan, snaps, mid>>

(* ---- SnapshotDeletionPolicy ---- *)
Snapshot ==
  /\ SNAP /\ wopen
  /\ \E c \in commits : (\A d \in commits : d.gen <= c.gen) /\ snaps' = snaps \cup {c.gen}
  /\ UNCHANGED <<disk, rc, sis, counter, commits, lastFiles, rbSegs, gen, pc, readers, merges, pit, wopen, orphan, delAfterClose, mid>>

Release(g) ==
  /\ SNAP /\ g \in snaps
  /\ snaps' = snaps \ {g}
  /\ UNCHANGED <<disk, rc, sis, counter, commits, lastFiles, rbSegs, gen, pc, readers, merges, pit, wopen, orphan, delAfterClose, mid>>

DeleteUnused ==
  /\ wopen /\ ~Busy
  /\ LET D == Doomed(commits) IN
     /\ rc' = DecCommits(rc, D)
     /\ disk' = disk \ ZeroCommits(rc, D)
     /\ commits' = commits \ D
  /\ UNCHANGED <<sis, counter, lastFiles, rbSegs, gen, pc, readers, merges, pit, wopen, orphan, snaps, delAfterClose, mid>>

(* ---- rollback(): abortMerges (waits for running merges), then one IW block:
        drop pending commit, restore rollbackSegments, checkpoint, refresh, IFD.close ---- *)
Rollback ==
  /\ wopen /\ ~pc.wait
  /\ LET mr  == UNION {m.rfiles : m \in merges}
         mout == {<<"s", m.out, 0>> : m \in merges}
         \* aborted merges: deleteNewFiles(merged files), closeMergeReaders decRef (multiset of 1 merge)
         r0  == Dec(rc, mr)
         z0  == Zero(rc, mr) \cup DeleteNew(rc, mout)
         r1  == IF pc.on THEN Dec(r0, pc.ftc) ELSE r0
         z1  == IF pc.on THEN Zero(r0, pc.ftc) ELSE {}
         r2  == CkRc(r1, rbSegs)
         z2  == CkDel(r1, rbSegs)
         d3  == (((disk \ z0) \ z1) \ z2)
         r3  == r2
         z4  == {f \in d3 : r3[f] <= 0}                      \* refresh(): unreferenced files
         r5  == Dec(r3, SisFiles(rbSegs))                    \* IFD.close(): decRef lastFiles
         z5  == Zero(r3, SisFiles(rbSegs))
     IN /\ rc' = r5
        /\ disk' = (d3 \ z4) \ z5
        \* files still held by NRT readers, or by a getReader call in flight (its first reader r and
        \* its merged readers), when the writer closes: leftovers documented as harmless
        \* (SDR.doClose comment) and removed by the next writer's IFD init
        /\ orphan' = UNION {readers[i].files : i \in Slots} \cup pit.r \cup SisFiles(pit.merged)
  /\ sis' = rbSegs
  /\ lastFiles' = {}
  /\ merges' = {}
  /\ pc' = NoPC
  /\ pit' = IF ~pit.on THEN pit ELSE [pit EXCEPT !.stop = TRUE]
  /\ wopen' = FALSE
  /\ UNCHANGED <<counter, commits, rbSegs, gen, readers, snaps, delAfterClose, mid>>

Next ==
  \/ Flush
  \/ \E s \in sis : WriteDeletes(s)
  \/ CommitPrepare
  \/ CommitPreparePIT
  \/ CommitWaitDone
  \/ CommitFinish
  \/ \E i \in Slots : NRTOpen(i) \/ NRTClose(i) \/ GetReaderStart(i)
  \/ MergeRegister
  \/ \E m \in merges : MergeWrite(m) \/ DeleteAllDuringMerge(m) \/ MergeCommit(m)
  \/ GetReaderFinish
  \/ Snapshot
  \/ \E g \in snaps : Release(g)
  \/ DeleteUnused
  \/ Rollback

Spec == Init /\ [][Next]_vars

(***************************************************************************)
(* Properties                                                              *)
(***************************************************************************)
TypeOK == /\ disk \subseteq AllFiles
          /\ rc \in [AllFiles -> Int]

RcNonNeg == \A f \in AllFiles : rc[f] >= 0

B(b) == IF b THEN 1 ELSE 0
Holders(f) ==
    Cardinality({c \in commits : f \in CommitFiles(c)})
  + B(f \in lastFiles)
  + Cardinality({i \in Slots : readers[i].on /\ f \in readers[i].files})
  + Cardinality({m \in merges : f \in m.rfiles})
  + B(pc.on /\ f \in (IF pc.wait THEN SisFiles(pc.segs) ELSE pc.ftc))
  + B(pit.on /\ f \in pit.r)
  + B(pit.on /\ f \in SisFiles(pit.merged))

\* exact accounting while the writer (and its IFD) is open
RcExact == wopen => \A f \in AllFiles : rc[f] = Holders(f)

Live == SisFiles(sis) \cup UNION {CommitFiles(c) : c \in commits}
        \cup UNION {readers[i].files : i \in Slots}
        \cup UNION {m.rfiles : m \in merges}
        \cup pc.ftc \cup (IF pc.wait THEN SisFiles(pc.segs) ELSE {})
        \cup (IF pit.on THEN pit.r \cup SisFiles(pit.merged) ELSE {})

NoDeleteWhileReferenced == wopen => Live \subseteq disk

\* once closed and no writer thread is left, only kept commits and the files of NRT
\* readers that outlived the writer (documented as harmless leftovers) may remain
NoLeakAtQuiescence ==
  (~wopen /\ ~pit.on) =>
     disk \subseteq (UNION {CommitFiles(c) : c \in commits} \cup orphan)

\* a closed writer (write lock released) never deletes files
NoDeleteAfterClose == delAfterClose = FALSE

\* vacuity probes (expected to be violated)
ProbeNeverDropped == \A m \in merges : ~(m.pit /\ m.dropped /\ m.phase = "written")
ProbeNeverClosed == wopen
ProbeNeverPitApplied == ~pit.on \/ pit.merged = {}
ProbeNeverCommitPitApplied == ~(pc.on /\ pc.applied)
ProbeNeverCommitMergedCommitted == \A c \in commits : ~c.pa
=============================================================================
