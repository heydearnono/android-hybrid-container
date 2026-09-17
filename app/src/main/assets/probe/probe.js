/**
 * Crab 探针 · 页面侧
 *
 * 每条断言打一行 `CRAB-PROBE <slug> PASS|FAIL <细节>` 到 console，由 WebChromeClient 转 logcat、
 * 再由 scripts/probe.sh 回读。**slug 拼写与固定标记来自 pro 的取值表**，Kotlin 侧的 ProbeContract 是
 * 端内定义处，ProbeContractAlignmentTest 盯着这个文件与它逐字一致。
 *
 * .js 不在 Spotless / lint / 编译器的覆盖范围里，改这里没有任何自动化兜底，只有那条扫文本的单测。
 */
(function () {
  'use strict';

  // 与 Kotlin 侧 HostingOrigin.ORIGIN 逐字相等（单测盯着）。承载 origin 的定义处在 Kotlin，这里只是期望值。
  var EXPECTED_ORIGIN = 'https://and.crab.invalid';
  var MARKER_INTERCEPTED = 'INTERCEPTED';
  var MARKER_OUT_OF_BOUNDS = 'OUT_OF_BOUNDS';
  var PROMPT_INPUT = 'CRAB';

  // 第二页留标记的键。sessionStorage 不是 localStorage：理由写在 second.html 里（跨启动的旧标记会假绿）。
  var NAV_MARKER_KEY = 'crab.probe.nav';

  // 页面自己能判的 slug，按 pro 的固定顺序排。剩下四条（nav-cross-origin / nav-blank /
  // nav-system-scheme / nav-unknown-scheme）页面判不了——被拦下的那一跳页面什么都收不到，
  // 证据只在容器打的 CRAB-NAV 里，由 probe.sh 回读。
  var PAGE_SLUGS = [
    'origin',
    'storage',
    'subresource',
    'intercept',
    'escape',
    'escape-encoded',
    'inject-order',
    'inject-scope',
    'nav-same-origin',
    'nav-back',
    'dialog',
    'permission'
  ];

  // inject-scope 要等 iframe 里的脚本回话，超时就判 FAIL——不回话和「注入范围漏到子帧」表现不同，
  // 前者是 iframe 起不来，后者是回话说 object。两种都得留下证据。
  var SCOPE_TIMEOUT_MS = 3000;

  // 定位被拒是要拿到 error 回调，不是无限等——挂着与拒了在页面侧看起来一样，超时给它一个结论。
  var PERMISSION_TIMEOUT_MS = 5000;

  var rows = {};

  function prepareRows() {
    var tbody = document.getElementById('results');
    PAGE_SLUGS.forEach(function (slug) {
      var tr = document.createElement('tr');
      var slugCell = document.createElement('td');
      slugCell.className = 'slug';
      slugCell.textContent = slug;
      var verdictCell = document.createElement('td');
      verdictCell.className = 'verdict';
      verdictCell.textContent = '…';
      var detailCell = document.createElement('td');
      detailCell.className = 'detail';
      tr.appendChild(slugCell);
      tr.appendChild(verdictCell);
      tr.appendChild(detailCell);
      tbody.appendChild(tr);
      rows[slug] = { verdict: verdictCell, detail: detailCell };
    });
  }

  function report(slug, pass, detail) {
    var verdict = pass ? 'PASS' : 'FAIL';
    console.log('CRAB-PROBE ' + slug + ' ' + verdict + (detail ? ' ' + detail : ''));
    var row = rows[slug];
    if (row) {
      row.verdict.textContent = verdict;
      row.verdict.className = 'verdict ' + verdict;
      row.detail.textContent = detail || '';
    }
  }

  function checkOrigin() {
    report('origin', location.origin === EXPECTED_ORIGIN, location.origin);
  }

  function checkStorage() {
    try {
      var key = 'crab.probe.storage';
      localStorage.setItem(key, 'ok');
      var readBack = localStorage.getItem(key);
      localStorage.removeItem(key);
      report('storage', readBack === 'ok', 'typeof localStorage=' + typeof localStorage);
    } catch (error) {
      report('storage', false, String(error));
    }
  }

  // 这个文件在跑本身就是 .js 加载成功的证据，剩下要看的是同目录那张图。
  function checkSubresource() {
    var img = document.getElementById('subresource');
    function judge() {
      var ok = img.complete && img.naturalWidth > 0;
      report('subresource', ok, 'js=ok png=' + img.naturalWidth + 'x' + img.naturalHeight);
    }
    if (img.complete) {
      judge();
    } else {
      img.addEventListener('load', judge);
      img.addEventListener('error', judge);
    }
  }

  // 承载目录里不存在的文件：要的是容器自己回的 404 + 固定标记，而不是请求跑到网上去。
  function checkIntercept() {
    fetch('does-not-exist.txt', { cache: 'no-store' }).then(function (response) {
      return response.text().then(function (body) {
        var ok = response.status === 404 && body.indexOf(MARKER_INTERCEPTED) >= 0;
        report('intercept', ok, 'status=' + response.status + ' body=' + body.slice(0, 32));
      });
    }, function (error) {
      report('intercept', false, '请求失败: ' + error);
    });
  }

  // 读到 OUT_OF_BOUNDS 就是穿越成功 = FAIL。请求失败、404、内容不对都算 PASS。
  function checkEscape(slug, url) {
    fetch(url, { cache: 'no-store' }).then(function (response) {
      return response.text().then(function (body) {
        var leaked = response.ok && body.indexOf(MARKER_OUT_OF_BOUNDS) >= 0;
        report(slug, !leaked, 'status=' + response.status + ' body=' + body.slice(0, 32));
      });
    }, function (error) {
      report(slug, true, '请求失败（没读到越界文件）: ' + error);
    });
  }

  function showEnvironment() {
    var line = 'UA: ' + navigator.userAgent + ' · typeof localStorage: ' + typeof localStorage;
    document.getElementById('env').textContent = line;
    // 环境行打到 console，probe.sh 可以回读；前缀区别于 CRAB-PROBE，不参与十六行。
    console.log('CRAB-ENV ' + line);
  }

  // inject-order：入口页自身首行 <script> 记了快照，到这里比对。
  // 注入成功的表现是 __CRAB_SNAPSHOT__ 存在且 injected >= 1。
  function checkInjectOrder() {
    var snapshot = window.__CRAB_SNAPSHOT__;
    if (!snapshot) {
      report('inject-order', false, 'window.__CRAB_SNAPSHOT__=null（注入没跑或快照没记）');
      return;
    }
    var ok = snapshot.origin === EXPECTED_ORIGIN && snapshot.injected >= 1;
    report('inject-order', ok,
      'origin=' + snapshot.origin + ' injected=' + snapshot.injected);
  }

  // inject-scope：data: URL 的 iframe 里**不应该**有 __CRAB__。
  // 如果注入的 origin 规则漏掉了，子帧也会拿到 __CRAB__；页面靠 postMessage 问子帧。
  function checkInjectScope() {
    var iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    var timer;

    function onMessage(event) {
      clearTimeout(timer);
      window.removeEventListener('message', onMessage);
      // 子帧脚本用 postMessage 报 typeof window.__CRAB__。
      // undefined = PASS（注入没漏到子帧），object = FAIL。
      var typeofCrab = event.data && event.data.typeofCrab;
      var pass = typeofCrab === 'undefined';
      report('inject-scope', pass, 'typeof window.__CRAB__ in data:iframe = ' + typeofCrab);
    }
    window.addEventListener('message', onMessage);

    timer = setTimeout(function () {
      window.removeEventListener('message', onMessage);
      // data: iframe 可能加载不起来（某些内核不允许）。
      // 这时无法判定注入范围，FAIL + 留下退路标记：需要回 pro 商量换 sandbox+srcdoc。
      report('inject-scope', false,
        'data: iframe 超时未回话，可能需要换 sandbox+srcdoc 退路（需回 pro 商量）');
    }, SCOPE_TIMEOUT_MS);

    // data: URL 在承载 origin 之外，注入不该漏过去。
    iframe.src =
      'data:text/html;charset=utf-8,' +
      encodeURIComponent(
        '<script>' +
        'parent.postMessage({typeofCrab: typeof window.__CRAB__}, "*");' +
        '</' + 'script>'
      );
    document.body.appendChild(iframe);
  }

  // nav-same-origin / nav-back：第二页留在 sessionStorage 里的标记是唯一的证据。
  // 这段脚本正在入口页上跑，标记又在，说明「去过第二页而且回来了」——页面上没有回链，
  // 唯一的回法是系统返回键，所以这两条一起判。
  function checkNavMarkers() {
    var raw = null;
    try {
      raw = sessionStorage.getItem(NAV_MARKER_KEY);
    } catch (error) {
      report('nav-same-origin', false, 'sessionStorage 读不了: ' + error);
      report('nav-back', false, 'sessionStorage 读不了: ' + error);
      return;
    }
    if (!raw) {
      // 人工步骤没做也是这条路，所以细节里点名步骤，别让人以为是容器拦错了。
      report('nav-same-origin', false, '还没去过第二页（人工步骤：点「跳到第二页」）');
      report('nav-back', false, '还没回过入口页（人工步骤：第二页上按系统返回键）');
      return;
    }
    var marker = null;
    try {
      marker = JSON.parse(raw);
    } catch (error) {
      marker = null;
    }
    if (!marker) {
      report('nav-same-origin', false, '标记读不出来: ' + raw);
      report('nav-back', false, '标记读不出来: ' + raw);
      return;
    }
    report('nav-same-origin', marker.origin === EXPECTED_ORIGIN,
      'second.html 的 origin=' + marker.origin);
    var onEntry = location.pathname.indexOf('index.html') >= 0;
    report('nav-back', onEntry, 'now=' + location.pathname + ' second-at=' + marker.at);
  }

  // dialog：一个按钮依次弹三种（pro 定的形状）。
  // alert 那一格判的不是文案，而是**脚本恢复执行了**——JsResult 没被回一次的话，
  // window.alert() 永远不返回，后面的 confirm / prompt 压根不会弹，表现是这条断言停在「…」。
  var dialogs = { alert: false, confirm: null, prompt: null };

  function reportDialog() {
    var ok = dialogs.alert === true && dialogs.confirm === true && dialogs.prompt === PROMPT_INPUT;
    report('dialog', ok,
      'alert=' + dialogs.alert + ' confirm=' + dialogs.confirm + ' prompt=' + dialogs.prompt);
  }

  // permission：容器一律拒绝，所以**拿到失败回调**才是 PASS；拿到坐标是漏了，一直挂着也是没做到
  // （pro 要的是「给页面一个明确的失败，不许挂着」，所以超时算 FAIL 而不是等下去）。
  //
  // 只按定位这一条：相机/麦克风走 getUserMedia，那两样 pro 明写只剩代码走查。
  var geolocationResult = null;

  function reportPermission() {
    var denied = typeof geolocationResult === 'string' && geolocationResult.indexOf('denied') === 0;
    report('permission', denied, 'geolocation=' + geolocationResult);
  }

  function bindManualButtons() {
    // 脚本发起的开窗：前置是 setJavaScriptCanOpenWindowsAutomatically，
    // 它默认关着，关着的时候这一句被静默拦掉，onCreateWindow 压根不回调、日志也就没有。
    document.getElementById('btn-window-open').addEventListener('click', function () {
      window.open('second.html', '_blank');
    });
    document.getElementById('btn-dialogs').addEventListener('click', function () {
      window.alert('Crab alert');
      dialogs.alert = true;
      dialogs.confirm = window.confirm('点「确定」');
      dialogs.prompt = window.prompt('输入 ' + PROMPT_INPUT, '');
      reportDialog();
    });
    document.getElementById('btn-geo').addEventListener('click', function () {
      if (!navigator.geolocation) {
        geolocationResult = 'no-api';
        reportPermission();
        return;
      }
      navigator.geolocation.getCurrentPosition(function () {
        geolocationResult = 'granted';
        reportPermission();
      }, function (error) {
        geolocationResult = 'denied:' + error.code;
        reportPermission();
      }, { timeout: PERMISSION_TIMEOUT_MS });
    });
    // 「切后台媒体停播」不进十六行（pro 要求人工看一次），但看得见需要有东西在响。
    // play() 返回 Promise：有声媒体要求手势这一项配错了它会 reject，那种失败在页面上看不出来，
    // 所以把 reject 打进 CRAB-ENV——否则「没声音」会被当成「后台停播生效了」。
    document.getElementById('btn-tone').addEventListener('click', function () {
      var tone = document.getElementById('tone');
      var started = tone.play();
      if (started && typeof started.catch === 'function') {
        started.catch(function (error) {
          console.log('CRAB-ENV tone play rejected: ' + error);
        });
      }
    });
  }

  // 计时器的观察面：每秒一跳，同时打进 CRAB-ENV。切后台十秒回来，时间戳该有一段断口——
  // 这样「计时器停没停」在 logcat 里也能回读，不必只靠眼睛盯着屏幕上那个数。
  function startTicker() {
    var tick = 0;
    var label = document.getElementById('tick');
    setInterval(function () {
      tick += 1;
      var line = 'tick ' + tick + ' ' + new Date().toISOString();
      label.textContent = line;
      console.log('CRAB-ENV ' + line);
    }, 1000);
  }

  prepareRows();
  showEnvironment();
  checkOrigin();
  checkStorage();
  checkSubresource();
  checkIntercept();
  checkEscape('escape', '../outside/out-of-bounds.txt');
  checkEscape('escape-encoded', '%2e%2e%2foutside/out-of-bounds.txt');
  checkInjectOrder();
  checkInjectScope();
  checkNavMarkers();
  bindManualButtons();
  startTicker();

  // 返回时页面若是从历史缓存里恢复的，上面那串不会再跑一遍——两条导航断言会停在返回前的结论。
  // persisted 为真才补判：正常加载时 pageshow 也会来，重复判会把细节里的时间戳搅乱。
  window.addEventListener('pageshow', function (event) {
    if (event.persisted) {
      checkNavMarkers();
    }
  });
})();
