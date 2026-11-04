---------------------------- MODULE MC ----------------------------
EXTENDS FindSegments
\* commit 1 = {a}; commit 2 flushes b; commit 3 merges a,b into c (a, b no longer referenced)
RefsMerge == <<{"a"}, {"a", "b"}, {"c"}>>
\* 4 commits: flush b, merge a,b->c, flush d
RefsMerge4 == <<{"a"}, {"a", "b"}, {"c"}, {"c", "d"}>>
\* commit 2 = metadata-only commit (same segments, e.g. setLiveCommitData + commit)
RefsMeta == <<{"a"}, {"a"}, {"a"}>>
RefsMeta4 == <<{"a"}, {"a"}, {"a"}, {"a"}>>
\* commit 2 replaces a by b (merge/forceMerge of a single segment)
RefsReplace == <<{"a"}, {"b"}, {"c"}>>
SegsABC == {"a", "b", "c"}
SegsABCD == {"a", "b", "c", "d"}
KOpen == [r1 |-> "open"]
KList == [r1 |-> "list"]
KReopen == [r1 |-> "reopen"]
KOpenList == [r1 |-> "open", r2 |-> "list"]
KOpenReopen == [r1 |-> "open", r2 |-> "reopen"]
=============================================================================
