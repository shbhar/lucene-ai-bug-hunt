; Fix lemma L1 (sigm_fixed_formula split): X = 1 + Q (binary64 RNE) is non-decreasing in Q, never NaN, X >= 1,
; for 0 <= Q1 <= Q2 (Q2 may be +Inf; Q = Math.pow(pivot / f, a) >= 0). Expected: unsat.
(set-logic QF_FP)
(define-sort D () (_ FloatingPoint 11 53))
(define-sort F () (_ FloatingPoint 8 24))
(define-fun one () D ((_ to_fp 11 53) RNE 1.0))
(define-fun dzero () D (_ +zero 11 53))
(define-fun fzero () F (_ +zero 8 24))
(declare-const Q1 D) (declare-const Q2 D)
(assert (fp.geq Q1 dzero)) (assert (not (fp.isNaN Q2))) (assert (fp.leq Q1 Q2))
(assert (or (fp.lt (fp.add RNE one Q2) (fp.add RNE one Q1)) (fp.isNaN (fp.add RNE one Q2)) (fp.lt (fp.add RNE one Q1) one)))
(check-sat)
