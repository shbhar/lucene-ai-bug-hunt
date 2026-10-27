; benchmark generated from python API
(set-info :status unknown)
(declare-fun phraseStart () (_ BitVec 32))
(declare-fun spanStart () (_ BitVec 32))
(declare-fun phraseEnd () (_ BitVec 32))
(declare-fun fragCharSize () (_ BitVec 32))
(assert
 (let (($x1796 (bvsle spanStart phraseStart)))
 (and (bvsge spanStart (_ bv0 32)) (bvsge fragCharSize (_ bv18 32)) (bvsge phraseStart (_ bv0 32)) (bvslt phraseStart phraseEnd) $x1796)))
(assert
 (let ((?x1774 (bvadd ((_ sign_extend 32) spanStart) ((_ sign_extend 32) fragCharSize))))
 (let (($x1480 (= ((_ sign_extend 32) (bvadd spanStart fragCharSize)) ?x1774)))
 (not $x1480))))
(check-sat)
