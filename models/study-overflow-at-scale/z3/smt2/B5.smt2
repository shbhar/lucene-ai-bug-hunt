; OneDimensionBKDWriter: new byte[maxPointsInLeafNode*packedBytesLength] exact under BKDConfig limits
; core/.../util/bkd/BKDWriter.java:687 (OneDimensionBKDWriter leafValues; only used when numDims == 1, BKDWriter.java:448)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun bytesPerDim () (_ BitVec 32))
(declare-fun numDims () (_ BitVec 32))
(declare-fun maxPointsInLeafNode () (_ BitVec 32))
(assert
 (let (($x50 (bvsle bytesPerDim (_ bv16 32))))
 (let (($x988 (= numDims (_ bv1 32))))
 (let (($x1789 (bvsle maxPointsInLeafNode (_ bv2147483631 32))))
 (and (bvsge maxPointsInLeafNode (_ bv1 32)) $x1789 $x988 (bvsge bytesPerDim (_ bv1 32)) $x50)))))
(assert
 (let ((?x30 (bvmul ((_ sign_extend 32) maxPointsInLeafNode) ((_ sign_extend 32) (bvmul numDims bytesPerDim)))))
 (let ((?x985 ((_ sign_extend 32) (bvmul maxPointsInLeafNode (bvmul numDims bytesPerDim)))))
 (let (($x5 (= ?x985 ?x30)))
 (not $x5)))))
(check-sat)
