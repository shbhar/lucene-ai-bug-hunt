---------------------------- MODULE MC_TopKAccM3 ----------------------------
EXTENDS TopKAcc
\* mutant configuration: a slice whose partitions are NOT in docBase order
L3 == << [base |-> 0, len |-> 2], [base |-> 2, len |-> 2], [base |-> 4, len |-> 2] >>
S3rev == << << [leaf |-> 3, lo |-> 0, hi |-> 1], [leaf |-> 1, lo |-> 0, hi |-> 2] >>,
         << [leaf |-> 2, lo |-> 0, hi |-> 2] >>, << [leaf |-> 3, lo |-> 1, hi |-> 2] >> >>
NoAfter == [s |-> 0, d |-> -1]
=============================================================================
