; SparseFixedBitSet.firstClearInBlock: wStart+64 does not overflow for length<=MAX_DOCS
; core/.../util/SparseFixedBitSet.java:434-479 (firstClearInBlock, #16072, 2026-05-18)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun pos () (_ BitVec 32))
(assert
 (let (($x32 (bvsle length (_ bv2147483519 32))))
 (let (($x19 (bvsge length (_ bv1 32))))
 (and $x19 $x32 (bvsge pos (_ bv0 32)) (bvslt pos length)))))
(assert
 (let (($x48 (= ((_ sign_extend 32) (bvadd (bvshl (bvlshr pos (_ bv6 32)) (_ bv6 32)) (_ bv64 32))) (bvadd ((_ sign_extend 32) (bvshl (bvlshr pos (_ bv6 32)) (_ bv6 32))) (_ bv64 64)))))
 (not $x48)))
(check-sat)
