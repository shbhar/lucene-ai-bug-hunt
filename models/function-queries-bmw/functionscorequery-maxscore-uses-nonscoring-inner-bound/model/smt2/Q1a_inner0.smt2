(set-logic QF_FPBV)
(declare-fun v () (_ FloatingPoint 11 53))
(declare-fun V () (_ FloatingPoint 11 53))
(declare-fun b () (_ FloatingPoint 8 24))
(assert (and (not (fp.isNaN v)) (not (fp.isInfinite v))))
(assert (and (not (fp.isNaN V)) (not (fp.isInfinite V))))
(assert (fp.leq v V))
(assert (fp.geq v (_ +zero 11 53)))
(assert (and (not (fp.isNaN b)) (not (fp.isInfinite b))))
(assert (fp.gt b (_ +zero 8 24)))
(assert (let ((a!1 (ite (fp.geq v (_ +zero 11 53))
                ((_ to_fp 8 24)
                  roundNearestTiesToEven
                  (fp.mul roundNearestTiesToEven
                          v
                          ((_ to_fp 11 53) roundNearestTiesToEven b)))
                (_ +zero 8 24)))
      (a!2 (ite (fp.isInfinite ((_ to_fp 8 24) roundNearestTiesToEven V))
                (_ +oo 8 24)
                (fp.mul roundNearestTiesToEven
                        (fp.mul roundNearestTiesToEven
                                (_ +zero 8 24)
                                ((_ to_fp 8 24) roundNearestTiesToEven V))
                        b))))
  (fp.gt a!1 a!2)))

(check-sat)
