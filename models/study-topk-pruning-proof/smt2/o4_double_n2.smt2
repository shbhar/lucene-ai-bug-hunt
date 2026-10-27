(set-option :produce-models true)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun x0 () (_ FloatingPoint 8 24))
(declare-fun m0 () (_ FloatingPoint 8 24))
(declare-fun x1 () (_ FloatingPoint 8 24))
(declare-fun m1 () (_ FloatingPoint 8 24))
(assert
 (let (($x15 (not (fp.isNegative x0))))
 (let (($x13 (not (fp.isInfinite x0))))
 (let (($x11 (not (fp.isNaN x0))))
 (and $x11 $x13 $x15)))))
(assert
 (let (($x22 (not (fp.isNegative m0))))
 (let (($x20 (not (fp.isInfinite m0))))
 (let (($x18 (not (fp.isNaN m0))))
 (and $x18 $x20 $x22)))))
(assert
 (fp.leq x0 m0))
(assert
 (let (($x48 (not (fp.isNegative x1))))
 (let (($x46 (not (fp.isInfinite x1))))
 (let (($x44 (not (fp.isNaN x1))))
 (and $x44 $x46 $x48)))))
(assert
 (let (($x55 (not (fp.isNegative m1))))
 (let (($x53 (not (fp.isInfinite m1))))
 (let (($x51 (not (fp.isNaN m1))))
 (and $x51 $x53 $x55)))))
(assert
 (fp.leq x1 m1))
(assert
 (let ((?x65 (fp.add roundNearestTiesToEven (_ +zero 11 53) ((_ to_fp 11 53) roundNearestTiesToEven m0))))
 (let ((?x67 (fp.add roundNearestTiesToEven ?x65 ((_ to_fp 11 53) roundNearestTiesToEven m1))))
 (let ((?x68 ((_ to_fp 8 24) roundNearestTiesToEven ?x67)))
 (let ((?x60 (fp.add roundNearestTiesToEven (_ +zero 11 53) ((_ to_fp 11 53) roundNearestTiesToEven x0))))
 (let ((?x62 (fp.add roundNearestTiesToEven ?x60 ((_ to_fp 11 53) roundNearestTiesToEven x1))))
 (let ((?x63 ((_ to_fp 8 24) roundNearestTiesToEven ?x62)))
 (fp.gt ?x63 ?x68))))))))
(check-sat)
