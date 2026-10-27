/*
 * indexingchain-schema model 1: per-segment schema validation state of ONE field name "f" in IndexingChain,
 * compared against a stateless reference of the documented contract.
 *
 * Contract (IndexingChain.java:2154-2161, FieldSchema javadoc; FieldInfos.java:358-368 and :511-548):
 *   a document is accepted for field f iff the schema built from ALL instances of f in that
 *   document is internally consistent and equal to the field's FieldInfo in this segment (or,
 *   if f has no FieldInfo in this segment yet, equal to the global schema of f if one exists).
 *   Acceptance must not depend on earlier, rejected documents (IndexWriter.java:1491: a
 *   non-aborting exception only rejects that document; the writer stays usable).
 *
 * Abstraction: a field schema is the triple (indexOptions, docValuesType, skipIndexType).
 * omitNorms / storeTermVectors are folded into the index-options atom; points and vectors
 * behave like doc values w.r.t. reset (they ARE reset) and are not modelled.
 * A second field "g" is abstracted to WHERE it throws (GFail), because its position decides
 * whether f was reset / updated / validated before the document failed.
 *
 * Mapping model element -> Java (lucene/core/src/java/org/apache/lucene/index/, main @ ea57360912ed)
 *   State.sch                      FieldSchema fields (IndexingChain.java:2162-2176)
 *   reset[]                        FieldSchema.reset(int) IndexingChain.java:2276-2288 (does NOT
 *                                  touch docValuesSkipIndex; Config.fixSkipReset models a fix)
 *   updIO / updDV                  updateDocFieldSchema 1500-1534 -> setIndexOptions 2223-2234,
 *                                  setDocValues 2236-2245, skip-without-DV check 1511-1518
 *   State.val / State.cand         PerField.validatedFrozenFieldType / candidateFieldType 1802-1804
 *   first instance step            processDocument 619-624 + PerField.reset 1821-1831
 *   later instance step            processDocument 625-630 (multiValueForcesDeoptimize 1833-1835)
 *                                  and 633-635 (updateDocFieldSchema only when val == null)
 *   validation step                initAndValidateFields 683-697: initializeFieldInfo 1317-1359
 *                                  (FieldInfo ctor checkConsistency FieldInfo.java:160-168, then
 *                                  Builder.add -> FieldNumbers.addOrGet FieldInfos.java:415-457 /
 *                                  verifySameSchema :511-548) or FieldSchema.assertSameSchema
 *                                  2290-2304; then trySetValidatedFrozenFieldType 1837-1843
 *   State.fi                       PerField.fieldInfo (segment FieldInfo of f)
 *   State.gs                       FieldNumbers.fieldProperties.get("f") (global schema)
 *   GFail                          a failure of another field g: in pass 1 (updateDocFieldSchema,
 *                                  e.g. 'cannot store term vectors for a field that is not indexed'),
 *                                  in initAndValidateFields (before/after f in fields[] order),
 *                                  or in pass 2 (processField, e.g. analyzer exception)
 * Documented limits: none relevant (no numeric bounds involved).
 */
module FieldSchemaCacheSmall
// GENERATED from FieldSchemaCache.als: same semantics, schema universe 2x2x2 (8 atoms) for deeper bounds

open util/ordering[State]

abstract sig IO {}
one sig ION, IO1 extends IO {}
abstract sig DV {}
one sig DVN, DVS extends DV {}
abstract sig SK {}
one sig SKN, SKR extends SK {}

/* canonical schema atoms: exactly one per triple */
sig Schema { io: one IO, dv: one DV, sk: one SK }
fact canonical {
  all disj a, b: Schema | a.io != b.io or a.dv != b.dv or a.sk != b.sk
  all i: IO, d: DV, k: SK | some s: Schema | s.io = i and s.dv = d and s.sk = k
}
fun mk[i: IO, d: DV, k: SK]: lone Schema { { s: Schema | s.io = i and s.dv = d and s.sk = k } }
fun EMPTY: Schema { mk[ION, DVN, SKN] }

/* a FieldType instance (identity matters: the cache compares references) */
sig FType { s: one Schema }
sig Frozen in FType {}

abstract sig GFail {}
one sig GNone, GP1Before, GP1After, GValBefore, GValAfter, GP2 extends GFail {}

/* a document: up to two instances of f (in order) plus where another field g throws */
sig Doc { i1: lone FType, i2: lone FType, gf: one GFail, col: lone FType }
fact { all d: Doc | some d.i2 => some d.i1 }
// a column batch (IndexWriter.addBatch, one column for f): no row instances, no other failing field
fact { all d: Doc | some d.col => (no d.i1 and d.gf = GNone) }

abstract sig Bool {}
one sig T, F extends Bool {}

one sig Config { fixSkipReset: one Bool, fixCache: one Bool, fixBatchCand: one Bool }

sig State {
  fi: lone Schema,       // segment FieldInfo of f (none = f not yet in this segment)
  gs: lone Schema,       // global schema of f (none = f unknown to the index)
  sch: one Schema,       // FieldSchema contents
  val: lone FType,       // validatedFrozenFieldType
  cand: lone FType,      // candidateFieldType
  rfi: lone Schema,      // reference model: segment FieldInfo
  rgs: lone Schema,      // reference model: global schema
  doc: lone Doc,         // document processed in the transition to next
  acc: lone Bool,        // implementation accepted doc
  racc: lone Bool,       // reference accepted doc (reference trace)
  lacc: lone Bool        // reference contract evaluated on the IMPLEMENTATION's current fi/gs
}

/* ---------- FieldSchema update semantics (in-place, component-wise like the Java setters) ---------- */
// io component after updateDocFieldSchema: none = throws (schema unchanged)
fun updIO[sc: Schema, t: FType]: lone IO {
  (t.s.io = ION) => sc.io
  else ((sc.io = ION) => t.s.io
  else ((sc.io = t.s.io) => sc.io else none))
}
// (dv, sk) component: none = throws. Java checks io first, then doc values.
fun updDVd[sc: Schema, t: FType]: lone DV {
  (t.s.dv = DVN) => ((t.s.sk = SKN) => sc.dv else none)
  else ((sc.dv = DVN) => t.s.dv
  else ((sc.dv = t.s.dv and sc.sk = t.s.sk) => sc.dv else none))
}
fun updDVk[sc: Schema, t: FType]: lone SK {
  (t.s.dv = DVN) => ((t.s.sk = SKN) => sc.sk else none)
  else ((sc.dv = DVN) => t.s.sk
  else ((sc.dv = t.s.dv and sc.sk = t.s.sk) => sc.sk else none))
}
// full update result (none = threw)
fun upd[sc: Schema, t: FType]: lone Schema {
  mk[updIO[sc, t], updDVd[sc, t], updDVk[sc, t]]
}
// schema left behind when upd throws: io may already be updated (setIndexOptions runs first)
fun updFail[sc: Schema, t: FType]: Schema {
  (no updIO[sc, t]) => sc else mk[updIO[sc, t], sc.dv, sc.sk]
}
// apply-or-keep: if the update throws, keep partial state
fun app[sc: Schema, t: FType]: Schema { (some upd[sc, t]) => upd[sc, t] else updFail[sc, t] }

fun reset[sc: Schema]: Schema {
  (Config.fixSkipReset = T) => EMPTY else mk[ION, DVN, sc.sk]
}

/* ---------- reference (stateless contract) ---------- */
fun docSchema[d: Doc]: lone Schema {
  (some d.col) => upd[EMPTY, d.col] else
  ((no d.i2) => upd[EMPTY, d.i1] else upd[upd[EMPTY, d.i1], d.i2])
}
pred refAccF[s: State, d: Doc] {
  (no d.i1 and no d.col) or {
    some docSchema[d]
    some s.rfi => docSchema[d] = s.rfi
    (no s.rfi and some s.rgs) => docSchema[d] = s.rgs
  }
}
// same contract, evaluated against the implementation's own segment/global schema
pred locAccF[s: State, d: Doc] {
  (no d.i1 and no d.col) or {
    some docSchema[d]
    some s.fi => docSchema[d] = s.fi
    (no s.fi and some s.gs) => docSchema[d] = s.gs
  }
}

/* ---------- implementation step for one document ---------- */
pred step[s, n: State] {
  let d = s.doc, t1 = d.i1, t2 = d.i2 {
    // reference
    (d.gf = GNone and refAccF[s, d]) => s.racc = T else s.racc = F
    (d.gf = GNone and locAccF[s, d]) => s.lacc = T else s.lacc = F
    // reference state: f's FieldInfo is created iff f's own check passes and f's validation is reached
    let fReached = ((some t1 or some d.col) and d.gf not in (GP1Before + GP1After + GValBefore)) |
    (fReached and no s.rfi and refAccF[s, d]) => {
       n.rfi = docSchema[d]
       n.rgs = ((no s.rgs) => docSchema[d] else s.rgs)
    } else { n.rfi = s.rfi and n.rgs = s.rgs }

    some d.col => batchStep[s, n, d.col]
    no t1 and no d.col => {
      // f absent: f's PerField untouched
      n.sch = s.sch and n.val = s.val and n.cand = s.cand and n.fi = s.fi and n.gs = s.gs
      (d.gf = GNone) => s.acc = T else s.acc = F
    }
    some t1 and d.gf = GP1Before => {
      // g throws in pass 1 before f's first instance: f untouched
      n.sch = s.sch and n.val = s.val and n.cand = s.cand and n.fi = s.fi and n.gs = s.gs
      s.acc = F
    }
    some t1 and d.gf != GP1Before => {
      // ---- pass 1, first instance (PerField.reset)
      let fast1 = (t1 = s.val),
          val1 = (fast1 => s.val else none),
          sch1 = (fast1 => s.sch else reset[s.sch]),
          ok1 = (some val1 or some upd[sch1, t1]),
          sch1u = ((some val1) => sch1 else app[sch1, t1]),
          // ---- pass 1, second instance (deoptimize if a different type than the cached one)
          deopt = (some t2 and some val1 and t2 != val1),
          val2 = ((deopt) => none else val1),
          // fixCache: on deopt rebuild the schema from scratch (all instances re-applied)
          base2 = ((deopt and Config.fixCache = T) => app[EMPTY, t1] else sch1u),
          ok2b = ((deopt and Config.fixCache = T) => some upd[EMPTY, t1] else ok1),
          ok2 = (no t2 or some val2 or some upd[base2, t2]),
          sch2 = ((no t2 or some val2) => base2 else app[base2, t2]),
          need = (no val2),
          p1ok = (ok1 and ok2b and ok2),
          // fixCache: only cache a frozen type if it was the only type used for f in the doc
          single = (no t2 or t2 = t1),
          // candidateFieldType is always the first instance's type (PerField.reset sets it)
          newVal = ((t1 in Frozen and (Config.fixCache = F or single)) => t1 else val2) {
        // ---- failure in pass 1 (f itself, or g after f)
        (not p1ok or d.gf = GP1After) => {
          n.sch = sch2 and n.val = val2 and n.cand = t1 and n.fi = s.fi and n.gs = s.gs
          s.acc = F
        } else {
          // ---- validation stage; g validated before f?
          (need and d.gf = GValBefore) => {
            n.sch = sch2 and n.val = val2 and n.cand = t1 and n.fi = s.fi and n.gs = s.gs
            s.acc = F
          } else (not need) => {
            // fast path: f skips validation entirely
            n.sch = sch2 and n.val = val2 and n.cand = t1 and n.fi = s.fi and n.gs = s.gs
            (d.gf = GNone) => s.acc = T else s.acc = F
          } else {
            no s.fi => {
              // initializeFieldInfo: FieldInfo.checkConsistency, then global addOrGet
              let ccOk = (sch2.sk = SKN or sch2.dv != DVN),
                  gOk = (no s.gs or s.gs = sch2) {
                (ccOk and gOk) => {
                  n.fi = sch2 and n.gs = ((no s.gs) => sch2 else s.gs)
                  n.val = newVal and n.cand = none and n.sch = sch2
                  (d.gf = GNone) => s.acc = T else s.acc = F
                } else {
                  n.fi = s.fi and n.gs = s.gs and n.val = val2 and n.cand = t1 and n.sch = sch2
                  s.acc = F
                }
              }
            } else {
              // assertSameSchema
              (s.fi = sch2) => {
                n.fi = s.fi and n.gs = s.gs and n.val = newVal and n.cand = none and n.sch = sch2
                (d.gf = GNone) => s.acc = T else s.acc = F
              } else {
                n.fi = s.fi and n.gs = s.gs and n.val = val2 and n.cand = t1 and n.sch = sch2
                s.acc = F
              }
            }
          }
        }
      }
    }
  }
}

/* IndexingChain.processBatch 715-783 for one column of f: FieldSchema.reset (762), updateDocFieldSchema
   (777), initAndValidateFields (782) -> trySetValidatedFrozenFieldType consumes candidateFieldType,
   which processBatch never sets: it is whatever an earlier processDocument left behind. */
pred batchStep[s, n: State, c: FType] {
  let sch1 = reset[s.sch],
      ok = (some upd[sch1, c]),
      sch2 = app[sch1, c],
      promoted = ((Config.fixBatchCand = F and s.cand in Frozen) => s.cand else s.val) {
    not ok => {
      n.sch = sch2 and n.val = s.val and n.cand = s.cand and n.fi = s.fi and n.gs = s.gs and s.acc = F
    } else no s.fi => {
      let ccOk = (sch2.sk = SKN or sch2.dv != DVN),
          gOk = (no s.gs or s.gs = sch2) {
        (ccOk and gOk) => {
          n.fi = sch2 and n.gs = ((no s.gs) => sch2 else s.gs)
          n.val = promoted and n.cand = none and n.sch = sch2 and s.acc = T
        } else {
          n.fi = s.fi and n.gs = s.gs and n.val = s.val and n.cand = s.cand and n.sch = sch2 and s.acc = F
        }
      }
    } else {
      (s.fi = sch2) => {
        n.fi = s.fi and n.gs = s.gs and n.val = promoted and n.cand = none and n.sch = sch2 and s.acc = T
      } else {
        n.fi = s.fi and n.gs = s.gs and n.val = s.val and n.cand = s.cand and n.sch = sch2 and s.acc = F
      }
    }
  }
}

fact traces {
  // fresh segment (new DWPT): no FieldInfo, empty schema, no cache; global may already know f
  let s0 = first | no s0.fi and s0.sch = EMPTY and no s0.val and no s0.cand and
                   no s0.rfi and s0.rgs = s0.gs
  all s: State - last | some s.doc and step[s, s.next]
  no last.doc and no last.acc and no last.racc and no last.lacc
}

/* ---------- properties ---------- */
// P1: the implementation accepts exactly the documents the stateless contract accepts
pred AcceptEqualsReference { all s: State - last | s.acc = s.racc }
// P2: segment / global schema evolve as in the reference
pred SchemaEqualsReference { all s: State | s.fi = s.rfi and s.gs = s.rgs }
// P3 (consequence of P1, stated separately): a rejected document never makes a later document
// that the reference accepts fail
pred NoPoison { all s: State - last | (s.racc = T) => s.acc = T }
// P4: fast path never accepts a document the reference rejects
pred CacheSound { all s: State - last | (s.acc = T) => s.racc = T }

// P5 (step-local, independent of earlier divergence): each document is accepted iff the contract,
// evaluated on the implementation's own current schema state, accepts it
pred LocalAccept { all s: State - last | s.acc = s.lacc }
pred LocalNoPoison { all s: State - last | (s.lacc = T) => s.acc = T }
pred LocalCacheSound { all s: State - last | (s.acc = T) => s.lacc = T }

pred noBatch { no Doc.col }
pred stock { Config.fixSkipReset = F and Config.fixCache = F and Config.fixBatchCand = F }
pred fixed { Config.fixSkipReset = T and Config.fixCache = T and Config.fixBatchCand = T }
pred onlySkipFix { Config.fixSkipReset = T and Config.fixCache = F and Config.fixBatchCand = F }
pred onlyCacheFix { Config.fixSkipReset = F and Config.fixCache = T and Config.fixBatchCand = F }
pred allButBatchFix { Config.fixSkipReset = T and Config.fixCache = T and Config.fixBatchCand = F }

// stock code: expect counterexamples (SAT)
check StockNoPoison { stock => NoPoison } for 8 Schema, 3 FType, 3 Doc, 4 State
check StockCacheSound { stock => CacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
check StockLocalNoPoison { stock => LocalNoPoison } for 8 Schema, 3 FType, 3 Doc, 4 State
check StockLocalCacheSound { stock => LocalCacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
// each fix alone removes exactly its own class (step-local properties)
check SkipFixLocalNoPoison { (onlySkipFix and noBatch) => LocalNoPoison } for 8 Schema, 3 FType, 3 Doc, 4 State
check SkipFixLocalCacheSound { (onlySkipFix and noBatch) => LocalCacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
check CacheFixLocalCacheSound { (onlyCacheFix and noBatch) => LocalCacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
check CacheFixLocalNoPoison { (onlyCacheFix and noBatch) => LocalNoPoison } for 8 Schema, 3 FType, 3 Doc, 4 State
// both fixes: bounded proof of the full contract (reference trace and step-local)
check FixedAccept { fixed => AcceptEqualsReference } for 8 Schema, 3 FType, 3 Doc, 4 State
check FixedSchema { fixed => SchemaEqualsReference } for 8 Schema, 3 FType, 3 Doc, 4 State
check FixedLocal { fixed => LocalAccept } for 8 Schema, 3 FType, 3 Doc, 4 State
// row-only (no addBatch) counterexamples, one per row-path root cause
check RowOnlyStockCacheSound { (stock and noBatch) => LocalCacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
check RowOnlyStockNoPoison { (stock and noBatch) => LocalNoPoison } for 8 Schema, 3 FType, 3 Doc, 4 State
// batch interplay: with the two row-path fixes but not the batch fix, a never-validated type is cached
check BatchStaleCandidate { allButBatchFix => LocalCacheSound } for 8 Schema, 3 FType, 3 Doc, 4 State
// non-vacuity: a 3-document trace with an accepted doc, a rejected doc and an accepted doc exists
run NonVacuous { fixed and some s: State - last | s.acc = T and some s2: s.nexts - last | s2.acc = F and
                 some s3: s2.nexts - last | s3.acc = T } for 8 Schema, 3 FType, 3 Doc, 4 State

// deeper bounds, small universe
check FixedLocalDeep { fixed => LocalAccept } for 8 Schema, 4 FType, 5 Doc, 6 State
check FixedAcceptDeep { fixed => AcceptEqualsReference } for 8 Schema, 4 FType, 5 Doc, 6 State
check StockLocalDeep { stock => LocalAccept } for 8 Schema, 4 FType, 5 Doc, 6 State
