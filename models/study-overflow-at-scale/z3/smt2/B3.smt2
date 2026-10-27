; BKDReader inner node ids: 2*nodeID+1 fits in int for every tree checkMaxLeafNodeCount accepts
; core/.../util/bkd/BKDWriter.java:904-911 checkMaxLeafNodeCount vs BKDReader.java:430 (nodeID *= 2), :463 (2*nodeID+1), :525-533 size()
; benchmark generated from python API
(set-info :status unknown)
(declare-fun numLeaves () (_ BitVec 32))
(declare-fun nodeID () (_ BitVec 32))
(declare-fun bytesPerDim () (_ BitVec 32))
(assert
 (let (($x47 (bvsle (bvmul ((_ sign_extend 32) bytesPerDim) ((_ sign_extend 32) numLeaves)) (_ bv2147483631 64))))
 (let (($x50 (bvsle bytesPerDim (_ bv16 32))))
 (let (($x15637 (and (bvsge bytesPerDim (_ bv1 32)) $x50 (bvsge numLeaves (_ bv1 32)) $x47)))
 (and $x15637 (bvsge nodeID (_ bv1 32)) (bvslt nodeID numLeaves))))))
(assert
 (let (($x30 (= ((_ sign_extend 32) (bvadd (bvmul nodeID (_ bv2 32)) (_ bv1 32))) (bvadd (bvmul ((_ sign_extend 32) nodeID) (_ bv2 64)) (_ bv1 64)))))
 (let ((?x1125 (bvmul ((_ sign_extend 32) nodeID) (_ bv2 64))))
 (let (($x5 (and (= ((_ sign_extend 32) (bvmul nodeID (_ bv2 32))) ?x1125) $x30)))
 (not $x5)))))
(check-sat)
