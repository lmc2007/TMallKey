import ida_auto, ida_ua, ida_funcs, ida_kernwin, idautils, idc
import ida_bytes

ida_auto.auto_wait()

TARGETS = {0x961: "2401", 0x963: "2403", 0x965: "2405"}
hits = []
for seg_ea in idautils.Segments():
    seg = ida_bytes.getseg(seg_ea) if hasattr(ida_bytes, "getseg") else None
    name = idc.get_segm_name(seg_ea)
    if name not in (".text",):
        continue
    start = idc.get_segm_start(seg_ea)
    end = idc.get_segm_end(seg_ea)
    ea = start
    while ea < end:
        insn = ida_ua.insn_t()
        length = ida_ua.decode_insn(insn, ea)
        if length <= 0:
            ea += 4
            continue
        for op in insn.ops:
            if op.type == 2:  # o_imm
                if op.value in TARGETS:
                    f = ida_funcs.get_func(ea)
                    fname = ida_funcs.get_func_name(f.start_ea) if f else "?"
                    hits.append("%s %s imm=%s func=%s off=%s" % (
                        hex(ea), insn.get_canon_mnem(), TARGETS[op.value], fname,
                        hex(ea - f.start_ea) if f else "?"))
        ea += length

hits = hits[:400]
out = "\n".join(hits) + "\nTOTAL %d\n" % len(hits)
with open("D:/sg2405_hits.txt", "w", encoding="utf-8") as fh:
    fh.write(out)
print(out[:2000])
idc.qexit(0)
