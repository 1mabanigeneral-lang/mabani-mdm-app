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
    $allowed = ['LOCK', 'UNLOCK', 'PING', 'WIPE_APP_DATA', 'HIDE_APP', 'SHOW_APP'];
    $pkg = trim($_POST['package'] ?? '');
    $needsPkg = in_array($cmd, ['WIPE_APP_DATA', 'HIDE_APP', 'SHOW_APP'], true);
    if (in_array($cmd, $allowed, true) && (!$needsPkg || $pkg !== '')) {
        $payload = $needsPkg ? json_encode(['package' => $pkg]) : null;
        $st2 = $pdo->prepare("INSERT INTO device_commands (device_id, command, payload, status) VALUES (?, ?, ?, 'pending')");
        $st2->execute([$id, $cmd, $payload]);
        $sent = $cmd . ($needsPkg ? " ($pkg)" : '');
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
];

$commonApps = [
    'com.whatsapp' => 'واتساب',
    'com.android.vending' => 'Google Play',
    'com.android.chrome' => 'Chrome',
    'com.facebook.katana' => 'فيسبوك',
    'com.instagram.android' => 'إنستغرام',
    'com.zhiliaoapp.musically' => 'تيك توك',
    'com.google.android.youtube' => 'يوتيوب',
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
    <div class="secTitle">إدارة التطبيقات (إخفاء / إظهار / مسح بيانات)</div>
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
        <button class="cmdBtn" type="submit" name="command" value="HIDE_APP" onclick="syncPkg()">🙈 إخفاء</button>
        <button class="cmdBtn" type="submit" name="command" value="SHOW_APP" onclick="syncPkg()">👁 إظهار</button>
        <button class="cmdBtn danger" type="submit" name="command" value="WIPE_APP_DATA" onclick="syncPkg()" style="grid-column:span 2;">🗑 مسح بيانات هذا التطبيق</button>
      </div>
      <input type="hidden" name="package" id="pkgHidden" value="">
    </form>
  </div>
  <script>
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
