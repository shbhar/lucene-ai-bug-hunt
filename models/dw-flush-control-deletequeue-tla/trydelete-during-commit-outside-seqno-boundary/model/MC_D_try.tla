---- MODULE MC_D_try ----
EXTENDS DWFlush
MCIdx == {"i1","i2"}
MCCtl == {"c1"}
MCIScript == [t \in {"i1","i2"} |-> IF t = "i1" THEN <<"add","try">> ELSE <<"add">>]
MCCScript == [t \in {"c1"} |-> <<"nrt","commit","final">>]
====
