(set-logic QF_BVFP)
(declare-fun bv () (_ BitVec 32))
(assert (not (fp.isNaN ((_ to_fp 8 24) bv))))
(assert (not (and (fp.isInfinite ((_ to_fp 8 24) bv))
          (fp.isPositive ((_ to_fp 8 24) bv)))))
(assert (not (and (fp.isZero ((_ to_fp 8 24) bv)) (fp.isNegative ((_ to_fp 8 24) bv)))))
(assert (fp.geq ((_ to_fp 8 24) bv) (_ +zero 8 24)))
(assert (let ((a!1 ((_ to_fp 8 24)
             (ite (= #b1 ((_ extract 31 31) bv))
                  (bvsub bv #x00000001)
                  (bvadd bv #x00000001)))))
(let ((a!2 (fp.geq (fp.div roundNearestTiesToEven
                           (fp #b0 #x7f #b00000000000000000000000)
                           (fp.add roundNearestTiesToEven
                                   (fp #b0 #x7f #b00000000000000000000000)
                                   ((_ to_fp 8 24) bv)))
                   (fp.div roundNearestTiesToEven
                           (fp #b0 #x7f #b00000000000000000000000)
                           (fp.add roundNearestTiesToEven
                                   (fp #b0 #x7f #b00000000000000000000000)
                                   a!1)))))
  (not a!2))))
(check-sat)
