; DenseBinaryDocValues(fixed length).binaryValues: new byte[size*length] is exact
; core/.../codecs/lucene90/Lucene90DocValuesProducer.java:1347 & :1389-1391 (#16286, 2026-06-30)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun size () (_ BitVec 32))
(assert
 (let (($x1364 (bvsle length (_ bv2147483631 32))))
 (let (($x16 (bvsle size (_ bv2147483631 32))))
 (and (bvsge size (_ bv1 32)) $x16 (bvsge length (_ bv0 32)) $x1364))))
(assert
 (let (($x1085 (= ((_ sign_extend 32) (bvmul size length)) (bvmul ((_ sign_extend 32) size) ((_ sign_extend 32) length)))))
 (not $x1085)))
(check-sat)
