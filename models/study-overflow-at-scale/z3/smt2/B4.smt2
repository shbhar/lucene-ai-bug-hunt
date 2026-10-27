; BKDReader.size(): leftMostLeafNode*2 stays positive while < leafNodeOffset
; core/.../util/bkd/BKDWriter.java:904-911 checkMaxLeafNodeCount vs BKDReader.java:430 (nodeID *= 2), :463 (2*nodeID+1), :525-533 size()
; benchmark generated from python API
(set-info :status unknown)
(declare-fun numLeaves () (_ BitVec 32))
(declare-fun x () (_ BitVec 32))
(declare-fun bytesPerDim () (_ BitVec 32))
(assert
 (let (($x47 (bvsle (bvmul ((_ sign_extend 32) bytesPerDim) ((_ sign_extend 32) numLeaves)) (_ bv2147483631 64))))
 (let (($x50 (bvsle bytesPerDim (_ bv16 32))))
 (let (($x15637 (and (bvsge bytesPerDim (_ bv1 32)) $x50 (bvsge numLeaves (_ bv1 32)) $x47)))
 (and $x15637 (bvsge x (_ bv1 32)) (bvslt x numLeaves))))))
(assert
 (let (($x4266 (bvsgt (bvmul x (_ bv2 32)) (_ bv0 32))))
 (not $x4266)))
(check-sat)
