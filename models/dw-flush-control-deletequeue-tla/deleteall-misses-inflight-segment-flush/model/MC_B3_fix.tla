---- MODULE MC_B3_fix ----
EXTENDS DWFlush
MCIdx == {"i1","i2","i3"}
MCCtl == {"c1"}
MCIScript == [t \in {"i1","i2","i3"} |-> IF t = "i1" THEN <<"add","add">> ELSE <<"add">>]
MCCScript == [t \in {"c1"} |-> <<"da","final">>]
====
