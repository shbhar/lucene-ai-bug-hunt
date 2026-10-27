; OffHeapScalarQuantized*VectorValues: byteSize=docPackedLength+16 exact (docPackedLength<=dims<=1024)
; core/.../codecs/lucene104/OffHeapScalarQuantizedVectorValues.java:108
; benchmark generated from python API
(set-info :status unknown)
(declare-fun docPackedLength () (_ BitVec 32))
(assert
 (let (($x92 (bvsle docPackedLength (_ bv1024 32))))
 (and (bvsge docPackedLength (_ bv0 32)) $x92)))
(assert
 (let (($x19689 (= ((_ sign_extend 32) (bvadd docPackedLength (_ bv16 32))) (bvadd ((_ sign_extend 32) docPackedLength) (_ bv16 64)))))
 (not $x19689)))
(check-sat)
