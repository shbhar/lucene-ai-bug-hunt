"""
queries-mlt-commonterms-reference bounded-exhaustive model of MoreLikeThis term selection and query construction (MoreLikeThis.java on main
@ ea57360912ed), checked against the javadoc reference.

Model element -> Java line:
  bounded queue, limit = min(maxQueryTerms, #terms)            createQueue :627-629
  add while size < limit, else replace top if top.score < score createQueue :663-673  (top() of an empty queue is
                                                                null when limit == 0 -> NPE, PriorityQueue :top)
  PriorityQueue lessThan = score ascending -> pop() lowest first FloatComparator.comparing(st -> st.score) :629
  createQuery: pop loop, first popped = 'bestScore' (boost norm),
               add SHOULD clause, break on TooManyClauses        createQuery :593-616
  BooleanQuery.Builder.add throws when size >= maxClauseCount   BooleanQuery.java :82-84
  retrieveInterestingTerms: pop loop, lim = maxQueryTerms        :888-926

Reference (javadoc): the query holds the min(maxQueryTerms, #eligible) best terms by score ("Return a Query with no
more than this many terms", "the most interesting words"); a clause-limit truncation keeps the best ones; boosts
are proportional to the score; retrieveInterestingTerms is documented through retrieveTerms(Reader, String):
"ordered by score, with the highest scoring, or best entry, first"; no exception for maxQueryTerms >= 0.

Enumerates every score vector over {1,2,3} of length 0..6, maxQueryTerms 0..7, clause limit 1..7 and checks:
  T1 kept set is a top set              (fails exactly when C < min(K, n) and a dropped score > a kept score)
  T2 no exception                       (fails exactly when K == 0 and n >= 1)
  T3 boost proportional to score        (always holds; normalisation is by the lowest selected score)
  T4 interesting-terms order best-first (fails exactly when the selected scores are not all equal)
and proves each failure characterisation over the whole scope (every case checked against the predicate).
"""
import heapq
import itertools
import json
import sys


class NPE(Exception):
    pass


def create_queue(scores, K):
    limit = min(K, len(scores))
    heap = []  # (score, seq) min-heap = Lucene PriorityQueue with lessThan score ascending
    for i, s in enumerate(scores):
        if len(heap) < limit:
            heapq.heappush(heap, (s, i))
        else:
            if not heap:
                raise NPE("queue.top() is null")  # limit == 0
            if heap[0][0] < s:
                heapq.heapreplace(heap, (s, i))
    return heap


def create_query(heap, C, boost_factor=1.0):
    heap = list(heap)
    heapq.heapify(heap)
    clauses = []
    best = -1
    while heap:
        s, i = heapq.heappop(heap)
        if best == -1:
            best = s
        b = boost_factor * s / best
        if len(clauses) >= C:  # BooleanQuery.Builder.add -> TooManyClauses -> break
            break
        clauses.append((s, i, b))
    return clauses


def interesting(heap, K):
    heap = list(heap)
    heapq.heapify(heap)
    out = []
    lim = K
    while heap:
        item = heapq.heappop(heap)
        if lim <= 0:
            break
        lim -= 1
        out.append(item)
    return out


def main():
    stats = {"cases": 0, "T1_fail": 0, "T2_fail": 0, "T3_fail": 0, "T4_fail": 0, "T4_cases": 0}
    char_ok = {"T1": True, "T2": True, "T4": True}
    first = {}
    for n in range(0, 7):
        for scores in itertools.product([1, 2, 3], repeat=n):
            for K in range(0, 8):
                for C in range(1, 8):
                    stats["cases"] += 1
                    try:
                        heap = create_queue(scores, K)
                        threw = False
                    except NPE:
                        threw = True
                    pred_T2 = K == 0 and n >= 1
                    if threw:
                        stats["T2_fail"] += 1
                        first.setdefault("T2", {"scores": scores, "K": K})
                    if threw != pred_T2:
                        char_ok["T2"] = False
                    if threw:
                        continue
                    clauses = create_query(heap, C)
                    expected_count = min(K, n, C)
                    kept = sorted(s for s, _, _ in clauses)
                    kept_idx = {i for _, i, _ in clauses}
                    dropped = [scores[i] for i in range(n) if i not in kept_idx]
                    t1_fail = len(kept) != expected_count or (kept and dropped and max(dropped) > min(kept))
                    # predicate: the code keeps the lowest C of the top min(K, n); it fails iff that set
                    # contains a score below one of the top min(K, n) it dropped
                    top = sorted(scores, reverse=True)[: min(K, n)]
                    kept_model = sorted(top)[:C] if C < len(top) else sorted(top)
                    pred_T1 = C < min(K, n) and max(sorted(top)[C:]) > min(kept_model)
                    if t1_fail:
                        stats["T1_fail"] += 1
                        first.setdefault("T1", {"scores": scores, "K": K, "C": C, "kept": kept})
                    if bool(t1_fail) != bool(pred_T1):
                        char_ok["T1"] = False
                        first.setdefault("T1_char_violation", {"scores": scores, "K": K, "C": C})
                    # T3: boost / score constant
                    if clauses:
                        r0 = clauses[0][2] / clauses[0][0]
                        if any(abs(b / s - r0) > 1e-12 for s, _, b in clauses):
                            stats["T3_fail"] += 1
                    # T4: interesting terms best first (only meaningful when nothing threw)
                    it = [s for s, _ in interesting(heap, K)]
                    if len(set(it)) > 1:
                        stats["T4_cases"] += 1
                    t4_fail = any(it[j] < it[j + 1] for j in range(len(it) - 1))
                    pred_T4 = len(set(it)) > 1
                    if t4_fail:
                        stats["T4_fail"] += 1
                    if t4_fail != pred_T4:
                        char_ok["T4"] = False
    # vacuity probe: a mutant createQuery that pops best-first must make T1 never fail
    mut_fail = 0
    for n in range(0, 7):
        for scores in itertools.product([1, 2, 3], repeat=n):
            for K in range(1, 8):
                for C in range(1, 8):
                    heap = create_queue(scores, K)
                    best_first = sorted(heap, reverse=True)[:C]
                    kept_idx = {i for _, i in best_first}
                    kept = [s for s, _ in best_first]
                    dropped = [scores[i] for i in range(n) if i not in kept_idx]
                    if kept and dropped and max(dropped) > min(kept):
                        mut_fail += 1
    out = {"stats": stats, "characterisations_hold": char_ok, "first_examples": first,
           "mutant_best_first_T1_failures": mut_fail}
    print(json.dumps(out, indent=1, default=list))
    return 0 if all(char_ok.values()) and mut_fail == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
