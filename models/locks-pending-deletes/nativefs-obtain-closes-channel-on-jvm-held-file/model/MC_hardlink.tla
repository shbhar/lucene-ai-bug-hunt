---- MODULE MC_hardlink ----
EXTENDS NativeFSLock
C_Threads == {"t1","t2","t3"}
C_ProcOf == ("t1" :> "J1" @@ "t2" :> "J1" @@ "t3" :> "J2")
C_LoaderOf == ("t1" :> "L1" @@ "t2" :> "L1" @@ "t3" :> "L3")
C_PathOf == ("t1" :> "pA" @@ "t2" :> "pB" @@ "t3" :> "pA")
C_Paths == {"pA","pB"}
C_Dirs == {"dA","dB"}
C_DirOf == ("pA" :> "dA" @@ "pB" :> "dB")
C_InitEntry == ("dA" :> 1 @@ "dB" :> 1)
C_Procs == {"J1","J2"}
C_Loaders == {"L1","L3"}
====
