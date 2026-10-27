; Fix lemma L2: s = w / X (binary64 RNE, w = (double) weight, weight in (0, 64]) is non-increasing in X,
; never NaN and 0 <= s <= w, for 1 <= X1 <= X2 (X2 may be +Inf). Expected: unsat.
(set-logic QF_FP)
(define-sort D () (_ FloatingPoint 11 53))
(define-sort F () (_ FloatingPoint 8 24))
(define-fun one () D ((_ to_fp 11 53) RNE 1.0))
(define-fun dzero () D (_ +zero 11 53))
(define-fun fzero () F (_ +zero 8 24))
(declare-const wf F) (declare-const X1 D) (declare-const X2 D)
(define-fun w () D ((_ to_fp 11 53) RNE wf))
(assert (fp.gt wf fzero)) (assert (fp.leq wf ((_ to_fp 8 24) RNE 64.0)))
(assert (fp.geq X1 one)) (assert (not (fp.isNaN X2))) (assert (fp.leq X1 X2))
(assert (or (fp.lt (fp.div RNE w X1) (fp.div RNE w X2)) (fp.isNaN (fp.div RNE w X2)) (fp.gt (fp.div RNE w X1) w) (fp.lt (fp.div RNE w X2) dzero)))
(check-sat)
