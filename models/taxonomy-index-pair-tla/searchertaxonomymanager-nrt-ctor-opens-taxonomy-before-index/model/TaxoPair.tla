------------------------------- MODULE TaxoPair -------------------------------
(***************************************************************************)
(* The main-index / taxonomy-index pair protocol of Lucene facets.         *)
(*                                                                         *)
(* Actors: an indexer (addCategory, then addDocument referencing the       *)
(* returned ordinal), a committer (taxonomy commit, then index commit), a  *)
(* SearcherTaxonomyManager (constructor, then maybeRefresh in a loop) and  *)
(* searchers that acquire the current pair at any time.                    *)
(*                                                                         *)
(* Lucene main @ ea57360912ed, file:line map (facet module unless noted):  *)
(*   AddCat      DirectoryTaxonomyWriter.java:381-402,452-482 (the ordinal *)
(*               is NRT-visible once indexWriter.addDocument (468) returns)*)
(*   AddDoc      user IndexWriter.addDocument with FacetsConfig.build      *)
(*   CommitTaxo  DirectoryTaxonomyWriter.java:511-528                       *)
(*   CommitIdx   user IndexWriter.commit (TaxonomyWriter.java:36-42: the   *)
(*               taxonomy must be committed *before* the main index)       *)
(*   Ctor1/Ctor2 SearcherTaxonomyManager.java:93 then :96-97 (NRT ctor),   *)
(*               :118 then :121 (Directory ctor): taxonomy opened FIRST    *)
(*   R1          SearcherTaxonomyManager.java:194-197 (index reopen, null  *)
(*               = no change; RefreshCommitSupplier may pick any commit)   *)
(*   R2          SearcherTaxonomyManager.java:204 (taxonomy reopen, latest)*)
(*   Publish     ReferenceManager.swapReference (core)                     *)
(*   Invariant   TaxonomyReader.java:46-55: the taxonomy must be opened    *)
(*               after the main index so the index never refers to a       *)
(*               category the taxonomy snapshot does not have;             *)
(*               SearcherTaxonomyManager.java:44-50 "a matched pair".      *)
(*                                                                         *)
(* Abstraction: taxonomy and index contents only grow here (no            *)
(* replaceTaxonomy; see TaxoEpoch.tla), so every snapshot is a length.     *)
(* Ordinal i is valid for a taxonomy snapshot of size t iff i < t.          *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets

CONSTANTS
  NLabels,       \* number of distinct non-root labels the indexer may use
  MaxDocs,       \* bound on documents the indexer adds
  MaxCommits,    \* bound on commit rounds
  Mode,          \* "NRT" (writer ctor) or "DIR" (Directory ctor)
  CtorOrder,     \* "TaxoFirst" (as coded) or "IndexFirst" (TaxonomyReader javadoc order)
  RefreshOrder,  \* "IndexFirst" (as coded) or "TaxoFirst" (mutant)
  CommitOrder,   \* "TaxoFirst" (documented) or "IndexFirst" (mutant)
  UseSupplier,   \* TRUE: RefreshCommitSupplier may return any existing index commit
  SameThread,    \* TRUE: commits and STM ctor/refresh run in one thread (Directory ctor NOTE)
  SeqCtor,       \* TRUE: no indexing while the STM constructor runs (single-threaded start-up)
  IndexDuringCommit \* FALSE: nothing is indexed between the taxonomy and the index commit
                    \* (TaxonomyWriter.java:42-45 locking advice; LUCENE-5139 Won't Fix)

Labels == 1..NLabels

VARIABLES
  taxo,      \* Seq of labels; ordinal i (0-based) = taxo[i+1]; taxo[1] = 0 is the root
  idx,       \* Seq of ordinals, one per document of the main index (NRT-visible)
  taxoC,     \* Seq of committed taxonomy sizes (latest last)
  idxC,      \* Seq of committed index doc counts (latest last)
  ipc, iord, \* indexer: "pick" | "doc"; ordinal returned by addCategory
  cpc, ncom, \* committer: "c1" | "c2"; commit rounds done
  spc,       \* STM: "ctor1" | "ctor2" | "idle" | "r2"
  cur,       \* current pair [i |-> #docs visible, t |-> taxonomy size]; t = -1 before ctor
  tmp        \* pair under construction / refresh

vars == <<taxo, idx, taxoC, idxC, ipc, iord, cpc, ncom, spc, cur, tmp>>

Ord(l) == CHOOSE i \in 0..(Len(taxo) - 1) : taxo[i + 1] = l
Known(l) == \E i \in 1..Len(taxo) : taxo[i] = l
Last(s) == s[Len(s)]

\* snapshot functions per mode
IdxSnap   == IF Mode = "NRT" THEN Len(idx) ELSE Last(idxC)
TaxoSnap  == IF Mode = "NRT" THEN Len(taxo) ELSE Last(taxoC)
\* index snapshots a RefreshCommitSupplier may return: any commit, or the default (latest/NRT)
IdxChoices == IF UseSupplier THEN {IdxSnap} \cup {idxC[k] : k \in 1..Len(idxC)} ELSE {IdxSnap}

StmBusy == spc \in {"ctor1", "ctor2", "r2"}
CommitBusy == cpc = "c2"

Init ==
  /\ taxo = <<0>>
  /\ idx = <<>>
  /\ taxoC = <<1>>
  /\ idxC = <<0>>
  /\ ipc = "pick" /\ iord = 0
  /\ cpc = "c1" /\ ncom = 0
  /\ spc = "ctor1"
  /\ cur = [i |-> 0, t |-> -1]
  /\ tmp = [i |-> 0, t |-> -1]

(* ---- indexer: FacetsConfig.build(taxoWriter, doc) then iw.addDocument ---- *)
NoIndexNow == (~IndexDuringCommit /\ cpc = "c2") \/ (SeqCtor /\ spc \in {"ctor1", "ctor2"})

AddCat(l) ==
  /\ ipc = "pick" /\ Len(idx) < MaxDocs /\ ~NoIndexNow
  /\ IF Known(l)
       THEN /\ iord' = Ord(l) /\ UNCHANGED taxo
       ELSE /\ taxo' = Append(taxo, l) /\ iord' = Len(taxo)
  /\ ipc' = "doc"
  /\ UNCHANGED <<idx, taxoC, idxC, cpc, ncom, spc, cur, tmp>>

AddDoc ==
  /\ ipc = "doc" /\ ~NoIndexNow
  /\ idx' = Append(idx, iord)
  /\ ipc' = "pick"
  /\ UNCHANGED <<taxo, taxoC, idxC, iord, cpc, ncom, spc, cur, tmp>>

(* ---- committer ---- *)
CommitFirst ==
  /\ cpc = "c1" /\ ncom < MaxCommits
  /\ ~(SameThread /\ StmBusy)
  /\ IF CommitOrder = "TaxoFirst"
       THEN taxoC' = Append(taxoC, Len(taxo)) /\ UNCHANGED idxC
       ELSE idxC' = Append(idxC, Len(idx)) /\ UNCHANGED taxoC
  /\ cpc' = "c2"
  /\ UNCHANGED <<taxo, idx, ipc, iord, ncom, spc, cur, tmp>>

CommitSecond ==
  /\ cpc = "c2"
  /\ IF CommitOrder = "TaxoFirst"
       THEN idxC' = Append(idxC, Len(idx)) /\ UNCHANGED taxoC
       ELSE taxoC' = Append(taxoC, Len(taxo)) /\ UNCHANGED idxC
  /\ cpc' = "c1" /\ ncom' = ncom + 1
  /\ UNCHANGED <<taxo, idx, ipc, iord, spc, cur, tmp>>

(* ---- SearcherTaxonomyManager constructor ---- *)
Ctor1 ==
  /\ spc = "ctor1" /\ ~(SameThread /\ CommitBusy)
  /\ IF CtorOrder = "TaxoFirst"
       THEN tmp' = [tmp EXCEPT !.t = TaxoSnap]
       ELSE tmp' = [tmp EXCEPT !.i = IdxSnap]
  /\ spc' = "ctor2"
  /\ UNCHANGED <<taxo, idx, taxoC, idxC, ipc, iord, cpc, ncom, cur>>

Ctor2 ==
  /\ spc = "ctor2"
  /\ LET p == IF CtorOrder = "TaxoFirst" THEN [tmp EXCEPT !.i = IdxSnap]
                                         ELSE [tmp EXCEPT !.t = TaxoSnap]
     IN cur' = p /\ tmp' = p
  /\ spc' = "idle"
  /\ UNCHANGED <<taxo, idx, taxoC, idxC, ipc, iord, cpc, ncom>>

(* ---- maybeRefresh -> refreshIfNeeded ---- *)
R1 ==
  /\ spc = "idle" /\ ~(SameThread /\ CommitBusy)
  /\ IF RefreshOrder = "IndexFirst"
       THEN \E n \in IdxChoices :
              IF n = cur.i THEN UNCHANGED <<spc, tmp>>          \* openIfChanged == null
              ELSE tmp' = [cur EXCEPT !.i = n] /\ spc' = "r2"
       ELSE tmp' = [cur EXCEPT !.t = TaxoSnap] /\ spc' = "r2"
  /\ UNCHANGED <<taxo, idx, taxoC, idxC, ipc, iord, cpc, ncom, cur>>

R2 ==
  /\ spc = "r2"
  /\ LET p == IF RefreshOrder = "IndexFirst"
                THEN [tmp EXCEPT !.t = IF TaxoSnap > cur.t THEN TaxoSnap ELSE cur.t]
                ELSE \* mutant: index second
                     [tmp EXCEPT !.i = IdxSnap]
     IN cur' = p /\ tmp' = p
  /\ spc' = "idle"
  /\ UNCHANGED <<taxo, idx, taxoC, idxC, ipc, iord, cpc, ncom>>

Next ==
  \/ \E l \in Labels : AddCat(l)
  \/ AddDoc
  \/ CommitFirst \/ CommitSecond
  \/ Ctor1 \/ Ctor2 \/ R1 \/ R2

Spec == Init /\ [][Next]_vars

(* An acquired pair never references an ordinal its taxonomy reader lacks. *)
PairSafe ==
  cur.t >= 0 => \A k \in 1..cur.i : idx[k] < cur.t

TypeOK ==
  /\ cur.i \in 0..MaxDocs
  /\ cur.t \in -1..(NLabels + 1)

\* Vacuity probes (expected to be violated: they show the interesting states are reachable)
ProbeNewOrdVisible == ~(cur.t >= 0 /\ \E k \in 1..cur.i : idx[k] >= 1 /\ cur.t >= 2 /\ ncom >= 1)
ProbeRefreshed == ~(spc = "idle" /\ cur.i >= 2)
=============================================================================
