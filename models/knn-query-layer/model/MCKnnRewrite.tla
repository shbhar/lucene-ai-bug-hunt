--------------------------- MODULE MCKnnRewrite ---------------------------
EXTENDS KnnRewrite, TLC
\* Three leaves of two accepted docs each; scores with ties across leaves.
MCLeaves == {1, 2, 3}
MCLeafDocs == (1 :> {1, 2}) @@ (2 :> {3, 4}) @@ (3 :> {5, 6})
MCScore == (1 :> 3) @@ (2 :> 3) @@ (3 :> 2) @@ (4 :> 1) @@ (5 :> 2) @@ (6 :> 1)
\* perLeafTopK below k (many-leaf regime) so that the re-entry pass can run.
MCPLK1 == (1 :> 1) @@ (2 :> 1) @@ (3 :> 1)
\* mixed: leaf 1 exact when filtered (cost 2 <= 2), others approximate.
MCPLKmix == (1 :> 2) @@ (2 :> 1) @@ (3 :> 1)
MCPLK2 == (1 :> 2) @@ (2 :> 2) @@ (3 :> 2)
=============================================================================
