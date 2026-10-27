; BitSetIterator.docIDRunEnd: upTo=doc+min(length-doc,2^16) is exact and <= length
; core/.../util/BitSetIterator.java:106-119
; benchmark generated from python API
(set-info :status unknown)
(declare-fun length () (_ BitVec 32))
(declare-fun doc () (_ BitVec 32))
(assert
 (and (bvsge length (_ bv1 32)) (bvsge doc (_ bv0 32)) (bvslt doc length)))
(assert
 (let ((?x1035 (bvadd doc (ite (bvsgt (_ bv65536 32) (bvsub length doc)) (bvsub length doc) (_ bv65536 32)))))
 (let ((?x2042 ((_ sign_extend 32) doc)))
 (let ((?x490 (bvsub ((_ sign_extend 32) length) ?x2042)))
 (let (($x1641 (and (= ((_ sign_extend 32) ?x1035) (bvadd ?x2042 (ite (bvslt ?x490 (_ bv65536 64)) ?x490 (_ bv65536 64)))) (bvsle ?x1035 length))))
 (not $x1641))))))
(check-sat)
