; BooleanScorer.scoreWindow: windowMax=min(max,windowBase+SIZE) > topDoc (window contains the match)
; core/.../search/BooleanScorer.java:270-272 (scoreWindow; line 272 from 03ab02157a2d, 2023-08-06)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun max () (_ BitVec 32))
(declare-fun topDoc () (_ BitVec 32))
(assert
 (and (bvsge topDoc (_ bv0 32)) (bvslt topDoc (_ bv2147483519 32)) (bvslt topDoc max)))
(assert
 (let ((?x5 (bvand topDoc (bvnot (_ bv4095 32)))))
 (let ((?x7 (bvadd ?x5 (_ bv4096 32))))
 (let ((?x23 (ite (bvslt max ?x7) max ?x7)))
 (let (($x34 (bvsgt ?x23 topDoc)))
 (not $x34))))))
(check-sat)
