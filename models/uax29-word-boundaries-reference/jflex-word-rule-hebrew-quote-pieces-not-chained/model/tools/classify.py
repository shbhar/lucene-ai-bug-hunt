"""Classify minimal mismatches (output of minimal.py) into root-cause families.
usage: python3 -I classify.py <min.txt>"""
import re, sys
IGN = r'(?: (?:0308|00AD|200D|FE0F|0E31|2060|3099|1D167|110BD))*'
HEB = r'(?:05D0|FB1D)'
MID = r'(?:003A|002E|0027|0022|00B7|05F4|FF0E)'
NUMENL = r'(?:0030|0661|0E50|1D7CE|005F|202F)'
LET = r'(?:05D0|FB1D|0061|05F3|AC00|3005|1100|11A8|10400)'
fam = {
  'H1 Hebrew SQ joined to following Numeric/ExtendNumLet (WB7a piece concatenated)': re.compile(HEB + IGN + r' 0027' + IGN + r' ' + NUMENL),
  'H2 trailing SQ dropped after Mid+Hebrew (WB7a unreachable)': re.compile(MID + IGN + r' ' + HEB + IGN + r' 0027'),
  'H3 word split at DQ after Mid+Hebrew (WB7b/7c unreachable)': re.compile(MID + IGN + r' ' + HEB + IGN + r' 0022'),
  'H4 word split at Mid after Hebrew DQ Hebrew (WB7b/7c piece cannot continue)': re.compile(HEB + IGN + r' 0022' + IGN + r' ' + HEB + IGN + r' ' + MID + IGN + r' ' + LET),
  'S1 SA Extend after non-SA unit starts a token (documented SA handling)': re.compile(r'0E31'),
  'E1 X ZWJ ExtPict (WB3c replaced by TR#51; upstream generator skips these cases)': re.compile(r'200D' + r'(?: 200D)*' + r' (00A9|1F44D|2139|231A|2639)'),
  'E2 text presentation selector FE0E dropped from emoji token': re.compile(r'FE0E'),
  'E3 ignorables after FE0F (or modifier) dropped from emoji token': re.compile(r'(FE0F|1F3FB)(?: \S+)*'),
  'E4 lone 20E3 at sot (harness keycap word-likeness artifact)': re.compile(r'^20E3$'),
  'U1 URL/email rule (UAX29URLEmailTokenizer feature)': re.compile(r'URL>|EMAIL>'),
}
counts = {k: 0 for k in fam}
other = []
lines = open(sys.argv[1], encoding='utf-8').read().splitlines()
print(lines[0])
for l in lines[1:]:
    inp = l.split('|')[0].strip()
    hit = None
    for k, rx in fam.items():
        if rx.search(inp if not k.startswith('U1') else l):
            hit = k
            break
    if hit:
        counts[hit] += 1
    else:
        other.append(l)
for k, v in counts.items():
    print(f'{v:6d}  {k}')
print(f'{len(other):6d}  UNCLASSIFIED')
for l in other[:40]:
    print('   ', l)
