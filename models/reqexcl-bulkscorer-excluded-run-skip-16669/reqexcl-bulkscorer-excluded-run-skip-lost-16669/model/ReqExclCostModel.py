#!/usr/bin/env python3
"""Bounded-exhaustive cost and correctness model of ReqExclBulkScorer.score (reqexcl-bulkscorer-excluded-run-skip-16669).

Three loop versions, transcribed line by line, driving the same required bulk scorer:

  model element                 Java (file:line)
  ----------------------------  ---------------------------------------------------------------------------------
  old_score  (10.3.0-10.5.2)    ReqExclBulkScorer.java:52-77 of releases/lucene/10.5.2 (leap-frog, docIDRunEnd skip;
                                non-two-phase branch only, as in 10.5.2)
  main_score (main, 10.6 RC1)   ReqExclBulkScorer.java:53-93 @ 645d4fe14a20 (4096-doc windows, intoBitSet, flip,
                                applyMask, req.score per window)
  fix=True                      fix/fix.diff (run skip at the window start before windowing)
  W                             ReqExclBulkScorer.WINDOW_SIZE = 4096 (scaled down for the exhaustive scopes)
  default_bulk_score            Weight.DefaultBulkScorer.score + scoreIterator, Weight.java:252-301 @ 645d4fe14a20
                                (positioning: nextDoc if docID == min-1 else advance; then one nextDoc per doc < max,
                                acceptDocs checked per doc)
  Excl.intoBitSet               DocIdSetIterator#intoBitSet default semantics (docs in [docID(), upTo), iterator left
                                on the first doc >= upTo)
  Excl.doc_id_run_end           DocIdSetIterator#docIDRunEnd: mode 'max' = end of the maximal run of consecutive
                                docs (an iterator that reports runs exactly), mode 'one' = docID()+1 (the default)
  window_bits                   ReqExclBulkScorer.WindowBits (get(i) = mask[i - windowBase])
  live                          acceptDocs (live docs); None = no deletions

Cost = number of nextDoc()/advance() calls on the required iterator ("required moves"), the quantity the JUnit
asserts on. Properties checked on every case of each scope:
  P1  collected docs are identical for old, main and fix, and equal (R \\ X) intersected with live
  P2  required moves(fix) <= required moves(main)
  P3  X = one run [a, b), R = all docs, no deletions: moves(fix) <= |R \\ X| + W + 2 (independent of the run length)
      while moves(main) = N + 1; checked for every (a, b)
  P4  sanity: moves(old) <= |R \\ X| + (#runs of X) + 2 in mode 'max'
  P5  sub-range scoring (one fresh scorer per [min, max) partition, as intra-segment concurrency does): the docs
      collected over a partition of [0, N) into consecutive ranges equal (R \\ X) & live, for old, main and fix
  P6  chunked re-entrant scoring of ONE scorer instance (TimeLimitingBulkScorer.java:70-85 pattern:
      min = score(min, min(min + interval, max)) until min >= max): collected docs equal (R \\ X) & live
Calibration: N = 1,000,000, W = 4096, X = [0, 999000), R = all: the model must give the measured jar numbers
(old 1,001; main 1,000,001; fix 1,001 -- harness/out-*.txt).
"""
import itertools
import sys

NO_MORE = 2**31 - 1


class Iter:
    """Sorted-list DocIdSetIterator with move counting."""

    def __init__(self, docs, run_mode="max"):
        self.docs = docs
        self.i = -1
        self.doc = -1
        self.moves = 0
        self.run_mode = run_mode
        self.docset = set(docs)

    def _set(self, i):
        self.i = i
        self.doc = self.docs[i] if i < len(self.docs) else NO_MORE
        return self.doc

    def next_doc(self):
        self.moves += 1
        return self._set(self.i + 1)

    def advance(self, target):
        self.moves += 1
        j = max(self.i + 1, 0)
        while j < len(self.docs) and self.docs[j] < target:
            j += 1
        return self._set(j)

    def doc_id_run_end(self):
        assert self.doc != -1 and self.doc != NO_MORE
        if self.run_mode == "one":
            return self.doc + 1
        e = self.doc + 1
        while e in self.docset:
            e += 1
        return e

    def into_bitset(self, up_to, bits, offset):
        d = self.doc
        while d < up_to:
            bits[d - offset] = True
            d = self.next_doc()


class WindowBits:
    def __init__(self, base, end, mask):
        self.base, self.end, self.mask = base, end, mask

    def get(self, i):
        assert self.base <= i < self.end
        return self.mask[i - self.base]


class Live:
    def __init__(self, live):
        self.live = live

    def get(self, i):
        return self.live[i]

    def length(self):
        return len(self.live)


def default_bulk_score(req, collected, accept, mn, mx):
    if req.doc < mn:
        if req.doc == mn - 1:
            req.next_doc()
        else:
            req.advance(mn)
    d = req.doc
    while d < mx:
        if accept is None or accept.get(d):
            collected.append(d)
        d = req.next_doc()
    return req.doc


def old_score(req, excl, accept, collected, mn, mx):
    up_to = mn
    excl_doc = excl.doc
    while up_to < mx:
        if excl_doc < up_to:
            excl_doc = excl.advance(up_to)
        if excl_doc == up_to:
            up_to = min(excl.doc_id_run_end(), mx)
            excl_doc = excl.next_doc()
        else:
            up_to = default_bulk_score(req, collected, accept, up_to, min(excl_doc, mx))
    if up_to == mx:
        up_to = default_bulk_score(req, collected, accept, up_to, up_to)
    return up_to


def main_score(req, excl, accept, collected, mn, mx, W, fix=False):
    up_to = mn
    while up_to < mx:
        wmin = up_to
        wmax = min(mx, wmin + W)
        if excl.doc < wmin:
            excl.advance(wmin)
        if fix and excl.doc == wmin:
            run_end = excl.doc_id_run_end()
            if run_end > wmin:
                up_to = min(mx, run_end)
                continue
        wlen = wmax - wmin
        mask = [False] * W
        excl.into_bitset(wmax, mask, wmin)
        valid = wlen if accept is None else max(0, min(wlen, accept.length() - wmin))
        for i in range(valid):
            mask[i] = not mask[i]
        for i in range(valid, wlen):
            mask[i] = False
        if accept is not None:
            for i in range(wlen):
                if mask[i] and not accept.get(wmin + i):
                    mask[i] = False
        up_to = default_bulk_score(req, collected, WindowBits(wmin, wmax, mask), wmin, wmax)
    if up_to == mx:
        up_to = default_bulk_score(req, collected, accept, mx, mx)
    return up_to


def score_call(alg, req, excl, acc, out, mn, mx, W):
    if alg == "old":
        return old_score(req, excl, acc, out, mn, mx)
    return main_score(req, excl, acc, out, mn, mx, W, fix=(alg == "fix"))


def run_partitioned(alg, R, X, live, W, mode, bounds):
    out = []
    for mn, mx in bounds:  # fresh scorer per partition
        req, excl = Iter(R), Iter(X, mode)
        score_call(alg, req, excl, None if live is None else Live(live), out, mn, mx, W)
    return out


def run_chunked(alg, n, R, X, live, W, mode, interval):
    req, excl = Iter(R), Iter(X, mode)
    acc = None if live is None else Live(live)
    out = []
    mn, mx = 0, NO_MORE
    while mn < mx:
        new_max = min(mn + interval, mx)
        mn = score_call(alg, req, excl, acc, out, mn, new_max, W)
    return out


def run(alg, n, R, X, live, W, mode):
    req = Iter(R)
    excl = Iter(X, mode)
    acc = None if live is None else Live(live)
    out = []
    if alg == "old":
        old_score(req, excl, acc, out, 0, NO_MORE)
    else:
        main_score(req, excl, acc, out, 0, NO_MORE, W, fix=(alg == "fix"))
    return out, req.moves


def runs_of(X):
    r, prev = 0, None
    for d in X:
        if prev is None or d != prev + 1:
            r += 1
        prev = d
    return r


def check_case(n, R, X, live, W, mode, stats):
    exp = [d for d in R if d not in set(X) and (live is None or live[d])]
    res = {}
    for alg in ("old", "main", "fix"):
        res[alg] = run(alg, n, R, X, live, W, mode)
        if res[alg][0] != exp:
            stats["P1_fail"] += 1
            if stats["P1_fail"] <= 5:
                print("P1 FAIL", alg, n, R, X, live, mode, res[alg][0], exp)
    stats["cases"] += 1
    if res["fix"][1] > res["main"][1]:
        stats["P2_fail"] += 1
        if stats["P2_fail"] <= 5:
            print("P2 FAIL", n, R, X, live, mode, res["fix"][1], res["main"][1])
    if mode == "max" and live is None:
        if res["old"][1] > len([d for d in R if d not in set(X)]) + runs_of(X) + 2:
            stats["P4_fail"] += 1
            if stats["P4_fail"] <= 5:
                print("P4 FAIL", n, R, X, res["old"][1])
    stats["main_moves"] += res["main"][1]
    stats["fix_moves"] += res["fix"][1]
    stats["old_moves"] += res["old"][1]


def subsets(n):
    for m in range(1 << n):
        yield [d for d in range(n) if (m >> d) & 1]


def main():
    W = 4
    stats = dict(cases=0, P1_fail=0, P2_fail=0, P4_fail=0, main_moves=0, fix_moves=0, old_moves=0)
    # Scope A: N <= 12, every X, R = all docs, no deletions, both run modes
    for n in range(1, 13):
        R = list(range(n))
        for X in subsets(n):
            for mode in ("max", "one"):
                check_case(n, R, X, None, W, mode, stats)
    print("scope A (N<=12, all X, R=all, no deletions, run modes max/one):", dict(stats))
    sA = dict(stats)
    # Scope B: N <= 8, every (R, X), no deletions, both run modes
    for k in stats:
        stats[k] = 0
    for n in range(1, 9):
        for R in subsets(n):
            for X in subsets(n):
                for mode in ("max", "one"):
                    check_case(n, R, X, None, W, mode, stats)
    print("scope B (N<=8, all R, all X, no deletions, run modes max/one):", dict(stats))
    sB = dict(stats)
    # Scope C: N <= 6, every (R, X, live), both run modes
    for k in stats:
        stats[k] = 0
    for n in range(1, 7):
        for R in subsets(n):
            for X in subsets(n):
                for lm in range(1 << n):
                    live = [bool((lm >> d) & 1) for d in range(n)]
                    for mode in ("max", "one"):
                        check_case(n, R, X, live, W, mode, stats)
    print("scope C (N<=6, all R, all X, all live-docs masks, run modes max/one):", dict(stats))
    sC = dict(stats)
    # P5/P6: sub-range scoring and chunked re-entrant scoring, N <= 7, all (R, X), live = None or one mask with doc 0 and
    # the last doc deleted, run modes max/one; partitions = every split of [0, N) into consecutive ranges (last range
    # ends at NO_MORE_DOCS); chunk intervals 1..N+1
    p5 = p5_fail = p6 = p6_fail = 0
    for n in range(1, 8):
        splits = []
        for m in range(1 << (n - 1)):
            cuts = [i + 1 for i in range(n - 1) if (m >> i) & 1]
            pts = [0] + cuts + [NO_MORE]
            splits.append(list(zip(pts[:-1], pts[1:])))
        lives = [None, [d not in (0, n - 1) for d in range(n)]]
        for R in subsets(n):
            for X in subsets(n):
                for live in lives:
                    exp = [d for d in R if d not in set(X) and (live is None or live[d])]
                    for mode in ("max", "one"):
                        for alg in ("old", "main", "fix"):
                            for b in splits:
                                p5 += 1
                                if run_partitioned(alg, R, X, live, W, mode, b) != exp:
                                    p5_fail += 1
                                    if p5_fail <= 5:
                                        print("P5 FAIL", alg, n, R, X, live, mode, b)
                            for interval in range(1, n + 2):
                                p6 += 1
                                if run_chunked(alg, n, R, X, live, W, mode, interval) != exp:
                                    p6_fail += 1
                                    if p6_fail <= 5:
                                        print("P6 FAIL", alg, n, R, X, live, mode, interval)
    print(f"P5 (partitioned, N<=7): checks={p5} failures={p5_fail}; P6 (chunked one instance, N<=7): checks={p6} failures={p6_fail}")
    # P3: one run [a, b), R = all, N = 64, W = 4, every (a, b)
    p3_cases = p3_fail = main_above = 0
    n = 64
    R = list(range(n))
    for a in range(n):
        for b in range(a + 1, n + 1):
            X = list(range(a, b))
            _, mf = run("fix", n, R, X, None, W, "max")
            _, mm = run("main", n, R, X, None, W, "max")
            p3_cases += 1
            bound = (n - (b - a)) + W + 2
            if mf > bound:
                p3_fail += 1
                if p3_fail <= 5:
                    print("P3 FAIL", a, b, mf, bound)
            if mm != n + 1:
                print("P3 main != N+1", a, b, mm)
            if mm > bound:
                main_above += 1
    print(f"P3 (N=64, W=4, X=[a,b), R=all): cases={p3_cases} fix_over_bound={p3_fail} main_over_bound={main_above}")
    # Calibration at full scale
    n, W2, cut = 1_000_000, 4096, 999_000
    R = list(range(n))
    X = list(range(cut))
    cal = {alg: run(alg, n, R, X, None, W2, "max")[1] for alg in ("old", "main", "fix")}
    print("calibration N=1,000,000 W=4096 X=[0,999000) R=all: required moves", cal)
    ok = (sA["P1_fail"] == sA["P2_fail"] == sA["P4_fail"] == 0 and sB["P1_fail"] == sB["P2_fail"] == sB["P4_fail"] == 0
          and sC["P1_fail"] == sC["P2_fail"] == 0 and p3_fail == 0 and p5_fail == 0 and p6_fail == 0
          and cal == {"old": 1001, "main": 1000001, "fix": 1001})
    print("ALL PROPERTIES HOLD" if ok else "SOME PROPERTY FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
