--------------------------- MODULE MC_LRUCache ---------------------------
EXTENDS LRUCache
SegsA == {"A", "B"}
InstsA == {"q1a", "q1b", "q2"}
\* q1a and q1b are equal queries (same value class 1) with DIFFERENT RAM estimates
QValA == [i \in InstsA |-> IF i = "q2" THEN 2 ELSE 1]
QBytesA == [i \in InstsA |-> CASE i = "q1a" -> 3 [] i = "q1b" -> 5 [] OTHER -> 4]
=============================================================================
