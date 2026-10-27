; benchmark generated from python API
(set-info :status unknown)
(declare-fun positionGaps () (_ BitVec 32))
(declare-fun slop () (_ BitVec 32))
(assert
 (let (($x2618 (and (bvsge slop (_ bv0 32)) (bvsge positionGaps (_ bv0 32)))))
 (and $x2618 (bvsle slop (bvsub (_ bv2147483647 32) positionGaps)))))
(assert
 (let (($x709 (= ((_ sign_extend 32) (bvadd slop positionGaps)) (bvadd ((_ sign_extend 32) slop) ((_ sign_extend 32) positionGaps)))))
 (let (($x243 (and (bvsge (bvadd slop positionGaps) (_ bv0 32)) $x709)))
 (not $x243))))
(check-sat)
