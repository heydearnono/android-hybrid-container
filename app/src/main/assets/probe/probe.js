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

  // 页面自己能判的 slug，按 pro 的固定顺序排（M2 六条；注入与导航那几条在 M3/M4 加）。
  var PAGE_SLUGS = ['origin', 'storage', 'subresource', 'intercept', 'escape', 'escape-encoded'];

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
    document.getElementById('env').textContent =
      'UA: ' + navigator.userAgent + ' · typeof localStorage: ' + typeof localStorage;
  }

  prepareRows();
  showEnvironment();
  checkOrigin();
  checkStorage();
  checkSubresource();
  checkIntercept();
  checkEscape('escape', '../outside/out-of-bounds.txt');
  checkEscape('escape-encoded', '%2e%2e%2foutside/out-of-bounds.txt');
})();
