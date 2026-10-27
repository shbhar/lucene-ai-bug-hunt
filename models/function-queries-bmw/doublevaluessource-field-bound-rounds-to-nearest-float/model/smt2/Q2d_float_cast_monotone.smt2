(set-logic QF_FPBV)
(declare-fun x2 () (_ FloatingPoint 11 53))
(declare-fun x1 () (_ FloatingPoint 11 53))
(assert (fp.leq x1 x2))
(assert (fp.gt ((_ to_fp 8 24) roundNearestTiesToEven x1)
       ((_ to_fp 8 24) roundNearestTiesToEven x2)))

(check-sat)
