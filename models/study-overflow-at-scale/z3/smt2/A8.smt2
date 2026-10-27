; readSparseFixedBitSet: baseDocId=wordIndex<<6 and baseDocId+64 exact for maxDoc<=MAX_DOCS
; core/.../codecs/lucene90/Lucene90LiveDocsFormat.java:149-151 (#15413, 2025-11-25)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun wordIndex () (_ BitVec 32))
(assert
 (let (($x1085 (bvsle length (_ bv2147483519 32))))
 (and (bvsge length (_ bv1 32)) $x1085 (bvsge wordIndex (_ bv0 32)) (bvslt wordIndex (bvadd (bvlshr (bvsub length (_ bv1 32)) (_ bv6 32)) (_ bv1 32))))))
(assert
 (let (($x87 (= ((_ sign_extend 32) (bvadd (bvshl wordIndex (_ bv6 32)) (_ bv64 32))) (bvadd ((_ sign_extend 32) (bvshl wordIndex (_ bv6 32))) (_ bv64 64)))))
 (let ((?x19 (bvshl wordIndex (_ bv6 32))))
 (let ((?x1074 ((_ sign_extend 32) ?x19)))
 (let (($x14 (and (= ?x1074 (bvmul ((_ sign_extend 32) wordIndex) (_ bv64 64))) $x87)))
 (not $x14))))))
(check-sat)
