---- MODULE MC_A_fix ----
EXTENDS DWFlush
MCIdx == {"i1"}
MCCtl == {"c1"}
MCIScript == [t \in {"i1"} |-> <<"add","add">>]
MCCScript == [t \in {"c1"} |-> <<"nrtfail","final">>]
====
