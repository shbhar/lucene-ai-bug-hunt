(set-option :produce-models true)
(set-logic QF_BVFP)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun mcsbits () (_ BitVec 32))
(declare-fun mrs () (_ FloatingPoint 11 53))
(assert
 (bvult mcsbits (_ bv2139095039 32)))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (fp.geq ?x10 (fp #b0 #x7f #b00000000000000000000000))))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (fp.lt ?x10 (fp #b0 #x80 #b00000000000000000000000))))
(assert
 (let (($x22 (not (fp.isInfinite mrs))))
 (let (($x20 (not (fp.isNaN mrs))))
 (and $x20 $x22))))
(assert
 (fp.lt mrs (fp #b1 #b10000011111 #x0000000000000)))
(assert
 (fp.geq mrs (fp #b1 #b10000100000 #x0000000000000)))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (let ((?x11 ((_ to_fp 11 53) roundNearestTiesToEven ?x10)))
 (let ((?x12 (fp.sub roundNearestTiesToEven ?x11 mrs)))
 (let ((?x94 (fp.mul roundNearestTiesToEven (fp #b0 #b10000000000 #x0000000000000) (fp.mul roundNearestTiesToEven (fp #b0 #b10000001000 #xff80000000000) (fp #b0 #b01111001011 #x0000000000000)))))
 (let ((?x97 (fp.mul roundNearestTiesToEven (fp.add roundNearestTiesToEven (fp #b0 #b01111111111 #x0000000000000) ?x94) (fp.add roundNearestTiesToEven ?x12 mrs))))
 (and (fp.gt ?x12 (_ +zero 11 53)) (fp.geq ((_ to_fp 8 24) roundNearestTiesToEven ?x97) ?x10))))))))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (let ((?x11 ((_ to_fp 11 53) roundNearestTiesToEven ?x10)))
 (let ((?x12 (fp.sub roundNearestTiesToEven ?x11 mrs)))
 (let ((?x17 ((_ to_fp 11 53) roundNearestTiesToEven (fp.sub roundNearestTiesToEven ((_ to_fp 8 24) (bvadd mcsbits (_ bv1 32))) ?x10))))
 (fp.eq (fp.sub roundNearestTiesToEven ?x12 ?x17) ?x12))))))
(check-sat)
(get-value (mcsbits mrs))
