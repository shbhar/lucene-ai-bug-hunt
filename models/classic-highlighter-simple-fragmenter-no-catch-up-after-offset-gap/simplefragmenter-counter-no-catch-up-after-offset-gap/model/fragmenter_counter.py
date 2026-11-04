"""Model of the fragment counter of SimpleFragmenter / SimpleSpanFragmenter (classic highlighter).

Mapping (model element -> Java, Lucene main 5adff78bb168):
  cnt            -> SimpleFragmenter.currentNumFrags (start(): = 1, line 49); SimpleSpanFragmenter line 101
  fs             -> fragmentSize (constructor, lines 39-41)
  e              -> offsetAtt.endOffset() of the token being looked at (isNewFragment, line 57)
  brk(cnt,fs,e)  -> 'e >= fs * cnt' (SimpleFragmenter line 57; SimpleSpanFragmenter line 85, which also
                    requires textSize - e >= fs/2 and is not modelled: it only suppresses breaks near the end)
  old update     -> 'currentNumFrags++' (line 59 / line 89)
  fix update     -> 'currentNumFrags = max(currentNumFrags + 1, e / fs + 1)' (fix/fix.diff, fs > 0)
Highlighter.getBestTextFragments (lines 209-231) calls isNewFragment() once per token group, with the
offsets of the token that starts the next group, and starts a new TextFragment when it returns true.

Part A (z3, unbounded ints): proof obligations about one update.
Part B (bounded exhaustive differential): all strictly increasing endOffset sequences of up to 6 tokens
over [1, 14] for fs in 1..5, old vs fixed vs the grid-cell rule.
"""
import itertools, json, sys
import z3

res = {}

# ---------- Part A: z3 ----------
cnt, fs, e = z3.Ints("cnt fs e")
pre = z3.And(cnt >= 1, fs >= 1, e >= 0, e >= fs * cnt)  # a break happens at this token
fixed = z3.If(cnt + 1 >= e / fs + 1, cnt + 1, e / fs + 1)  # z3 '/' on Ints is floor division for e,fs >= 0

def prove(name, claim):
    s = z3.Solver(); s.add(z3.Not(claim)); r = s.check()
    res[name] = "proved" if r == z3.unsat else ("counterexample: %s" % s.model() if r == z3.sat else str(r))

def witness(name, cond):
    s = z3.Solver(); s.add(cond); r = s.check()
    res[name] = ("sat: %s" % s.model()) if r == z3.sat else str(r)

# O1: the fix is a no-op unless the token passes two boundaries (old and new agree when e < fs*(cnt+1))
prove("O1_fix_equals_old_without_jump", z3.Implies(z3.And(pre, e < fs * (cnt + 1)), fixed == cnt + 1))
# O2: after the fixed update the next boundary lies past this token (invariant fs*cnt > e restored)
prove("O2_fix_restores_boundary_ahead", z3.Implies(pre, fs * fixed > e))
# O3: the fixed update is exactly the grid-cell rule: next boundary = first multiple of fs past e
prove("O3_fix_is_grid_rule", z3.Implies(pre, fixed == e / fs + 1))
# O4: the old update leaves the boundary at or behind the token when e >= fs*(cnt+1): the NEXT token,
#     whatever its offset (> e), is then also a break -> a one-token fragment
witness("O4_old_boundary_behind_token", z3.And(pre, fs * (cnt + 1) <= e))
# O5: number of consecutive forced one-token fragments after one jump under the old rule = e/fs - cnt
#     (the next k tokens break as long as cnt+k <= e/fs; prove the boundary after k old steps is <= e
#      exactly when k <= e/fs - cnt)
k = z3.Int("k")
prove("O5_old_lag_length", z3.Implies(z3.And(pre, k >= 1), (fs * (cnt + k) <= e) == (k <= e / fs - cnt)))

# ---------- Part B: bounded exhaustive differential ----------
def run(ends, fsz, rule):
    c = 1; breaks = []
    for i, end in enumerate(ends):
        if i == 0:
            continue  # the first token never reaches isNewFragment (its group is not yet closed)
        if end >= fsz * c:
            breaks.append(i)
            c = c + 1 if rule == "old" else max(c + 1, end // fsz + 1)
    return breaks

def grid(ends, fsz):
    last_cell = 0; breaks = []
    for i, end in enumerate(ends):
        if i == 0:
            continue
        if end // fsz > last_cell:
            breaks.append(i); last_cell = end // fsz
    return breaks

checked = differ = fix_vs_grid_mismatch = 0
old_singletons_without_cell_change = 0
example = None
for fsz in range(1, 6):
    for n in range(1, 7):
        for ends in itertools.combinations(range(1, 15), n):
            checked += 1
            bo, bf, bg = run(ends, fsz, "old"), run(ends, fsz, "fix"), grid(ends, fsz)
            if bf != bg:
                fix_vs_grid_mismatch += 1
            if bo != bf:
                differ += 1
                # the old rule broke before a token whose endOffset lies in the SAME grid cell as the
                # token that started the current fragment
                cells = [x // fsz for x in ends]
                bset = set(bo); start = 0; bad = False
                for i in range(1, n):
                    if i in bset:
                        if cells[i] == cells[start]:
                            bad = True
                        start = i
                if bad:
                    old_singletons_without_cell_change += 1
                    if example is None or len(ends) < len(example[1]):
                        example = (fsz, ends, bo, bf)
                # differences must only arise after a jump (O1): some break token had end >= fs*(c+1)
res["B_sequences_checked"] = checked
res["B_fix_vs_grid_rule_mismatches"] = fix_vs_grid_mismatch
res["B_old_vs_fix_differ"] = differ
res["B_old_breaks_inside_one_grid_cell"] = old_singletons_without_cell_change
res["B_smallest_example(fs, endOffsets, old_breaks, fixed_breaks)"] = example
json.dump(res, sys.stdout, indent=1, default=str); print()
