------------------------------- MODULE HistMgr -------------------------------
(***************************************************************************)
(* Lifecycle of HistogramCollectorManager's leafBulkCollected map across  *)
(* searches that reuse one manager.                                       *)
(*                                                                         *)
(* Model element            -> Java (main f794ff5a505c, lucene/sandbox/...)*)
(*  map                     -> HistogramCollectorManager.leafBulkCollected *)
(*                             (:56, created once at :83)                  *)
(*  Start(s)                -> IndexSearcher.search(q, manager): one       *)
(*                             newCollector() per slice (:88, same map)    *)
(*  Collect(s, l, p)        -> HistogramCollector.getLeafCollector(ctx) on *)
(*                             partition p of leaf l, point-tree path      *)
(*                             (HistogramCollector.java:86-99):            *)
(*                             putIfAbsent(ctx) == null -> bulk-collect    *)
(*                             the WHOLE leaf; else skip; then             *)
(*                             CollectionTerminatedException               *)
(*  Reduce(s)               -> HistogramCollectorManager.reduce (:91-101): *)
(*                             Variant "stock" never clears the map,       *)
(*                             Variant "fix" clears it first (fix.diff)    *)
(*  Fail(s)                 -> any exception before reduce (another        *)
(*                             collector of a MultiCollectorManager, a     *)
(*                             timeout, an IOException): reduce not called *)
(*  Reopen                  -> DirectoryReader.openIfChanged: new          *)
(*                             LeafReaderContext objects (new identities)  *)
(*  Leaf identity (gen, i)  -> LeafReaderContext uses identity equals      *)
(* Property: every search that reaches reduce counts each leaf of its     *)
(* reader exactly once (ExactlyOnce), i.e. equals a fresh manager.        *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS NLeaves, NParts, NSearches, Variant, AllowFail, AllowConcurrent,
          AllowReopen, MaxGen, NoDedupMutant

Searches == 1..NSearches
Idx == 1..NLeaves
Parts == 1..NParts

VARIABLES map, gen, phase, sgen, pending, counted

vars == <<map, gen, phase, sgen, pending, counted>>

Init ==
  /\ map = {}
  /\ gen = 0
  /\ phase = [s \in Searches |-> "idle"]
  /\ sgen = [s \in Searches |-> 0]
  /\ pending = [s \in Searches |-> {}]
  /\ counted = [s \in Searches |-> [i \in Idx |-> 0]]

Collecting == {s \in Searches : phase[s] = "collecting"}

\* searches run in program order 1, 2, 3 (the caller reuses one manager)
Start(s) ==
  /\ phase[s] = "idle"
  /\ \A t \in Searches : t < s => phase[t] # "idle"
  /\ AllowConcurrent \/ Collecting = {}
  /\ phase' = [phase EXCEPT ![s] = "collecting"]
  /\ sgen' = [sgen EXCEPT ![s] = gen]
  /\ pending' = [pending EXCEPT ![s] = Idx \X Parts]
  /\ UNCHANGED <<map, gen, counted>>

Collect(s, i, p) ==
  /\ phase[s] = "collecting"
  /\ <<i, p>> \in pending[s]
  /\ LET leaf == <<sgen[s], i>> IN
       IF NoDedupMutant \/ leaf \notin map
         THEN /\ map' = map \cup {leaf}
              /\ counted' = [counted EXCEPT ![s][i] = @ + 1]
         ELSE UNCHANGED <<map, counted>>
  /\ pending' = [pending EXCEPT ![s] = @ \ {<<i, p>>}]
  /\ UNCHANGED <<gen, phase, sgen>>

Fail(s) ==
  /\ AllowFail
  /\ phase[s] = "collecting"
  /\ phase' = [phase EXCEPT ![s] = "failed"]
  /\ pending' = [pending EXCEPT ![s] = {}]
  /\ UNCHANGED <<map, gen, sgen, counted>>

Reduce(s) ==
  /\ phase[s] = "collecting"
  /\ pending[s] = {}
  /\ phase' = [phase EXCEPT ![s] = "reduced"]
  /\ map' = IF Variant = "fix" THEN {} ELSE map
  /\ UNCHANGED <<gen, sgen, pending, counted>>

Reopen ==
  /\ AllowReopen
  /\ gen < MaxGen
  /\ Collecting = {}
  /\ gen' = gen + 1
  /\ UNCHANGED <<map, phase, sgen, pending, counted>>

Next ==
  \/ \E s \in Searches : Start(s) \/ Fail(s) \/ Reduce(s)
  \/ \E s \in Searches, i \in Idx, p \in Parts : Collect(s, i, p)
  \/ Reopen

Spec == Init /\ [][Next]_vars

TypeOK ==
  /\ map \subseteq ((0..MaxGen) \X Idx)
  /\ phase \in [Searches -> {"idle", "collecting", "reduced", "failed"}]

\* the safety property: a search that returns a result counted every leaf once
ExactlyOnce ==
  \A s \in Searches : phase[s] = "reduced" => \A i \in Idx : counted[s][i] = 1

\* retention face: the map holds at most the leaves of one reader
MapHoldsOneReader == Cardinality(map) <= NLeaves

\* vacuity probes (must be VIOLATED, i.e. the states are reachable)
NotAllReduced == ~(\A s \in Searches : phase[s] = "reduced")
=============================================================================
