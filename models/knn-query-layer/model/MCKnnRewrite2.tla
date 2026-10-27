--------------------------- MODULE MCKnnRewrite2 --------------------------
EXTENDS KnnRewrite, TLC
\* Larger bound: three leaves of three accepted docs; scores 1..3 with ties within and across leaves.
MCLeaves == {1, 2, 3}
MCLeafDocs == (1 :> {1, 2, 3}) @@ (2 :> {4, 5, 6}) @@ (3 :> {7, 8, 9})
MCScore == (1 :> 3) @@ (2 :> 3) @@ (3 :> 1) @@ (4 :> 2) @@ (5 :> 3) @@ (6 :> 1) @@ (7 :> 2) @@ (8 :> 2) @@ (9 :> 1)
MCPLKa == (1 :> 1) @@ (2 :> 2) @@ (3 :> 3)
MCPLKb == (1 :> 3) @@ (2 :> 3) @@ (3 :> 3)
=============================================================================
