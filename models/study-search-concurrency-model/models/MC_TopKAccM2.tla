---------------------------- MODULE MC_TopKAccM2 ----------------------------
EXTENDS TopKAccM2
\* two segments, one slice each
L1 == << [base |-> 0, len |-> 3], [base |-> 3, len |-> 2] >>
S1 == << << [leaf |-> 1, lo |-> 0, hi |-> 3] >>, << [leaf |-> 2, lo |-> 0, hi |-> 2] >> >>
\* segment 1 split into two partitions (two slices) + segment 2 in a third slice
L2 == << [base |-> 0, len |-> 4], [base |-> 4, len |-> 2] >>
S2 == << << [leaf |-> 1, lo |-> 0, hi |-> 2] >>, << [leaf |-> 1, lo |-> 2, hi |-> 4] >>,
         << [leaf |-> 2, lo |-> 0, hi |-> 2] >> >>
\* a slice with two segments (in docBase order) + a partitioned segment
L3 == << [base |-> 0, len |-> 2], [base |-> 2, len |-> 2], [base |-> 4, len |-> 2] >>
S3 == << << [leaf |-> 1, lo |-> 0, hi |-> 2], [leaf |-> 3, lo |-> 0, hi |-> 1] >>,
         << [leaf |-> 2, lo |-> 0, hi |-> 2] >>, << [leaf |-> 3, lo |-> 1, hi |-> 2] >> >>
\* bigger slices so that every collector can exceed a threshold of 2
L4 == << [base |-> 0, len |-> 6], [base |-> 6, len |-> 3] >>
S4 == << << [leaf |-> 1, lo |-> 0, hi |-> 3] >>, << [leaf |-> 1, lo |-> 3, hi |-> 6] >>,
         << [leaf |-> 2, lo |-> 0, hi |-> 3] >> >>
NoAfter == [s |-> 0, d |-> -1]
After21 == [s |-> 2, d |-> 1]
After12 == [s |-> 1, d |-> 2]
=============================================================================
