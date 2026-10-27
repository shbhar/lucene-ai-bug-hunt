; RoaringDocIdSet.Builder(maxDoc): (maxDoc+65535)>>>16 == ceil(maxDoc/65536) for all maxDoc>=0
; core/.../util/RoaringDocIdSet.java:63
; benchmark generated from python API
(set-info :status unknown)
(declare-fun m () (_ BitVec 32))
(assert
 (bvsge m (_ bv0 32)))
(assert
 (let (($x1789 (= ((_ zero_extend 32) (bvlshr (bvadd m (_ bv65535 32)) (_ bv16 32))) (bvsdiv (bvadd ((_ zero_extend 32) m) (_ bv65535 64)) (_ bv65536 64)))))
 (not $x1789)))
(check-sat)
