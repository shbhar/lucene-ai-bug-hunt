; Candidate fix: (float) (weight / (1 + Math.pow(pivot / f, a)))  i.e. w / (1 + (k/f)^a), no k^a term.
; Q = Math.pow(pivot / f, a) is a free binary64 in [0, +Inf] (pivot / f is in (0, +Inf] in double).
; Obligation: never NaN, in [0, w], and non-increasing in Q (Q is non-increasing in f). Expected: unsat.
(set-logic QF_FP)
(define-sort D () (_ FloatingPoint 11 53))
(define-sort F () (_ FloatingPoint 8 24))
(declare-const Q1 D)
(declare-const Q2 D)
(declare-const wf F)
(define-fun w () D ((_ to_fp 11 53) RNE wf))
(define-fun one () D ((_ to_fp 11 53) RNE 1.0))
(define-fun dzero () D (_ +zero 11 53))
(define-fun sc ((Q D)) F ((_ to_fp 8 24) RNE (fp.div RNE w (fp.add RNE one Q))))
(assert (fp.geq Q1 dzero)) (assert (not (fp.isNaN Q2)))
(assert (fp.leq Q1 Q2))
(assert (fp.gt wf (_ +zero 8 24))) (assert (fp.leq wf ((_ to_fp 8 24) RNE 64.0)))
(assert (or (fp.isNaN (sc Q1)) (fp.isNaN (sc Q2)) (fp.lt (sc Q1) (sc Q2)) (fp.lt (sc Q2) (_ +zero 8 24)) (fp.gt (sc Q1) wf)))
(check-sat)
(exit)
