; SparseFixedBitSet.blockCount(length) == ceil(length/4096) for all int length>=1
; core/.../util/SparseFixedBitSet.java:45-51
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(assert
 (bvsge length (_ bv1 32)))
(assert
 (let ((?x28 (bvlshr length (_ bv12 32))))
 (let ((?x17 (ite (bvslt (bvshl ?x28 (_ bv12 32)) length) (bvadd ?x28 (_ bv1 32)) ?x28)))
 (let (($x18 (= ((_ zero_extend 32) ?x17) (bvsdiv (bvadd ((_ zero_extend 32) length) (_ bv4095 64)) (_ bv4096 64)))))
 (not $x18)))))
(check-sat)
