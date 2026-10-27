(set-logic QF_FPBV)
(declare-fun i2 () (_ BitVec 32))
(declare-fun i1 () (_ BitVec 32))
(assert (bvslt i1 i2))
(assert (not (fp.isNaN ((_ to_fp 8 24) i1))))
(assert (not (fp.isNaN ((_ to_fp 8 24) i2))))
(assert (fp.gt ((_ to_fp 8 24) i1) ((_ to_fp 8 24) i2)))

(check-sat)
