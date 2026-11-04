---------------------------- MODULE FindSegments ----------------------------
(***************************************************************************)
(* Reader-side commit discovery of Lucene main @ ea57360912ed while one    *)
(* IndexWriter commits concurrently.                                        *)
(*                                                                         *)
(* Files are tokens: <<"S",g>> = segments_g, <<"P",g>> = pending_segments_g, *)
(*   <<"I",s>> = the .si file of segment s,  <<"D",s>> = its data files    *)
(*   (one token: .cfs/.cfe or per-format files, opened by SegmentReader).  *)
(* Every file is write-once (names are unique inside one writer session;   *)
(* cross-session name reuse is LFV-015/LFV-109 and is out of this model).  *)
(* An opened file stays readable after it is deleted (POSIX unlink; the    *)
(*                                                                         *)
(* Model element          -> Java (lucene/core/src/java/org/apache/lucene/) *)
(*  WCreate               -> segment files written by flush/merge before   *)
(*                           the commit (IndexWriter.startCommit syncs them)*)
(*  WPending              -> SegmentInfos.write: pending_segments_N         *)
(*                           (index/SegmentInfos.java:605-620)              *)
(*  WRename               -> SegmentInfos.finishCommit rename to segments_N *)
(*                           (index/SegmentInfos.java ~980-1000)            *)
(*  WDelSn / WDelFiles    -> IndexFileDeleter.checkpoint(sis,true):544-575, *)
(*                           deleteCommits:401-448, policy.onCommit         *)
(*                           (KeepOnlyLastCommitDeletionPolicy = KeepLast 1,*)
(*                           keep-last-K policies = KeepLast K); deletion   *)
(*                           order util/FileDeleter.java:207-229: all       *)
(*                           segments_N first, then the other files         *)
(*  listing (atomic/weak) -> Directory.listAll; FSDirectory.listAll:192 via *)
(*                           Files.newDirectoryStream (readdir: a file that *)
(*                           exists during the whole listing is returned,   *)
(*                           one added or removed during it may or may not) *)
(*  FsfTop..FsfDecide     -> SegmentInfos.FindSegmentsFile.run:835-906      *)
(*                           (lastGen = gen; listAll twice; compare;        *)
(*                           getLastCommitGeneration:203-217; gen == -1 ->  *)
(*                           IndexNotFoundException; gen > lastGen ->       *)
(*                           doBody; else throw exc)                        *)
(*  FsfBody phase 1,2     -> SegmentInfos.readCommit:305-319 (open          *)
(*                           segments_N) and parseSegmentInfos:403-422      *)
(*                           (read each segment's .si)                      *)
(*  FsfBody phase 3       -> StandardDirectoryReader.open doBody:84-107 ->  *)
(*                           createSegmentReaders:187-247 -> SegmentReader  *)
(*                           (SegmentCoreReaders opens the data files)      *)
(*  sub "latest"          -> SegmentInfos.readLatestCommit:589-597          *)
(*  sub "reopen_cur"      -> StandardDirectoryReader.isCurrent:529-544      *)
(*  sub "reopen_open"     -> doOpenNoWriter/doOpenFromCommit:477-505 (old   *)
(*                           segment readers are reused: only new segments' *)
(*                           data files are opened; every .si is re-read)   *)
(*  Lc*                   -> DirectoryReader.listCommits:448-492            *)
(*                           (listAll; readLatestCommit; readCommit of each *)
(*                           older segments_k; catch FileNotFound/NoSuchFile*)
(*                           only around the whole readCommit call)         *)
(*  exception classes     -> SegmentInfos.java:314-317 wraps EOF/NoSuchFile/*)
(*                           FileNotFound raised while PARSING segments_k   *)
(*                           (i.e. a missing .si, :411-421) into            *)
(*                           CorruptIndexException; a missing segments_k at *)
(*                           open (:311) stays NoSuchFileException          *)
(***************************************************************************)
EXTENDS Naturals, Integers, FiniteSets, TLC

CONSTANTS MaxGen,      \* the writer makes commits 2..MaxGen; commit 1 exists at start
          Refs,        \* [1..MaxGen -> SUBSET Segs]: segments referenced by each commit
          Segs,
          KeepLast,    \* deletion policy keeps the newest KeepLast commits
          AtomicList,  \* TRUE: listAll is one atomic step; FALSE: weakly consistent (readdir)
          Kind,        \* [Readers -> {"open","list","reopen"}]
          LateStart,   \* TRUE: readers may start at any time (else at time 0)
          MUT          \* "none" | "noRetry" | "singleList" | "listFix" | "noLcCatch"

Readers == DOMAIN Kind

S(g) == <<"S", g>>
P(g) == <<"P", g>>
I(s) == <<"I", s>>
D(s) == <<"D", s>>

SegFiles(g) == {I(s) : s \in Refs[g]} \cup {D(s) : s \in Refs[g]}
NewFiles(g) == {I(s) : s \in Refs[g] \ Refs[g-1]} \cup {D(s) : s \in Refs[g] \ Refs[g-1]}
Gens(F)  == {f[2] : f \in {x \in F : x[1] = "S"}}
MaxOf(G) == IF G = {} THEN -1 ELSE CHOOSE g \in G : \A h \in G : h <= g
MinOf(G) == CHOOSE g \in G : \A h \in G : g <= h

\* write-once names: a segment is referenced by a contiguous run of commits
ASSUME \A s \in Segs : \A i, k \in 1..MaxGen :
          (i < k /\ s \in Refs[i] /\ s \in Refs[k]) => \A j \in i..k : s \in Refs[j]
ASSUME KeepLast >= 1

VARIABLES dir, wg, wpc, wtodo, live, delc,
          pc, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
          phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest

wvars == <<dir, wg, wpc, wtodo, live, delc>>
rvars == <<pc, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
           phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest>>
vars == <<wvars, rvars>>

FirstSub(r) == CASE Kind[r] = "open"   -> "open"
               []   Kind[r] = "list"   -> "latest"
               []   Kind[r] = "reopen" -> "reopen_cur"
FirstPc(r) == IF Kind[r] = "list" THEN "lc_list" ELSE "fsf_top"

Init ==
  /\ dir = {S(1)} \cup SegFiles(1)
  /\ wg = 2 /\ wpc = IF MaxGen >= 2 THEN "create" ELSE "stop"
  /\ wtodo = IF MaxGen >= 2 THEN NewFiles(2) ELSE {}
  /\ live = {1} /\ delc = {}
  /\ pc = [r \in Readers |-> IF LateStart THEN "idle" ELSE FirstPc(r)]
  /\ sub = [r \in Readers |-> FirstSub(r)]
  /\ lastGen = [r \in Readers |-> -1]
  /\ gen = [r \in Readers |-> -1]
  /\ exc = [r \in Readers |-> FALSE]
  /\ l1 = [r \in Readers |-> {}]
  /\ inList = [r \in Readers |-> FALSE]
  /\ lsStart = [r \in Readers |-> {}]
  /\ lsChg = [r \in Readers |-> {}]
  /\ phase = [r \in Readers |-> 0]
  /\ todo = [r \in Readers |-> {}]
  /\ attempts = [r \in Readers |-> 0]
  /\ files = [r \in Readers |-> {}]
  /\ cand = [r \in Readers |-> {}]
  /\ lk = [r \in Readers |-> 0]
  /\ outcome = [r \in Readers |-> "none"]
  /\ retGen = [r \in Readers |-> -1]
  /\ startLatest = [r \in Readers |-> 1]

(* every directory change is seen as "changed during the listing" by readers listing now *)
Touch(F) == lsChg' = [r \in Readers |-> IF inList[r] THEN lsChg[r] \cup F ELSE lsChg[r]]

(***************************** writer *****************************)
WCreate ==
  /\ wpc = "create"
  /\ IF wtodo # {}
       THEN \E f \in wtodo : /\ dir' = dir \cup {f} /\ wtodo' = wtodo \ {f}
                             /\ Touch({f}) /\ UNCHANGED wpc
       ELSE /\ wpc' = "pending" /\ UNCHANGED <<dir, wtodo, lsChg>>
  /\ UNCHANGED <<wg, live, delc>>

WPending ==
  /\ wpc = "pending"
  /\ dir' = dir \cup {P(wg)} /\ Touch({P(wg)}) /\ wpc' = "rename"
  /\ UNCHANGED <<wg, wtodo, live, delc>>

WRename ==
  /\ wpc = "rename"
  /\ dir' = (dir \ {P(wg)}) \cup {S(wg)}
  /\ Touch({P(wg), S(wg)})
  /\ live' = live \cup {wg}
  /\ delc' = {k \in live \cup {wg} : k <= wg - KeepLast}
  /\ wtodo' = {S(k) : k \in {k2 \in live \cup {wg} : k2 <= wg - KeepLast}}
  /\ wpc' = "delsn"
  /\ UNCHANGED wg

WDelSn ==
  /\ wpc = "delsn"
  /\ IF wtodo # {}
       THEN \E f \in wtodo : /\ dir' = dir \ {f} /\ wtodo' = wtodo \ {f}
                             /\ Touch({f}) /\ UNCHANGED <<wpc, live, delc>>
       ELSE /\ wtodo' = (UNION {SegFiles(k) : k \in delc})
                          \ (UNION {SegFiles(k) : k \in live \ delc})
            /\ live' = live \ delc
            /\ wpc' = "delfiles"
            /\ UNCHANGED <<dir, lsChg, delc>>
  /\ UNCHANGED wg

WDelFiles ==
  /\ wpc = "delfiles"
  /\ IF wtodo # {}
       THEN \E f \in wtodo : /\ dir' = dir \ {f} /\ wtodo' = wtodo \ {f}
                             /\ Touch({f}) /\ UNCHANGED <<wpc, wg, delc>>
       ELSE /\ delc' = {}
            /\ IF wg + 1 <= MaxGen
                 THEN /\ wg' = wg + 1 /\ wpc' = "create" /\ wtodo' = NewFiles(wg + 1)
                 ELSE /\ wg' = wg + 1 /\ wpc' = "stop" /\ wtodo' = {}
            /\ UNCHANGED <<dir, lsChg>>
  /\ UNCHANGED live

rNoChg == <<pc, sub, lastGen, gen, exc, l1, inList, lsStart,
            phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest>>
Writer == (WCreate \/ WPending \/ WRename \/ WDelSn \/ WDelFiles) /\ UNCHANGED rNoChg

(***************************** readers *****************************)
Start(r) ==
  /\ pc[r] = "idle"
  /\ pc' = [pc EXCEPT ![r] = FirstPc(r)]
  /\ startLatest' = [startLatest EXCEPT ![r] = MaxOf(live)]
  /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
                 phase, todo, attempts, files, cand, lk, outcome, retGen>>

\* FindSegmentsFile.run loop head: lastGen = gen
FsfTop(r) ==
  /\ pc[r] = "fsf_top"
  /\ lastGen' = [lastGen EXCEPT ![r] = gen[r]]
  /\ pc' = [pc EXCEPT ![r] = "fsf_l1"]
  /\ UNCHANGED <<wvars, sub, gen, exc, l1, inList, lsStart, lsChg,
                 phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest>>

\* beginning of a weakly consistent listing
BeginList(r, from, to) ==
  /\ pc[r] = from
  /\ ~AtomicList
  /\ inList' = [inList EXCEPT ![r] = TRUE]
  /\ lsStart' = [lsStart EXCEPT ![r] = dir]
  /\ lsChg' = [lsChg EXCEPT ![r] = {}]
  /\ pc' = [pc EXCEPT ![r] = to]
  /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1, phase, todo, attempts,
                 files, cand, lk, outcome, retGen, startLatest>>

\* possible results of the listing that ends now
ListResults(r) ==
  IF AtomicList THEN {dir}
  ELSE {(lsStart[r] \ lsChg[r]) \cup X : X \in SUBSET lsChg[r]}

EndListReset(r) ==
  /\ inList' = [inList EXCEPT ![r] = FALSE]
  /\ lsStart' = [lsStart EXCEPT ![r] = {}]
  /\ lsChg' = [lsChg EXCEPT ![r] = {}]

\* first listAll()
FsfL1(r) ==
  \/ /\ AtomicList /\ pc[r] = "fsf_l1"
     /\ l1' = [l1 EXCEPT ![r] = dir]
     /\ pc' = [pc EXCEPT ![r] = "fsf_l2"]
     /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, inList, lsStart, lsChg,
                    phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest>>
  \/ BeginList(r, "fsf_l1", "fsf_l1e")
  \/ /\ pc[r] = "fsf_l1e"
     /\ \E L \in ListResults(r) : l1' = [l1 EXCEPT ![r] = L]
     /\ EndListReset(r)
     /\ pc' = [pc EXCEPT ![r] = "fsf_l2"]
     /\ UNCHANGED <<wvars, sub, lastGen, gen, exc,
                    phase, todo, attempts, files, cand, lk, outcome, retGen, startLatest>>

\* compare the two listings and pick the generation (SegmentInfos.java:851-906)
Decide(r, L2) ==
  IF l1[r] # L2
    THEN \* listAll() is weakly consistent: "continue"
         /\ pc' = [pc EXCEPT ![r] = "fsf_top"]
         /\ l1' = [l1 EXCEPT ![r] = {}]
         /\ UNCHANGED <<gen, phase, todo, attempts, outcome>>
    ELSE LET g == MaxOf(Gens(L2)) IN
         /\ l1' = [l1 EXCEPT ![r] = {}]
         /\ IF g = -1
              THEN /\ outcome' = [outcome EXCEPT ![r] = "IndexNotFound"]
                   /\ pc' = [pc EXCEPT ![r] = "err"]
                   /\ UNCHANGED <<gen, phase, todo, attempts>>
              ELSE IF g > lastGen[r]
                THEN /\ gen' = [gen EXCEPT ![r] = g]
                     /\ phase' = [phase EXCEPT ![r] = 1]
                     /\ todo' = [todo EXCEPT ![r] = {S(g)}]
                     /\ attempts' = [attempts EXCEPT ![r] = @ + 1]
                     /\ pc' = [pc EXCEPT ![r] = "fsf_body"]
                     /\ UNCHANGED outcome
                ELSE /\ outcome' = [outcome EXCEPT ![r] = "Rethrow"]
                     /\ pc' = [pc EXCEPT ![r] = "err"]
                     /\ UNCHANGED <<gen, phase, todo, attempts>>

\* second listAll() (or none in the "singleList" mutant)
FsfL2(r) ==
  \/ /\ (AtomicList \/ MUT = "singleList") /\ pc[r] = "fsf_l2"
     /\ Decide(r, IF MUT = "singleList" THEN l1[r] ELSE dir)
     /\ UNCHANGED <<wvars, sub, lastGen, exc, inList, lsStart, lsChg,
                    files, cand, lk, retGen, startLatest>>
  \/ /\ MUT # "singleList"
     /\ BeginList(r, "fsf_l2", "fsf_l2e")
  \/ /\ pc[r] = "fsf_l2e"
     /\ \E L \in ListResults(r) : Decide(r, L)
     /\ EndListReset(r)
     /\ UNCHANGED <<wvars, sub, lastGen, exc, files, cand, lk, retGen, startLatest>>

DataFiles(r, g) ==
  CASE sub[r] = "open"        -> {D(s) : s \in Refs[g]}
  []   sub[r] = "reopen_open" -> {D(s) : s \in Refs[g] \ Refs[1]}
  []   OTHER                  -> {}

\* doBody finished: hand the result to the caller
Success(r) ==
  CASE sub[r] = "open" ->
         /\ outcome' = [outcome EXCEPT ![r] = "ok"]
         /\ retGen' = [retGen EXCEPT ![r] = gen[r]]
         /\ pc' = [pc EXCEPT ![r] = "done"]
         /\ UNCHANGED <<sub, lastGen, gen, exc, cand, files>>
  []   sub[r] = "reopen_open" ->
         /\ outcome' = [outcome EXCEPT ![r] = "ok"]
         /\ retGen' = [retGen EXCEPT ![r] = gen[r]]
         /\ pc' = [pc EXCEPT ![r] = "done"]
         /\ UNCHANGED <<sub, lastGen, gen, exc, cand, files>>
  []   sub[r] = "reopen_cur" ->
         IF gen[r] = 1
           THEN /\ outcome' = [outcome EXCEPT ![r] = "null"]
                /\ retGen' = [retGen EXCEPT ![r] = 1]
                /\ pc' = [pc EXCEPT ![r] = "done"]
                /\ UNCHANGED <<sub, lastGen, gen, exc, cand, files>>
           ELSE \* a fresh FindSegmentsFile for doOpenFromCommit
                /\ sub' = [sub EXCEPT ![r] = "reopen_open"]
                /\ lastGen' = [lastGen EXCEPT ![r] = -1]
                /\ gen' = [gen EXCEPT ![r] = -1]
                /\ exc' = [exc EXCEPT ![r] = FALSE]
                /\ pc' = [pc EXCEPT ![r] = "fsf_top"]
                /\ UNCHANGED <<outcome, retGen, cand, files>>
  []   sub[r] = "latest" ->   \* readLatestCommit inside listCommits
         /\ cand' = [cand EXCEPT ![r] = {k \in Gens(files[r]) : k < gen[r]}]
         /\ pc' = [pc EXCEPT ![r] = "lc_loop"]
         /\ UNCHANGED <<sub, lastGen, gen, exc, outcome, retGen, files>>

FsfBody(r) ==
  /\ pc[r] = "fsf_body"
  /\ IF todo[r] = {}
       THEN IF phase[r] = 1
              THEN /\ phase' = [phase EXCEPT ![r] = 2]
                   /\ todo' = [todo EXCEPT ![r] = {I(s) : s \in Refs[gen[r]]}]
                   /\ UNCHANGED <<pc, sub, lastGen, gen, exc, cand, files, outcome, retGen>>
              ELSE IF phase[r] = 2
                THEN /\ phase' = [phase EXCEPT ![r] = 3]
                     /\ todo' = [todo EXCEPT ![r] = DataFiles(r, gen[r])]
                     /\ UNCHANGED <<pc, sub, lastGen, gen, exc, cand, files, outcome, retGen>>
                ELSE /\ phase' = [phase EXCEPT ![r] = 0]
                     /\ UNCHANGED todo
                     /\ Success(r)
       ELSE \E f \in todo[r] :
              IF f \in dir
                THEN /\ todo' = [todo EXCEPT ![r] = @ \ {f}]
                     /\ UNCHANGED <<pc, sub, lastGen, gen, exc, phase, cand, files, outcome, retGen>>
                ELSE \* IOException (NoSuchFile, or CorruptIndexException wrapping it)
                     /\ todo' = [todo EXCEPT ![r] = {}]
                     /\ phase' = [phase EXCEPT ![r] = 0]
                     /\ IF MUT = "noRetry"
                          THEN /\ outcome' = [outcome EXCEPT ![r] = "NoRetryIOE"]
                               /\ pc' = [pc EXCEPT ![r] = "err"]
                               /\ UNCHANGED <<exc>>
                          ELSE /\ exc' = [exc EXCEPT ![r] = TRUE]
                               /\ pc' = [pc EXCEPT ![r] = "fsf_top"]
                               /\ UNCHANGED <<outcome>>
                     /\ UNCHANGED <<sub, lastGen, gen, cand, files, retGen>>
  /\ UNCHANGED <<wvars, l1, inList, lsStart, lsChg, attempts, lk, startLatest>>

(* DirectoryReader.listCommits *)
LcList(r) ==
  \/ /\ AtomicList /\ pc[r] = "lc_list"
     /\ files' = [files EXCEPT ![r] = dir]
     /\ pc' = [pc EXCEPT ![r] = "fsf_top"]
     /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
                    phase, todo, attempts, cand, lk, outcome, retGen, startLatest>>
  \/ BeginList(r, "lc_list", "lc_liste")
  \/ /\ pc[r] = "lc_liste"
     /\ \E L \in ListResults(r) : files' = [files EXCEPT ![r] = L]
     /\ EndListReset(r)
     /\ pc' = [pc EXCEPT ![r] = "fsf_top"]
     /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1,
                    phase, todo, attempts, cand, lk, outcome, retGen, startLatest>>

LcLoop(r) ==
  /\ pc[r] = "lc_loop"
  /\ IF cand[r] = {}
       THEN /\ outcome' = [outcome EXCEPT ![r] = "ok"]
            /\ pc' = [pc EXCEPT ![r] = "done"]
            /\ UNCHANGED <<cand, lk, todo>>
       ELSE LET k == MinOf(cand[r]) IN
            IF S(k) \in dir
              THEN \* openChecksumInput(segments_k) succeeded; now parse it
                   /\ lk' = [lk EXCEPT ![r] = k]
                   /\ todo' = [todo EXCEPT ![r] = {I(s) : s \in Refs[k]}]
                   /\ pc' = [pc EXCEPT ![r] = "lc_si"]
                   /\ UNCHANGED <<cand, outcome>>
              ELSE \* NoSuchFileException at open: caught (LUCENE-948), skip
                   /\ cand' = [cand EXCEPT ![r] = @ \ {k}]
                   /\ UNCHANGED <<pc, lk, todo, outcome>>
  /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
                 phase, attempts, files, retGen, startLatest>>

LcSi(r) ==
  /\ pc[r] = "lc_si"
  /\ IF todo[r] = {}
       THEN /\ cand' = [cand EXCEPT ![r] = @ \ {lk[r]}]
            /\ pc' = [pc EXCEPT ![r] = "lc_loop"]
            /\ UNCHANGED <<todo, outcome>>
       ELSE \E f \in todo[r] :
              IF f \in dir
                THEN /\ todo' = [todo EXCEPT ![r] = @ \ {f}]
                     /\ UNCHANGED <<pc, cand, outcome>>
                ELSE IF MUT = "listFix" /\ S(lk[r]) \notin dir
                  THEN \* candidate fix: the read failed and segments_k itself is gone now:
                       \* the commit was deleted while we read it (IndexFileDeleter removes
                       \* segments_N before the files it references), so skip it
                       /\ todo' = [todo EXCEPT ![r] = {}]
                       /\ cand' = [cand EXCEPT ![r] = @ \ {lk[r]}]
                       /\ pc' = [pc EXCEPT ![r] = "lc_loop"]
                       /\ UNCHANGED outcome
                  ELSE \* NoSuchFile on the .si -> CorruptSegmentInfoException: not caught
                       /\ outcome' = [outcome EXCEPT ![r] = "CorruptIndex"]
                       /\ pc' = [pc EXCEPT ![r] = "err"]
                       /\ UNCHANGED <<todo, cand>>
  /\ UNCHANGED <<wvars, sub, lastGen, gen, exc, l1, inList, lsStart, lsChg,
                 phase, attempts, files, lk, retGen, startLatest>>

Reader(r) == Start(r) \/ FsfTop(r) \/ FsfL1(r) \/ FsfL2(r) \/ FsfBody(r)
             \/ LcList(r) \/ LcLoop(r) \/ LcSi(r)

Terminal == /\ wpc = "stop"
            /\ \A r \in Readers : pc[r] \in {"done", "err"}

Next == \/ Writer
        \/ \E r \in Readers : Reader(r)
        \/ (Terminal /\ UNCHANGED vars)

Spec == Init /\ [][Next]_vars /\ WF_vars(Writer) /\ \A r \in Readers : WF_vars(Reader(r))

(***************************** properties *****************************)
TypeOK == /\ wpc \in {"create", "pending", "rename", "delsn", "delfiles", "stop"}
          /\ \A r \in Readers : pc[r] \in {"idle", "fsf_top", "fsf_l1", "fsf_l1e", "fsf_l2",
                 "fsf_l2e", "fsf_body", "lc_list", "lc_liste", "lc_loop", "lc_si", "done", "err"}

\* the index is healthy: the newest commit and all files it references always exist
Healthy == /\ S(MaxOf(live)) \in dir
           /\ SegFiles(MaxOf(live)) \subseteq dir

\* NoSpuriousError: no IOException escapes on a healthy index
NoSpuriousError == \A r \in Readers : pc[r] # "err"
NoSpuriousErrorOpen == \A r \in Readers : (Kind[r] \in {"open", "reopen"}) => pc[r] # "err"
NoSpuriousErrorList == \A r \in Readers : (Kind[r] = "list") => pc[r] # "err"
\* listCommits-specific failure only (excludes the FindSegmentsFile weak-listing outcome)
NoCorruptFromList == \A r \in Readers : outcome[r] # "CorruptIndex"

\* Freshness: a successful open returns a commit at least as new as the newest commit
\* that was complete when the call started (informational: no javadoc states it)
Fresh == \A r \in Readers :
           (pc[r] = "done" /\ outcome[r] = "ok" /\ Kind[r] \in {"open", "reopen"})
              => retGen[r] >= startLatest[r]

\* BoundedRetries: every doBody attempt of one FindSegmentsFile run uses a strictly larger gen
BoundedRetries == \A r \in Readers : attempts[r] <= 2 * MaxGen

\* liveness: every call returns (or throws) once the writer has stopped
Completes == <>(\A r \in Readers : pc[r] \in {"done", "err"})

\* vacuity probes (each must be VIOLATED)
ProbeRetrySucceeds == ~(\E r \in Readers : pc[r] = "done" /\ outcome[r] = "ok" /\ attempts[r] >= 2)
ProbeListSkips == ~(\E r \in Readers : Kind[r] = "list" /\ pc[r] = "done"
                         /\ \E k \in Gens(files[r]) : k < gen[r])
=============================================================================
