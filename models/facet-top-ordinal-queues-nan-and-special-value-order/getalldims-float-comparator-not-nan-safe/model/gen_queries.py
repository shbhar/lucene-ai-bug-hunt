"""comparator-law and special-value reachability obligations (SMT-LIB QF_FPBV), run with cvc5 and z3.

Model element -> Java line (lucene main a9779ef3c193):
  LT_Q(a,b)      TopOrdAndFloatQueue.OrdAndFloat#lessThan          lucene/facet/.../TopOrdAndFloatQueue.java:36-45
                 (value < other -> true; value > other -> false; else ord > other.ord)
  LT_FIX(a,b)    proposed: Float.compare(value, other) then ord     (fix sketch)
  JCMP(a,b)      java.lang.Float#compare (fp.lt / fp.gt, then floatToIntBits with canonical NaN 0x7fc00000)
  BEFORE_ALL     TaxonomyFacets#getAllDims sort comparator          lucene/facet/.../taxonomy/TaxonomyFacets.java:581-590
                 (a.value.doubleValue() > b.value.doubleValue() -> a first; reverse -> b first; else dim name)
  BEFORE_TOP     TaxonomyFacets#getTopDims order: valueComparator = Float.compare (FloatTaxonomyFacets.java:48),
                 then dim reverse-order in a min-queue => higher value first, ties by dim name ascending
                 (TaxonomyFacets.java:610-614)
  SUM            AssociationAggregationFunction.SUM float aggregate  AssociationAggregationFunction.java:62-64 (existing + new)
  MAX            AssociationAggregationFunction.MAX float aggregate  AssociationAggregationFunction.java:48-50 (Math.max)
  start value    values[] init 0f / IntFloatHashMap default 0f; FloatAggregatedValue(0f) (FloatTaxonomyFacets.java:62,149)
  CAST           (float) functionValues.doubleValue()               TaxonomyFacetFloatAssociations.java:151
Ordinals are 32-bit ints; labels in one queue have distinct ordinals. Names (dim strings) are abstracted as
distinct 32-bit keys compared with signed order (only their total order matters).
Expected answers are recorded per query; run_all.sh compares them.
"""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
F = "(_ FloatingPoint 8 24)"
D = "(_ FloatingPoint 11 53)"
HDR = "(set-logic QF_FPBV)\n(set-option :produce-models true)\n"

def elems(names, sort=F):
    s = ""
    for n in names:
        s += f"(declare-const v{n} {sort})\n(declare-const b{n} (_ BitVec 32))\n(declare-const o{n} (_ BitVec 32))\n"
        if sort == F:
            # bits view of the float (for Float.compare); canonical NaN as floatToIntBits does
            s += f"(assert (= v{n} ((_ to_fp 8 24) b{n})))\n"
            s += f"(define-fun c{n} () (_ BitVec 32) (ite (fp.isNaN v{n}) #x7fc00000 b{n}))\n"
        s += f"(assert (bvsge o{n} #x00000000))\n"
    for i in range(len(names)):
        for j in range(i + 1, len(names)):
            s += f"(assert (distinct o{names[i]} o{names[j]}))\n"
    return s

FUNS = f"""
(define-fun LT_Q ((x {F}) (ox (_ BitVec 32)) (y {F}) (oy (_ BitVec 32))) Bool
  (ite (fp.lt x y) true (ite (fp.gt x y) false (bvsgt ox oy))))
(define-fun JCMP ((x {F}) (cx (_ BitVec 32)) (y {F}) (cy (_ BitVec 32))) (_ BitVec 2)
  (ite (fp.lt x y) #b11 (ite (fp.gt x y) #b01 (ite (= cx cy) #b00 (ite (bvslt cx cy) #b11 #b01)))))
(define-fun LT_FIX ((x {F}) (cx (_ BitVec 32)) (ox (_ BitVec 32)) (y {F}) (cy (_ BitVec 32)) (oy (_ BitVec 32))) Bool
  (ite (= (JCMP x cx y cy) #b00) (bvsgt ox oy) (= (JCMP x cx y cy) #b11)))
(define-fun BEFORE_ALL ((x {F}) (nx (_ BitVec 32)) (y {F}) (ny (_ BitVec 32))) Bool
  (ite (fp.gt x y) true (ite (fp.gt y x) false (bvslt nx ny))))
(define-fun BEFORE_TOP ((x {F}) (cx (_ BitVec 32)) (nx (_ BitVec 32)) (y {F}) (cy (_ BitVec 32)) (ny (_ BitVec 32))) Bool
  (ite (= (JCMP x cx y cy) #b01) true (ite (= (JCMP x cx y cy) #b11) false (bvslt nx ny))))
"""
NONNAN = lambda *ns: "".join(f"(assert (not (fp.isNaN v{n})))\n" for n in ns)
q = {}
# Q1: the queue comparator is not transitive once NaN is allowed (expected sat; model = witness)
q["q1_lt_q_transitivity_with_nan"] = ("sat", elems("abc") + FUNS +
    "(assert (LT_Q va oa vb ob))\n(assert (LT_Q vb ob vc oc))\n(assert (not (LT_Q va oa vc oc)))\n")
# Q1b: witness shape of the repro: a and c finite, c > a, b NaN, ords a<b<c, and the queue ranks c below a
q["q1b_lt_q_witness_repro_shape"] = ("sat", elems("abc") + FUNS +
    "(assert (= va ((_ to_fp 8 24) RNE 5.0)))\n(assert (= vc ((_ to_fp 8 24) RNE 10.0)))\n(assert (fp.isNaN vb))\n"
    "(assert (= oa #x00000001))\n(assert (= ob #x00000002))\n(assert (= oc #x00000003))\n"
    "(assert (LT_Q vc oc vb ob))\n(assert (LT_Q vb ob va oa))\n")
# Q2: without NaN the queue comparator is a strict total order (transitive, asymmetric): expected unsat
q["q2_lt_q_transitive_without_nan"] = ("unsat", elems("abc") + FUNS + NONNAN("a", "b", "c") +
    "(assert (LT_Q va oa vb ob))\n(assert (LT_Q vb ob vc oc))\n(assert (not (LT_Q va oa vc oc)))\n")
q["q2b_lt_q_asymmetric_or_total_violation_without_nan"] = ("unsat", elems("ab") + FUNS + NONNAN("a", "b") +
    "(assert (or (and (LT_Q va oa vb ob) (LT_Q vb ob va oa)) (and (not (LT_Q va oa vb ob)) (not (LT_Q vb ob va oa)))))\n")
# Q2c: with NaN the queue comparator is still total + asymmetric on distinct ords (a tournament): expected unsat
q["q2c_lt_q_asymmetric_total_with_nan"] = ("unsat", elems("ab") + FUNS +
    "(assert (or (and (LT_Q va oa vb ob) (LT_Q vb ob va oa)) (and (not (LT_Q va oa vb ob)) (not (LT_Q vb ob va oa)))))\n")
# Q3: the Float.compare fix is transitive and asymmetric over ALL floats incl. NaN payloads and signed zeros: unsat
q["q3_fix_transitive_all_floats"] = ("unsat", elems("abc") + FUNS +
    "(assert (LT_FIX va ca oa vb cb ob))\n(assert (LT_FIX vb cb ob vc cc oc))\n(assert (not (LT_FIX va ca oa vc cc oc)))\n")
q["q3b_fix_asymmetric_total_all_floats"] = ("unsat", elems("ab") + FUNS +
    "(assert (or (and (LT_FIX va ca oa vb cb ob) (LT_FIX vb cb ob va ca oa)) (and (not (LT_FIX va ca oa vb cb ob)) (not (LT_FIX vb cb ob va ca oa)))))\n")
# Q3c: the fix agrees with the current comparator whenever neither value is NaN and they are not two opposite zeros: unsat
q["q3c_fix_agrees_except_nan_and_signed_zero"] = ("unsat", elems("ab") + FUNS + NONNAN("a", "b") +
    "(assert (not (and (fp.isZero va) (fp.isZero vb))))\n(assert (not (= (LT_Q va oa vb ob) (LT_FIX va ca oa vb cb ob))))\n")
# Q4: getAllDims order is not transitive once NaN is allowed (expected sat), and is with non-NaN values (unsat)
q["q4_getalldims_order_transitivity_with_nan"] = ("sat", elems("abc") + FUNS +
    "(assert (BEFORE_ALL va oa vb ob))\n(assert (BEFORE_ALL vb ob vc oc))\n(assert (not (BEFORE_ALL va oa vc oc)))\n")
q["q4b_getalldims_order_transitive_without_nan"] = ("unsat", elems("abc") + FUNS + NONNAN("a", "b", "c") +
    "(assert (BEFORE_ALL va oa vb ob))\n(assert (BEFORE_ALL vb ob vc oc))\n(assert (not (BEFORE_ALL va oa vc oc)))\n")
# Q5: getTopDims order (Float.compare) and getAllDims order disagree only on NaN or signed-zero pairs
q["q5_topdims_vs_alldims_disagree_with_nan"] = ("sat", elems("ab") + FUNS +
    "(assert (not (fp.isNaN va)))\n(assert (fp.isNaN vb))\n(assert (not (= (BEFORE_ALL va oa vb ob) (BEFORE_TOP va ca oa vb cb ob))))\n")
q["q5b_topdims_vs_alldims_agree_otherwise"] = ("unsat", elems("ab") + FUNS + NONNAN("a", "b") +
    "(assert (not (and (fp.isZero va) (fp.isZero vb))))\n(assert (not (= (BEFORE_ALL va oa vb ob) (BEFORE_TOP va ca oa vb cb ob))))\n")
# Reachability of special values through the aggregation folds (start value +0f)
R = f"(declare-const s {F})\n(declare-const x {F})\n"
q["r1_sum_fold_never_negative_zero"] = ("unsat", HDR.replace("QF_FPBV", "QF_FP") + R +
    "(assert (not (= s (_ -zero 8 24))))\n(assert (= (fp.add RNE s x) (_ -zero 8 24)))\n")
q["r2_sum_of_finite_inputs_never_nan"] = ("unsat", R +
    "(assert (not (fp.isNaN s)))\n(assert (not (fp.isNaN x)))\n(assert (not (fp.isInfinite x)))\n(assert (fp.isNaN (fp.add RNE s x)))\n")
q["r3_sum_of_finite_inputs_can_overflow_to_inf"] = ("sat", R +
    "(assert (not (fp.isNaN s)))\n(assert (not (fp.isInfinite s)))\n(assert (not (fp.isNaN x)))\n(assert (not (fp.isInfinite x)))\n(assert (fp.isInfinite (fp.add RNE s x)))\n")
q["r4_sum_inf_plus_opposite_inf_is_nan"] = ("sat", R +
    "(assert (fp.isInfinite s))\n(assert (fp.isInfinite x))\n(assert (fp.isNaN (fp.add RNE s x)))\n")
# Java Math.max(float a, float b): a NaN -> a; a==0 && b==0 && a is -0 -> b; else a >= b ? a : b
JMAX = f"(define-fun JMAX ((a {F}) (b {F})) {F} (ite (fp.isNaN a) a (ite (and (fp.isZero a) (fp.isZero b) (fp.isNegative a)) b (ite (fp.geq a b) a b))))\n"
q["r5_max_fold_from_zero_never_negative_or_negzero"] = ("unsat", R + JMAX +
    "(assert (not (fp.isNaN s)))\n(assert (fp.isPositive s))\n(assert (let ((m (JMAX s x))) (and (not (fp.isNaN m)) (not (fp.isPositive m)))))\n")
q["r6_max_fold_with_nan_input_is_nan"] = ("sat", R + JMAX +
    "(assert (fp.isPositive s))\n(assert (not (fp.isNaN s)))\n(assert (fp.isNaN x))\n(assert (fp.isNaN (JMAX s x)))\n")
q["r7_cast_of_finite_double_can_be_inf"] = ("sat", f"(declare-const d {D})\n" +
    "(assert (not (fp.isNaN d)))\n(assert (not (fp.isInfinite d)))\n(assert (fp.isInfinite ((_ to_fp 8 24) RNE d)))\n")
for name, (exp, body) in q.items():
    text = (body if body.startswith("(set-logic") else HDR + body)
    if "(check-sat)" not in text:
        text += "(check-sat)\n"
        if exp == "sat":
            text += "(get-model)\n"
    with open(os.path.join(HERE, name + ".smt2"), "w") as f:
        f.write(f"; expected: {exp}\n" + text)
print(len(q), "queries written")
