; SigmoidFunction.doScore (FeatureField.java:527-532):
;   (float) (weight * (1 - pivotPa / (Math.pow(f, a) + pivotPa)))   in binary64, then narrowed to binary32
; pow is outside SMT-LIB FP, so P = Math.pow(f, a) and K = pivotPa = Math.pow(pivot, a) (FeatureField.java:500)
; are free binary64 values in [0, +Inf] (the range of pow on a positive base). w = (double) weight, weight in (0, 64].
; Query: can the score be NaN?  Expected: sat; the model shows which (K, P) give NaN.
(set-logic QF_FP)
(define-sort D () (_ FloatingPoint 11 53))
(define-sort F () (_ FloatingPoint 8 24))
(declare-const K D)
(declare-const P D)
(declare-const wf F)
(define-fun w () D ((_ to_fp 11 53) RNE wf))
(define-fun one () D ((_ to_fp 11 53) RNE 1.0))
(define-fun dzero () D (_ +zero 11 53))
(define-fun score () F ((_ to_fp 8 24) RNE (fp.mul RNE w (fp.sub RNE one (fp.div RNE K (fp.add RNE P K))))))
(assert (fp.geq K dzero)) (assert (not (fp.isNaN K)))
(assert (fp.geq P dzero)) (assert (not (fp.isNaN P)))
(assert (fp.gt wf (_ +zero 8 24))) (assert (fp.leq wf ((_ to_fp 8 24) RNE 64.0)))
(assert (fp.isNaN score))
(check-sat)
(get-value (K P wf))
(exit)
