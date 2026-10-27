;   ... (the expression itself: 2*hashSize overflows at hashSize=2^30)
; core/.../util/BytesRefHash.java:338-340, :412-414 rehash(2 * hashSize)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun hashSize () (_ BitVec 32))
(assert
 (let (($x21548 (bvsle hashSize (_ bv1073741824 32))))
 (and (bvsge hashSize (_ bv16 32)) $x21548 (= (bvand hashSize (bvsub hashSize (_ bv1 32))) (_ bv0 32)))))
(assert
 (let (($x7280 (= ((_ sign_extend 32) (bvmul (_ bv2 32) hashSize)) (bvmul (_ bv2 64) ((_ sign_extend 32) hashSize)))))
 (not $x7280)))
(check-sat)
