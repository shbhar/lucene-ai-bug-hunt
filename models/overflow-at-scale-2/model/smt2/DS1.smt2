; benchmark generated from python API
(set-info :status unknown)
(declare-fun maxDoc () (_ BitVec 32))
(declare-fun chunkStart () (_ BitVec 32))
(declare-fun k () (_ BitVec 32))
(assert
 (let (($x16 (= chunkStart (bvmul (_ bv2048 32) k))))
 (let (($x12 (bvsle maxDoc (_ bv2147483519 32))))
 (let (($x13 (and (bvsge maxDoc (_ bv1 32)) $x12)))
 (and $x13 $x16 (bvsge chunkStart (_ bv1 32)) (bvslt chunkStart maxDoc))))))
(assert
 (let (($x25 (= ((_ sign_extend 32) (bvadd chunkStart (_ bv2048 32))) (bvadd ((_ sign_extend 32) chunkStart) (_ bv2048 64)))))
 (not $x25)))
(check-sat)
