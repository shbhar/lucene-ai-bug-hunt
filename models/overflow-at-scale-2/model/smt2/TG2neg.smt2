; benchmark generated from python API
(set-info :status unknown)
(declare-fun v1 () (_ BitVec 64))
(declare-fun v0 () (_ BitVec 64))
(assert
 (let (($x94 (bvsle v1 (_ bv2147483519 64))))
 (let (($x146 (bvsge v1 (_ bv0 64))))
 (let (($x101 (bvsle v0 (_ bv2147483519 64))))
 (let (($x188 (bvsge v0 (_ bv0 64))))
 (and $x188 $x101 $x146 $x94 (bvsgt (bvadd v0 v1) (_ bv2147483647 64))))))))
(assert
 (let ((?x2618 ((_ extract 31 0) (bvadd ((_ sign_extend 32) ((_ extract 31 0) v0)) v1))))
 (let (($x131 (bvslt ?x2618 (_ bv0 32))))
 (not $x131))))
(check-sat)
