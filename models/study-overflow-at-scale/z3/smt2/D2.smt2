; OffHeap{Float,Float16}VectorValues.load: byteSize=dimension*{4,2} exact for dims<=1024
; core/.../codecs/lucene95/OffHeapFloatVectorValues.java:112, OffHeapFloat16VectorValues.java:118
; benchmark generated from python API
(set-info :status unknown)
(declare-fun dims () (_ BitVec 32))
(assert
 (let (($x20944 (bvsle dims (_ bv1024 32))))
 (and (bvsge dims (_ bv1 32)) $x20944)))
(assert
 (let (($x12041 (and (= ((_ sign_extend 32) (bvmul dims (_ bv4 32))) (bvmul ((_ sign_extend 32) dims) (_ bv4 64))) (bvsgt (bvmul dims (_ bv4 32)) (_ bv0 32)))))
 (not $x12041)))
(check-sat)
