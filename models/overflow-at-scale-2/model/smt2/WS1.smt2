; benchmark generated from python API
(set-info :status unknown)
(declare-fun positionGaps () (_ BitVec 32))
(declare-fun slop () (_ BitVec 32))
(assert
 (and (bvsge slop (_ bv0 32)) (bvsge positionGaps (_ bv0 32))))
(assert
 (let (($x416 (bvsge (bvadd slop positionGaps) (_ bv0 32))))
 (not $x416)))
(check-sat)
