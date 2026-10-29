---------------------------- MODULE OfflineDictLoad ----------------------------
(***************************************************************************)
(* Resource-lifecycle model of hunspell Dictionary construction with the   *)
(* offline SortingStrategy (Lucene main 645d4fe14a20), and of              *)
(* ICUTokenizerFactory.parseRules, with an exception allowed at every      *)
(* step that can throw.  Question: after the constructor / inform() has    *)
(* thrown, is every handle it opened closed and every temp file it created *)
(* deleted?                                                                *)
(*                                                                         *)
(* Mapping, model element -> Java (paths relative to the checkout):        *)
(*  D = lucene/analysis/common/src/java/org/apache/lucene/analysis/        *)
(*      hunspell/Dictionary.java                                           *)
(*  S = .../hunspell/SortingStrategy.java                                  *)
(*  I = lucene/analysis/icu/src/java/org/apache/lucene/analysis/icu/       *)
(*      segmentation/ICUTokenizerFactory.java                              *)
(*  "start"      S:71  tempDir.createTempOutput (file "in", handle "out")  *)
(*               S:72  new ByteSequencesWriter(output); called at D:267    *)
(*  "accum"      D:268 mergeDictionaries -> D:1010-1037 (stream readLine,  *)
(*               unescape/morph parsing) -> S:79 writer.write (disk I/O)   *)
(*  "footer"     S:84  CodecUtil.writeFooter(output)                       *)
(*  "wclose"     S:85  writer.close() -> output.close()                    *)
(*  "sort"       S:86 -> S:112-123 sorter.sort(in) at S:117; on Throwable  *)
(*               deletes "in" (S:119 deleteFilesSuppressingExceptions)  *)
(*  "delin"      S:122 tempDir.deleteFile(in) after a successful sort      *)
(*  "openSorted" S:87-88 tempDir.openChecksumInput(sorted)                 *)
(*  "read"       D:269-271 try (EntrySupplier sorted = ...) {              *)
(*               readSortedDictionaries } -> close() at S:105-108 closes   *)
(*               the reader and deletes "sorted" on success AND failure    *)
(*  Throw        exception leaves the constructor.  BUGGY (Fixed=FALSE):   *)
(*               no handler between D:267 and D:269 and none in            *)
(*               finishAndSort, so nothing is released.  Fixed=TRUE models *)
(*               the fix sketch in candidates/.../fix/: on failure close   *)
(*               every open handle and delete every temp file, ignoring    *)
(*               secondary exceptions.                                     *)
(*  ICU: "iopen" I:139 loader.openResource; "iread" I:140-147 decoding     *)
(*  reader readLine (IOException / MalformedInputException); "iclose"      *)
(*  I:149 reader.close(); "icompile" I:150 new RuleBasedBreakIterator      *)
(*  (IllegalArgumentException on bad rules, stream already closed).        *)
(* Assumption (offline-sorter's area, not modelled): OfflineSorter.sort without an    *)
(* executor deletes its own intermediate files when it throws.             *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANTS N,      \* number of dictionary entries written by mergeDictionaries
          Fixed   \* FALSE = code on main, TRUE = fix sketch

VARIABLES pc, files, open, n, failedAt, ipc, iopen, ifailedAt

vars == <<pc, files, open, n, failedAt, ipc, iopen, ifailedAt>>

Steps == {"start", "accum", "footer", "wclose", "sort", "delin", "openSorted", "read", "readclose"}

Init == /\ pc = "start" /\ files = {} /\ open = {} /\ n = 0 /\ failedAt = "none"
        /\ ipc = "iopen" /\ iopen = {} /\ ifailedAt = "none"

(* An exception thrown at step s leaves the constructor.  "cleanFiles" /   *)
(* "cleanOpen" are what the code on main releases on its way out at that  *)
(* point (try-with-resources at D:269 and the catch at S:118-121).         *)
Throw(s, cleanFiles, cleanOpen) ==
  /\ failedAt' = s
  /\ pc' = "failed"
  /\ IF Fixed
       THEN /\ files' = {} /\ open' = {}
       ELSE /\ files' = files \ cleanFiles /\ open' = open \ cleanOpen
  /\ n' = n

Start ==
  /\ pc = "start"
  /\ \/ /\ pc' = "accum" /\ files' = files \cup {"in"} /\ open' = open \cup {"out"}
        /\ UNCHANGED <<n, failedAt>>
     \/ /\ pc' = "failed" /\ failedAt' = "start"      \* createTempOutput threw: nothing created
        /\ UNCHANGED <<files, open, n>>

Accum ==
  /\ pc = "accum"
  /\ IF n < N
       THEN \/ /\ n' = n + 1 /\ UNCHANGED <<pc, files, open, failedAt>>
            \/ Throw("accum", {}, {})               \* stream IOException, disk full, parse RuntimeException
       ELSE /\ pc' = "footer" /\ UNCHANGED <<files, open, n, failedAt>>

Footer ==
  /\ pc = "footer"
  /\ \/ /\ pc' = "wclose" /\ UNCHANGED <<files, open, n, failedAt>>
     \/ Throw("footer", {}, {})                     \* disk full while writing the footer

WClose ==
  /\ pc = "wclose"
  /\ \/ /\ pc' = "sort" /\ open' = open \ {"out"} /\ UNCHANGED <<files, n, failedAt>>
     \/ /\ open' = open \ {"out"}                   \* close() threw, handle released anyway
        /\ failedAt' = "wclose" /\ pc' = "failed" /\ n' = n
        /\ files' = IF Fixed THEN {} ELSE files

Sort ==
  /\ pc = "sort"
  /\ \/ /\ pc' = "delin" /\ files' = files \cup {"sorted"} /\ UNCHANGED <<open, n, failedAt>>
     \/ Throw("sort", {"in"}, {})                   \* S:118-121 deletes the input

DelIn ==
  /\ pc = "delin"
  /\ \/ /\ pc' = "openSorted" /\ files' = files \ {"in"} /\ UNCHANGED <<open, n, failedAt>>
     \/ Throw("delin", {}, {})                      \* deleteFile(in) threw: sorted is orphaned

OpenSorted ==
  /\ pc = "openSorted"
  /\ \/ /\ pc' = "read" /\ open' = open \cup {"reader"} /\ UNCHANGED <<files, n, failedAt>>
     \/ Throw("openSorted", {}, {})                 \* openChecksumInput threw: sorted is orphaned

Read ==
  /\ pc = "read"
  /\ \/ /\ pc' = "done" /\ files' = files \ {"sorted"} /\ open' = open \ {"reader"}
        /\ UNCHANGED <<n, failedAt>>
     \/ Throw("read", {"sorted"}, {"reader"})       \* try-with-resources at D:269 closes and deletes
     \* model fix 1 (from the MBT run, op 141): EntrySupplier.close (S:105-108) runs reader.close()
     \* and then deleteFile(sorted); when reader.close() throws (handle released), the delete is
     \* skipped, on the success path and on the failure path alike.
     \/ /\ open' = open \ {"reader"} /\ failedAt' = "readclose" /\ pc' = "failed" /\ n' = n
        /\ files' = IF Fixed THEN {} ELSE files

DictStep == (Start \/ Accum \/ Footer \/ WClose \/ Sort \/ DelIn \/ OpenSorted \/ Read)
            /\ UNCHANGED <<ipc, iopen, ifailedAt>>

(* ICUTokenizerFactory.parseRules *)
IOpen == /\ ipc = "iopen"
         /\ \/ /\ ipc' = "iread" /\ iopen' = {"rules"} /\ ifailedAt' = ifailedAt
            \/ /\ ipc' = "ifailed" /\ ifailedAt' = "iopen" /\ iopen' = iopen
IRead == /\ ipc = "iread"
         /\ \/ /\ ipc' = "iclose" /\ UNCHANGED <<iopen, ifailedAt>>
            \/ /\ ipc' = "ifailed" /\ ifailedAt' = "iread"
               /\ iopen' = IF Fixed THEN {} ELSE iopen   \* no finally on main
IClose == /\ ipc = "iclose" /\ ipc' = "icompile" /\ iopen' = {} /\ ifailedAt' = ifailedAt
ICompile == /\ ipc = "icompile"
            /\ \/ /\ ipc' = "idone" /\ UNCHANGED <<iopen, ifailedAt>>
               \/ /\ ipc' = "ifailed" /\ ifailedAt' = "icompile" /\ iopen' = iopen
IcuStep == (IOpen \/ IRead \/ IClose \/ ICompile) /\ UNCHANGED <<pc, files, open, n, failedAt>>

Done == pc \in {"done", "failed"} /\ ipc \in {"idone", "ifailed"} /\ UNCHANGED vars

Next == DictStep \/ IcuStep \/ Done

Spec == Init /\ [][Next]_vars

TypeOK == /\ files \subseteq {"in", "sorted"} /\ open \subseteq {"out", "reader"} /\ n \in 0..N
          /\ failedAt \in Steps \cup {"none"} /\ iopen \subseteq {"rules"}

(* The release obligation: once the call has returned or thrown, nothing it *)
(* acquired is still held.                                                  *)
DictReleased == pc \in {"done", "failed"} => (files = {} /\ open = {})
IcuReleased  == ipc \in {"idone", "ifailed"} => iopen = {}

(* Vacuity probes: each must be VIOLATED (a state exists), proving the     *)
(* model reaches success and every failure edge.                            *)
NeverDone        == pc # "done"
NeverFailRead    == failedAt # "read"
NeverFailSort    == failedAt # "sort"
NeverIcuDone     == ipc # "idone"
NeverIcuCompile  == ifailedAt # "icompile"
=============================================================================
