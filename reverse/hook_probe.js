// 最小侵入测试：只确认能不能 attach、SG 的 so 有没加载
setTimeout(function () {
    var mods = Process.enumerateModules();
    var sg = mods.filter(function (m) { return /sgmain|sgmiddle|sgsecurity|libmain|libsecuritybody/i.test(m.name); });
    console.log("[T] pid=" + Process.id + " 模块数=" + mods.length);
    sg.forEach(function (m) { console.log("[T] SG so: " + m.name + " @ " + m.base + " size=" + m.size); });
    console.log("[T] alive ok");
}, 200);
