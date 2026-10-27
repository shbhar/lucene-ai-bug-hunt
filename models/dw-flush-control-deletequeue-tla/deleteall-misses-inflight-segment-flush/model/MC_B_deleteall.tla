---- MODULE MC_B_deleteall ----
EXTENDS DWFlush
MCIdx == {"i1"}
MCCtl == {"c1"}
MCIScript == [t \in {"i1"} |-> <<"add","add">>]
MCCScript == [t \in {"c1"} |-> <<"da","final">>]
====
