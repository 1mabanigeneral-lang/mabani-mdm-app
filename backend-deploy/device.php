<?php
require_once __DIR__ . '/../config.php';
mdm_require_login();
$pdo = mdm_getDB();

$id = (int)($_GET['id'] ?? 0);
$st = $pdo->prepare("SELECT * FROM devices WHERE id = ? LIMIT 1");
$st->execute([$id]);
$device = $st->fetch();
if (!$device) { header('Location: devices.php'); exit(); }

$sent = null;
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['action']) && $_POST['action'] === 'send_command') {
    $cmd = $_POST['command'] ?? '';
    $allowed = ['LOCK', 'UNLOCK', 'PING', 'WIPE_APP_DATA', 'HIDE_APP', 'SHOW_APP', 'OPEN_APP', 'LOCK_KIOSK', 'UNLOCK_KIOSK'];
    $pkg = trim($_POST['package'] ?? '');
    $needsPkg = in_array($cmd, ['WIPE_APP_DATA', 'HIDE_APP', 'SHOW_APP', 'OPEN_APP'], true);
    $isKioskLock = $cmd === 'LOCK_KIOSK';

    // LOCK_KIOSK's "package" field is a comma-separated list of one or more
    // package names (built client-side from the checked apps + any custom
    // ones typed in), unlike the other commands which take exactly one.
    $kioskPkgs = [];
    if ($isKioskLock) {
        foreach (explode(',', $pkg) as $p) {
            $p = trim($p);
            if ($p !== '') $kioskPkgs[] = $p;
        }
    }

    $valid = in_array($cmd, $allowed, true)
        && (!$needsPkg || $pkg !== '')
        && (!$isKioskLock || count($kioskPkgs) > 0);

    if ($valid) {
        if ($isKioskLock) {
            $payload = json_encode(['packages' => $kioskPkgs]);
        } elseif ($needsPkg) {
            $payload = json_encode(['package' => $pkg]);
        } else {
            $payload = null;
        }
        $st2 = $pdo->prepare("INSERT INTO device_commands (device_id, command, payload, status) VALUES (?, ?, ?, 'pending')");
        $st2->execute([$id, $cmd, $payload]);
        $sent = $cmd;
        if ($isKioskLock) $sent .= ' (' . implode(', ', $kioskPkgs) . ')';
        elseif ($needsPkg) $sent .= " ($pkg)";
    }
}

$commands = [];
try {
    $st3 = $pdo->prepare("SELECT * FROM device_commands WHERE device_id = ? ORDER BY created_at DESC LIMIT 15");
    $st3->execute([$id]);
    $commands = $st3->fetchAll();
} catch (Throwable $e) {}

$cmdLabels = [
    'LOCK' => 'قفل الجهاز',
    'UNLOCK' => 'فتح القفل',
    'PING' => 'فحص اتصال',
    'WIPE_APP_DATA' => 'مسح بيانات تطبيق',
    'HIDE_APP' => 'إخفاء تطبيق',
    'SHOW_APP' => 'إظهار تطبيق',
    'OPEN_APP' => 'فتح تطبيق عن بعد',
    'LOCK_KIOSK' => 'تفعيل وضع Kiosk',
    'UNLOCK_KIOSK' => 'إلغاء وضع Kiosk',
];

$commonApps = [
    'com.mabani.foreman' => 'تطبيق الفورمن',
    'com.whatsapp' => 'واتساب',
    'com.android.vending' => 'Google Play',
    'com.android.chrome' => 'Chrome',
    'com.facebook.katana' => 'فيسبوك',
    'com.instagram.android' => 'إنستغرام',
    'com.zhiliaoapp.musically' => 'تيك توك',
    'com.google.android.youtube' => 'يوتيوب',
];
// This is the actual Kiosk whitelist: Foreman + WhatsApp, pre-checked by
// default. Calls/SMS/Camera are NOT listed here -- the app resolves each
// device's own default dialer/SMS/camera app automatically at runtime
// (handles the G-Tab/Honor package-name differences on its own), so they're
// always included without needing to be picked here.
$kioskApps = [
    'com.mabani.foreman' => 'تطبيق الفورمن',
    'com.whatsapp' => 'واتساب',
];
$statusLabels = [
    'pending' => ['بالانتظار', '#8a7f6e', '#f1ede3'],
    'sent'    => ['أُرسل', '#B45309', '#FFFBEB'],
    'acked'   => ['نُفّذ', '#059669', '#ECFDF5'],
    'failed'  => ['فشل', '#B91C1C', '#FEF2F2'],
];
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title><?= htmlspecialchars($device['device_name']) ?> — 1MABANI MDM</title>
<link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
*{box-sizing:border-box;margin:0;padding:0;font-family:'Cairo',system-ui,sans-serif}
body{background:#f4f1ea;min-height:100vh;}
.top{background:#3d0f1c;color:#fff;padding:18px 24px;display:flex;align-items:center;gap:12px;}
.top a.back{color:#fff;text-decoration:none;}
.top .brand{font-size:14.5px;font-weight:800;}
.wrap{max-width:700px;margin:0 auto;padding:24px 16px;}
.card{background:#fff;border-radius:14px;border:1px solid #ece7db;padding:18px;margin-bottom:16px;}
.dName{font-size:15px;font-weight:800;color:#1a0a10;margin-bottom:10px;}
.infoRow{display:flex;justify-content:space-between;font-size:12px;padding:6px 0;border-bottom:1px solid #f4f1ea;}
.infoRow span:first-child{color:#8a7f6e;}
.infoRow span:last-child{font-weight:700;color:#1a0a10;}
.secTitle{font-size:11.5px;font-weight:800;color:#8a7f6e;margin-bottom:10px;}
.btnGrid{display:grid;grid-template-columns:1fr 1fr;gap:10px;}
.cmdBtn{background:#fff;border:1.5px solid #ece7db;border-radius:12px;padding:14px;font-size:12.5px;font-weight:700;color:#1a0a10;cursor:pointer;text-align:center;}
.cmdBtn.danger{border-color:#FCA5A5;color:#B91C1C;}
.sentMsg{background:#ECFDF5;color:#059669;border-radius:10px;padding:10px 14px;font-size:11.5px;margin-bottom:14px;}
.hRow{display:flex;align-items:center;justify-content:space-between;background:#fff;border:1px solid #ece7db;border-radius:12px;padding:11px 14px;margin-bottom:7px;}
.hName{font-size:12px;font-weight:700;color:#1a0a10;}
.hTime{font-size:10px;color:#8a7f6e;margin-top:2px;}
.badge{font-size:10px;font-weight:700;padding:4px 10px;border-radius:20px;}
</style>
</head>
<body>
<div class="top">
  <a class="back" href="devices.php">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5"></path><path d="M12 19l-7-7 7-7"></path></svg>
  </a>
  <div class="brand">تفاصيل الجهاز</div>
</div>
<div class="wrap">
  <div class="card">
    <div class="dName"><?= htmlspecialchars($device['device_name']) ?></div>
    <div class="infoRow"><span>الحالة</span><span><?= htmlspecialchars($device['status']) ?></span></div>
    <div class="infoRow"><span>الموديل</span><span><?= htmlspecialchars($device['model'] ?: '—') ?></span></div>
    <div class="infoRow"><span>إصدار أندرويد</span><span><?= htmlspecialchars($device['android_version'] ?: '—') ?></span></div>
    <div class="infoRow"><span>آخر ظهور</span><span><?= $device['last_seen'] ? htmlspecialchars(date('Y-m-d h:i A', strtotime($device['last_seen']))) : '—' ?></span></div>
    <div class="infoRow"><span>آخر موقع</span><span><?= ($device['last_lat'] && $device['last_lng']) ? htmlspecialchars($device['last_lat'] . ', ' . $device['last_lng']) : '—' ?></span></div>
    <div class="infoRow"><span>البطارية</span><span><?= $device['battery_pct'] !== null ? $device['battery_pct'] . '%' : '—' ?></span></div>
    <?php if ($device['last_lat'] && $device['last_lng']):
        $lat = (float)$device['last_lat']; $lng = (float)$device['last_lng'];
        $d = 0.003; // small bounding box around the point, for the embedded map
        $bbox = ($lng - $d) . ',' . ($lat - $d) . ',' . ($lng + $d) . ',' . ($lat + $d);
    ?>
      <iframe style="width:100%;height:220px;border:0;border-radius:10px;margin-top:10px;" loading="lazy"
        src="https://www.openstreetmap.org/export/embed.html?bbox=<?= htmlspecialchars($bbox) ?>&marker=<?= $lat ?>,<?= $lng ?>"></iframe>
      <a href="https://www.google.com/maps?q=<?= $lat ?>,<?= $lng ?>" target="_blank" style="display:block;text-align:center;font-size:11.5px;color:#B45309;margin-top:8px;text-decoration:none;">📍 فتح الموقع بخرائط قوقل</a>
    <?php endif; ?>
  </div>

  <?php if ($sent): ?>
    <div class="sentMsg">تم إرسال الأمر "<?= htmlspecialchars($sent) ?>" — بينفذ أول ما الجهاز يتواصل.</div>
  <?php endif; ?>

  <div class="card">
    <div class="secTitle">أوامر سريعة</div>
    <div class="btnGrid">
      <form method="post"><input type="hidden" name="action" value="send_command"><input type="hidden" name="command" value="LOCK"><button class="cmdBtn" type="submit">🔒 قفل الجهاز</button></form>
      <form method="post"><input type="hidden" name="action" value="send_command"><input type="hidden" name="command" value="UNLOCK"><button class="cmdBtn" type="submit">🔓 فتح القفل</button></form>
      <form method="post"><input type="hidden" name="action" value="send_command"><input type="hidden" name="command" value="PING"><button class="cmdBtn" type="submit">📡 فحص اتصال</button></form>
      <a class="cmdBtn" style="display:block;text-decoration:none;" href="qr.php?id=<?= (int)$device['id'] ?>">📷 QR التسجيل</a>
    </div>
  </div>

  <div class="card">
    <div class="secTitle">إدارة التطبيقات (فتح عن بعد / إخفاء / إظهار / مسح بيانات)</div>
    <form method="post" onsubmit="return confirmAppAction(this);">
      <input type="hidden" name="action" value="send_command">
      <select id="pkgSelect" style="width:100%;padding:11px;border-radius:10px;border:1.5px solid #ece7db;font-size:12.5px;font-family:'Cairo',system-ui,sans-serif;margin-bottom:8px;" onchange="document.getElementById('pkgCustom').value=this.value==='__other__'?'':this.value;">
        <?php foreach ($commonApps as $pkgId => $label): ?>
          <option value="<?= htmlspecialchars($pkgId) ?>"><?= htmlspecialchars($label) ?> (<?= htmlspecialchars($pkgId) ?>)</option>
        <?php endforeach; ?>
        <option value="__other__">تطبيق آخر (اكتب اسم الحزمة يدويًا)...</option>
      </select>
      <input type="text" id="pkgCustom" placeholder="أو اكتب اسم الحزمة مباشرة، مثل: com.whatsapp" style="width:100%;padding:11px;border-radius:10px;border:1.5px solid #ece7db;font-size:12.5px;font-family:'Cairo',system-ui,sans-serif;margin-bottom:10px;">
      <div class="btnGrid">
        <button class="cmdBtn" type="submit" name="command" value="OPEN_APP" onclick="syncPkg()" style="grid-column:span 2;border-color:#A7D7C5;">📲 فتح هذا التطبيق عن بعد الآن</button>
        <button class="cmdBtn" type="submit" name="command" value="HIDE_APP" onclick="syncPkg()">🙈 إخفاء</button>
        <button class="cmdBtn" type="submit" name="command" value="SHOW_APP" onclick="syncPkg()">👁 إظهار</button>
        <button class="cmdBtn danger" type="submit" name="command" value="WIPE_APP_DATA" onclick="syncPkg()" style="grid-column:span 2;">🗑 مسح بيانات هذا التطبيق</button>
      </div>
      <input type="hidden" name="package" id="pkgHidden" value="">
    </form>
  </div>
  <div class="card">
    <div class="secTitle">وضع Kiosk (قفل الجهاز بالكامل على التطبيقات المختارة بس)</div>
    <p style="font-size:11.5px;color:#8a7f6e;margin-bottom:10px;line-height:1.7;">
      لما تفعّله، الجهاز بيصير مقفول بالكامل — الفورمن بيشوف بس التطبيقات المحددة تحت على الهوم، وما بيوصل لأي شي تاني ولا للإعدادات، لين ترسل أمر "إلغاء" من هون. المكالمات والرسائل والكاميرا الأصلية للجهاز تُضاف تلقائياً من التطبيق نفسه (ما تحتاج تحددها هون).
    </p>
    <form method="post" onsubmit="return confirmKiosk(this);">
      <input type="hidden" name="action" value="send_command">
      <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:10px;">
        <?php foreach ($kioskApps as $pkgId => $label): ?>
          <label style="display:flex;align-items:center;gap:8px;font-size:12.5px;color:#1a0a10;">
            <input type="checkbox" class="kioskCheck" value="<?= htmlspecialchars($pkgId) ?>" checked>
            <?= htmlspecialchars($label) ?> (<?= htmlspecialchars($pkgId) ?>)
          </label>
        <?php endforeach; ?>
      </div>
      <input type="text" id="kioskCustom" placeholder="تطبيقات إضافية، مفصولة بفاصلة — مثلاً: com.mabani.foreman, com.android.chrome" style="width:100%;padding:11px;border-radius:10px;border:1.5px solid #ece7db;font-size:12.5px;font-family:'Cairo',system-ui,sans-serif;margin-bottom:10px;">
      <div class="btnGrid">
        <button class="cmdBtn" type="submit" name="command" value="LOCK_KIOSK" onclick="syncKioskPkg()">🔒 تفعيل القفل الكامل</button>
        <button class="cmdBtn danger" type="submit" name="command" value="UNLOCK_KIOSK" onclick="syncKioskPkg()">🔓 إلغاء القفل الكامل</button>
      </div>
      <input type="hidden" name="package" id="kioskPkgHidden" value="">
    </form>
  </div>
  <script>
    function syncKioskPkg(){
      var checked = Array.prototype.slice.call(document.querySelectorAll('.kioskCheck:checked')).map(function(el){ return el.value; });
      var custom = document.getElementById('kioskCustom').value.split(',').map(function(s){ return s.trim(); }).filter(function(s){ return s !== ''; });
      document.getElementById('kioskPkgHidden').value = checked.concat(custom).join(',');
    }
    function confirmKiosk(form){
      syncKioskPkg();
      var cmd = document.activeElement ? document.activeElement.value : '';
      if (cmd === 'LOCK_KIOSK') {
        var pkgs = document.getElementById('kioskPkgHidden').value;
        if (!pkgs) { alert('اختر تطبيق واحد على الأقل أولاً'); return false; }
        return confirm('متأكد؟ الجهاز رح يصير مقفول بالكامل على: ' + pkgs.split(',').join('، ') + ' — لين ترسل أمر إلغاء.');
      }
      return true;
    }
    function syncPkg(){
      var sel = document.getElementById('pkgSelect');
      var custom = document.getElementById('pkgCustom').value.trim();
      var val = custom !== '' ? custom : (sel.value !== '__other__' ? sel.value : '');
      document.getElementById('pkgHidden').value = val;
    }
    function confirmAppAction(form){
      syncPkg();
      var cmd = document.activeElement ? document.activeElement.value : '';
      if (cmd === 'WIPE_APP_DATA') return confirm('متأكد تبي تمسح بيانات هذا التطبيق من هالجهاز؟');
      return true;
    }
  </script>

  <?php if ($commands): ?>
    <div class="secTitle">آخر الأوامر</div>
    <?php foreach ($commands as $c):
      $s = $statusLabels[$c['status']] ?? $statusLabels['pending'];
      $pkgSuffix = '';
      if (!empty($c['payload'])) {
          $p = json_decode($c['payload'], true);
          if (!empty($p['package'])) $pkgSuffix = ' — ' . $p['package'];
          elseif (!empty($p['packages']) && is_array($p['packages'])) $pkgSuffix = ' — ' . implode(', ', $p['packages']);
      }
    ?>
      <div class="hRow">
        <div>
          <div class="hName"><?= htmlspecialchars(($cmdLabels[$c['command']] ?? $c['command']) . $pkgSuffix) ?></div>
          <div class="hTime"><?= htmlspecialchars(date('Y-m-d h:i A', strtotime($c['created_at']))) ?></div>
        </div>
        <span class="badge" style="color:<?= $s[1] ?>;background:<?= $s[2] ?>;"><?= $s[0] ?></span>
      </div>
    <?php endforeach; ?>
  <?php endif; ?>
</div>
</body>
</html>
