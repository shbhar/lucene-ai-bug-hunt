module FieldExistsCount

-- Alloy 6 model of FieldExistsQuery.count (Lucene main @ ea57360912ed), one leaf, one field f.
-- Model element -> Java line:
--   count        FieldExistsQuery.java:222-274 (norms; vectors; doc values: skipper docCount, else
--                points docCount, else terms docCount, as a proxy when the leaf has no deletions)
--   matching     FieldExistsQuery.java:166-219 (scorerSupplier: norms iterator, else vector iterator,
--                else the doc-values iterator), filtered by live docs
--   wellFormed   IndexingChain.java:2290-2305 FieldSchema.assertSameSchema: a doc that carries f
--                carries every data structure recorded in f's FieldInfo;
--                IndexingChain.java:1871-1887: a doc that has f but zero indexed tokens still gets a
--                norm ("the field exists in this document, but it did not have any indexed tokens");
--                FieldInfos.java:597-645: doc-values updates are refused unless f is doc-values only,
--                so update-time divergence between DV and terms/points is not modelled (cannot happen).
--   termsDocCount  Terms.getDocCount (docs with >= 1 term); pointsDocCount PointValues.getDocCount;
--   fieldDocCount  docs that carry f = norms / doc values / skipper / vector doc count.
-- Untokenized indexed values (StringField, KeywordField) always produce exactly one term; a tokenized
-- value may produce zero tokens (empty string, only stop words).

abstract sig Bool {}
one sig T, F extends Bool {}

one sig FI {
  norms, indexed, tokenized, points, dv, skip, vectors : one Bool
}

sig Doc {
  hasField : one Bool,
  nTerms : one Int,
  nPoints : one Int,
  live : one Bool
}

fact wellFormed {
  FI.norms = T or FI.vectors = T or FI.dv = T   -- otherwise FieldExistsQuery throws
  FI.norms = T implies FI.indexed = T
  FI.tokenized = T implies FI.indexed = T
  FI.skip = T implies FI.dv = T
  all d : Doc {
    d.nTerms >= 0 and d.nTerms <= 3 and d.nPoints >= 0 and d.nPoints <= 3
    d.hasField = F implies (d.nTerms = 0 and d.nPoints = 0)
    d.hasField = T implies {
      FI.indexed = F implies d.nTerms = 0
      (FI.indexed = T and FI.tokenized = F) implies d.nTerms >= 1
      FI.points = T implies d.nPoints >= 1
      FI.points = F implies d.nPoints = 0
    }
  }
}

fun numDocs : Int { #{d : Doc | d.live = T} }
fun maxDoc : Int { #Doc }
fun hasDeletions : Bool { (numDocs < maxDoc) => T else F }
fun termsDocCount : Int { #{d : Doc | d.nTerms > 0} }
fun pointsDocCount : Int { #{d : Doc | d.nPoints > 0} }
fun fieldDocCount : Int { #{d : Doc | d.hasField = T} }

fun matching : set Doc { {d : Doc | d.hasField = T and d.live = T} }

fun proxy : Int {
  (FI.vectors = T) => fieldDocCount
  else (FI.skip = T) => fieldDocCount
  else (hasDeletions = F and FI.points = T) => pointsDocCount
  else (hasDeletions = F and FI.indexed = T) => termsDocCount
  else -1
}

fun count : Int {
  (FI.norms = T) => ((termsDocCount = maxDoc) => numDocs else -1)
  else (proxy = 0) => 0
  else (proxy = maxDoc) => numDocs
  else (proxy >= 0 and hasDeletions = F) => proxy
  else -1
}

-- Contract (Weight.java:180-200): the exact number of matching live docs, or -1.
assert countSound { count = -1 or count = #matching }
check countSound for 4 Doc, 5 Int expect 1

-- The only failing precondition: a tokenized, norms-free indexed field that also has doc values.
assert countSoundWithoutTokenizedNoNorms {
  (FI.tokenized = F or FI.norms = T or FI.dv = F) implies (count = -1 or count = #matching)
}
check countSoundWithoutTokenizedNoNorms for 5 Doc, 5 Int expect 0

-- Vacuity: exact answers are reachable through each proxy branch.
run exactViaTerms { count != -1 and FI.norms = F and FI.vectors = F and FI.skip = F and FI.points = F and FI.indexed = T and count = #matching } for 4 Doc, 5 Int expect 1
run exactViaPoints { count != -1 and FI.points = T and FI.vectors = F and FI.skip = F and FI.norms = F } for 4 Doc, 5 Int expect 1
run exactViaNorms { count != -1 and FI.norms = T } for 4 Doc, 5 Int expect 1
