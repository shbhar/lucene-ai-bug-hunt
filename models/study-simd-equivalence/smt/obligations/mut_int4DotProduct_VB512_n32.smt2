(set-logic QF_BV)
; benchmark generated from python API
(set-info :status unknown)
(declare-fun p0 () (_ BitVec 8))
(declare-fun p1 () (_ BitVec 8))
(declare-fun p2 () (_ BitVec 8))
(declare-fun p3 () (_ BitVec 8))
(declare-fun p4 () (_ BitVec 8))
(declare-fun p5 () (_ BitVec 8))
(declare-fun p6 () (_ BitVec 8))
(declare-fun p7 () (_ BitVec 8))
(declare-fun p8 () (_ BitVec 8))
(declare-fun p9 () (_ BitVec 8))
(declare-fun p10 () (_ BitVec 8))
(declare-fun p11 () (_ BitVec 8))
(declare-fun p12 () (_ BitVec 8))
(declare-fun p13 () (_ BitVec 8))
(declare-fun p14 () (_ BitVec 8))
(declare-fun p15 () (_ BitVec 8))
(declare-fun p16 () (_ BitVec 8))
(declare-fun p17 () (_ BitVec 8))
(declare-fun p18 () (_ BitVec 8))
(declare-fun p19 () (_ BitVec 8))
(declare-fun p20 () (_ BitVec 8))
(declare-fun p21 () (_ BitVec 8))
(declare-fun p22 () (_ BitVec 8))
(declare-fun p23 () (_ BitVec 8))
(declare-fun p24 () (_ BitVec 8))
(declare-fun p25 () (_ BitVec 8))
(declare-fun p26 () (_ BitVec 8))
(declare-fun p27 () (_ BitVec 8))
(declare-fun p28 () (_ BitVec 8))
(declare-fun p29 () (_ BitVec 8))
(declare-fun p30 () (_ BitVec 8))
(declare-fun p31 () (_ BitVec 8))
(assert
 (bvule p0 (_ bv225 8)))
(assert
 (bvule p1 (_ bv225 8)))
(assert
 (bvule p2 (_ bv225 8)))
(assert
 (bvule p3 (_ bv225 8)))
(assert
 (bvule p4 (_ bv225 8)))
(assert
 (bvule p5 (_ bv225 8)))
(assert
 (bvule p6 (_ bv225 8)))
(assert
 (bvule p7 (_ bv225 8)))
(assert
 (bvule p8 (_ bv225 8)))
(assert
 (bvule p9 (_ bv225 8)))
(assert
 (bvule p10 (_ bv225 8)))
(assert
 (bvule p11 (_ bv225 8)))
(assert
 (bvule p12 (_ bv225 8)))
(assert
 (bvule p13 (_ bv225 8)))
(assert
 (bvule p14 (_ bv225 8)))
(assert
 (bvule p15 (_ bv225 8)))
(assert
 (bvule p16 (_ bv225 8)))
(assert
 (bvule p17 (_ bv225 8)))
(assert
 (bvule p18 (_ bv225 8)))
(assert
 (bvule p19 (_ bv225 8)))
(assert
 (bvule p20 (_ bv225 8)))
(assert
 (bvule p21 (_ bv225 8)))
(assert
 (bvule p22 (_ bv225 8)))
(assert
 (bvule p23 (_ bv225 8)))
(assert
 (bvule p24 (_ bv225 8)))
(assert
 (bvule p25 (_ bv225 8)))
(assert
 (bvule p26 (_ bv225 8)))
(assert
 (bvule p27 (_ bv225 8)))
(assert
 (bvule p28 (_ bv225 8)))
(assert
 (bvule p29 (_ bv225 8)))
(assert
 (bvule p30 (_ bv225 8)))
(assert
 (bvule p31 (_ bv225 8)))
(assert
 (let ((?x3560 ((_ zero_extend 24) p31)))
 (let ((?x4365 ((_ zero_extend 24) p30)))
 (let ((?x1396 ((_ zero_extend 24) p29)))
 (let ((?x1135 ((_ zero_extend 24) p28)))
 (let ((?x3524 ((_ zero_extend 24) p27)))
 (let ((?x3234 ((_ zero_extend 24) p26)))
 (let ((?x2326 ((_ zero_extend 24) p25)))
 (let ((?x3185 ((_ zero_extend 24) p24)))
 (let ((?x2984 ((_ zero_extend 24) p23)))
 (let ((?x4068 ((_ zero_extend 24) p22)))
 (let ((?x3689 ((_ zero_extend 24) p21)))
 (let ((?x3338 ((_ zero_extend 24) p20)))
 (let ((?x3285 ((_ zero_extend 24) p19)))
 (let ((?x2906 ((_ zero_extend 24) p18)))
 (let ((?x4241 ((_ zero_extend 24) p17)))
 (let ((?x3337 ((_ zero_extend 24) p16)))
 (let ((?x3129 ((_ zero_extend 24) p15)))
 (let ((?x1247 ((_ zero_extend 24) p14)))
 (let ((?x3052 ((_ zero_extend 24) p13)))
 (let ((?x3314 ((_ zero_extend 24) p12)))
 (let ((?x61 ((_ zero_extend 24) p11)))
 (let ((?x4108 ((_ zero_extend 24) p10)))
 (let ((?x3872 ((_ zero_extend 24) p9)))
 (let ((?x3300 ((_ zero_extend 24) p8)))
 (let ((?x93 ((_ zero_extend 24) p7)))
 (let ((?x4135 ((_ zero_extend 24) p6)))
 (let ((?x153 ((_ zero_extend 24) p5)))
 (let ((?x4107 ((_ zero_extend 24) p4)))
 (let ((?x3435 ((_ zero_extend 24) p3)))
 (let ((?x3203 ((_ zero_extend 24) p2)))
 (let ((?x3914 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) ((_ zero_extend 24) p0)) ((_ zero_extend 24) p1)) ?x3203) ?x3435)))
 (let ((?x3905 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x3914 ?x4107) ?x153) ?x4135) ?x93) ?x3300) ?x3872)))
 (let ((?x2942 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x3905 ?x4108) ?x61) ?x3314) ?x3052) ?x1247) ?x3129)))
 (let ((?x2353 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x2942 ?x3337) ?x4241) ?x2906) ?x3285) ?x3338) ?x3689)))
 (let ((?x3653 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x2353 ?x4068) ?x2984) ?x3185) ?x2326) ?x3234) ?x3524)))
 (let ((?x3938 (bvadd (bvadd (bvadd (bvadd ?x3653 ?x1135) ?x1396) ?x4365) ?x3560)))
 (let ((?x60 (bvadd (bvadd (bvadd (bvadd (_ bv0 32) (_ bv0 32)) ((_ zero_extend 24) p0)) ((_ zero_extend 24) p1)) ?x3203)))
 (let ((?x397 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x60 ?x3435) ?x4107) ?x153) ?x4135) ?x93) ?x3300)))
 (let ((?x3216 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x397 ?x3872) ?x4108) ?x61) ?x3314) ?x3052) ?x1247)))
 (let ((?x4071 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x3216 ?x3129) ?x3337) ?x4241) ?x2906) ?x3285) ?x3338)))
 (let ((?x2921 (bvadd (bvadd (bvadd (bvadd (bvadd (bvadd ?x4071 ?x3689) ?x4068) ?x2984) ?x3185) ?x2326) ?x3234)))
 (let ((?x3247 (bvadd (bvadd (bvadd (bvadd (bvadd ?x2921 ?x3524) ?x1135) ?x1396) ?x4365) ?x3560)))
 (and (distinct (bvadd ?x3247 (_ bv1 32)) (bvadd (bvadd ?x3938 (_ bv1 32)) (_ bv1 32))) true))))))))))))))))))))))))))))))))))))))))))))
(check-sat)
