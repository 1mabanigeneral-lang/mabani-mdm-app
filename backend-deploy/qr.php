<?php
require_once __DIR__ . '/../config.php';
mdm_require_login();
$pdo = mdm_getDB();

$id = (int)($_GET['id'] ?? 0);
$st = $pdo->prepare("SELECT * FROM devices WHERE id = ? LIMIT 1");
$st->execute([$id]);
$device = $st->fetch();
if (!$device) { header('Location: devices.php'); exit(); }

$metaPath = __DIR__ . '/../download/app-meta.json';
$meta = null;
if (is_file($metaPath)) {
    $meta = json_decode(file_get_contents($metaPath), true);
}

$provisioningJson = null;
if ($meta && !empty($meta['checksum']) && !empty($device['enroll_token'])) {
    $payload = [
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME' => $meta['admin_receiver'],
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION' => $meta['download_url'],
        'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM' => $meta['checksum'],
        'android.app.extra.PROVISIONING_SKIP_ENCRYPTION' => false,
        'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED' => true,
        'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE' => [
            'enroll_token' => $device['enroll_token'],
        ],
    ];
    $provisioningJson = json_encode($payload, JSON_UNESCAPED_SLASHES);
}
?>
<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>QR التسجيل — <?= htmlspecialchars($device['device_name']) ?></title>
<link href="https://fonts.googleapis.com/css2?family=Cairo:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<script><?php
$qrLibPath = __DIR__ . '/qrcode.min.js';
if (is_file($qrLibPath)) { echo file_get_contents($qrLibPath); }
?></script>
<style>
*{box-sizing:border-box;margin:0;padding:0;font-family:'Cairo',system-ui,sans-serif}
body{background:#f4f1ea;min-height:100vh;}
.top{background:#3d0f1c;color:#fff;padding:18px 24px;display:flex;align-items:center;gap:12px;}
.top a.back{color:#fff;text-decoration:none;}
.top .brand{font-size:14.5px;font-weight:800;}
.wrap{max-width:460px;margin:0 auto;padding:24px 16px;text-align:center;}
.card{background:#fff;border-radius:14px;border:1px solid #ece7db;padding:22px;margin-bottom:16px;}
.dName{font-size:14.5px;font-weight:800;color:#1a0a10;margin-bottom:14px;}
#qrBox{display:flex;justify-content:center;margin-bottom:12px;}
.steps{text-align:right;font-size:12px;color:#4a4036;line-height:2;padding:0 6px;}
.steps b{color:#1a0a10;}
.warn{background:#FFFBEB;color:#B45309;border-radius:10px;padding:12px 14px;font-size:11.5px;text-align:right;margin-bottom:16px;}
.err{background:#FEF2F2;color:#B91C1C;border-radius:10px;padding:14px;font-size:12px;text-align:right;}
</style>
</head>
<body>
<div class="top">
  <a class="back" href="device.php?id=<?= (int)$device['id'] ?>">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5"></path><path d="M12 19l-7-7 7-7"></path></svg>
  </a>
  <div class="brand">QR تسجيل الجهاز</div>
</div>
<div class="wrap">
  <div class="card">
    <div class="dName"><?= htmlspecialchars($device['device_name']) ?></div>

    <?php if (!$provisioningJson): ?>
      <div class="err">
        لسا ما رفعنا ملف التطبيق (app.apk) وملف app-meta.json على السيرفر بمجلد
        <code>/opt/mdm/app/download/</code>. لازم نرفعهم أول (من نتيجة بناء GitHub Actions) عشان يظهر QR هون.
      </div>
    <?php else: ?>
      <div id="qrBox"></div>
      <script>
        QRCode.toCanvas(document.createElement('canvas'), <?= json_encode($provisioningJson) ?>, {width: 300}, function (err, canvas) {
          if (err) { document.getElementById('qrBox').innerText = 'خطأ بتوليد QR: ' + err; return; }
          document.getElementById('qrBox').appendChild(canvas);
        });
      </script>
    <?php endif; ?>
  </div>

  <div class="warn">⚠️ لازم يكون الجهاز "مفرمت حديثًا" (إعادة ضبط مصنع) ولسا ما دخلت عليه بأي حساب Google أو أي إعداد، وإلا ما راح يقبل مسح QR هذا.</div>

  <div class="card steps">
    <b>خطوات التسجيل على الجهاز:</b><br>
    1. فورمات الجهاز (إعادة ضبط المصنع).<br>
    2. بشاشة "مرحبًا" الأولى، دوس بنفس المكان بالشاشة 6 مرات متتالية.<br>
    3. اختر "مسح QR Code" وصوّر الرمز اللي فوق.<br>
    4. الجهاز بيحمّل التطبيق ويسجله تلقائيًا — خلص.
  </div>
</div>
</body>
</html>
