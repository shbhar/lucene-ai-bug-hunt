;   ... would overflow if docRange could exceed 2^31-64
; core/.../codecs/lucene104/Lucene104PostingsWriter.java:436-441 (#15174, 2025-09-17)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun docRange () (_ BitVec 32))
(assert
 (let (($x9 (bvsge docRange (_ bv256 32))))
 (and $x9)))
(assert
 (let ((?x23 (bvmul ((_ sign_extend 32) (bvadd (bvlshr (bvsub docRange (_ bv1 32)) (_ bv6 32)) (_ bv1 32))) (_ bv64 64))))
 (let ((?x1104 ((_ sign_extend 32) (bvmul (bvadd (bvlshr (bvsub docRange (_ bv1 32)) (_ bv6 32)) (_ bv1 32)) (_ bv64 32)))))
 (let (($x985 (= ?x1104 ?x23)))
 (not $x985)))))
(check-sat)
