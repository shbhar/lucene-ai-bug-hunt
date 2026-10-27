/*
 * indexingchain-schema model 2: the global FieldInfos.FieldNumbers registry of an IndexWriter session versus the
 * per-segment FieldInfos (flushed segments, the open DWPT's FieldInfos.Builder, added indexes).
 *
 * Mapping model element -> Java (lucene/core/src/java/org/apache/lucene/index/, main @ ea57360912ed)
 *   W.glob                         FieldNumbers.fieldProperties (FieldInfos.java:370-457)
 *   W.dwpt                         FieldInfos.Builder of the open DWPT (FieldInfos.java:701-831),
 *                                  filled by IndexingChain.initializeFieldInfo (IndexingChain.java:1337)
 *   W.live / W.segFI               SegmentInfos + each segment's FieldInfos
 *   addDocOk / addDocFail          IndexingChain.processDocument 581-681 + initAndValidateFields
 *                                  683-697: fields are initialised one by one; a failure at field k
 *                                  leaves fields < k registered in the builder AND globally
 *   flush                          DocumentsWriterPerThread.flush; a 100%-deleted segment is
 *                                  dropped (IndexWriter.java:1831 dropDeletedSegment)
 *   addIndexesDir                  IndexWriter.addIndexes(Directory...) 3103-3215: for each incoming
 *                                  segment, for each FieldInfo: globalFieldNumberMap.addOrGet(fi)
 *                                  (3178-3182) -- registers while iterating; on a conflict the copied
 *                                  segments are deleted (3186-3192) but glob is not rolled back
 *   addIndexesReaders              IndexWriter.addIndexes(CodecReader...) 3275-3395: verifyFieldInfo
 *                                  (3290-3293, no mutation) for every reader first, then merge
 *   deleteAll                      IndexWriter.deleteAll 2780-2815: globalFieldNumberMap.clear() (2800)
 *   updateDvNewField               IndexWriter.buildDocValuesUpdate 2081-2124 ->
 *                                  FieldNumbers.verifyOrCreateDvOnlyField(..., false) 562-647
 * Contracts checked:
 *   SchemaAgree    every segment / builder FieldInfo of a name equals the global schema
 *   AddIndexesTxn  IndexWriter.java:3086-3088 "This method is transactional in how Exceptions are
 *                  handled ... either no indexes will have been added or they all will have been"
 *                  -> a failed addIndexes leaves the writer's schema state unchanged
 *   NoPhantom      every globally known name is in some live segment or the open builder
 *                  (getFieldNames javadoc IndexWriter.java:2129-2134: "all field names as visible
 *                  from this IndexWriter, across all segments of the index")
 * Field numbers are not modelled as a contract: since LUCENE-2881 per-segment numbers may differ
 * from the global preferred number (addIndexes(Directory) copies segments as-is).
 */
module FieldNumbersTxn

sig Name {}
sig Sch {}
sig Seg { fis: Name -> lone Sch }    // incoming or flushed segment FieldInfos (immutable)
sig Doc { dsch: Name -> lone Sch }   // a document's per-field schema
sig Batch { segs: some Seg }         // the argument of one addIndexes call

abstract sig Op {}
one sig OAddOk, OAddFail, OFlush, OAddDir, OAddDirFail, OAddRd, OAddRdFail, ODeleteAll, OUpdNew, ONone extends Op {}

one sig W {
  var glob: Name -> lone Sch,
  var dwpt: Name -> lone Sch,
  var dwptLive: lone W,              // non-empty = the open DWPT holds at least one live doc
  var live: set Seg,
  var op: one Op,
  var pre: Name -> lone Sch          // nondeterministic choice: fields registered before a failure
}

fact init {
  no W.glob and no W.dwpt and no W.dwptLive and no W.live
}

/* a document's schema: Name -> Sch, consistent with glob for every field (else it is rejected) */
pred addDocOk[d: Name -> lone Sch] {
  some d
  all n: d.Sch | no n.(W.glob) or n.(W.glob) = n.d
  W.glob' = W.glob ++ d
  W.dwpt' = W.dwpt ++ d
  W.dwptLive' = W
  W.live' = W.live
  W.op' = OAddOk
}
/* failing document: the fields in W.pre (a subset of the doc) were initialised before the failing field */
pred addDocFail[d: Name -> lone Sch] {
  let done = W.pre |
  done in d and
  (all n: done.Sch | no n.(W.glob) or n.(W.glob) = n.done) and
  W.glob' = W.glob ++ done and
  W.dwpt' = W.dwpt ++ done and
  W.dwptLive' = W.dwptLive and
  W.live' = W.live and
  W.op' = OAddFail
}
pred flush[s: Seg] {
  some W.dwpt
  s not in W.live
  // a 100%-deleted segment is dropped
  (some W.dwptLive) => (s.fis = W.dwpt and W.live' = W.live + s) else W.live' = W.live
  no W.dwpt' and no W.dwptLive'
  W.glob' = W.glob
  W.op' = OFlush
}
/* addIndexes(Directory): sequential addOrGet over incoming FieldInfos */
pred addIndexesDir[inc: set Seg] {
  no inc & W.live
  let incoming = inc.fis |
  (all n: incoming.Sch | lone n.incoming and (no n.(W.glob) or n.(W.glob) = n.incoming)) => {
    W.glob' = W.glob ++ incoming and W.live' = W.live + inc and W.op' = OAddDir
  } else {
    // failure: the fields W.pre were registered (addOrGet) before the conflicting field was reached
    W.pre in incoming
    all n: W.pre.Sch | lone n.(W.pre) and (no n.(W.glob) or n.(W.glob) = n.(W.pre))
    W.glob' = W.glob ++ W.pre
    W.live' = W.live and W.op' = OAddDirFail
  }
  W.dwpt' = W.dwpt and W.dwptLive' = W.dwptLive
}
/* addIndexes(CodecReader): verify all first, then merge into one new segment */
pred addIndexesReaders[inc: set Seg, out: Seg] {
  no inc & W.live and out not in W.live + inc
  let incoming = inc.fis |
  (all n: incoming.Sch | lone n.incoming and (no n.(W.glob) or n.(W.glob) = n.incoming)) => {
    out.fis = incoming
    W.glob' = W.glob ++ incoming and W.live' = W.live + out and W.op' = OAddRd
  } else {
    W.glob' = W.glob and W.live' = W.live and W.op' = OAddRdFail
  }
  W.dwpt' = W.dwpt and W.dwptLive' = W.dwptLive
}
pred deleteAll {
  no W.glob' and no W.dwpt' and no W.dwptLive' and no W.live'
  W.op' = ODeleteAll
}
/* updateDocValues on a field that does not exist yet (fieldMustExist = false) */
pred updNew[n: Name, s: Sch] {
  no n.(W.glob)
  W.glob' = W.glob + n -> s
  W.dwpt' = W.dwpt and W.dwptLive' = W.dwptLive and W.live' = W.live
  W.op' = OUpdNew
}
pred stutter {
  W.glob' = W.glob and W.dwpt' = W.dwpt and W.dwptLive' = W.dwptLive and W.live' = W.live
  W.op' = ONone
}

fact transitions {
  W.op = ONone
  always (
    (some d: Doc | addDocOk[d.dsch]) or
    (some d: Doc | addDocFail[d.dsch]) or
    (some s: Seg | flush[s]) or
    (some b: Batch | addIndexesDir[b.segs]) or
    (some b: Batch, out: Seg | addIndexesReaders[b.segs, out]) or
    deleteAll or
    (some n: Name, s: Sch | updNew[n, s]) or
    stutter)
}

pred SchemaAgree {
  always (all s: W.live, n: Name | some n.(s.fis) => n.(s.fis) = n.(W.glob))
  always (all n: Name | some n.(W.dwpt) => n.(W.dwpt) = n.(W.glob))
}
pred AddIndexesTxn { always ((W.op' in OAddDirFail + OAddRdFail) => W.glob' = W.glob) }
pred NoPhantom { always (all n: W.glob.Sch | n in (W.live.fis).Sch + (W.dwpt).Sch) }
/* the same contract, excluding the documented/long-standing sources of global-only names
   (rejected documents whose segment is dropped, DV updates that create a field) */
pred NoPhantomExceptKnown {
  always ((W.op' = OAddDirFail) => (W.glob'.Sch in W.glob.Sch + (W.live'.fis).Sch + W.dwpt'.Sch))
}

check SchemaAgreeHolds { SchemaAgree } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
check AddIndexesTxnHolds { AddIndexesTxn } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
check NoPhantomHolds { NoPhantom } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
check NoPhantomExceptKnownHolds { NoPhantomExceptKnown } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
// the reader-based variant alone is transactional (bounded proof)
check ReadersTxn { always ((W.op' = OAddRdFail) => W.glob' = W.glob) } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
// non-vacuity: a successful addIndexes(Directory) after a flush is reachable
run NonVacuous2 { eventually (W.op = OFlush and eventually W.op = OAddDir) } for 3 Name, 2 Sch, 3 Seg, 3 Doc, 2 Batch, 6 steps
