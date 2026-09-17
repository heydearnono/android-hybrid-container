/*
 * 验收页的入口:显环境、装两个人工按钮、开跑。
 *
 * 单独一个文件而不是内联在 index.html 里,是为了不依赖 `script-src 'unsafe-inline'`。
 * 断言不写在这里 —— 这里一条断言都没有,全在 cases.js。
 */
(function (global) {
  'use strict';

  var doc = global.document;
  var summary = doc.getElementById('summary');
  var env = doc.getElementById('env');
  var B = global.HybridBridge;

  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }

  /*
   * 把 origin 显在页面上,人一眼就能看出主文档承载在哪(§8.3 不变量 1)。
   * 显示的是**运行期真值**,不是仓库里写死的取值——仓库里不许出现 runtime host。
   */
  env.innerHTML = '<b>origin</b> ' + esc(global.location.origin) +
    '<br><b>path</b> ' + esc(global.location.pathname) +
    '<br><b>注入对象</b> ' + (B && B.available
      ? esc(B.nativeObjectName) + ' 已注入,契约版本 v' + esc(B.contractVersion)
      : '未注入');

  /*
   * 没有注入对象就不要跑用例。
   *
   * 跑了会得到一屏 BRIDGE_UNAVAILABLE,把「通道没建起来」这一个原因显示成几十条失败,
   * 真正的线索反而被埋掉。§4.4 第 1 条要求不静默降级,页面这边的对应动作就是
   * 明确报出「通道不可用」,而不是假装在验收。
   *
   * 这里仍然写出 __acceptance 且 failed=1:端侧自动化只认这一个对象,
   * 不能让「通道没建起来」表现成「还没跑完」。
   */
  if (!B || !B.available) {
    var name = (B && B.nativeObjectName) || '__hybridNative';
    summary.className = 'bad';
    summary.textContent = '通道不可用:没有注入 ' + name + '。用例一条都没跑。';
    global.__acceptance = {
      done: true, total: 1, passed: 0, failed: 1, manual: 0,
      cases: [{
        ref: '§4.4', title: 'bridge 通道可用', status: 'fail',
        detail: '注入对象 ' + name + ' 不存在。要么 origin 不在白名单里(§4.1),' +
          '要么注入层没装上(§4.3),要么页面不是经离线包链路打开的',
      }],
    };
    return;
  }

  doc.getElementById('btn-reload').onclick = function () { global.location.reload(); };

  doc.getElementById('btn-close').onclick = function () {
    var out = doc.getElementById('manual-detail');
    out.textContent = '';
    B.call('page.close').then(function () {
      // 正常情况下这行看不见:页面已经关了。看见了就说明 native 回了成功但没真关
      out.textContent = 'page.close 回了成功,但页面还在 —— 这是失败';
    }, function (err) {
      out.textContent = 'page.close 失败:[' + err.code + '] ' + err.message;
    });
  };

  global.Harness.run(doc.getElementById('results'), function (r) {
    summary.className = r.failed === 0 ? 'ok' : 'bad';
    summary.textContent = (r.failed === 0 ? '全过' : r.failed + ' 条失败') +
      ' · 共 ' + r.total + ' 条,通过 ' + r.passed + ' 条,其中 ' +
      r.manual + ' 条要人眼确认(不计入失败)';
  });
})(window);
