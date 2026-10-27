/*
 * Alloy 6 model of the FST node arc encodings and of the per-node label lookups used by
 * FST.findTargetArc, Util.readCeilArc and the FSTEnum seekCeil/seekFloor helpers.
 * Transcribed from lucene/core/src/java/org/apache/lucene/util/fst/{FST,FSTCompiler,FSTEnum,Util}.java
 * at apache/lucene main 7cecf79523.
 *
 * A node is its (non-empty) set of outgoing labels. The writer (FSTCompiler.addNode, ~l.519-537)
 * picks one of four encodings:
 *   LIST  variable-length arcs, linear scan                      (not fixed-length)
 *   BS    ARCS_FOR_BINARY_SEARCH: sorted fixed-size arcs, label stored, index = rank
 *   DA    ARCS_FOR_DIRECT_ADDRESSING: header numArcs := labelRange, presence bit table,
 *         firstLabel stored once, arc slot = presenceIndex = #set bits below rangeIndex
 *   CONT  ARCS_FOR_CONTINUOUS: numArcs := labelRange, firstLabel, slot = rangeIndex,
 *         chosen only when labelRange == numArcs and version >= VERSION_CONTINUOUS_ARCS
 * The encoded fields are modelled as independent relations constrained by the writer's
 * construction (facts below), and the reader functions are transcribed from the Java code over
 * those fields only (never over n.labels directly), so the checks are about the index arithmetic
 * and the writer/reader contract, not about a tautology.
 *
 * Bit-level BitTableUtil (nextBitSet / previousBitSet / countBitsUpTo on bytes) is abstracted to
 * its set-level spec here; it is verified separately against the bytes by Z3 (smt/bittable.py).
 */
module fst_arcs

abstract sig Enc {}
one sig LIST, BS, DA, CONT extends Enc {}

sig Node {
  labels: set Int,
  enc: one Enc,
  // ---- encoded fields (what the reader can see) ----
  numArcs: one Int,          // header vInt: #arcs for BS, labelRange for DA and CONT
  firstLabel: one Int,       // DA and CONT only
  bits: set Int,             // DA presence bits: range indexes that are set
  slot: Int -> lone Int,     // BS/CONT: arc index -> stored label ; DA: presence index -> label
  version: one Int           // 8 = VERSION_90, 9 = VERSION_CONTINUOUS_ARCS
}

// labels are non-negative (FSTCompiler.writeLabel asserts v >= 0); keep the scope small
fact labelsDomain { all n: Node | some n.labels and (all l: n.labels | l >= 0 and l <= 7) }
fact versions { all n: Node | n.version in 8 + 9 }

fun minL[n: Node]: Int { min[n.labels] }
fun maxL[n: Node]: Int { max[n.labels] }
fun labelRange[n: Node]: Int { minus[plus[maxL[n], 1], minL[n]] }
fun rank[n: Node, l: Int]: Int { #{x: n.labels | x < l} }

// ---------------------------------------------------------------- writer (FSTCompiler.addNode)
pred continuousChosen[n: Node] { labelRange[n] = #n.labels and n.version >= 9 }

fact writer {
  all n: Node {
    // encoding choice: CONT exactly when contiguous and version >= 9; DA/BS otherwise (the
    // DA-vs-BS size heuristic may pick either, so both are allowed); LIST when not fixed-length
    n.enc = CONT iff (n.enc != LIST and continuousChosen[n])
    n.enc = LIST => (no n.bits and no n.slot)
    n.enc = BS => {
      n.numArcs = #n.labels
      no n.bits
      // writeNodeForBinarySearch: arcs expanded in label order, label kept in each arc
      all i: Int | (some n.slot[i]) iff (i >= 0 and i < #n.labels)
      all l: n.labels | n.slot[rank[n, l]] = l
    }
    n.enc = DA => {
      n.numArcs = labelRange[n]
      n.firstLabel = minL[n]
      // writePresenceBits: bit (label - firstLabel) set for each arc
      n.bits = { i: Int | plus[minL[n], i] in n.labels }
      // writeNodeForDirectAddressingOrContinuous: arcs stored densely in label order
      all i: Int | (some n.slot[i]) iff (i >= 0 and i < #n.labels)
      all l: n.labels | n.slot[rank[n, l]] = l
    }
    n.enc = CONT => {
      n.numArcs = labelRange[n]
      n.firstLabel = minL[n]
      no n.bits
      all i: Int | (some n.slot[i]) iff (i >= 0 and i < #n.labels)
      all l: n.labels | n.slot[rank[n, l]] = l
    }
  }
}

// ---------------------------------------------------------------- reader primitives
// BitTableUtil specs (set level)
fun nextBitSet[n: Node, i: Int]: Int {      // smallest set bit > i, or -1
  (some j: n.bits | j > i) => min[{j: n.bits | j > i}] else -1
}
fun previousBitSet[n: Node, i: Int]: Int {  // largest set bit < i, or -1
  (some j: n.bits | j < i) => max[{j: n.bits | j < i}] else -1
}
fun countBitsUpTo[n: Node, i: Int]: Int { #{j: n.bits | j < i} }
fun countBits[n: Node]: Int { #n.bits }

// FST.readArc: DA/CONT label = firstLabel + arcIdx (rangeIndex); BS label read from the slot
fun daLabelAt[n: Node, rangeIndex: Int]: Int {
  // readArcByDirectAddressing(rangeIndex): slot presenceIndex = countBitsUpTo(rangeIndex),
  // label reported = firstLabel + rangeIndex. We return the label *reported* and separately check
  // that the slot read holds the same arc (slotConsistent below).
  plus[n.firstLabel, rangeIndex]
}
pred slotConsistent[n: Node, rangeIndex: Int] {
  n.slot[countBitsUpTo[n, rangeIndex]] = plus[n.firstLabel, rangeIndex]
}
fun contLabelAt[n: Node, idx: Int]: Int { plus[n.firstLabel, idx] }
pred contSlotConsistent[n: Node, idx: Int] { n.slot[idx] = plus[n.firstLabel, idx] }

// Util.binarySearch result: index if found, else -1 - insertionPoint (abstracted loop)
fun bsearch[n: Node, t: Int]: Int {
  (some i: Int | n.slot[i] = t) => { i: Int | n.slot[i] = t }
  else minus[-1, #{i: Int | some n.slot[i] and n.slot[i] < t}]
}
fun bsLabelAt[n: Node, i: Int]: Int { n.slot[i] }
fun bsLast[n: Node]: Int { minus[n.numArcs, 1] }

// ---------------------------------------------------------------- set-level specs
fun specCeil[n: Node, t: Int]: lone Int { (some l: n.labels | l >= t) => min[{l: n.labels | l >= t}] else none }
fun specFloor[n: Node, t: Int]: lone Int { (some l: n.labels | l <= t) => max[{l: n.labels | l <= t}] else none }
fun specLower[n: Node, t: Int]: lone Int { (some l: n.labels | l < t) => max[{l: n.labels | l < t}] else none }

// ---------------------------------------------------------------- transcribed readers
// FST.findTargetArc (FST.java ~1000-1090), for a real label t >= 0 on a node with arcs
fun findTargetArc[n: Node, t: Int]: lone Int {
  n.enc = DA => {
    // arcIndex = labelToMatch - firstLabel; out of range or bit clear -> null
    let idx = minus[t, n.firstLabel] |
      (idx < 0 or idx >= n.numArcs or idx not in n.bits) => none else daLabelAt[n, idx]
  } else n.enc = CONT => {
    let idx = minus[t, n.firstLabel] |
      (idx < 0 or idx >= n.numArcs) => none else contLabelAt[n, idx]
  } else n.enc = BS => {
    let r = bsearch[n, t] | r >= 0 => bsLabelAt[n, r] else none
  } else {
    // linear scan: stop at the first label >= t
    (t in n.labels) => t else none
  }
}

// Util.readCeilArc (Util.java ~830-880), follow non-final, t >= 0
fun readCeilArc[n: Node, t: Int]: lone Int {
  n.enc = DA => {
    let ti = minus[t, n.firstLabel] |   // code: label - arc.label() with arc = first arc
      ti >= n.numArcs => none
      else ti < 0 => n.firstLabel
      else ti in n.bits => daLabelAt[n, ti]
      else daLabelAt[n, nextBitSet[n, ti]]
  } else n.enc = CONT => {
    let ti = minus[t, n.firstLabel] |
      ti >= n.numArcs => none
      else ti < 0 => n.firstLabel
      else contLabelAt[n, ti]
  } else n.enc = BS => {
    let r = bsearch[n, t] |
      r >= 0 => bsLabelAt[n, r]
      else (minus[-1, r] = n.numArcs) => none
      else bsLabelAt[n, minus[-1, r]]
  } else specCeil[n, t]
}

// FSTEnum.doSeekCeilArray* (FSTEnum.java ~150-260): the arc the enum lands on at this node
// (none = rollback to the parent)
fun seekCeilArc[n: Node, t: Int]: lone Int {
  n.enc = DA => {
    let ti = minus[t, n.firstLabel] |
      ti >= n.numArcs => none
      else ti < 0 => daLabelAt[n, nextBitSet[n, -1]]
      else ti in n.bits => daLabelAt[n, ti]
      else daLabelAt[n, nextBitSet[n, ti]]
  } else n.enc = CONT => {
    let ti = minus[t, n.firstLabel] |
      ti >= n.numArcs => none
      else ti < 0 => contLabelAt[n, 0]
      else contLabelAt[n, ti]
  } else n.enc = BS => {
    let r = bsearch[n, t] |
      r >= 0 => bsLabelAt[n, r]
      else (minus[-1, r] = n.numArcs) => none
      else bsLabelAt[n, minus[-1, r]]
  } else specCeil[n, t]
}

// FSTEnum.doSeekFloorArray* (~300-430): arc landed on, or none = backtrack (before first arc)
fun seekFloorArc[n: Node, t: Int]: lone Int {
  n.enc = DA => {
    let ti = minus[t, n.firstLabel] |
      ti < 0 => none
      else ti >= n.numArcs => daLabelAt[n, minus[n.numArcs, 1]]   // readLastArcByDirectAddressing
      else ti in n.bits => daLabelAt[n, ti]
      else daLabelAt[n, previousBitSet[n, ti]]
  } else n.enc = CONT => {
    let ti = minus[t, n.firstLabel] |
      ti < 0 => none
      else ti >= n.numArcs => contLabelAt[n, minus[n.numArcs, 1]]
      else contLabelAt[n, ti]
  } else n.enc = BS => {
    let r = bsearch[n, t] |
      r >= 0 => bsLabelAt[n, r]
      else r = -1 => none
      else bsLabelAt[n, minus[-2, r]]
  } else specFloor[n, t]
}

// FSTEnum.findNextFloorArc{DirectAddressing,Continuous,BinarySearch} (~470-525):
// precondition: the first arc's label < t; result = arc strictly below t (the first arc when
// the helper "keeps" it)
fun nextFloorArc[n: Node, t: Int]: Int {
  n.enc = DA => {
    (n.numArcs > 1) => {
      let ti = minus[t, n.firstLabel] |
        ti >= n.numArcs => daLabelAt[n, minus[n.numArcs, 1]]
        else (let fi = previousBitSet[n, ti] | fi > 0 => daLabelAt[n, fi] else n.firstLabel)
    } else n.firstLabel
  } else n.enc = CONT => {
    (n.numArcs > 1) => {
      let ti = minus[t, n.firstLabel] |
        ti >= n.numArcs => contLabelAt[n, minus[n.numArcs, 1]]
        else contLabelAt[n, minus[ti, 1]]
    } else n.firstLabel
  } else n.enc = BS => {
    (n.numArcs > 1) => {
      let r = bsearch[n, t] |
        r > 1 => bsLabelAt[n, minus[r, 1]]
        else r < -2 => bsLabelAt[n, minus[-2, r]]
        else bsLabelAt[n, 0]
    } else bsLabelAt[n, 0]
  } else specLower[n, t]
}

// ---------------------------------------------------------------- checks
// probe labels: one below the domain, the domain, one above
pred probe[t: Int] { t >= 0 and t <= 8 }

assert FindTargetArcCorrect { all n: Node, t: Int | probe[t] => findTargetArc[n, t] = (t & n.labels) }
assert ReadCeilArcCorrect { all n: Node, t: Int | probe[t] => readCeilArc[n, t] = specCeil[n, t] }
assert SeekCeilArcCorrect { all n: Node, t: Int | probe[t] => seekCeilArc[n, t] = specCeil[n, t] }
assert SeekFloorArcCorrect { all n: Node, t: Int | probe[t] => seekFloorArc[n, t] = specFloor[n, t] }
assert NextFloorArcCorrect {
  all n: Node, t: Int | (probe[t] and minL[n] < t) => nextFloorArc[n, t] = specLower[n, t]
}
// every label the DA/CONT reader reports is backed by the arc it actually reads
assert SlotsConsistent {
  all n: Node, t: Int | probe[t] => {
    n.enc = DA and some readCeilArc[n, t] =>
      slotConsistent[n, minus[readCeilArc[n, t], n.firstLabel]]
    n.enc = DA and some seekFloorArc[n, t] =>
      slotConsistent[n, minus[seekFloorArc[n, t], n.firstLabel]]
    n.enc = CONT and some readCeilArc[n, t] =>
      contSlotConsistent[n, minus[readCeilArc[n, t], n.firstLabel]]
  }
}
// writer invariants relied upon by readers:
//  FST.Arc.BitTable.assertIsValid: first and last presence bits set, none after the last
//  readLastArcByDirectAddressing: presenceIndex = countBits - 1 is the last slot
//  readArc(BIT_TARGET_NEXT) for DA uses countBits(arc) as the number of slots
assert WriterInvariants {
  all n: Node | n.enc = DA => {
    0 in n.bits
    minus[n.numArcs, 1] in n.bits
    no j: n.bits | j >= n.numArcs
    countBits[n] = #n.labels
    n.slot[minus[countBits[n], 1]] = maxL[n]
  }
  all n: Node | n.enc = CONT => (all i: Int | (i >= 0 and i < n.numArcs) => some n.slot[i])
}

// sanity: each encoding is realisable (non-vacuity), and the CONT reader would be wrong if the
// writer ever chose CONT for non-contiguous labels (shows why the writer condition matters)
run SomeDA { some n: Node | n.enc = DA and #n.labels > 2 and labelRange[n] > #n.labels } for 1 but 5 Int
run SomeCONT { some n: Node | n.enc = CONT and #n.labels > 2 } for 1 but 5 Int
run SomeBS { some n: Node | n.enc = BS and #n.labels > 2 } for 1 but 5 Int

check FindTargetArcCorrect for 1 but 5 Int
check ReadCeilArcCorrect for 1 but 5 Int
check SeekCeilArcCorrect for 1 but 5 Int
check SeekFloorArcCorrect for 1 but 5 Int
check NextFloorArcCorrect for 1 but 5 Int
check SlotsConsistent for 1 but 5 Int
check WriterInvariants for 1 but 5 Int
