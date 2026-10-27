# mkcfg.py NAME key=value ... ; writes model/NAME.cfg
import sys
name = sys.argv[1]
opt = dict(N='3', W='2', M='1', BEAM='2', NLOCKS='2', SPLIT='FALSE', FIX_SCORER='FALSE', FIX_DESCENT='FALSE', PRINT_FINAL='FALSE',
           SimChoices='C1_Sim', LevelChoices='C1_Levels', InitChoices='C1_Init', RepairD='NoRepair', Order='C1_Order',
           INV='NoCodeError,NoSelfLoop,NoDuplicate,SizeBound,RefsExist,SortedPrefix,EntryOK,SizeCounter,Published,'
               'TopLevelIsEntryLevel,DownwardClosed,AllInserted,Linked,ScorerQueryOK,ScoreBookkeeping', DEADLOCK='TRUE')
for kv in sys.argv[2:]:
    k, v = kv.split('=', 1); opt[k] = v
ops = {'SimChoices', 'LevelChoices', 'InitChoices', 'RepairD', 'Order'} | set(opt.get('OPS', '').split(',')) - {''}
with open(f'model/{name}.cfg', 'w') as f:
    f.write('CONSTANTS\n')
    for k in ['N', 'W', 'M', 'BEAM', 'NLOCKS', 'SPLIT', 'FIX_SCORER', 'FIX_DESCENT', 'PRINT_FINAL'] + sorted(ops):
        f.write(f'  {k} {"<-" if k in ops else "="} {opt[k]}\n')
    for k in sorted(set(opt.get('OPS', '').split(',')) - {''}):
        pass
    if opt.get('SEQ'):
        f.write(f'  SeqViews <- {opt["SEQ"]}\n')
    f.write('SPECIFICATION Spec\nINVARIANTS\n')
    for i in opt['INV'].split(','):
        if i: f.write(f'  {i}\n')
    f.write(f'CHECK_DEADLOCK {opt["DEADLOCK"]}\n')
