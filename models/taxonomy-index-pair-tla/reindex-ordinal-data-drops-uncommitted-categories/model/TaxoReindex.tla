----------------------------- MODULE TaxoReindex -----------------------------
(***************************************************************************)
(* ReindexingEnrichedDirectoryTaxonomyWriter.reindexWithNewOrdinalData vs *)
(* the main index that references taxonomy ordinals.                       *)
(*                                                                         *)
(* Lucene main @ ea57360912ed, file:line map (facet module,                *)
(* taxonomy/directory/ReindexingEnrichedDirectoryTaxonomyWriter.java):     *)
(*   Snap     :60-63,91-94 recordPathsInOrder(getDirectory()) opens a       *)
(*            DirectoryTaxonomyReader on the Directory = the LAST COMMIT    *)
(*   Del      :97 deleteAll() (DirectoryTaxonomyWriter.java:902-919)        *)
(*   ReAdd    :100-102 addCategory for each recorded path, in ordinal order *)
(*   Commit   :103 commit()                                                 *)
(*   AddCat   DirectoryTaxonomyWriter.java:381-402 (NRT-visible at once,    *)
(*            durable only after commit)                                    *)
(*   Contract :82-86 "The ordinals themselves will be preserved, so the     *)
(*            caller does not need to update references to them in the     *)
(*            main index."                                                  *)
(* The whole reindex is synchronized on the writer (:87), so it is one      *)
(* atomic step here; only what happens before it matters.                   *)
(***************************************************************************)
EXTENDS Integers, Sequences, FiniteSets

CONSTANTS NLabels, MaxDocs, SnapFrom   \* SnapFrom: "commit" (as coded) | "writer" (fix)

VARIABLES taxo, committed, docs, reindexed, ipc, iord

vars == <<taxo, committed, docs, reindexed, ipc, iord>>
Labels == 1..NLabels
Known(l) == \E i \in 1..Len(taxo) : taxo[i] = l
Ord(l) == CHOOSE i \in 0..(Len(taxo) - 1) : taxo[i + 1] = l

Init == /\ taxo = <<0>> /\ committed = <<0>> /\ docs = <<>> /\ reindexed = FALSE
        /\ ipc = "pick" /\ iord = 0

AddCat(l) ==
  /\ ipc = "pick" /\ Len(docs) < MaxDocs
  /\ IF Known(l) THEN iord' = Ord(l) /\ UNCHANGED taxo
                 ELSE taxo' = Append(taxo, l) /\ iord' = Len(taxo)
  /\ ipc' = "doc"
  /\ UNCHANGED <<committed, docs, reindexed>>

AddDoc ==   \* a main-index document referencing ordinal iord, meant for label taxo[iord+1]
  /\ ipc = "doc"
  /\ docs' = Append(docs, [ord |-> iord, label |-> taxo[iord + 1]])
  /\ ipc' = "pick"
  /\ UNCHANGED <<taxo, committed, reindexed, iord>>

Commit ==
  /\ committed' = taxo
  /\ UNCHANGED <<taxo, docs, reindexed, ipc, iord>>

Reindex ==   \* atomic (synchronized); snapshot, deleteAll, re-add, commit
  /\ ~reindexed /\ ipc = "pick"
  /\ LET snap == IF SnapFrom = "commit" THEN committed ELSE taxo
     IN /\ taxo' = snap /\ committed' = snap
  /\ reindexed' = TRUE
  /\ UNCHANGED <<docs, ipc, iord>>

Next == (\E l \in Labels : AddCat(l)) \/ AddDoc \/ Commit \/ Reindex
Spec == Init /\ [][Next]_vars

\* every main-index document still resolves to the label it was indexed with
OrdinalsPreserved ==
  \A k \in 1..Len(docs) : docs[k].ord < Len(taxo) /\ taxo[docs[k].ord + 1] = docs[k].label
ProbeReindexAfterDocs == ~(reindexed /\ Len(docs) >= 2 /\ Len(taxo) >= 3)
=============================================================================
