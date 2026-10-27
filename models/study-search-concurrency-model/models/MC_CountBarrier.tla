------------------------- MODULE MC_CountBarrier -------------------------
EXTENDS CountBarrier
\* Scenario A: one segment split into two partitions searched by two slices
PlanA == << <<[leaf |-> 1, m |-> 2]>>, <<[leaf |-> 1, m |-> 3]>> >>
\* Scenario C: segment 1 split over slices 1 and 2; slice 3 also holds whole segment 2;
\*             slice 1 also holds whole segment 3 (slices with several partitions)
PlanC == << <<[leaf |-> 1, m |-> 1], [leaf |-> 3, m |-> 2]>>,
           <<[leaf |-> 1, m |-> 2]>>,
           <<[leaf |-> 2, m |-> 1]>> >>
\* Scenario D: two segments, each split in two partitions, crossing slices
PlanD == << <<[leaf |-> 1, m |-> 1], [leaf |-> 2, m |-> 1]>>,
           <<[leaf |-> 1, m |-> 2], [leaf |-> 2, m |-> 2]>> >>
NoThrow1 == << [l \in {1} |-> {"count", "nocount"}] >>
Throw1 == << [l \in {1} |-> {"count", "nocount", "throw"}] >>
NoThrow3 == << [l \in {1, 2, 3} |-> {"count", "nocount"}] >>
Throw3 == << [l \in {1, 2, 3} |-> {"count", "nocount", "throw"}] >>
NoThrow2 == << [l \in {1, 2} |-> {"count", "nocount"}] >>
\* Scenario B: search 1 may throw, search 2 (same manager) does not
ReuseB == << [l \in {1} |-> {"throw"}], [l \in {1} |-> {"count", "nocount"}] >>
\* Scenario E (assurance): 3 slices, segment 1 split in 3, segment 2 split in 2, segment 3 whole
PlanE == << <<[leaf |-> 1, m |-> 1], [leaf |-> 2, m |-> 1]>>,
           <<[leaf |-> 1, m |-> 2], [leaf |-> 3, m |-> 1]>>,
           <<[leaf |-> 1, m |-> 1], [leaf |-> 2, m |-> 2]>> >>
NoThrowE2 == << [l \in {1, 2, 3} |-> {"count", "nocount"}], [l \in {1, 2, 3} |-> {"count", "nocount"}] >>
ThrowE2 == << [l \in {1, 2, 3} |-> {"count", "nocount", "throw"}], [l \in {1, 2, 3} |-> {"count", "nocount"}] >>
\* Scenario F: segment 1 (split in 2) is countable; segment 2 (whole) is not countable and its
\* scoring throws; the manager is then reused for a second search that does not throw
PlanF == << <<[leaf |-> 1, m |-> 5]>>, <<[leaf |-> 1, m |-> 5]>>, <<[leaf |-> 2, m |-> 3]>> >>
ReuseF == << [l \in {1, 2} |-> IF l = 1 THEN {"count"} ELSE {"scorethrow"}],
            [l \in {1, 2} |-> {"count", "nocount"}] >>
=============================================================================
