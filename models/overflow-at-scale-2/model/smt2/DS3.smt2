; benchmark generated from python API
(set-info :status unknown)
(declare-fun maxDoc () (_ BitVec 32))
(declare-fun docID () (_ BitVec 32))
(declare-fun k () (_ BitVec 32))
(declare-fun chunkStart () (_ BitVec 32))
(assert
 (let (($x16 (= chunkStart (bvmul (_ bv2048 32) k))))
 (let (($x12 (bvsle maxDoc (_ bv2147483519 32))))
 (and (bvsgt maxDoc (_ bv2147481600 32)) $x12 $x16 (bvsge docID (_ bv2147481600 32)) (bvslt docID maxDoc)))))
(assert
 (let (($x37 (not (bvslt docID chunkStart))))
 (not $x37)))
(check-sat)
