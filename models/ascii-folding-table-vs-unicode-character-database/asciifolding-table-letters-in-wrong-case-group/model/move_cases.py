"""Move case labels of ASCIIFoldingFilter.foldToASCII into the single-letter output group given.
Usage: python3 -I move_cases.py IN.java OUT.java CP=TARGET [CP=TARGET ...]   e.g. 01E5=g"""
import re, sys
src, dst, moves = sys.argv[1], sys.argv[2], [a.split('=') for a in sys.argv[3:]]
lines = open(src, encoding='utf-8').read().split('\n')
case_re = re.compile(r"^\s*case '\\u([0-9A-Fa-f]{4})':")
emit = lambda ch: "            output[outputPos++] = '%s';" % ch
for cp, target in moves:
    idx = [i for i, l in enumerate(lines) if (m := case_re.match(l)) and m.group(1).upper() == cp.upper()]
    assert len(idx) == 1, cp
    moved = lines.pop(idx[0])
    # target group: a run of case lines followed by exactly one emit of `target` and then break
    for i, l in enumerate(lines):
        if l == emit(target) and lines[i + 1].strip() == 'break;' and case_re.match(lines[i - 1]):
            j = i
            while case_re.match(lines[j - 1]):
                j -= 1
            # keep code-point order inside the group
            k = j
            while k < i and int(case_re.match(lines[k]).group(1), 16) < int(cp, 16):
                k += 1
            lines.insert(k, moved)
            break
    else:
        raise SystemExit('no group for ' + target)
open(dst, 'w', encoding='utf-8').write('\n'.join(lines))
