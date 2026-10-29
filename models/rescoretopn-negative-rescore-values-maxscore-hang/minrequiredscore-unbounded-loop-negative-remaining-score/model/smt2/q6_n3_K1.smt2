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
 (fp.lt mrs (_ +zero 11 53)))
(assert
 (fp.geq mrs (fp #b1 #b10000011011 #x0000000000000)))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (let ((?x11 ((_ to_fp 11 53) roundNearestTiesToEven ?x10)))
 (let ((?x12 (fp.sub roundNearestTiesToEven ?x11 mrs)))
 (let ((?x45 (fp.mul roundNearestTiesToEven (fp #b0 #b10000000000 #x0000000000000) (fp.mul roundNearestTiesToEven (fp #b0 #b10000000000 #x0000000000000) (fp #b0 #b01111001011 #x0000000000000)))))
 (let ((?x61 (fp.add roundNearestTiesToEven (fp #b0 #b01111111111 #x0000000000000) ?x45)))
 (let ((?x39 (fp.mul roundNearestTiesToEven ?x61 (fp.add roundNearestTiesToEven ?x12 mrs))))
 (and (fp.gt ?x12 (_ +zero 11 53)) (fp.geq ((_ to_fp 8 24) roundNearestTiesToEven ?x39) ?x10)))))))))
(assert
 (let ((?x10 ((_ to_fp 8 24) mcsbits)))
 (let ((?x17 ((_ to_fp 11 53) roundNearestTiesToEven (fp.sub roundNearestTiesToEven ((_ to_fp 8 24) (bvadd mcsbits (_ bv1 32))) ?x10))))
 (let ((?x11 ((_ to_fp 11 53) roundNearestTiesToEven ?x10)))
 (let ((?x12 (fp.sub roundNearestTiesToEven ?x11 mrs)))
 (let ((?x18 (fp.sub roundNearestTiesToEven ?x12 ?x17)))
 (let ((?x45 (fp.mul roundNearestTiesToEven (fp #b0 #b10000000000 #x0000000000000) (fp.mul roundNearestTiesToEven (fp #b0 #b10000000000 #x0000000000000) (fp #b0 #b01111001011 #x0000000000000)))))
 (let ((?x61 (fp.add roundNearestTiesToEven (fp #b0 #b01111111111 #x0000000000000) ?x45)))
 (let ((?x57 (fp.mul roundNearestTiesToEven ?x61 (fp.add roundNearestTiesToEven ?x18 mrs))))
 (and (fp.gt ?x18 (_ +zero 11 53)) (fp.geq ((_ to_fp 8 24) roundNearestTiesToEven ?x57) ?x10)))))))))))
(check-sat)
(get-value (mcsbits mrs))
