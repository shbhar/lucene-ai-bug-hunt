---------------------------- MODULE MC ----------------------------
EXTENDS HnswConc
\* similarity from 1-D positions: sim(a,b) = -2*|p[a]-p[b]| (self = 0 is maximal);
\* positions chosen so that all pairwise distances are distinct (no score ties)
SimFromPos(p) == [ab \in Nodes \X Nodes |->
                    -2 * (IF p[ab[1]+1] >= p[ab[2]+1] THEN p[ab[1]+1] - p[ab[2]+1] ELSE p[ab[2]+1] - p[ab[1]+1])]
\* 2-D points, squared euclidean distance
SimFrom2D(p) == [ab \in Nodes \X Nodes |->
                    LET dx == p[ab[1]+1][1] - p[ab[2]+1][1]  dy == p[ab[1]+1][2] - p[ab[2]+1][2]
                    IN -2 * (dx*dx + dy*dy)]
NoRepair == <<>>
EmptyGraph == [n \in Nodes |-> <<>>]
AllLevels(maxl) == [Nodes -> 0..maxl]

\* ---- configuration C1: 3 nodes, 2 workers, two level-1 nodes racing for promotion
C1_Sim    == { SimFromPos(<<0, 1, 3>>) }
C1_Levels == { [n \in Nodes |-> IF n = 0 THEN 0 ELSE 1] }
C1_Init   == { EmptyGraph }
C1_Order  == <<0, 1, 2>>

\* ---- configuration C2: all level assignments in 0..2 for 3 nodes, several tables
C2_Sim    == { SimFromPos(<<0, 1, 3>>), SimFromPos(<<0, 2, 3>>), SimFromPos(<<3, 0, 1>>) }
C2_Levels == AllLevels(2)

\* ---- configuration C3: 4 nodes, 2 workers, all level assignments in 0..1
C3_Sim    == { SimFromPos(<<0, 1, 3, 7>>), SimFromPos(<<0, 4, 1, 6>>) }
C3_Levels == AllLevels(1)
C3_Order  == <<0, 1, 2, 3>>
\* ---- configuration C4: 4 nodes, levels in 0..2, 2-D tables
C4_Sim    == { SimFrom2D(<<<<0,0>>, <<3,1>>, <<1,4>>, <<5,5>>>>), SimFromPos(<<0, 1, 3, 7>>) }
C4_Levels == AllLevels(2)
\* ---- configuration C5: 5 nodes, levels in 0..1, one table
C5_Sim    == { SimFromPos(<<0, 1, 3, 7, 12>>) }
C5_Levels == AllLevels(1)
C5_Order  == <<0, 1, 2, 3, 4>>
\* ---- repair configurations (pruned graph as produced by copyAndPruneGraphStructure; NaN scores)
\* R1: entry node 0 lost all its neighbors on level 0; 1-2-3 kept theirs
R1_Init   == { (0 :> << <<>> >>) @@ (1 :> << <<2>> >>) @@ (2 :> << <<1, 3>> >>) @@ (3 :> << <<2>> >>) }
R1_Levels == { [n \in Nodes |-> 0] }
R1_D      == << <<0>> >>
R1_Sim    == { SimFromPos(<<0, 5, 6, 8>>), SimFromPos(<<6, 0, 1, 3>>) }
NoOrder   == << >>
\* R2: two zero-neighbor nodes repaired concurrently (0 = entry, 1), plus a 2-3 component
R2_Init   == { (0 :> << <<>> >>) @@ (1 :> << <<>> >>) @@ (2 :> << <<3>> >>) @@ (3 :> << <<2>> >>) }
R2_D      == << <<0, 1>> >>
\* R3: two levels; entry 0 at level 1 with neighbor 1; node 3 lost all level-0 neighbors
\*     (addConnections descends level 1 with a beamWidth collector)
R3_Init   == { (0 :> << <<1, 2>>, <<1>> >>) @@ (1 :> << <<0>>, <<0>> >>) @@ (2 :> << <<0>> >>) @@ (3 :> << <<>> >>) }
R3_Levels == { (0 :> 1) @@ (1 :> 1) @@ (2 :> 0) @@ (3 :> 0) }
R3_D      == << <<3>>, <<>> >>
R3_Sim    == { SimFromPos(<<0, 10, 2, 9>>), SimFromPos(<<0, 3, 10, 4>>) }
\* R4: concurrent repair of 2 nodes that each lost neighbors (non-empty entry points) + 1 insert
R4_Init   == { (0 :> << <<1>> >>) @@ (1 :> << <<0>> >>) @@ (2 :> << <<1>> >>) @@ (3 :> << <<>> >>) }
R4_D      == << <<2, 1>> >>
\* R5: 5 nodes; pruned graph with 3 flagged nodes (2 with entry points, 1 with none) repaired
\*     concurrently on level 0, a two-level graph, then node 4 is inserted
R5_Init   == { (0 :> << <<1, 2>>, <<1>> >>) @@ (1 :> << <<0>>, <<0>> >>) @@ (2 :> << <<0>> >>)
               @@ (3 :> << <<>> >>) @@ (4 :> << >>) }
R5_Levels == { (0 :> 1) @@ (1 :> 1) @@ (2 :> 0) @@ (3 :> 0) @@ (4 :> l) : l \in 0..2 }
R5_D      == << <<3, 2, 1>>, <<>> >>
R5_Sim    == { SimFromPos(<<0, 10, 2, 9, 5>>), SimFromPos(<<0, 3, 10, 4, 8>>), SimFrom2D(<<<<0,0>>, <<3,1>>, <<1,4>>, <<5,5>>, <<2,2>>>>) }
R5_Order  == << 4 >>
\* R6: 4 nodes on one level, all flagged, all with entry points (no addConnections), W=2/3
R6_Init   == { (0 :> << <<1>> >>) @@ (1 :> << <<2>> >>) @@ (2 :> << <<3>> >>) @@ (3 :> << <<0>> >>) }
R6_Levels == { [n \in Nodes |-> 0] }
R6_D      == << <<0, 1, 2, 3>> >>
R6_Sim    == { SimFromPos(<<0, 1, 3, 7>>), SimFromPos(<<0, 4, 1, 6>>), SimFrom2D(<<<<0,0>>, <<3,1>>, <<1,4>>, <<5,5>>>>) }
\* C6: merge-reuse without deletes: initialized nodes 0 (level 1, entry) and 1, workers insert 2,3,4
C6_Init   == { (0 :> << <<1>>, <<>> >>) @@ (1 :> << <<0>> >>) @@ (2 :> << >>) @@ (3 :> << >>) @@ (4 :> << >>) }
C6_Levels == { f \in [Nodes -> 0..2] : f[0] = 1 /\ f[1] = 0 }
C6_Sim    == { SimFromPos(<<0, 1, 3, 7, 12>>), SimFrom2D(<<<<0,0>>, <<3,1>>, <<1,4>>, <<5,5>>, <<2,2>>>>) }
C6_Order  == << 2, 3, 4 >>
=============================================================================
