; OffHeap*VectorValues: (long)ord*byteSize exact for any int ord>=0, byteSize>=0
; core/.../codecs/lucene95/OffHeapFloatVectorValues.java:82,95; lucene104/OffHeapScalarQuantized*:112-161
; benchmark generated from python API
(set-info :status unknown)
(declare-fun byteSize () (_ BitVec 32))
(declare-fun ord () (_ BitVec 32))
(assert
 (and (bvsge ord (_ bv0 32)) (bvsge byteSize (_ bv0 32))))
(assert
 (let (($x32 (= ((_ sign_extend 64) (bvmul ((_ sign_extend 32) ord) ((_ sign_extend 32) byteSize))) (bvmul ((_ sign_extend 96) ord) ((_ sign_extend 96) byteSize)))))
 (not $x32)))
(check-sat)
