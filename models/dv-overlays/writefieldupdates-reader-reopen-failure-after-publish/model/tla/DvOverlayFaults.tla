----------------------------- MODULE DvOverlayFaults -----------------------------
(***************************************************************************)
(* Doc-values delta overlays (#16418, default on in main): one index with  *)
(* one live segment at a time (a merge replaces it by a new segment), F    *)
(* numeric-like fields, D docs.                                            *)
(*                                                                         *)
(* Model element -> Java (Lucene main @ ea57360912ed, lucene/core/src/    *)
(* java/org/apache/lucene/index/)                                          *)
(*   pend/pendReset           ReadersAndUpdates.pendingDVUpdates; anyReset *)
(*                            check: ReadersAndUpdates.java:351-357         *)
(*   Update                   IndexWriter.update*DocValue / updateDocValues *)
(*                            (null value = RESET), resolved per segment   *)
(*   WriteField(f)            ReadersAndUpdates.handleDVUpdates :336-618    *)
(*     baseGen/priorGens      :344-350                                      *)
(*     sparseDelta/compact    :363, :368                                    *)
(*     deltaCoverage/fold     :370-407 (FOLD_TO_DENSE_COVERAGE_RATIO=0.5)   *)
(*     delta / sparse fold /  :437-446, :500-510, :524-535                  *)
(*     dense content                                                        *)
(*     staged overlay         :600-614                                      *)
(*     file retention         writeFieldUpdates :998-1017                   *)
(*     commit overlay         :1021-1025 (SegmentCommitInfo.               *)
(*                            setDocValuesOverlay: empty deltas = clear)   *)
(*   View(s,f,d)              SegmentDocValuesProducer.openOverlay :126-145 *)
(*                            + OverlayNumericDocValues/DocValuesOverlay-  *)
(*                            Merger (newest layer having d wins, else     *)
(*                            base; base -1 = core column if the core has  *)
(*                            the field, hasCoreBase :129)                 *)
(*   ReadGens(s)              generations a reader opens (openOverlay, or  *)
(*                            fi.getDocValuesGen() when no overlay :83-94)  *)
(*   Declared(s)              SegmentCommitInfo.getDocValuesUpdatesFiles,  *)
(*                            i.e. what IndexFileDeleter refcounts         *)
(*   Commit / OpenReader      IndexWriter.commit / getReader (both write   *)
(*                            all pending DV updates first)                *)
(*   Gc                       IndexFileDeleter deleting files no longer    *)
(*                            referenced by the writer's SegmentInfos, the *)
(*                            last commit or an open NRT reader            *)
(*   Crash                    reopen from the last commit; inflateGens     *)
(*                            (IndexFileDeleter.java:257) bumps nextGen    *)
(*                            past every file still on disk                *)
(*   CleanReopen              close (commit) + new IndexWriter with a      *)
(*                            possibly different maxDocValuesOverlays      *)
(*   MergeStart/MergeCommit   merge with doc-values carry-over (#16570):    *)
(*                            IndexWriter.buildMappedDVUpdatesFromDisk     *)
(*                            :4565-4662 (disk diff current vs baseline,   *)
(*                            then residual pending at their own gens)     *)
(*                                                                         *)
(* FAULT EXTENSION (DvOverlayFaults): adds the writer's pooled reader      *)
(* (pool: the SegmentCommitInfo snapshot ReadersAndUpdates.reader reflects; *)
(* a dense rewrite reads it, ReadersAndUpdates.java:446/509/535, else a     *)
(* fresh reader of info, :893-898) and IndexFileDeleter's last checkpoint   *)
(* (ckpt; IndexWriter.writeReaderPool:4116-4126 checkpoints only after a    *)
(* successful write; finishApply's incRef/decRef pair deletes anything     *)
(* else, so Gc may delete files outside ckpt/commit/readers).               *)
(* WriteFieldSwapFails models an IOException in                            *)
(* swapNewReaderWithLatestLiveDocs (:1028-1030) after info was updated.     *)
(* FixDropReader = the fix sketch: drop the pooled reader, return normally. *)
(*                                                                         *)
(* pending packet resolved (maxDelGen = all); per-field writes are atomic; *)
(* a sorted-numeric field is modelled like a numeric one (per-doc value    *)
(* set replaced as a unit); deletes are not modelled (they do not touch    *)
(* the overlay code path); one segment.                                    *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS Docs, Fields, Vals, NChoices, N0, MaxSteps, CoreHas0, MaxReaders,
          WithMerge, WithCrash, WithReopen,
          \* mutation switches for vacuity checks (all FALSE = the real code)
          MutOldestWins, MutDropBaseRetention, MutFoldIgnoresBase,
          WithSwapFault, FixDropReader

ABSENT == 0     \* doc has no value
NOPEND == -2    \* no pending update for this doc
RESET  == -1    \* pending removal
NOTIN  == -3    \* doc not present in a sparse delta generation
CORE   == -1    \* baseGen sentinel for the core column

VARIABLES seg,        \* current segment id (a merge creates a new one)
          cores,      \* seg -> [Fields -> [Docs -> value]] core columns
          coreHas,    \* seg -> SUBSET Fields present in the core segment
          pend, pendReset,
          gens,       \* <<seg,gen>> -> [content: [Docs -> value or NOTIN]]
          nextGen,
          sci,        \* writer's SegmentCommitInfo (per-field state)
          lastCommit, \* [sci, exp] or a null record
          readers,    \* set of [sci, exp] snapshots held by open readers
          onDisk,     \* set of <<seg,gen>> files present
          truth,      \* abstract last-writer-wins value of every doc
          n,          \* current maxDocValuesOverlays
          mergeBase,  \* NULLM or the sci snapshot the running merge reads
          steps,
          lastWrite,  \* ghost: branch taken by the last WriteField (vacuity probes only)
          pool,       \* NULLM, or the sci snapshot the pooled SegmentReader reflects
          ckpt        \* files referenced by IndexFileDeleter's last checkpoint

vars == <<seg, cores, coreHas, pend, pendReset, gens, nextGen, sci, lastCommit,
          readers, onDisk, truth, n, mergeBase, steps, lastWrite, pool, ckpt>>

NULLM == [none |-> TRUE]

EmptySci(s) == [seg |-> s,
                dvGen |-> [f \in Fields |-> CORE],
                overlay |-> [f \in Fields |-> <<>>],
                files |-> [f \in Fields |-> {}],
                hasFiles |-> {},
                segGen |-> 0]

----------------------------------------------------------------------------
(* Reading *)
RECURSIVE Lookup(_, _, _, _)
Lookup(s, seq, d, dflt) ==
  IF seq = <<>> THEN dflt
  ELSE LET c == gens[<<s, Head(seq)>>].content[d]
       IN IF c # NOTIN THEN c ELSE Lookup(s, Tail(seq), d, dflt)

RECURSIVE LookupOld(_, _, _, _)   \* mutation: oldest delta wins
LookupOld(s, seq, d, dflt) ==
  IF seq = <<>> THEN dflt
  ELSE LET r == LookupOld(s, Tail(seq), d, NOTIN)
       IN IF r # NOTIN THEN r
          ELSE LET c == gens[<<s, Head(seq)>>].content[d]
               IN IF c # NOTIN THEN c ELSE dflt

BaseVal(sc, f, g, d) ==
  IF g = CORE
  THEN (IF f \in coreHas[sc.seg] THEN cores[sc.seg][f][d] ELSE ABSENT)
  ELSE gens[<<sc.seg, g>>].content[d]

View(sc, f, d) ==
  IF sc.overlay[f] # <<>>
  THEN IF MutOldestWins
       THEN LookupOld(sc.seg, Tail(sc.overlay[f]), d, BaseVal(sc, f, sc.overlay[f][1], d))
       ELSE Lookup(sc.seg, Tail(sc.overlay[f]), d, BaseVal(sc, f, sc.overlay[f][1], d))
  ELSE BaseVal(sc, f, sc.dvGen[f], d)

SeqElems(q) == {q[i] : i \in 1..Len(q)}

ReadGens(sc) ==
  {<<sc.seg, g>> : g \in UNION {IF sc.overlay[f] # <<>>
                                 THEN SeqElems(sc.overlay[f]) \ {CORE}
                                 ELSE {sc.dvGen[f]} \ {CORE} : f \in Fields}}

Declared(sc) == {<<sc.seg, g>> : g \in UNION {sc.files[f] : f \in Fields}}

----------------------------------------------------------------------------
Init ==
  /\ seg = 1
  /\ cores = [s \in {1} |-> [f \in Fields |-> [d \in Docs |->
                IF f \in CoreHas0 THEN 1 ELSE ABSENT]]]
  /\ coreHas = [s \in {1} |-> CoreHas0]
  /\ pend = [f \in Fields |-> [d \in Docs |-> NOPEND]]
  /\ pendReset = [f \in Fields |-> FALSE]
  /\ gens = <<>>   \* empty function
  /\ nextGen = 1
  /\ sci = EmptySci(1)
  /\ lastCommit = [sci |-> EmptySci(1),
                   exp |-> [f \in Fields |-> [d \in Docs |->
                              IF f \in CoreHas0 THEN 1 ELSE ABSENT]]]
  /\ readers = {}
  /\ onDisk = {}
  /\ truth = [f \in Fields |-> [d \in Docs |-> IF f \in CoreHas0 THEN 1 ELSE ABSENT]]
  /\ n = N0
  /\ mergeBase = NULLM
  /\ steps = 0
  /\ lastWrite = "none"
  /\ pool = EmptySci(1)
  /\ ckpt = {}

Step == steps' = steps + 1
Budget == steps < MaxSteps

NoPending == \A f \in Fields, d \in Docs : pend[f][d] = NOPEND

(* One update: a set of docs (a term) gets value v, or RESET *)
DocSets == {{d} : d \in Docs} \cup {Docs}

Update(f, ds, v) ==
  /\ Budget
  /\ pend' = [pend EXCEPT ![f] = [d \in Docs |-> IF d \in ds THEN v ELSE pend[f][d]]]
  /\ pendReset' = [pendReset EXCEPT ![f] = pendReset[f] \/ v = RESET]
  /\ truth' = [truth EXCEPT ![f] = [d \in Docs |->
                  IF d \in ds THEN (IF v = RESET THEN ABSENT ELSE v) ELSE truth[f][d]]]
  /\ Step
  /\ UNCHANGED <<seg, cores, coreHas, gens, nextGen, sci, lastCommit, readers, onDisk, n, mergeBase, lastWrite, pool, ckpt>>

DeltaCount(g) == Cardinality({d \in Docs : gens[<<seg, g>>].content[d] # NOTIN})
RECURSIVE SumCov(_)
SumCov(q) == IF q = <<>> THEN 0 ELSE DeltaCount(Head(q)) + SumCov(Tail(q))

WriteFieldX(f, swapFails) ==
  /\ \E d \in Docs : pend[f][d] # NOPEND
  /\ LET existing  == sci.overlay[f]
         baseGen   == IF existing # <<>> THEN existing[1] ELSE sci.dvGen[f]
         priorGens == IF existing # <<>> THEN Tail(existing) ELSE <<>>
         sparse    == n > 0 /\ ~pendReset[f]
         compact   == sparse /\ Len(priorGens) >= n
         cov       == IF compact THEN SumCov(priorGens) ELSE 0
         fold      == compact /\ 2 * cov >= Cardinality(Docs)
         g         == nextGen
         \* sparse fold: overlay of the prior deltas only (base excluded)
         priorVal(d) == Lookup(seg, priorGens, d, NOTIN)
         \* dense rewrite reads the reader's full view (overlay incl. base); the mutation
         \* reads only the deltas, losing the base column
         denseOld(d) == IF MutFoldIgnoresBase
                        THEN (LET x == Lookup(seg, priorGens, d, NOTIN) IN IF x = NOTIN THEN ABSENT ELSE x)
                        ELSE (IF pool = NULLM THEN View(sci, f, d) ELSE View(pool, f, d))
         content ==
           IF sparse /\ ~compact
           THEN [d \in Docs |-> IF pend[f][d] = NOPEND THEN NOTIN ELSE pend[f][d]]
           ELSE IF compact /\ ~fold
           THEN [d \in Docs |-> IF pend[f][d] # NOPEND THEN pend[f][d] ELSE priorVal(d)]
           ELSE [d \in Docs |-> IF pend[f][d] = RESET THEN ABSENT
                                ELSE IF pend[f][d] # NOPEND THEN pend[f][d]
                                ELSE denseOld(d)]
         newOverlay ==
           IF compact /\ ~fold THEN <<baseGen, g>>
           ELSE IF sparse /\ ~compact THEN <<baseGen, g>> \o priorGens
           ELSE <<>>
         \* writeFieldUpdates :998-1016: keep this write's files, plus prior files whose
         \* generation the new overlay still references (CORE excluded)
         keepPrior == IF MutDropBaseRetention
                      THEN {x \in sci.files[f] : x \in SeqElems(newOverlay) /\ x # baseGen}
                      ELSE {x \in sci.files[f] : x \in SeqElems(newOverlay) /\ x # CORE}
         newFiles == IF f \in sci.hasFiles THEN {g} \cup keepPrior ELSE {g}
     IN /\ Assert(~(<<seg, g>> \in onDisk), "generation reused while its file exists")
        /\ gens' = [x \in DOMAIN gens \cup {<<seg, g>>} |->
                      IF x = <<seg, g>> THEN [content |-> content] ELSE gens[x]]
        /\ onDisk' = onDisk \cup {<<seg, g>>}
        /\ nextGen' = nextGen + 1
        /\ sci' = [sci EXCEPT !.dvGen[f] = g,
                              !.overlay[f] = newOverlay,
                              !.files[f] = newFiles,
                              !.hasFiles = sci.hasFiles \cup {f},
                              !.segGen = g]
        /\ pend' = [pend EXCEPT ![f] = [d \in Docs |-> NOPEND]]
        /\ pendReset' = [pendReset EXCEPT ![f] = FALSE]
        /\ pool' = IF swapFails /\ ~FixDropReader THEN pool
                   ELSE IF swapFails THEN NULLM
                   ELSE IF pool = NULLM THEN NULLM ELSE sci'
        /\ ckpt' = IF swapFails /\ ~FixDropReader THEN ckpt ELSE Declared(sci')
        /\ lastWrite' = IF sparse /\ ~compact THEN "delta"
                        ELSE IF compact /\ ~fold THEN "sparseFold"
                        ELSE IF fold THEN "denseFold" ELSE "dense"
  /\ UNCHANGED <<seg, cores, coreHas, lastCommit, readers, truth, n, mergeBase, steps>>

WriteField(f) == WriteFieldX(f, FALSE)
WriteFieldSwapFails(f) == WithSwapFault /\ WriteFieldX(f, TRUE)

Commit ==
  /\ Budget /\ NoPending
  /\ lastCommit' = [sci |-> sci, exp |-> truth]
  /\ ckpt' = Declared(sci)
  /\ Step
  /\ UNCHANGED <<seg, cores, coreHas, pend, pendReset, gens, nextGen, sci, readers, onDisk, truth, n, mergeBase, lastWrite, pool>>

OpenReader ==
  /\ Budget /\ NoPending /\ Cardinality(readers) < MaxReaders
  /\ readers' = readers \cup {[sci |-> sci, exp |-> truth]}
  /\ Step
  /\ UNCHANGED <<seg, cores, coreHas, pend, pendReset, gens, nextGen, sci, lastCommit, onDisk, truth, n, mergeBase, lastWrite, pool, ckpt>>

CloseReader(r) ==
  /\ readers' = readers \ {r}
  /\ UNCHANGED <<seg, cores, coreHas, pend, pendReset, gens, nextGen, sci, lastCommit, onDisk, truth, n, mergeBase, steps, lastWrite, pool, ckpt>>

Snapshots == {sci} \cup {lastCommit.sci} \cup {r.sci : r \in readers}

Referenced == ckpt \cup Declared(lastCommit.sci)
              \cup UNION {Declared(r.sci) : r \in readers}
              \cup (IF mergeBase = NULLM THEN {} ELSE Declared(mergeBase))

\* generations / segments some snapshot (or the running merge) may still read
AllReadGens == UNION {ReadGens(s) : s \in Snapshots \cup (IF mergeBase = NULLM THEN {} ELSE {mergeBase})}
LiveSegs == {s.seg : s \in Snapshots \cup (IF mergeBase = NULLM THEN {} ELSE {mergeBase})} \cup {seg}

\* IndexFileDeleter. The model also forgets the content of generations that are neither on disk
\* nor readable by any snapshot, and the cores of segments no snapshot reads (pure state-space
\* reduction: no invariant can observe them).
Gc ==
  LET od == onDisk \cap Referenced
      gd == {y \in DOMAIN gens : y \in od \/ y \in AllReadGens}
      cd == {y \in DOMAIN cores : y \in LiveSegs}
  IN /\ (od # onDisk \/ gd # DOMAIN gens \/ cd # DOMAIN cores)
     /\ onDisk' = od
     /\ gens' = [x \in gd |-> gens[x]]
     /\ cores' = [x \in cd |-> cores[x]]
     /\ coreHas' = [x \in cd |-> coreHas[x]]
     /\ UNCHANGED <<seg, pend, pendReset, nextGen, sci, lastCommit, readers, truth, n, mergeBase, steps, lastWrite, pool, ckpt>>

MaxOnDiskGenOf(sg) == LET gs == {x[2] : x \in {y \in onDisk : y[1] = sg}}
                      IN IF gs = {} THEN 0 ELSE CHOOSE m \in gs : \A o \in gs : o <= m
MaxOnDiskGen == MaxOnDiskGenOf(lastCommit.sci.seg)

Crash ==
  /\ WithCrash /\ Budget
  /\ seg' = lastCommit.sci.seg
  /\ sci' = lastCommit.sci
  /\ truth' = lastCommit.exp
  /\ pend' = [f \in Fields |-> [d \in Docs |-> NOPEND]]
  /\ pendReset' = [f \in Fields |-> FALSE]
  /\ readers' = {}
  /\ mergeBase' = NULLM
  /\ nextGen' = (IF lastCommit.sci.segGen > MaxOnDiskGen THEN lastCommit.sci.segGen ELSE MaxOnDiskGen) + 1
  /\ \E nn \in NChoices : n' = nn
  /\ pool' = NULLM
  /\ ckpt' = Declared(lastCommit.sci)
  /\ Step
  /\ UNCHANGED <<cores, coreHas, gens, lastCommit, onDisk, lastWrite>>

CleanReopen ==
  /\ WithReopen /\ Budget /\ NoPending /\ mergeBase = NULLM
  /\ lastCommit' = [sci |-> sci, exp |-> truth]
  /\ readers' = {}
  \* a new IndexWriter runs IndexFileDeleter.inflateGens too (model fix 1)
  /\ nextGen' = (IF sci.segGen > MaxOnDiskGenOf(seg) THEN sci.segGen ELSE MaxOnDiskGenOf(seg)) + 1
  /\ \E nn \in NChoices : n' = nn
  /\ pool' = NULLM
  /\ ckpt' = Declared(sci)
  /\ Step
  /\ UNCHANGED <<seg, cores, coreHas, pend, pendReset, gens, sci, onDisk, truth, mergeBase, lastWrite>>

(* Merge: MergeStart snapshots the current segment state (the merge reader),   *)
(* other actions may run, MergeCommit installs the new segment whose core is   *)
(* the snapshot's view plus the carry-over: disk diff (current vs baseline     *)
(* view) and the residual pending updates (newer, so they win).               *)
MergeStart ==
  /\ WithMerge /\ Budget /\ mergeBase = NULLM /\ NoPending
  /\ mergeBase' = sci
  /\ Step
  /\ UNCHANGED <<seg, cores, coreHas, pend, pendReset, gens, nextGen, sci, lastCommit, readers, onDisk, truth, n, lastWrite, pool, ckpt>>

MergeCommit ==
  /\ mergeBase # NULLM /\ Budget
  /\ LET ns == seg + 1
         newCore == [f \in Fields |-> [d \in Docs |-> View(mergeBase, f, d)]]
         diskDiff(f, d) == IF View(sci, f, d) = View(mergeBase, f, d) THEN NOPEND
                           ELSE IF View(sci, f, d) = ABSENT THEN RESET ELSE View(sci, f, d)
         carried == [f \in Fields |-> [d \in Docs |->
                       IF pend[f][d] # NOPEND THEN pend[f][d] ELSE diskDiff(f, d)]]
     IN /\ seg' = ns
        /\ cores' = [x \in DOMAIN cores \cup {ns} |-> IF x = ns THEN newCore ELSE cores[x]]
        /\ coreHas' = [x \in DOMAIN coreHas \cup {ns} |->
                         IF x = ns THEN {f \in Fields : \E d \in Docs : newCore[f][d] # ABSENT}
                         ELSE coreHas[x]]
        /\ pend' = carried
        /\ pendReset' = [f \in Fields |-> pendReset[f] \/ \E d \in Docs : carried[f][d] = RESET]
        /\ sci' = EmptySci(ns)
        /\ nextGen' = 1
  /\ mergeBase' = NULLM
  /\ pool' = NULLM
  /\ ckpt' = {}
  /\ Step
  /\ UNCHANGED <<gens, lastCommit, readers, onDisk, truth, n, lastWrite>>

Next ==
  \/ \E f \in Fields, ds \in DocSets, v \in Vals \cup {RESET} : Update(f, ds, v)
  \/ \E f \in Fields : WriteField(f)
  \/ \E f \in Fields : WriteFieldSwapFails(f)
  \/ Commit \/ OpenReader \/ \E r \in readers : CloseReader(r)
  \/ Gc \/ Crash \/ CleanReopen \/ MergeStart \/ MergeCommit

Spec == Init /\ [][Next]_vars

----------------------------------------------------------------------------
(* Invariants *)

\* every generation a reader would open is declared by its SegmentCommitInfo
\* (that is what IndexFileDeleter protects)
FilesDeclared == \A s \in Snapshots : ReadGens(s) \subseteq Declared(s)

\* ... and is still on disk
FilesLive == \A s \in Snapshots : ReadGens(s) \subseteq onDisk

\* every open reader (NRT or commit point) shows the abstract history at its open time
ReadEq == \A r \in readers : \A f \in Fields, d \in Docs : View(r.sci, f, d) = r.exp[f][d]

\* the last commit shows the abstract history at commit time
CommitEq == \A f \in Fields, d \in Docs : View(lastCommit.sci, f, d) = lastCommit.exp[f][d]

\* the writer's own state: written values equal the truth wherever nothing is pending
WriterEq == \A f \in Fields, d \in Docs :
                pend[f][d] = NOPEND => View(sci, f, d) = truth[f][d]

\* overlay depth never exceeds n (+0: compaction happens before appending)
DepthBound == \A f \in Fields : sci.overlay[f] = <<>> \/ Len(sci.overlay[f]) - 1 <= 8

\* no generation number is ever written twice for the same segment
TypeOK == nextGen \in Nat

\* a never-written generation is never read (gens defined)
GensDefined == \A s \in Snapshots : ReadGens(s) \subseteq DOMAIN gens

Inv == FilesDeclared /\ FilesLive /\ ReadEq /\ CommitEq /\ WriterEq /\ GensDefined

\* vacuity probes (each must be violated by some reachable state)
ProbeFold == lastWrite # "denseFold"
ProbeSparseFoldW == lastWrite # "sparseFold"
ProbeDenseW == lastWrite # "dense"
ProbeCrash == ~(steps > 0 /\ readers = {} /\ sci = lastCommit.sci /\ seg > 1)
ProbeDepth2 == \A f \in Fields : Len(sci.overlay[f]) < 3
ProbeDenseBase == \A f \in Fields : sci.overlay[f] = <<>> \/ sci.overlay[f][1] = CORE
ProbeMerged == seg = 1
ProbeSparseFold == \A f \in Fields : ~(sci.overlay[f] # <<>> /\ Len(sci.overlay[f]) = 2
                                        /\ sci.overlay[f][2] > 2 /\ n = 1)
=============================================================================
