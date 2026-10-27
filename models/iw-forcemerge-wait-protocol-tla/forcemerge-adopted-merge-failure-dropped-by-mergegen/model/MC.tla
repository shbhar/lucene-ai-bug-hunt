---- MODULE MC ----
EXTENDS ForceMergeWait
CONSTANTS c1, c2, t1, t2
N_1c == [c \in {c1} |-> 1]
N_2c == [c \in {c1, c2} |-> IF c = c1 THEN 1 ELSE 2]
N_2c_same == [c \in {c1, c2} |-> 1]
S_1c == {c1}
S_2c == {c1, c2}
T_1 == {t1}
T_2 == {t1, t2}
====
