; benchmark generated from python API
(set-info :status unknown)
(declare-fun k () (_ BitVec 32))
(declare-fun chunkStart () (_ BitVec 32))
(declare-fun maxDoc () (_ BitVec 32))
(assert
 (let (($x16 (= chunkStart (bvmul (_ bv2048 32) k))))
 (let (($x12 (bvsle maxDoc (_ bv2147483519 32))))
 (and (bvsgt maxDoc (_ bv2147481600 32)) $x12 $x16))))
(assert
 (let (($x22 (bvslt chunkStart maxDoc)))
 (not $x22)))
(check-sat)
