"""Vacuity probes and mutants for gen_queries.py (each must come out as expected, else the unsat proofs are vacuous)."""
import os, re, importlib.util
HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("g", os.path.join(HERE, "gen_queries.py"))
src = open(os.path.join(HERE, "gen_queries.py")).read()
def read(n): return open(os.path.join(HERE, n + ".smt2")).read()
def write(n, exp, text):
    text = re.sub(r"^; expected: \w+\n", "", text)
    text = text.replace("(check-sat)\n(get-model)\n", "(check-sat)\n")
    open(os.path.join(HERE, n + ".smt2"), "w").write(f"; expected: {exp}\n" + text)
# v1: preconditions alone are satisfiable (strip the goal asserts of q2/q3 by keeping only declarations+defs+NONNAN)
t = read("q2_lt_q_transitive_without_nan")
t = "\n".join(l for l in t.splitlines() if "LT_Q va" not in l and "LT_Q vb" not in l) + "\n"
write("vac1_q2_preconditions_sat", "sat", t)
t = read("q3_fix_transitive_all_floats")
t = "\n".join(l for l in t.splitlines() if not l.startswith("(assert (LT_FIX") and not l.startswith("(assert (not (LT_FIX")) + "\n"
write("vac2_q3_preconditions_sat", "sat", t)
# m1: Float.compare mutant with an unsigned bits comparison must break q3 (sat)
t = read("q3_fix_transitive_all_floats").replace("(ite (bvslt cx cy) #b11 #b01)", "(ite (bvult cx cy) #b11 #b01)")
write("mut1_q3_unsigned_bits_compare", "sat", t)
# m1b: mutant that drops the canonical-NaN step must break q3b asymmetry/totality? (NaN payloads differ -> still total) keep as info
# m2: queue comparator mutant with <= must violate asymmetry without NaN (q2b sat)
t = read("q2b_lt_q_asymmetric_or_total_violation_without_nan").replace("(ite (fp.lt x y) true", "(ite (fp.leq x y) true")
write("mut2_q2b_leq", "sat", t)
# m3: q2 transitivity with the NaN exclusion removed is the bug itself (= q1): sat
t = read("q2_lt_q_transitive_without_nan")
t = "\n".join(l for l in t.splitlines() if "fp.isNaN" not in l or "define-fun" in l) + "\n"
write("mut3_q2_without_nonnan_precondition", "sat", t)
# m4: r1 with a -0 start must allow -0 (sat)
t = read("r1_sum_fold_never_negative_zero").replace("(assert (not (= s (_ -zero 8 24))))\n", "")
write("mut4_r1_negzero_start", "sat", t)
# m5: r5 with a negative start must allow negative results (sat)
t = read("r5_max_fold_from_zero_never_negative_or_negzero").replace("(assert (fp.isPositive s))\n", "")
write("mut5_r5_negative_start", "sat", t)
# m6: q5b without the signed-zero exclusion must find the -0/+0 disagreement (sat)
t = read("q5b_topdims_vs_alldims_agree_otherwise").replace("(assert (not (and (fp.isZero va) (fp.isZero vb))))\n", "")
write("mut6_q5b_signed_zero", "sat", t)
print("ok")
