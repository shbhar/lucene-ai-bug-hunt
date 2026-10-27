(set-logic QF_FPBV)
(declare-fun raw () (_ BitVec 64))
(declare-fun b () (_ FloatingPoint 8 24))
(assert (bvsge raw #x0000000000000000))
(assert (bvsle raw #x0000010000000000))
(assert (and (not (fp.isNaN b)) (not (fp.isInfinite b))))
(assert (fp.gt b (_ +zero 8 24)))
(assert (fp.lt b (fp #b0 #x88 #b11110100000000000000000)))
(assert (let ((a!1 (ite (fp.geq ((_ to_fp 11 53) roundNearestTiesToEven raw)
                        (_ +zero 11 53))
                ((_ to_fp 8 24)
                  roundNearestTiesToEven
                  (fp.mul roundNearestTiesToEven
                          ((_ to_fp 11 53) roundNearestTiesToEven raw)
                          ((_ to_fp 11 53) roundNearestTiesToEven b)))
                (_ +zero 8 24)))
      (a!2 (fp.mul roundNearestTiesToEven
                   (fp.mul roundNearestTiesToEven
                           (fp #b0 #x7f #b00000000000000000000000)
                           ((_ to_fp 8 24)
                             roundNearestTiesToEven
                             ((_ to_fp 11 53) roundNearestTiesToEven raw)))
                   b)))
(let ((a!3 (ite (fp.isInfinite ((_ to_fp 8 24)
                                 roundNearestTiesToEven
                                 ((_ to_fp 11 53) roundNearestTiesToEven raw)))
                (_ +oo 8 24)
                a!2)))
  (fp.gt a!1 a!3))))

(check-sat)
