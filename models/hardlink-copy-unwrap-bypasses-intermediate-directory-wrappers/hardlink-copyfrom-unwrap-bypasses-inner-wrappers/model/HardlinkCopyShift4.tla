---------------------------- MODULE HardlinkCopyShift4 ----------------------------
(***************************************************************************)
(* Reference model of Directory#copyFrom through chains of directory       *)
(* wrappers, with HardlinkCopyDirectoryWrapper somewhere in the target     *)
(* chain.  One copy target.copyFrom(source, "s", "d") is modelled after    *)
(* the source file "s" was written through the source chain.               *)
(*                                                                         *)
(* Model element -> Java (Lucene main-a9779ef @ a9779ef3c193)              *)
(*  chain = <<outermost, ..., innermost>> over an FSDirectory               *)
(*  "Plain"  FilterDirectory subclass overriding nothing: copyFrom routes   *)
(*           through createOutput (FilterDirectory.java:120-128,            *)
(*           Directory.java:189-198 copyThroughCreateOutput)                *)
(*  "Xor"    conforming FilterDirectory transforming bytes in createOutput  *)
(*           and openInput (the 'magic bytes' directory of #14985; an       *)
(*           encrypting directory); copyFrom inherited (routes)             *)
(*  "Count"  FilterDirectory recording names in createOutput, copyFrom      *)
(*           inherited (ByteWritesTrackingDirectoryWrapper.java:49-53,      *)
(*           MockDirectoryWrapper.java:690-712, DirectIODirectory.java:186) *)
(*  "Known"  FilterDirectory whose namespace is the files created through   *)
(*           it: openInput of other names fails (a downstream index         *)
(*           directory of this shape exists); copyFrom inherited            *)
(*  "Track"  TrackingDirectoryWrapper: createOutput records (:40-44),       *)
(*           copyFrom = in.copyFrom then record dest (:55-58)               *)
(*  "LockV"  LockValidatingDirectoryWrapper: createOutput/copyFrom call     *)
(*           writeLock.ensureValid() then delegate (:41-51)                 *)
(*  "NRT"    NRTCachingDirectory: a source file may live in its RAM cache   *)
(*           (createOutput :155-167, openInput :190-201); target side never *)
(*           caches in this model (abstraction, see README)                 *)
(*  "Switch" FileSwitchDirectory: NOT a FilterDirectory, so                 *)
(*           FilterDirectory.unwrap (FilterDirectory.java:39-44) stops at   *)
(*           it; copyFrom routes to the owning sub-directory (:241-244);   *)
(*           modelled with a single sub-directory = rest of the chain       *)
(*  "Hard"   HardlinkCopyDirectoryWrapper.copyFrom                          *)
(*           (HardlinkCopyDirectoryWrapper.java:50-96): unwrap(from) and    *)
(*           unwrap(this) (:52-53); if both are FSDirectory and the source  *)
(*           file is readable on disk (:64) -> Files.createLink (:68);      *)
(*           otherwise getDelegate().copyFrom (:88)                         *)
(* Contents: a logical value in 0..3; Xor adds 1 (mod 4) on write and       *)
(* subtracts 1 on read.  NoFile = the read fails (NoSuchFileException).     *)
(* Laws (Directory.java:174-180, FilterDirectory.java:120-123):             *)
(*  L1 content: target.openInput(d) reads the bytes source.openInput(s)     *)
(*     reads;                                                               *)
(*  L2 bookkeeping: every target layer whose createOutput does per-file     *)
(*     bookkeeping (Count, Known, Track) covers d, and every LockV layer    *)
(*     validated its lock for this copy.                                    *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets, TLC

CONSTANTS TKinds, SKinds, MaxT, MaxS, Fix

NoFile == 99
FilterKinds == {"Plain", "Xor", "Count", "Known", "Track", "LockV", "NRT", "Hard"}
BookKinds == {"Count", "Known", "Track"}

Enc(x) == (x + 1) % 4
Dec(x) == IF x = NoFile THEN NoFile ELSE (x + 3) % 4

SeqsUpTo(K, n) == UNION {[1..m -> K] : m \in 0..n}

(* --- source side: where "s" lives and with which raw value -------------- *)
(* cacheAt = 0: on the source FSDirectory; cacheAt = j: in the RAM cache of  *)
(* the NRT layer at position j (the first NRT that cached the write).        *)
CacheChoices(S) == {0} \cup {j \in 1..Len(S) : S[j] = "NRT"}

RECURSIVE EncodeDown(_, _, _, _)
EncodeDown(C, i, stop, x) == IF i >= stop THEN x
                             ELSE EncodeDown(C, i + 1, stop, IF C[i] = "Xor" THEN Enc(x) ELSE x)

SrcStored(S, cacheAt, v) == EncodeDown(S, 1, IF cacheAt = 0 THEN Len(S) + 1 ELSE cacheAt, v)

(* Reading "s" through the source chain from layer i (all source layers      *)
(* created "s", so Known layers serve it).                                    *)
RECURSIVE SrcRead(_, _, _, _)
SrcRead(S, i, cacheAt, raw) ==
  IF i > Len(S) THEN (IF cacheAt = 0 THEN raw ELSE NoFile)
  ELSE IF S[i] = "NRT" /\ cacheAt = i THEN raw
  ELSE IF S[i] = "Xor" THEN Dec(SrcRead(S, i + 1, cacheAt, raw))
  ELSE SrcRead(S, i + 1, cacheAt, raw)

(* --- target side ------------------------------------------------------- *)
(* A target world: [fs |-> raw value of "d" on the target FSDirectory or      *)
(* NoFile, rec |-> set of layer positions that recorded/validated "d",        *)
(* linked |-> TRUE iff Files.createLink was used].                            *)

(* createOutput("d") through T from layer i writing logical value x.         *)
RECURSIVE WriteDown(_, _, _, _)
WriteDown(T, i, x, rec) ==
  IF i > Len(T) THEN [fs |-> x, rec |-> rec, linked |-> FALSE]
  ELSE WriteDown(T, i + 1, IF T[i] = "Xor" THEN Enc(x) ELSE x,
                 IF T[i] \in BookKinds \cup {"LockV"} THEN rec \cup {i} ELSE rec)

ReachesFS(C, i) == \A k \in i..Len(C) : C[k] \in FilterKinds

(* Which hard-link guard is used at a Hard layer at position i.              *)
(*  "none": the current code (unwrap both chains).                            *)
(*  "F1":   link only if Hard directly wraps the FSDirectory and the source   *)
(*          is an FSDirectory itself.                                         *)
(*  "F2":   link only if Hard directly wraps the FSDirectory; source side     *)
(*          still unwrapped.                                                  *)
(*  "F3":   link only if every layer between Hard and the FSDirectory is      *)
(*          Plain/Hard and every source layer leaves openInput alone           *)
(*          (Plain, Hard, Count, Track, LockV).                               *)
(*  "F4":   like F3 on the target side (walk one level at a time through     *)
(*          Plain/Hard only); the source side still unwrapped (status quo).   *)
LinkGuard(f, T, i, S) ==
  CASE f = "none" -> ReachesFS(T, i + 1) /\ ReachesFS(S, 1)
    [] f = "F1"   -> i = Len(T) /\ Len(S) = 0
    [] f = "F2"   -> i = Len(T) /\ ReachesFS(S, 1)
    [] f = "F3"   -> (\A k \in (i+1)..Len(T) : T[k] \in {"Plain", "Hard"})
                       /\ (\A k \in 1..Len(S) : S[k] \in {"Plain", "Hard", "Count", "Track", "LockV"})
    [] f = "F4"   -> (\A k \in (i+1)..Len(T) : T[k] \in {"Plain", "Hard"}) /\ ReachesFS(S, 1)

RECURSIVE CopyAt(_, _, _, _, _, _, _)
CopyAt(f, T, i, S, cacheAt, raw, rec) ==
  LET val == SrcRead(S, 1, cacheAt, raw) IN
  IF i > Len(T) THEN
     (* FSDirectory.copyFrom = BaseDirectory -> copyThroughCreateOutput *)
     [fs |-> val, rec |-> rec, linked |-> FALSE]
  ELSE CASE T[i] \in {"Plain", "Xor", "Count", "Known", "NRT"} ->
              (* inherited FilterDirectory.copyFrom: copyThroughCreateOutput at layer i *)
              WriteDown(T, i, val, rec)
         [] T[i] = "Track" ->
              LET w == CopyAt(f, T, i + 1, S, cacheAt, raw, rec)
              IN [w EXCEPT !.rec = @ \cup {i}]
         [] T[i] = "LockV" -> CopyAt(f, T, i + 1, S, cacheAt, raw, rec \cup {i})
         [] T[i] = "Switch" -> CopyAt(f, T, i + 1, S, cacheAt, raw, rec)
         [] T[i] = "Hard" ->
              IF LinkGuard(f, T, i, S) /\ cacheAt = 0   \* Files.isReadable(fromPath.resolve(src))
              THEN [fs |-> raw, rec |-> rec, linked |-> TRUE]
              ELSE CopyAt(f, T, i + 1, S, cacheAt, raw, rec)

(* Reading "d" back through T.                                                 *)
RECURSIVE TgtRead(_, _, _)
TgtRead(T, i, w) ==
  IF i > Len(T) THEN w.fs
  ELSE CASE T[i] = "Xor" -> Dec(TgtRead(T, i + 1, w))
         [] T[i] = "Known" -> IF i \in w.rec THEN TgtRead(T, i + 1, w) ELSE NoFile
         [] OTHER -> TgtRead(T, i + 1, w)

(* Reference: the same copy routed through createOutput at the top of T.     *)
RefRec(T) == {i \in 1..Len(T) : T[i] \in BookKinds \cup {"LockV"}}

Configs == {c \in [t : SeqsUpTo(TKinds, MaxT), s : SeqsUpTo(SKinds, MaxS), ca : 0..MaxS] :
              (\E i \in 1..Len(c.t) : c.t[i] = "Hard") /\ c.ca \in CacheChoices(c.s)}

V == 2
ResultF(f, c) == CopyAt(f, c.t, 1, c.s, c.ca, SrcStored(c.s, c.ca, V), {})
Expected(c) == SrcRead(c.s, 1, c.ca, SrcStored(c.s, c.ca, V))
L1F(f, c) == TgtRead(c.t, 1, ResultF(f, c)) = Expected(c)
L2F(f, c) == RefRec(c.t) \subseteq ResultF(f, c).rec
LawfulF(f, c) == L1F(f, c) /\ L2F(f, c)
FirstHard(t) == CHOOSE k \in 1..Len(t) : t[k] = "Hard" /\ \A j \in 1..(k-1) : t[j] # "Hard"
Symmetric(c) == \* same transform composition below the outermost Hard layer and on the source side
  Cardinality({k \in (FirstHard(c.t)+1)..Len(c.t) : c.t[k] = "Xor"}) % 4
    = Cardinality({k \in 1..Len(c.s) : c.s[k] = "Xor"}) % 4

Fixes == {"none", "F1", "F2", "F3", "F4"}
Count(P(_)) == Cardinality({c \in Configs : P(c)})

ASSUME PrintT(<<"configs", Cardinality(Configs)>>)
ASSUME \A f \in Fixes :
  LET linked(c) == ResultF(f, c).linked
      l1v(c) == ~L1F(f, c)
      l2v(c) == ~L2F(f, c)
      l1vSym(c) == ~L1F(f, c) /\ Symmetric(c)
      l1vSymNoBook(c) == ~L1F(f, c) /\ Symmetric(c) /\ \A k \in 1..Len(c.t) : c.t[k] # "Known"
      viol(c) == ~LawfulF(f, c)
      lost(c) == ResultF("none", c).linked /\ LawfulF("none", c) /\ ~ResultF(f, c).linked
      lawfulLink(c) == linked(c) /\ LawfulF(f, c)
  IN PrintT(<<f, "linked", Count(linked), "lawful_links", Count(lawfulLink),
              "L1_violations", Count(l1v), "L1_violations_symmetric", Count(l1vSym), "L1_violations_symmetric_without_Known", Count(l1vSymNoBook),
              "L2_violations", Count(l2v), "any_violation", Count(viol),
              "lost_lawful_links_vs_none", Count(lost)>>)

(* Every violation of the current code involves a hard link (the fallback  *)
(* path is lawful); checked as an ASSUME so TLC fails if it is false.       *)
ASSUME \A c \in Configs : ~LawfulF("none", c) => ResultF("none", c).linked
(* The fixed guards are lawful on every configuration.                       *)
ASSUME \A f \in Fixes : PrintT(<<f, "sound_L1", \A c \in Configs : L1F(f, c), "sound_L2", \A c \in Configs : L2F(f, c)>>)

VARIABLE cfg
Init == cfg \in Configs
Next == UNCHANGED cfg

InvL1 == L1F(Fix, cfg)
InvL2 == L2F(Fix, cfg)
(* Vacuity probe: must be violated (a hard link is actually taken somewhere) *)
ProbeNeverLinks == ResultF(Fix, cfg).linked = FALSE
=============================================================================
