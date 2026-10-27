; benchmark generated from python API
(set-info :status unknown)
(declare-fun fragCharSize () (_ BitVec 32))
(declare-fun spanStart () (_ BitVec 32))
(declare-fun phraseStart () (_ BitVec 32))
(declare-fun phraseEnd () (_ BitVec 32))
(assert
 (let ((?x1774 (bvadd ((_ sign_extend 32) spanStart) ((_ sign_extend 32) fragCharSize))))
 (let (($x1796 (bvsle spanStart phraseStart)))
 (let (($x198 (and (bvsge spanStart (_ bv0 32)) (bvsge fragCharSize (_ bv18 32)) (bvsge phraseStart (_ bv0 32)) (bvslt phraseStart phraseEnd) $x1796)))
 (and $x198 (bvsgt ?x1774 (_ bv2147483647 64)))))))
(assert
 (let ((?x741 (bvadd spanStart fragCharSize)))
 (let ((?x287 (ite (bvsgt phraseEnd ?x741) phraseEnd ?x741)))
 (let (($x2326 (= ?x287 phraseEnd)))
 (not $x2326)))))
(check-sat)
