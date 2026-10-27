; benchmark generated from python API
(set-info :status unknown)
(declare-fun positionGaps () (_ BitVec 32))
(declare-fun slop () (_ BitVec 32))
(assert
 (and (bvsge slop (_ bv0 32)) (bvsge positionGaps (_ bv0 32))))
(assert
 (let ((?x2232 (bvadd slop positionGaps)))
 (let ((?x2374 (ite (bvult ?x2232 (_ bv2147483647 32)) ?x2232 (_ bv2147483647 32))))
 (let (($x20 (and (bvsge ?x2374 slop) (bvsge ?x2374 (_ bv0 32)))))
 (not $x20)))))
(check-sat)
