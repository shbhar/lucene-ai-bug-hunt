; benchmark generated from python API
(set-info :status unknown)
(declare-fun v1 () (_ BitVec 64))
(declare-fun v0 () (_ BitVec 64))
(assert
 (and (and (bvsge v0 (_ bv0 64)) (bvsle v0 (_ bv2147483519 64))) (and (bvsge v1 (_ bv0 64)) (bvsle v1 (_ bv2147483519 64)))))
(assert
 (let ((?x133 (bvadd (bvadd (_ bv0 64) v0) v1)))
 (let ((?x416 ((_ extract 31 0) (bvadd ((_ sign_extend 32) ((_ extract 31 0) (bvadd ((_ sign_extend 32) (_ bv0 32)) v0))) v1))))
 (let (($x33 (= ((_ sign_extend 32) ?x416) ?x133)))
 (not $x33)))))
(check-sat)
