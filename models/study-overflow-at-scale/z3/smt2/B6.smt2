; BKDWriter.getNumLeftLeafNodes: 1 <= left < n, left >= right, left <= 2*right, all n in [2, 2^31-1]
; core/.../util/bkd/BKDWriter.java:864-881 getNumLeftLeafNodes
; benchmark generated from python API
(set-info :status unknown)
(declare-fun lg () (_ BitVec 32))
(declare-fun numLeaves () (_ BitVec 32))
(assert
 (let (($x743 (bvsle (bvshl (_ bv1 32) lg) numLeaves)))
 (let (($x24499 (bvsle lg (_ bv30 32))))
 (and (bvsge numLeaves (_ bv2 32)) (bvsge lg (_ bv0 32)) $x24499 $x743 (or (= lg (_ bv30 32)) (bvslt numLeaves (bvshl (_ bv1 32) (bvadd lg (_ bv1 32)))))))))
(assert
 (let ((?x1789 (bvshl (_ bv1 32) (bvand lg (_ bv31 32)))))
 (let ((?x988 (bvlshr ?x1789 (_ bv1 32))))
 (let ((?x1098 (bvsub numLeaves ?x1789)))
 (let ((?x20 (bvadd ?x988 (ite (bvslt ?x1098 ?x988) ?x1098 ?x988))))
 (let ((?x2078 ((_ sign_extend 32) ?x20)))
 (let (($x5351 (and (bvsge ?x20 (bvsub numLeaves ?x20)) (bvsle ?x2078 (bvmul (_ bv2 64) (bvsub ((_ sign_extend 32) numLeaves) ?x2078))) (bvsge ?x20 (_ bv1 32)) (bvslt ?x20 numLeaves))))
 (not $x5351))))))))
(check-sat)
