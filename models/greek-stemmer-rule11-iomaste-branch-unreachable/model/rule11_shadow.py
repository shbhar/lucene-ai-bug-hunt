# Solver query: GreekStemmer.rule11 (lucene/analysis/common/.../el/GreekStemmer.java @ 5adff78bb168)
#
# Mapping table (model element -> Java line):
#   w, |w|          -> the word s[0..len) and len (rule11(char[] s, int len), :474)
#   G1 = |w|>6 && suffix("ομαστε", w)   -> :475  'if (len > 6 && endsWith(s, len, "ομαστε"))'
#   G2 = |w|>7 && suffix("ιομαστε", w)  -> :480  '} else if (len > 7 && endsWith(s, len, "ιομαστε"))'
#   endsWith(s,len,suf) == suffix(suf, w) -> :902-909 (char-by-char compare of the last |suf| chars; false if |suf|>len)
#   branch 2 taken  <=>  !G1 && G2 (else-if)
#   stem() skips rule21 when rule11 changed len -> :70 'if (len == origLen) len = rule21(s, len);'
#
# Queries (z3 string theory; each answer is re-checked by execution on the stock jar, see ../candidates/*/repro):
#   Q1 UNSAT : exists w. G2(w) && !G1(w)                 -> the :480 branch (and its 'ον' add-back :482-489) is unreachable
#   Q2 SAT   : exists w. G2(w)                           -> vacuity probe: the guard of :480 is satisfiable by itself
#   Q3 UNSAT : exists w. G2(w) && !endsWith(w[0..|w|-6), "ι") -> every -ιομαστε word leaves a trailing ι after :476
#   Q4 SAT   : exists w. G1(w) && !G2(w)                 -> after the swap the 'ομαστε' branch (and its add-back) stays reachable
#   Q5 SAT   : exists w. G1'(w) where the swapped order's first guard is G2 -> after the swap :480's body runs
import sys
from z3 import String, StringVal, Length, SuffixOf, SubString, And, Not, Solver, sat, unsat

w = String("w")
OM = StringVal("ομαστε")
IOM = StringVal("ιομαστε")
G1 = And(Length(w) > 6, SuffixOf(OM, w))
G2 = And(Length(w) > 7, SuffixOf(IOM, w))
rem6 = SubString(w, 0, Length(w) - 6)

def q(name, expect, *cs):
    s = Solver()
    s.set("timeout", 60000)
    s.add(*cs)
    r = s.check()
    ok = (r == expect)
    model = ""
    if r == sat:
        model = " model w=" + s.model()[w].as_string()
    print(f"{name}: {r} (expected {expect}) {'OK' if ok else 'MISMATCH'}{model}")
    return ok

ok = True
ok &= q("Q1 branch :480 reachable (G2 && !G1)", unsat, G2, Not(G1))
ok &= q("Q2 vacuity: G2 satisfiable", sat, G2)
ok &= q("Q3 G2 && remainder after -6 does not end in iota", unsat, G2, Not(SuffixOf(StringVal("ι"), rem6)))
ok &= q("Q4 after swap: G1 && !G2 (omaste branch still reachable)", sat, G1, Not(G2))
ok &= q("Q5 after swap: G2 first is reachable", sat, G2, Length(w) == 11)
print("ALL_OK" if ok else "SOME_MISMATCH")
sys.exit(0 if ok else 1)
