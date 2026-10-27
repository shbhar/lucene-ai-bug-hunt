------------------------- MODULE MC_QueryCacheDV -------------------------
EXTENDS QueryCacheDV
\* qOk      : SortedNumericDocValuesRangeQuery shape (checks every field it reads)
\* qBool    : BooleanWeight over two qOk-like clauses (conjunction of isCacheable)
\* qConst   : BaseShapeDocValuesQuery / LateInteractionFloatValuesSource shape (constant true)
\* qPartial : facet range ValueSourceQuery with a fastMatchQuery (checks only the value source field)
MCFields == {"f", "g"}
MCDocs == {1, 2}
MCRead == [q \in {"qOk", "qBool", "qConst", "qPartial"} |->
             CASE q = "qOk" -> {"f"} [] q = "qBool" -> {"f", "g"}
               [] q = "qConst" -> {"f"} [] q = "qPartial" -> {"f", "g"}]
MCCheck == [q \in {"qOk", "qBool", "qConst", "qPartial"} |->
             CASE q = "qOk" -> {"f"} [] q = "qBool" -> {"f", "g"}
               [] q = "qConst" -> {} [] q = "qPartial" -> {"g"}]
MCConstTrue == [q \in {"qOk", "qBool", "qConst", "qPartial"} |-> q = "qConst"]
=============================================================================
