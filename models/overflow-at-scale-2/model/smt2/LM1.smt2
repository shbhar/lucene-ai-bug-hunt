; benchmark generated from python API
(set-info :status unknown)
(declare-fun numSegments () (_ BitVec 32))
(declare-fun upto () (_ BitVec 32))
(declare-fun start () (_ BitVec 32))
(declare-fun mergeFactor () (_ BitVec 32))
(assert
 (let (($x38 (bvsle numSegments (_ bv1000000 32))))
 (and (bvsge mergeFactor (_ bv2 32)) (bvsge numSegments (_ bv1 32)) $x38 (bvsge start (_ bv0 32)) (bvslt start numSegments) (bvsge upto start) (bvslt upto numSegments))))
(assert
 (let (($x200 (= ((_ sign_extend 32) (bvadd start mergeFactor)) (bvadd ((_ sign_extend 32) start) ((_ sign_extend 32) mergeFactor)))))
 (not $x200)))
(check-sat)
