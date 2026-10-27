(set-logic QF_BV)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun s0 () (_ BitVec 16))
(declare-fun s1 () (_ BitVec 16))
(declare-fun s2 () (_ BitVec 16))
(declare-fun s3 () (_ BitVec 16))
(declare-fun s4 () (_ BitVec 16))
(declare-fun s5 () (_ BitVec 16))
(declare-fun s6 () (_ BitVec 16))
(declare-fun s7 () (_ BitVec 16))
(declare-fun s8 () (_ BitVec 16))
(declare-fun s9 () (_ BitVec 16))
(declare-fun s10 () (_ BitVec 16))
(declare-fun s11 () (_ BitVec 16))
(declare-fun s12 () (_ BitVec 16))
(declare-fun s13 () (_ BitVec 16))
(declare-fun s14 () (_ BitVec 16))
(declare-fun s15 () (_ BitVec 16))
(declare-fun s16 () (_ BitVec 16))
(declare-fun s17 () (_ BitVec 16))
(declare-fun s18 () (_ BitVec 16))
(declare-fun s19 () (_ BitVec 16))
(declare-fun s20 () (_ BitVec 16))
(declare-fun s21 () (_ BitVec 16))
(declare-fun s22 () (_ BitVec 16))
(declare-fun s23 () (_ BitVec 16))
(declare-fun s24 () (_ BitVec 16))
(declare-fun s25 () (_ BitVec 16))
(declare-fun s26 () (_ BitVec 16))
(declare-fun s27 () (_ BitVec 16))
(declare-fun s28 () (_ BitVec 16))
(declare-fun s29 () (_ BitVec 16))
(declare-fun s30 () (_ BitVec 16))
(declare-fun s31 () (_ BitVec 16))
(declare-fun s32 () (_ BitVec 16))
(assert
 (bvule s0 (_ bv225 16)))
(assert
 (bvule s1 (_ bv225 16)))
(assert
 (bvule s2 (_ bv225 16)))
(assert
 (bvule s3 (_ bv225 16)))
(assert
 (bvule s4 (_ bv225 16)))
(assert
 (bvule s5 (_ bv225 16)))
(assert
 (bvule s6 (_ bv225 16)))
(assert
 (bvule s7 (_ bv225 16)))
(assert
 (bvule s8 (_ bv225 16)))
(assert
 (bvule s9 (_ bv225 16)))
(assert
 (bvule s10 (_ bv225 16)))
(assert
 (bvule s11 (_ bv225 16)))
(assert
 (bvule s12 (_ bv225 16)))
(assert
 (bvule s13 (_ bv225 16)))
(assert
 (bvule s14 (_ bv225 16)))
(assert
 (bvule s15 (_ bv225 16)))
(assert
 (bvule s16 (_ bv225 16)))
(assert
 (bvule s17 (_ bv225 16)))
(assert
 (bvule s18 (_ bv225 16)))
(assert
 (bvule s19 (_ bv225 16)))
(assert
 (bvule s20 (_ bv225 16)))
(assert
 (bvule s21 (_ bv225 16)))
(assert
 (bvule s22 (_ bv225 16)))
(assert
 (bvule s23 (_ bv225 16)))
(assert
 (bvule s24 (_ bv225 16)))
(assert
 (bvule s25 (_ bv225 16)))
(assert
 (bvule s26 (_ bv225 16)))
(assert
 (bvule s27 (_ bv225 16)))
(assert
 (bvule s28 (_ bv225 16)))
(assert
 (bvule s29 (_ bv225 16)))
(assert
 (bvule s30 (_ bv225 16)))
(assert
 (bvule s31 (_ bv225 16)))
(assert
 (bvule s32 (_ bv225 16)))
(assert
 (let ((?x6375 ((_ zero_extend 16) s32)))
 (let ((?x9439 ((_ zero_extend 16) s31)))
 (let ((?x673 ((_ zero_extend 16) s30)))
 (let ((?x406 ((_ zero_extend 16) s29)))
 (let ((?x13404 ((_ zero_extend 16) s28)))
 (let ((?x8250 ((_ zero_extend 16) s27)))
 (let ((?x1357 ((_ zero_extend 16) s26)))
 (let ((?x13981 ((_ zero_extend 16) s25)))
 (let ((?x3735 ((_ zero_extend 16) s24)))
 (let ((?x12747 ((_ zero_extend 16) s23)))
 (let ((?x9313 ((_ zero_extend 16) s22)))
 (let ((?x11800 ((_ zero_extend 16) s21)))
 (let ((?x13127 ((_ zero_extend 16) s20)))
 (let ((?x13912 ((_ zero_extend 16) s19)))
 (let ((?x9465 ((_ zero_extend 16) s18)))
 (let ((?x8461 ((_ zero_extend 16) s17)))
 (let ((?x15 ((_ zero_extend 16) s16)))
 (let ((?x13895 ((_ zero_extend 16) s15)))
 (let ((?x4531 ((_ zero_extend 16) s14)))
 (let ((?x3892 ((_ zero_extend 16) s13)))
 (let ((?x2075 ((_ zero_extend 16) s12)))
 (let ((?x13334 ((_ zero_extend 16) s11)))
 (let ((?x912 ((_ zero_extend 16) s10)))
 (let ((?x3592 ((_ zero_extend 16) s9)))
 (let ((?x1033 ((_ zero_extend 16) s8)))
 (let ((?x12749 ((_ zero_extend 16) s7)))
 (let ((?x8542 ((_ zero_extend 16) s6)))
 (let ((?x2180 ((_ zero_extend 16) s5)))
 (let ((?x9091 ((_ zero_extend 16) s4)))
 (let ((?x13843 ((_ zero_extend 16) s3)))
 (let ((?x1713 ((_ zero_extend 16) s2)))
 (let ((?x2513 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) ((_ zero_extend 16) s0)) ((_ zero_extend 16) s1)) ?x1713) ?x13843)))
 (let ((?x2730 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x2513 ?x9091) ?x2180) ?x8542) ?x12749) ?x1033) ?x3592)))
 (let ((?x13581 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x2730 ?x912) ?x13334) ?x2075) ?x3892) ?x4531) ?x13895)))
 (let ((?x11842 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x13581 ?x15) ?x8461) ?x9465) ?x13912) ?x13127) ?x11800)))
 (let ((?x6977 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x11842 ?x9313) ?x12747) ?x3735) ?x13981) ?x1357) ?x8250)))
 (let ((?x463 (bvadd (bvadd (bvadd (bvadd (bvadd ?x6977 ?x13404) ?x406) ?x673) ?x9439) ?x6375)))
 (let ((?x395 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) (_ bv0 32)) ((_ zero_extend 16) s0)) ((_ zero_extend 16) s1)) ?x1713)))
 (let ((?x13890 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x395 ?x13843) ?x9091) ?x2180) ?x8542) ?x12749) ?x1033)))
 (let ((?x9867 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x13890 ?x3592) ?x912) ?x13334) ?x2075) ?x3892) ?x4531)))
 (let ((?x2254 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x9867 ?x13895) ?x15) ?x8461) ?x9465) ?x13912) ?x13127)))
 (let ((?x10490 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x2254 ?x11800) ?x9313) ?x12747) ?x3735) ?x13981) ?x1357)))
 (let ((?x2391 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x10490 ?x8250) ?x13404) ?x406) ?x673) ?x9439) ?x6375)))
 (and (distinct (bvadd ?x2391 (_ bv1 32)) (bvadd (bvadd ?x463 (_ bv1 32)) (_ bv1 32))) true)))))))))))))))))))))))))))))))))))))))))))))
(check-sat)
