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
 (let ((?x741 (bvadd spanStart fragCharSize)))
 (let ((?x275 (ite (bvult ?x741 (_ bv2147483647 32)) ?x741 (_ bv2147483647 32))))
 (let ((?x1536 (ite (bvsgt phraseEnd ?x275) phraseEnd ?x275)))
 (let ((?x1774 (bvadd ((_ sign_extend 32) spanStart) ((_ sign_extend 32) fragCharSize))))
 (let (($x709 (and (bvsge ((_ sign_extend 32) ?x1536) (ite (bvsgt ?x1774 (_ bv2147483647 64)) (_ bv2147483647 64) ?x1774)) (bvsge ?x1536 phraseEnd))))
 (not $x709)))))))
(check-sat)
