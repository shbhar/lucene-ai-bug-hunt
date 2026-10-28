---------------------------- MODULE BBDir ----------------------------
(***************************************************************************)
(* Two-thread model of ByteBuffersDirectory's file map (bytebuffers-output-block-expansion-recycler-and-view-aliasing step 4).       *)
(*                                                                         *)
(* Model element -> Java line (lucene/core/src/java/org/apache/lucene/      *)
(*   store/ByteBuffersDirectory.java @ a9779ef3c193):                       *)
(*   files            -> :93  ConcurrentHashMap<String, FileEntry> files    *)
(*   Create           -> :155-162 createOutput: files.putIfAbsent(name, e)  *)
(*   (createTempOutput :165-175 draws AtomicLong-unique names: not modelled) *)
(*   Delete           -> :130-136 deleteFile: files.remove(name)            *)
(*   RenGet  (r1)     -> :181 FileEntry file = files.get(source)            *)
(*   RenPut  (r2)     -> :185 files.putIfAbsent(dest, file)                 *)
(*   RenCas  (r3)     -> :188 files.remove(source, file)                    *)
(*   RenDrop (r4)     -> :191 files.remove(source)                          *)
(* Each ConcurrentHashMap call is one atomic step (CHM ops are atomic per   *)
(* key); the Java method bodies are NOT atomic, so a step of the other      *)
(* thread may run between r1..r4.                                           *)
(*                                                                         *)
(* Ghost state: every FileEntry gets a fresh id. An id leaves the map       *)
(* legitimately only through Delete (the caller named it) or RenCas (it     *)
(* moved to dest). NoLostFile: every created id is either legitimately      *)
(* removed or still bound to some name.                                     *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS Threads, Names, MaxOps, UseFix

VARIABLES files, nextId, created, legitGone, pc, src, dst, fid, ops

vars == <<files, nextId, created, legitGone, pc, src, dst, fid, ops>>

NONE == 0

Init == /\ files = [n \in Names |-> NONE]
        /\ nextId = 1
        /\ created = {}
        /\ legitGone = {}
        /\ pc = [t \in Threads |-> "idle"]
        /\ src = [t \in Threads |-> CHOOSE n \in Names : TRUE]
        /\ dst = [t \in Threads |-> CHOOSE n \in Names : TRUE]
        /\ fid = [t \in Threads |-> NONE]
        /\ ops = [t \in Threads |-> 0]

Idle(t) == pc[t] = "idle" /\ ops[t] < MaxOps

\* createOutput(n): putIfAbsent; FileAlreadyExistsException when bound.
Create(t, n) ==
  /\ Idle(t)
  /\ ops' = [ops EXCEPT ![t] = @ + 1]
  /\ IF files[n] = NONE
       THEN /\ files' = [files EXCEPT ![n] = nextId]
            /\ created' = created \cup {nextId}
            /\ nextId' = nextId + 1
       ELSE UNCHANGED <<files, created, nextId>>
  /\ UNCHANGED <<legitGone, pc, src, dst, fid>>

\* deleteFile(n): remove(n); NoSuchFileException when unbound.
Delete(t, n) ==
  /\ Idle(t)
  /\ ops' = [ops EXCEPT ![t] = @ + 1]
  /\ IF files[n] # NONE
       THEN /\ legitGone' = legitGone \cup {files[n]}
            /\ files' = [files EXCEPT ![n] = NONE]
       ELSE UNCHANGED <<legitGone, files>>
  /\ UNCHANGED <<nextId, created, pc, src, dst, fid>>

\* rename(a, b): four CHM steps.
RenStart(t, a, b) ==
  /\ Idle(t) /\ a # b
  /\ ops' = [ops EXCEPT ![t] = @ + 1]
  /\ src' = [src EXCEPT ![t] = a]
  /\ dst' = [dst EXCEPT ![t] = b]
  /\ pc' = [pc EXCEPT ![t] = "r1"]
  /\ UNCHANGED <<files, nextId, created, legitGone, fid>>

RenGet(t) ==
  /\ pc[t] = "r1"
  /\ IF files[src[t]] = NONE
       THEN /\ pc' = [pc EXCEPT ![t] = "idle"]          \* NoSuchFileException
            /\ UNCHANGED fid
       ELSE /\ fid' = [fid EXCEPT ![t] = files[src[t]]]
            /\ pc' = [pc EXCEPT ![t] = "r2"]
  /\ UNCHANGED <<files, nextId, created, legitGone, src, dst, ops>>

RenPut(t) ==
  /\ pc[t] = "r2"
  /\ IF files[dst[t]] # NONE
       THEN /\ pc' = [pc EXCEPT ![t] = "idle"]          \* FileAlreadyExistsException
            /\ UNCHANGED files
       ELSE /\ files' = [files EXCEPT ![dst[t]] = fid[t]]
            /\ pc' = [pc EXCEPT ![t] = "r3"]
  /\ UNCHANGED <<nextId, created, legitGone, src, dst, fid, ops>>

RenCas(t) ==
  /\ pc[t] = "r3"
  /\ IF files[src[t]] = fid[t]
       THEN /\ files' = [files EXCEPT ![src[t]] = NONE]
            /\ pc' = [pc EXCEPT ![t] = IF UseFix THEN "idle" ELSE "r4"]
       ELSE /\ pc' = [pc EXCEPT ![t] = "idle"]          \* IllegalStateException
            /\ UNCHANGED files
  /\ UNCHANGED <<nextId, created, legitGone, src, dst, fid, ops>>

\* The trailing, unconditional files.remove(source).
RenDrop(t) ==
  /\ pc[t] = "r4"
  /\ files' = [files EXCEPT ![src[t]] = NONE]
  /\ pc' = [pc EXCEPT ![t] = "idle"]
  /\ UNCHANGED <<nextId, created, legitGone, src, dst, fid, ops>>

Next == \E t \in Threads :
          \/ \E n \in Names : Create(t, n) \/ Delete(t, n)
          \/ \E a, b \in Names : RenStart(t, a, b)
          \/ RenGet(t) \/ RenPut(t) \/ RenCas(t) \/ RenDrop(t)

Spec == Init /\ [][Next]_vars

Bound(i) == \E n \in Names : files[n] = i

\* Safety: no FileEntry disappears unless a caller deleted it or it was renamed away.
NoLostFile == \A i \in created : i \in legitGone \/ Bound(i)

\* Vacuity probe: must be violated (some rename completes).
ProbeRenameCompletes == ~(\E t \in Threads : pc[t] = "r4" \/ (UseFix /\ pc[t] = "r3"))

TypeOK == /\ files \in [Names -> 0..(MaxOps * Cardinality(Threads) + 1)]
          /\ pc \in [Threads -> {"idle", "r1", "r2", "r3", "r4"}]
=============================================================================
