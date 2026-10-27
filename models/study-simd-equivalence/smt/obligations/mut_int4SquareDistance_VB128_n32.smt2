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
 (let ((?x4224 ((_ zero_extend 16) s31)))
 (let ((?x4915 ((_ zero_extend 16) s30)))
 (let ((?x5649 ((_ zero_extend 16) s29)))
 (let ((?x10946 ((_ zero_extend 16) s28)))
 (let ((?x564 ((_ zero_extend 16) s27)))
 (let ((?x1413 ((_ zero_extend 16) s26)))
 (let ((?x2525 ((_ zero_extend 16) s25)))
 (let ((?x11016 ((_ zero_extend 16) s24)))
 (let ((?x8666 ((_ zero_extend 16) s23)))
 (let ((?x3059 ((_ zero_extend 16) s22)))
 (let ((?x9683 ((_ zero_extend 16) s21)))
 (let ((?x5057 ((_ zero_extend 16) s20)))
 (let ((?x6804 ((_ zero_extend 16) s19)))
 (let ((?x11685 ((_ zero_extend 16) s18)))
 (let ((?x5956 ((_ zero_extend 16) s17)))
 (let ((?x775 ((_ zero_extend 16) s16)))
 (let ((?x1358 ((_ zero_extend 16) s15)))
 (let ((?x558 ((_ zero_extend 16) s14)))
 (let ((?x6567 ((_ zero_extend 16) s13)))
 (let ((?x8250 ((_ zero_extend 16) s12)))
 (let ((?x5357 ((_ zero_extend 16) s11)))
 (let ((?x5404 ((_ zero_extend 16) s10)))
 (let ((?x2240 ((_ zero_extend 16) s9)))
 (let ((?x10789 ((_ zero_extend 16) s8)))
 (let ((?x6604 ((_ zero_extend 16) s7)))
 (let ((?x2452 ((_ zero_extend 16) s6)))
 (let ((?x8825 ((_ zero_extend 16) s5)))
 (let ((?x2278 ((_ zero_extend 16) s4)))
 (let ((?x8560 ((_ zero_extend 16) s3)))
 (let ((?x10088 ((_ zero_extend 16) s2)))
 (let ((?x5293 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) ((_ zero_extend 16) s0)) ((_ zero_extend 16) s1)) ?x10088) ?x8560)))
 (let ((?x9093 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x5293 ?x2278) ?x8825) ?x2452) ?x6604) ?x10789) ?x2240)))
 (let ((?x598 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x9093 ?x5404) ?x5357) ?x8250) ?x6567) ?x558) ?x1358)))
 (let ((?x5273 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x598 ?x775) ?x5956) ?x11685) ?x6804) ?x5057) ?x9683)))
 (let ((?x383 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x5273 ?x3059) ?x8666) ?x11016) ?x2525) ?x1413) ?x564)))
 (let ((?x857 (bvadd (bvadd (bvadd (bvadd ?x383 ?x10946) ?x5649) ?x4915) ?x4224)))
 (let ((?x5667 (concat (bvadd (bvadd (_ bv0 16) s15) s31) (bvadd (bvadd (_ bv0 16) s13) s29))))
 (let ((?x13899 (concat (bvadd (bvadd (_ bv0 16) s14) s30) (bvadd (bvadd (_ bv0 16) s12) s28))))
 (let ((?x2734 (bvadd (bvadd (bvand ?x13899 (_ bv65535 32)) (bvlshr ?x13899 (_ bv16 32))) (bvand ?x5667 (_ bv65535 32)))))
 (let ((?x10987 (concat (bvadd (bvadd (_ bv0 16) s11) s27) (bvadd (bvadd (_ bv0 16) s9) s25))))
 (let ((?x7865 (concat (bvadd (bvadd (_ bv0 16) s10) s26) (bvadd (bvadd (_ bv0 16) s8) s24))))
 (let ((?x13695 (bvadd (bvadd (bvand ?x7865 (_ bv65535 32)) (bvlshr ?x7865 (_ bv16 32))) (bvand ?x10987 (_ bv65535 32)))))
 (let ((?x4174 (concat (bvadd (bvadd (_ bv0 16) s7) s23) (bvadd (bvadd (_ bv0 16) s5) s21))))
 (let ((?x13797 (concat (bvadd (bvadd (_ bv0 16) s6) s22) (bvadd (bvadd (_ bv0 16) s4) s20))))
 (let ((?x7716 (bvadd (bvadd (bvand ?x13797 (_ bv65535 32)) (bvlshr ?x13797 (_ bv16 32))) (bvand ?x4174 (_ bv65535 32)))))
 (let ((?x3342 (concat (bvadd (bvadd (_ bv0 16) s3) s19) (bvadd (bvadd (_ bv0 16) s1) s17))))
 (let ((?x10785 (concat (bvadd (bvadd (_ bv0 16) s2) s18) (bvadd (bvadd (_ bv0 16) s0) s16))))
 (let ((?x13809 (bvadd (bvadd (bvand ?x10785 (_ bv65535 32)) (bvlshr ?x10785 (_ bv16 32))) (bvand ?x3342 (_ bv65535 32)))))
 (let ((?x4110 (bvadd (bvadd ?x13809 (bvlshr ?x3342 (_ bv16 32))) (bvadd ?x7716 (bvlshr ?x4174 (_ bv16 32))))))
 (let ((?x6851 (bvadd (bvadd ?x4110 (bvadd ?x13695 (bvlshr ?x10987 (_ bv16 32)))) (bvadd ?x2734 (bvlshr ?x5667 (_ bv16 32))))))
 (let ((?x4325 (bvadd (_ bv0 32) (bvadd (_ bv0 32) ?x6851))))
 (and (distinct (bvadd ?x4325 (_ bv1 32)) (bvadd (bvadd ?x857 (_ bv1 32)) (_ bv1 32))) true)))))))))))))))))))))))))))))))))))))))))))))))))))))
(check-sat)
