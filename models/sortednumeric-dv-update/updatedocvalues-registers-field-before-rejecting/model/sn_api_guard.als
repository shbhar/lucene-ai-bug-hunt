/*
 * sortednumeric-dv-update model C: argument checks of the SORTED_NUMERIC update entry points against the writer's global
 * field schema (FieldInfos.FieldNumbers), and the index-sort rejection.
 *
 * Lucene main ea57360912ed. Model element -> Java line:
 *   S.schema                FieldInfos.FieldNumbers.fieldProperties (FieldInfos.java:416-452 addOrGet)
 *   pred updSN              IndexWriter.updateSortedNumericDocValue (IndexWriter.java:2037-2057):
 *                           1. index-sort field -> IAE; 2. verifyOrCreateDvOnlyField(f, SORTED_NUMERIC,
 *                           fieldMustExist=true) -> IAE if absent / other type / not dv-only
 *   pred updDV              IndexWriter.updateDocValues -> buildDocValuesUpdate (IndexWriter.java:2080-2126),
 *                           one field per call: 1. verifyOrCreateDvOnlyField(f, t, fieldMustExist=false)
 *                           (FieldInfos.java:562-595: CREATES a dv-only field if absent);
 *                           2. index-sort field -> IAE; 3. type not NUMERIC/BINARY/SORTED_NUMERIC -> IAE
 *   pred updDV2             the same with two fields: the loop creates field 1 before field 2 is checked
 *   pred addDoc             IndexWriter.addDocument -> FieldNumbers.addOrGet / verifySameSchema
 *                           (FieldInfos.java:416-422): IAE if the schema differs from the registered one
 *   Props                   DvOnlySN (SortedNumericDocValuesField), PointsSN (LongField: 1-dim points +
 *                           SORTED_NUMERIC), DvOnlySorted (SortedDocValuesField), KeywordSorted
 *                           (KeywordField: indexed terms + SORTED_SET is not needed here; we use
 *                           "indexed + SORTED" as a stand-in for any non-dv-only schema)
 * Model fix 1 (2026-10-25): the first CheckFirst variant validated field by field inside the loop; the solver
 * showed that this still leaks field 1 when field 2 is rejected (UpdDV2), so CheckFirst now validates every
 * field before creating any, as fix/fix.diff does.
 * Property (exception safety): a call that throws IllegalArgumentException leaves the writer's global
 * schema unchanged, so a later document that is consistent with every ACCEPTED operation is accepted.
 */
module sn_api_guard

abstract sig DvType {}
one sig NUMERIC, BINARY, SORTED_NUMERIC, SORTED extends DvType {}

abstract sig Props { dv: one DvType, dvOnly: one Bool }
abstract sig Bool {}
one sig True, False extends Bool {}
one sig DvOnlySN extends Props {}      { dv = SORTED_NUMERIC and dvOnly = True }
one sig PointsSN extends Props {}      { dv = SORTED_NUMERIC and dvOnly = False }
one sig DvOnlySorted extends Props {}  { dv = SORTED and dvOnly = True }
one sig IndexedSorted extends Props {} { dv = SORTED and dvOnly = False }

sig Field {}
one sig SortField in Field {}           // IndexWriterConfig#setIndexSort on this field

fun dvOnlyOf[t: DvType]: Props { { p: Props | p.dv = t and p.dvOnly = True } }

abstract sig Mode {}
one sig Shipped, CheckFirst extends Mode {}   // CheckFirst = candidate fix: validate before creating
one sig Cfg { mode: one Mode }

abstract sig Op {}
one sig UpdSN, UpdDV, UpdDV2, AddDoc, Stutter extends Op {}

one sig S {
  var schema: Field -> lone Props,
  var acc: Field -> lone Props,         // schema implied by ACCEPTED operations only
  var op: one Op,
  var f1: one Field, var f2: one Field,
  var t1: one DvType, var t2: one DvType,
  var p: one Props,
  var rejected: one Bool,               // outcome of the op taken FROM this state
  var mid: Field -> lone Props,         // schema between the two fields of UpdDV2
  var th1: one Bool
}

pred init { no S.schema and no S.acc }

pred updatable[t: DvType] { t in NUMERIC + BINARY + SORTED_NUMERIC }

pred updSN {
  S.op = UpdSN
  let f = S.f1 |
    (f in SortField or no S.schema[f] or S.schema[f] != dvOnlyOf[SORTED_NUMERIC])
      implies S.rejected = True else S.rejected = False
  S.schema' = S.schema and S.acc' = S.acc
}

// one field of buildDocValuesUpdate; returns the schema after the step and whether it threw
pred dvStep[sIn, sOut: Field -> lone Props, f: Field, t: DvType, threw: Bool] {
  Cfg.mode = Shipped implies {
    (some sIn[f] and sIn[f] != dvOnlyOf[t]) implies (sOut = sIn and threw = True)
    else {
      sOut = sIn ++ (f -> dvOnlyOf[t])
      (f in SortField or not updatable[t]) implies threw = True else threw = False
    }
  } else {
    ((some sIn[f] and sIn[f] != dvOnlyOf[t]) or f in SortField or not updatable[t])
      implies (sOut = sIn and threw = True)
      else (sOut = sIn ++ (f -> dvOnlyOf[t]) and threw = False)
  }
}

pred updDV {
  S.op = UpdDV
  dvStep[S.schema, S.schema', S.f1, S.t1, S.rejected]
  S.rejected = True implies S.acc' = S.acc else S.acc' = S.acc ++ (S.f1 -> dvOnlyOf[S.t1])
}

pred valid[sIn: Field -> lone Props, f: Field, t: DvType] {
  not (some sIn[f] and sIn[f] != dvOnlyOf[t])
  f not in SortField
  updatable[t]
}

pred updDV2 {
  S.op = UpdDV2
  S.f1 != S.f2
  Cfg.mode = Shipped implies {
    // IndexWriter.java:2083-2126: one loop, create-then-check per field
    dvStep[S.schema, S.mid, S.f1, S.t1, S.th1]
    S.th1 = True implies (S.schema' = S.mid and S.rejected = True)
    else dvStep[S.mid, S.schema', S.f2, S.t2, S.rejected]
  } else {
    // fix/fix.diff: validate every field first, then create
    (valid[S.schema, S.f1, S.t1] and valid[S.schema, S.f2, S.t2])
      implies (S.rejected = False and S.schema' = S.schema ++ (S.f1 -> dvOnlyOf[S.t1]) ++ (S.f2 -> dvOnlyOf[S.t2]))
      else (S.rejected = True and S.schema' = S.schema)
  }
  S.rejected = True implies S.acc' = S.acc
    else S.acc' = S.acc ++ (S.f1 -> dvOnlyOf[S.t1]) ++ (S.f2 -> dvOnlyOf[S.t2])
}

pred addDoc {
  S.op = AddDoc
  let f = S.f1 |
    (some S.schema[f] and S.schema[f] != S.p)
      implies (S.rejected = True and S.schema' = S.schema and S.acc' = S.acc)
      else (S.rejected = False and S.schema' = S.schema ++ (f -> S.p) and S.acc' = S.acc ++ (f -> S.p))
}

pred stutter { S.op = Stutter and S.rejected = False and S.schema' = S.schema and S.acc' = S.acc }

fact traces { init and always (updSN or updDV or updDV2 or addDoc or stutter) }

// A document is rejected only if it conflicts with an ACCEPTED operation.
pred docRejectedOnlyOnRealConflict {
  always ((S.op = AddDoc and S.rejected = True) implies (some S.acc[S.f1] and S.acc[S.f1] != S.p))
}
pred rejectedNoSideEffect { always (S.rejected = True implies S.schema' = S.schema) }

check Shipped_exceptionSafety { Cfg.mode = Shipped implies (rejectedNoSideEffect and docRejectedOnlyOnRealConflict) } for 3 but 6 steps
check Shipped_docRejectedOnlyOnRealConflict { Cfg.mode = Shipped implies docRejectedOnlyOnRealConflict } for 3 but 6 steps
check Fixed_exceptionSafety { Cfg.mode = CheckFirst implies (rejectedNoSideEffect and docRejectedOnlyOnRealConflict) } for 3 but 1..8 steps
run Reach_fixed_rejects { Cfg.mode = CheckFirst and eventually (S.op = UpdDV and S.rejected = True)
  and eventually (S.op = AddDoc and S.rejected = True) } for 3 but 1..8 steps
