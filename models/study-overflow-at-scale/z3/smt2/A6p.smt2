;   ... with the other clauses exhausted, scoreWindowSingleScorer collects nothing (end<=topDoc) => no progress
; core/.../search/BooleanScorer.java:247-256
; benchmark generated from python API
(set-info :status unknown)
(declare-fun topDoc () (_ BitVec 32))
(declare-fun max () (_ BitVec 32))
(assert
 (let (($x33 (and (bvsge topDoc (_ bv0 32)) (bvslt topDoc (_ bv2147483519 32)) (bvslt topDoc max))))
 (and $x33 (bvsge topDoc (_ bv2147479552 32)))))
(assert
 (let ((?x1029 (bvnot (_ bv4095 32))))
 (let ((?x9 (bvand (_ bv2147483647 32) ?x1029)))
 (let ((?x229 (ite (bvslt max ?x9) max ?x9)))
 (let ((?x5 (bvand topDoc ?x1029)))
 (let ((?x7 (bvadd ?x5 (_ bv4096 32))))
 (let ((?x23 (ite (bvslt max ?x7) max ?x7)))
 (let ((?x17 (ite (bvsgt ?x23 ?x229) ?x23 ?x229)))
 (let (($x1789 (not (bvsgt ?x17 topDoc))))
 (not $x1789))))))))))
(check-sat)
