"""Static rule/action model of BrazilianStemmer.step2 (lines 573-1058 at 5adff78bb168).

Mapping (model element -> Java):
  block k        -> 'if (RV.length() >= k) {' guards (lines 577, ..., 941, 997)
  branch (t, r)  -> 'if (suffix(RV, t)) { CT = removeSuffix(CT, r); return true; }'
  purity         -> suffix(RV, t) reads RV only; step2 never assigns RV (only line 574 'RV == null')

Obligations, derived from the step2 javadoc (lines 566-572):
  O1 rule = action: 'if found, delete' -> every branch deletes the suffix it tests (t == r)
  O2 reachability: every branch is reachable for some RV (no earlier branch whose test implies it)
     earlier test t1 implies later test t2 iff t1 is a suffix-extension of t2? No: a later branch t2 is dead iff
     some earlier branch t1 (in a block whose guard is implied) satisfies endsWith(t2) => endsWith(t1),
     i.e. t1 is a suffix of t2 (t2.endswith(t1)); the identical-test case t1 == t2 is included.
  O3 'longest first': implied by O2 (a shorter suffix tested before a longer one that ends with it makes the longer dead)
Prints every violation with lines. Usage: python3 -I step2_rules.py <BrazilianStemmer.java>
"""
import re, sys

src = open(sys.argv[1], encoding="utf-8").read().split("\n")
start = next(i for i, l in enumerate(src) if "private boolean step2()" in l)
end = next(i for i in range(start, len(src)) if src[i].startswith("  }"))
branches = []  # (line, block_guard, tested, removed)
guard = None
for i in range(start, end + 1):
    l = src[i]
    m = re.search(r"if \(RV\.length\(\) >= (\d+)\)", l)
    if m:
        guard = int(m.group(1))
    m = re.search(r'if \(suffix\(RV, "([a-z]+)"\)\) \{', l)
    if m:
        t = m.group(1)
        body = src[i + 1].strip()
        mr = re.match(r'CT = removeSuffix\(CT, "([a-z]+)"\);', body)
        assert mr and src[i + 2].strip() == "return true;", (i + 1, body)
        branches.append((i + 1, guard, t, mr.group(1)))
assert all(not re.search(r"\bRV\s*=[^=]", src[i]) for i in range(start, end + 1)), "RV assigned in step2"
print(f"step2 lines {start + 1}-{end + 1}: {len(branches)} branches, RV never assigned")
bad = [(ln, t, r) for ln, g, t, r in branches if t != r]
print(f"O1 rule!=action: {len(bad)}", *[f"  line {ln}: tests '{t}' removes '{r}'" for ln, t, r in bad], sep="\n")
dead = []
for j, (lj, gj, tj, rj) in enumerate(branches):
    for li, gi, ti, ri in branches[:j]:
        # earlier branch is evaluated whenever the later one is (guards: RV.length() >= gi holds when RV ends with tj
        # and len(tj) >= gi, which is true for every branch here since len(t) >= its block guard)
        if tj.endswith(ti) and len(tj) >= gi:
            dead.append((lj, tj, rj, li, ti, ri))
            break
print(f"O2 unreachable branches: {len(dead)}")
for lj, tj, rj, li, ti, ri in dead:
    kind = "identical test, identical action (no behaviour)" if (ti == tj and ri == rj) else (
        "identical test, DIFFERENT action" if ti == tj else "shorter suffix tested first")
    print(f"  line {lj} '{tj}'->'{rj}' dead: shadowed by line {li} '{ti}'->'{ri}' [{kind}]")
assert all(len(t) >= g for _, g, t, _ in branches)
tested = {t for _, _, t, _ in branches}
removed = {r for _, _, _, r in branches}
print("removed-but-never-tested suffixes:", sorted(removed - tested))
print("effective (reachable) removals of 'ira':", [ln for ln, g, t, r in branches if r == "ira" and t == "ira" and ln not in {d[0] for d in dead}])
print("branches testing 'ava'* :", [(ln, t) for ln, g, t, r in branches if t.endswith("ava") or t.startswith("ava")])
