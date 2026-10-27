------------------------------ MODULE TopoSort ------------------------------
(* Model of Operations.topoSortStates(Automaton, int[]) (Operations.java:1396-1431, main 7cecf79523):
   iterative DFS with an explicit stack, `visited` and `onStack` bitsets, cycle detection added by
   PR #12286.  The graph is a free choice in Init (all graphs on N nodes, every successor order), so
   TLC checks the algorithm on EVERY graph with N nodes.  FIX = TRUE models the proposed fix
   (mark a state on-stack when it is pushed, so a self loop is seen). *)
EXTENDS Naturals, Sequences, FiniteSets
CONSTANTS N, FIX, ORDERED
Nodes == 0..(N-1)

(* all sequences of distinct nodes: successor lists in transition order *)
Perms(S) == { f \in [1..Cardinality(S) -> S] : \A i, j \in 1..Cardinality(S) : i # j => f[i] # f[j] }
SuccLists == IF ORDERED
             THEN UNION { Perms(S) : S \in SUBSET Nodes }
             ELSE { [i \in 1..Cardinality(S) |-> CHOOSE x \in S : Cardinality({y \in S : y < x}) = i - 1] : S \in SUBSET Nodes }

VARIABLES succ, stack, visited, onStack, out, status
vars == <<succ, stack, visited, onStack, out, status>>

Init == /\ succ \in [Nodes -> SuccLists]
        /\ stack = <<0>>
        /\ visited = IF FIX THEN {0} ELSE {}      \* Java never sets visited(0)
        /\ onStack = IF FIX THEN {0} ELSE {}
        /\ out = << >>
        /\ status = "running"

Step == /\ status = "running"
        /\ IF stack = << >> THEN /\ status' = "done" /\ UNCHANGED <<succ, stack, visited, onStack, out>>
           ELSE LET s == Head(stack)
                    L == succ[s]
                    unv == { i \in 1..Len(L) : L[i] \notin visited }
                    first == IF unv = {} THEN Len(L) + 1 ELSE CHOOSE i \in unv : \A j \in unv : i <= j
                    (* Java: in transition order, an already-visited dest that is on stack throws *)
                    throws == \E j \in 1..(first - 1) : L[j] \in onStack
                IN IF throws THEN /\ status' = "cycle" /\ UNCHANGED <<succ, stack, visited, onStack, out>>
                   ELSE IF first <= Len(L)
                        THEN /\ visited' = visited \cup {L[first]}
                             /\ stack' = <<L[first]>> \o stack
                             /\ onStack' = IF FIX THEN onStack \cup {L[first]} ELSE onStack \cup {s}
                             /\ UNCHANGED <<succ, out, status>>
                        ELSE /\ onStack' = onStack \ {s}
                             /\ stack' = Tail(stack)
                             /\ out' = Append(out, s)
                             /\ UNCHANGED <<succ, visited, status>>

Next == Step \/ (status # "running" /\ UNCHANGED vars)
Spec == Init /\ [][Next]_vars

(* ---- reference semantics ---- *)
Edge(u, v) == \E i \in 1..Len(succ[u]) : succ[u][i] = v
RECURSIVE ReachN(_, _)
ReachN(S, k) == IF k = 0 THEN S ELSE ReachN(S \cup { v \in Nodes : \E u \in S : Edge(u, v) }, k - 1)
Reach == ReachN({0}, N)
RECURSIVE PathN(_, _, _)
PathN(u, v, k) == IF k = 0 THEN FALSE ELSE (Edge(u, v) \/ \E w \in Nodes : Edge(u, w) /\ PathN(w, v, k - 1))
HasCycle == \E u \in Reach : PathN(u, u, N)

(* Javadoc: "will throw an IllegalArgumentException if a cycle is detected" / "assumes no cycles" *)
NoMissedCycle == status = "done" => ~HasCycle
NoFalseCycle  == status = "cycle" => HasCycle
(* returned order (before the final reverse) lists every reachable state once, successors first *)
TopoOK == status = "done" =>
            /\ { out[i] : i \in 1..Len(out) } = Reach
            /\ Len(out) = Cardinality(Reach)
            /\ \A i, j \in 1..Len(out) : Edge(out[j], out[i]) => i < j
Terminates == <>(status # "running")
=============================================================================
