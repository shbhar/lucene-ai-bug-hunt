(set-option :produce-models true)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun x0 () (_ FloatingPoint 8 24))
(declare-fun m0 () (_ FloatingPoint 8 24))
(declare-fun x1 () (_ FloatingPoint 8 24))
(declare-fun m1 () (_ FloatingPoint 8 24))
(declare-fun x2 () (_ FloatingPoint 8 24))
(declare-fun m2 () (_ FloatingPoint 8 24))
(assert
 (let (($x17 (not (fp.isNegative x0))))
 (let (($x15 (not (fp.isInfinite x0))))
 (let (($x13 (not (fp.isNaN x0))))
 (and $x13 $x15 $x17)))))
(assert
 (let (($x24 (not (fp.isNegative m0))))
 (let (($x22 (not (fp.isInfinite m0))))
 (let (($x20 (not (fp.isNaN m0))))
 (and $x20 $x22 $x24)))))
(assert
 (fp.leq x0 m0))
(assert
 (let (($x50 (not (fp.isNegative x1))))
 (let (($x48 (not (fp.isInfinite x1))))
 (let (($x46 (not (fp.isNaN x1))))
 (and $x46 $x48 $x50)))))
(assert
 (let (($x57 (not (fp.isNegative m1))))
 (let (($x55 (not (fp.isInfinite m1))))
 (let (($x53 (not (fp.isNaN m1))))
 (and $x53 $x55 $x57)))))
(assert
 (fp.leq x1 m1))
(assert
 (let (($x65 (not (fp.isNegative x2))))
 (let (($x63 (not (fp.isInfinite x2))))
 (let (($x61 (not (fp.isNaN x2))))
 (and $x61 $x63 $x65)))))
(assert
 (let (($x72 (not (fp.isNegative m2))))
 (let (($x70 (not (fp.isInfinite m2))))
 (let (($x68 (not (fp.isNaN m2))))
 (and $x68 $x70 $x72)))))
(assert
 (fp.leq x2 m2))
(assert
 (let ((?x84 (fp.add roundNearestTiesToEven (_ +zero 11 53) ((_ to_fp 11 53) roundNearestTiesToEven m0))))
 (let ((?x86 (fp.add roundNearestTiesToEven ?x84 ((_ to_fp 11 53) roundNearestTiesToEven m1))))
 (let ((?x88 (fp.add roundNearestTiesToEven ?x86 ((_ to_fp 11 53) roundNearestTiesToEven m2))))
 (let ((?x89 ((_ to_fp 8 24) roundNearestTiesToEven ?x88)))
 (let ((?x77 (fp.add roundNearestTiesToEven (_ +zero 11 53) ((_ to_fp 11 53) roundNearestTiesToEven x0))))
 (let ((?x79 (fp.add roundNearestTiesToEven ?x77 ((_ to_fp 11 53) roundNearestTiesToEven x1))))
 (let ((?x81 (fp.add roundNearestTiesToEven ?x79 ((_ to_fp 11 53) roundNearestTiesToEven x2))))
 (let ((?x82 ((_ to_fp 8 24) roundNearestTiesToEven ?x81)))
 (fp.gt ?x82 ?x89))))))))))
(check-sat)
