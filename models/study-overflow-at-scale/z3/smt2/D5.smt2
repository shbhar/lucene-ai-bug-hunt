; FilteredHnswGraphSearcher: maxConn*maxConn*2 exact for maxConn<=2*512
; core/.../util/hnsw/FilteredHnswGraphSearcher.java:129-130
; benchmark generated from python API
(set-info :status unknown)
(declare-fun M () (_ BitVec 32))
(assert
 (let (($x12080 (bvsle M (_ bv1024 32))))
 (and (bvsge M (_ bv1 32)) $x12080)))
(assert
 (let (($x16199 (= ((_ sign_extend 32) (bvmul (bvmul M M) (_ bv2 32))) (bvmul (bvmul ((_ sign_extend 32) M) ((_ sign_extend 32) M)) (_ bv2 64)))))
 (not $x16199)))
(check-sat)
