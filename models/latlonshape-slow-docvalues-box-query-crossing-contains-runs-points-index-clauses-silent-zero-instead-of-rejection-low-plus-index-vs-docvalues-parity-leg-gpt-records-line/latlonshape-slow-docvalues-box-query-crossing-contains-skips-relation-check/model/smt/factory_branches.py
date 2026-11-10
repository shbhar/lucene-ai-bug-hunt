# latlonshape-slow-docvalues-box-query-crossing-contains-runs-points-index-clauses-silent-zero-instead-of-rejection-low-plus-index-vs-docvalues-parity-leg-gpt-records-line: solver query over the transcribed branch structure of LatLonShape.newSlowDocValuesBoxQuery.
#
# Model element -> Lucene line (main f794ff5a505c):
#   split        LatLonShape.java:270  if (queryRelation == CONTAINS && minLongitude > maxLongitude)
#   halves       LatLonShape.java:271-279 newBoxQuery(.., minLon, 180) MUST newBoxQuery(.., -180, maxLon)
#   newBoxQuery  LatLonShape.java:246-259 same split, else new Rectangle(...) (validates) + LatLonShapeBoundingBoxQuery
#   dv path      LatLonShape.java:282-283 new LatLonShapeDocValuesQuery(field, rel, new Rectangle(...))
#   Rectangle    geo/Rectangle.java:85-97 checkLatitude/checkLongitude (IAE outside [-90,90] / [-180,180] or NaN)
#   validate     BaseShapeDocValuesQuery.java:46-51 CONTAINS -> IAE "does not yet support CONTAINS queries"
#
# Outcome kinds: IAE (rejected), DVQ (doc-values query), PTQ (points-index query), SOE (unbounded recursion).
# Contract (the doc-values factory's own validation, applied to every box): for a valid box the outcome is IAE when
# rel == CONTAINS and DVQ otherwise; it is never PTQ.
# Query 1 (stock): exists a valid box and relation with outcome != contract?  expected SAT, region = CONTAINS & minLon > maxLon
# Query 2 (stock): is that the whole region? (outcome != contract  <=>  CONTAINS & minLon > maxLon)  expected UNSAT of negation
# Query 3 (fix = split removed from the dv factory): exists a valid box with outcome != contract?  expected UNSAT
# Doubles are modelled as reals (only comparisons occur on these paths; NaN is excluded by Rectangle and
# handled by the bounded run on the real jar, model/bx).
from z3 import Real, Int, And, Or, Not, If, Solver, sat, unsat

INTERSECTS, WITHIN, DISJOINT, CONTAINS = 0, 1, 2, 3
IAE, DVQ, PTQ, SOE = 0, 1, 2, 3

rel = Int("rel")
minLat, maxLat, minLon, maxLon = Real("minLat"), Real("maxLat"), Real("minLon"), Real("maxLon")


def valid_lat(x):
    return And(x >= -90, x <= 90)


def valid_lon(x):
    return And(x >= -180, x <= 180)


valid_box = And(valid_lat(minLat), valid_lat(maxLat), valid_lon(minLon), valid_lon(maxLon), minLat <= maxLat)
rel_dom = And(rel >= 0, rel <= 3)


def new_box_query(r, a, b, c, d, depth=2):
    """outcome kind of newBoxQuery(r, a, b, c, d); the split recursion is unrolled twice: a re-entry with the same
    arguments (c > d still holds) is SOE, the case contract-audit unit I027 records."""
    rect_ok = And(valid_lat(a), valid_lat(b), valid_lon(c), valid_lon(d))
    if depth == 0:
        return If(And(r == CONTAINS, c > d), SOE, If(rect_ok, PTQ, IAE))
    h1 = new_box_query(r, a, b, c, 180, depth - 1)
    h2 = new_box_query(r, a, b, -180, d, depth - 1)
    split = If(Or(h1 == SOE, h2 == SOE), SOE, If(Or(h1 == IAE, h2 == IAE), IAE, PTQ))
    return If(And(r == CONTAINS, c > d), split, If(rect_ok, PTQ, IAE))


def dv_factory(r, a, b, c, d, fixed):
    rect_ok = And(valid_lat(a), valid_lat(b), valid_lon(c), valid_lon(d))
    dv = If(Not(rect_ok), IAE, If(r == CONTAINS, IAE, DVQ))
    if fixed:
        return dv
    h1 = new_box_query(r, a, b, c, 180)
    h2 = new_box_query(r, a, b, -180, d)
    split = If(Or(h1 == SOE, h2 == SOE), SOE, If(Or(h1 == IAE, h2 == IAE), IAE, PTQ))
    return If(And(r == CONTAINS, c > d), split, dv)


contract = If(rel == CONTAINS, IAE, DVQ)
lines = []

for fixed in (False, True):
    out = dv_factory(rel, minLat, maxLat, minLon, maxLon, fixed)
    s = Solver()
    s.add(rel_dom, valid_box, out != contract)
    r1 = s.check()
    tag = "fix" if fixed else "stock"
    if r1 == sat:
        m = s.model()
        lines.append(f"Q1[{tag}] exists valid box with outcome != contract: SAT  witness rel={m[rel]} lat {m[minLat]}..{m[maxLat]} lon {m[minLon]}..{m[maxLon]}  outcome={m.eval(out)} (2=PTQ points query)")
    else:
        lines.append(f"Q1[{tag}] exists valid box with outcome != contract: {r1}")
    if not fixed:
        s2 = Solver()
        region = And(rel == CONTAINS, minLon > maxLon)
        s2.add(rel_dom, valid_box, Not((out != contract) == region))
        lines.append(f"Q2[stock] outcome != contract  <=>  CONTAINS & minLon > maxLon (negation): {s2.check()}  (unsat = exact region)")
        s3 = Solver()
        s3.add(rel_dom, valid_box, region, out != PTQ)
        lines.append(f"Q2b[stock] in that region the outcome is always a points query (negation): {s3.check()}")
    # I027 region, for the record: invalid longitudes with CONTAINS & minLon > maxLon
    s4 = Solver()
    s4.add(rel_dom, valid_lat(minLat), valid_lat(maxLat), minLat <= maxLat, out == SOE)
    r4 = s4.check()
    if r4 == sat:
        m = s4.model()
        lines.append(f"R[{tag}] SOE reachable (contract-audit unit I027 region): SAT  e.g. rel={m[rel]} lon {m[minLon]}..{m[maxLon]}")
    else:
        lines.append(f"R[{tag}] SOE reachable (contract-audit unit I027 region): {r4}")

print("\n".join(lines))
ok = (lines[0].startswith("Q1[stock]") and "SAT" in lines[0] and "unsat" in lines[1] and "unsat" in lines[2]
      and any(l.startswith("Q1[fix]") and l.endswith("unsat") for l in lines))
print("RESULT", "as-expected" if ok else "UNEXPECTED")
raise SystemExit(0 if ok else 1)
