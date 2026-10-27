; Fix lemma L3: the (float) narrowing is monotone and keeps 0 <= score <= weight when 0 <= y <= (double) weight.
; Expected: unsat.
(set-logic QF_FP)
(define-sort D () (_ FloatingPoint 11 53))
(define-sort F () (_ FloatingPoint 8 24))
(define-fun one () D ((_ to_fp 11 53) RNE 1.0))
(define-fun dzero () D (_ +zero 11 53))
(define-fun fzero () F (_ +zero 8 24))
(declare-const wf F) (declare-const y1 D) (declare-const y2 D)
(define-fun w () D ((_ to_fp 11 53) RNE wf))
(assert (fp.gt wf fzero)) (assert (fp.leq wf ((_ to_fp 8 24) RNE 64.0)))
(assert (fp.geq y1 dzero)) (assert (fp.leq y1 y2)) (assert (fp.leq y2 w))
(assert (or (fp.lt ((_ to_fp 8 24) RNE y2) ((_ to_fp 8 24) RNE y1)) (fp.gt ((_ to_fp 8 24) RNE y2) wf) (fp.lt ((_ to_fp 8 24) RNE y1) fzero)))
(check-sat)
