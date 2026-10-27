; benchmark generated from python API
(set-info :status unknown)
(declare-fun mergeFactor () (_ BitVec 32))
(declare-fun start () (_ BitVec 32))
(declare-fun numSegments () (_ BitVec 32))
(declare-fun upto () (_ BitVec 32))
(assert
 (let (($x38 (bvsle numSegments (_ bv1000000 32))))
 (let (($x94 (and (bvsge mergeFactor (_ bv2 32)) (bvsge numSegments (_ bv1 32)) $x38 (bvsge start (_ bv0 32)) (bvslt start numSegments) (bvsge upto start) (bvslt upto numSegments))))
 (and $x94 (bvsgt (bvadd ((_ sign_extend 32) start) ((_ sign_extend 32) mergeFactor)) (_ bv2147483647 64))))))
(assert
 (let (($x28 (and (bvsle (bvadd start mergeFactor) (bvadd upto (_ bv1 32))) (bvsge (bvsub (bvadd start mergeFactor) start) mergeFactor) (bvslt (bvadd start mergeFactor) (_ bv0 32)) (bvslt (bvadd start mergeFactor) (bvadd upto (_ bv1 32))))))
 (not $x28)))
(check-sat)
