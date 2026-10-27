---- MODULE MC_A_nrtfail ----
EXTENDS DWFlush
MCIdx == {"i1"}
MCCtl == {"c1"}
MCIScript == [t \in {"i1"} |-> <<"add","add">>]
MCCScript == [t \in {"c1"} |-> <<"nrtfail","final">>]
====
