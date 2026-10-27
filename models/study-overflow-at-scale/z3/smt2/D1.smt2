; Lucene99HnswVectorsReader.OffHeapHnswGraph: new int[M*2] exact (M<=512)
; core/.../codecs/lucene99/Lucene99HnswVectorsReader.java:578
; benchmark generated from python API
(set-info :status unknown)
(declare-fun M () (_ BitVec 32))
(assert
 (let (($x15757 (bvsle M (_ bv512 32))))
 (and (bvsge M (_ bv1 32)) $x15757)))
(assert
 (let (($x17821 (= ((_ sign_extend 32) (bvmul M (_ bv2 32))) (bvmul ((_ sign_extend 32) M) (_ bv2 64)))))
 (not $x17821)))
(check-sat)
