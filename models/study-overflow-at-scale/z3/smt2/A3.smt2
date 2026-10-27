; SparseFixedBitSet.firstClearInBlock: blockEnd=min(blockBase+4096,length) is exact, length<=MAX_DOCS
; core/.../util/SparseFixedBitSet.java:434-479 (firstClearInBlock, #16072, 2026-05-18)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun i4096 () (_ BitVec 32))
(assert
 (let ((?x28 (bvlshr length (_ bv12 32))))
 (let ((?x17 (ite (bvslt (bvshl ?x28 (_ bv12 32)) length) (bvadd ?x28 (_ bv1 32)) ?x28)))
 (let (($x32 (bvsle length (_ bv2147483519 32))))
 (and (bvsge length (_ bv1 32)) $x32 (bvsge i4096 (_ bv0 32)) (bvslt i4096 ?x17))))))
(assert
 (let ((?x10 (bvshl i4096 (_ bv12 32))))
 (let ((?x12 (bvadd ?x10 (_ bv4096 32))))
 (let ((?x14 (ite (bvslt ?x12 length) ?x12 length)))
 (let ((?x23 ((_ sign_extend 32) length)))
 (let ((?x22 (bvadd ((_ sign_extend 32) ?x10) (_ bv4096 64))))
 (let ((?x25 (ite (bvslt ?x22 ?x23) ?x22 ?x23)))
 (let (($x490 (and (= ((_ sign_extend 32) ?x14) ?x25) (bvsgt ?x14 ?x10))))
 (not $x490)))))))))
(check-sat)
