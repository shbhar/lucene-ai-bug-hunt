(set-logic QF_BVFP)
(declare-fun bv () (_ BitVec 32))
(assert (not (fp.isNaN ((_ to_fp 8 24) bv))))
(assert (not (and (fp.isInfinite ((_ to_fp 8 24) bv))
          (fp.isPositive ((_ to_fp 8 24) bv)))))
(assert (not (and (fp.isZero ((_ to_fp 8 24) bv)) (fp.isNegative ((_ to_fp 8 24) bv)))))
(assert (let ((a!1 (fp.div roundNearestTiesToEven
                   (fp #b0 #x7f #b00000000000000000000000)
                   (fp.add roundNearestTiesToEven
                           (fp #b0 #x7f #b00000000000000000000000)
                           (fp.mul roundNearestTiesToEven
                                   (fp #b1 #x7f #b00000000000000000000000)
                                   ((_ to_fp 8 24) bv)))))
      (a!2 ((_ to_fp 8 24)
             (ite (= #b1 ((_ extract 31 31) bv))
                  (bvsub bv #x00000001)
                  (bvadd bv #x00000001)))))
(let ((a!3 (ite (fp.lt a!2 (_ +zero 8 24))
                (fp.div roundNearestTiesToEven
                        (fp #b0 #x7f #b00000000000000000000000)
                        (fp.add roundNearestTiesToEven
                                (fp #b0 #x7f #b00000000000000000000000)
                                (fp.mul roundNearestTiesToEven
                                        (fp #b1 #x7f #b00000000000000000000000)
                                        a!2)))
                (fp.add roundNearestTiesToEven
                        a!2
                        (fp #b0 #x7f #b00000000000000000000000)))))
(let ((a!4 (fp.leq (ite (fp.lt ((_ to_fp 8 24) bv) (_ +zero 8 24))
                        a!1
                        (fp.add roundNearestTiesToEven
                                ((_ to_fp 8 24) bv)
                                (fp #b0 #x7f #b00000000000000000000000)))
                   a!3)))
  (not a!4)))))
(check-sat)
