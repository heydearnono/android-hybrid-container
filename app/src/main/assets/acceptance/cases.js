/*
 * 三端一致性验收断言。
 *
 * 这个文件是[README 完成标准](../README.md)第 3 条的全部内容,也是第 2 条的载体:
 * 它必须**经离线包链路**被加载(入口分发 → manifest → route → entry → resource),
 * 不是从 assets 直读。
 *
 * 三条硬规矩:
 *   1. **一份 JS,三端各跑一次。** 页面里不许出现任何按端分叉的代码。
 *      唯一允许因端而异的是 device.info 的 platform 取值,以及 extra 里的东西。
 *   2. **不许出现厂内取值。** 没有 scheme 名、没有 runtime host、没有示例包名——
 *      要引用同包资源就用相对路径(见 CLAUDE.md)。
 *   3. 断言失败就是失败,不豁免。豁免要写进 PROTOCOL,不是写进这里。
 *
 * 覆盖不到的两块,写在文件末尾:page.close(会关掉页面)与 §8.3 第 2 条不变量
 * (「不发真实请求」从页面里看不见)。
 */
(function (global) {
  'use strict';

  var T = global.Harness;
  var B = global.HybridBridge;
  var a = T.assert;

  /** PROTOCOL §3:九个必须级能力。可选级为空,所以这也是完整清单 */
  var MANDATORY_METHODS = [
    'bridge.capabilities',
    'device.info',
    'ui.toast',
    'page.close',
    'page.setTitle',
    'router.open',
    'storage.get',
    'storage.set',
    'storage.remove',
  ];

  /** PROTOCOL §3:三端各自硬编码自己那个值,这是跨端代码分辨 native 的唯一锚点 */
  var PLATFORMS = ['android', 'ios', 'harmony'];

  var DEVICE_REQUIRED = {
    platform: 'string',
    osVersion: 'string',
    model: 'string',
    appVersionName: 'string',
    appVersionCode: 'number',
    locale: 'string',
  };

  /** PROTOCOL §3 那条继承下来的硬约束:device.info 不得返回可用于追踪用户的标识 */
  var TRACKING_KEYS = [
    'androidId', 'android_id', 'imei', 'mac', 'macAddress', 'adId', 'advertisingId',
    'idfa', 'idfv', 'oaid', 'serial', 'serialNumber',
  ];

  var KEY = 'acceptance.probe';   // storage 用的 key,一眼看得出是验收造的

  /*
   * 裸报文通道。
   *
   * §1 的解码容错(非法 JSON、缺字段、在途重复 id)没法用 bridge.js 测——bridge.js 的
   * 职责就是永远发出格式正确的报文。所以这里绕过它直接跟注入对象说话,
   * 并且接管一次 onmessage:先看是不是我们等的裸回包,不是就交回 bridge.js。
   */
  var rawWaiters = {};
  var unclaimed = [];
  var native = global.__hybridNative;

  if (native) {
    var bridgeOnMessage = native.onmessage;
    native.onmessage = function (event) {
      var msg = null;
      try {
        msg = JSON.parse(event.data);
      } catch (e) {
        // 解不开就不是给我们的,原样交回去
      }
      if (msg && msg.id !== undefined && rawWaiters[msg.id]) {
        var waiter = rawWaiters[msg.id];
        delete rawWaiters[msg.id];
        waiter(msg);
        return;
      }
      // 没人认领的回包。「整条丢弃、不回包」那条要靠它判——断言的是「什么都没来」
      if (msg && Object.prototype.hasOwnProperty.call(msg, 'ok')) unclaimed.push(msg);
      if (bridgeOnMessage) bridgeOnMessage.call(native, event);
    };
  }

  /**
   * 发一条裸报文,等它的回包。
   *
   * @param id       期望 native 原样回传的 id;传 null 表示这条报文不带可抠出的 id
   * @param payload  要发的**字符串**,故意不经 JSON.stringify
   * @param waitMs   等多久。id 为 null 时,等满即视为「没回包」
   * @return Promise<回包对象 | null>
   */
  function raw(id, payload, waitMs) {
    return new Promise(function (resolve) {
      var timer = global.setTimeout(function () {
        if (id !== null) delete rawWaiters[id];
        resolve(null);
      }, waitMs || 2000);
      if (id !== null) {
        rawWaiters[id] = function (msg) {
          global.clearTimeout(timer);
          resolve(msg);
        };
      }
      native.postMessage(payload);
    });
  }

  function expectRaw(id, payload, code, what) {
    return raw(id, payload).then(function (msg) {
      a.ok(msg, what + ':没等到回包');
      a.eq(msg.ok, false, what + ':期望 ok=false');
      a.eq(msg.error && msg.error.code, code, what);
      a.eq(msg.id, id, what + ':id 必须原样回传');
      return msg;
    });
  }

  // ── §1 报文格式 ──────────────────────────────────────────────────────────

  T.test('§1', '握手回包形状是 {v, methods}', function () {
    return a.succeeds(B.ready, 'bridge.capabilities').then(function (data) {
      a.eq(data.v, B.contractVersion, 'v 必须等于本 SDK 的契约版本');
      a.ok(Array.isArray(data.methods), 'methods 必须是数组');
    });
  });

  T.test('§1', '响应原样回传请求里的 id', function () {
    return raw('probe-echo', JSON.stringify({
      v: 1, id: 'probe-echo', method: 'device.info', params: {},
    })).then(function (msg) {
      a.ok(msg, '没等到回包');
      a.eq(msg.id, 'probe-echo', 'id');
      a.eq(msg.v, 1, '响应要回传 v');
      a.eq(msg.ok, true, 'ok');
    });
  });

  T.test('§1', '无返回值的能力回 null,不是 {}', function () {
    return a.succeeds(B.call('storage.remove', { key: KEY }), 'storage.remove')
      .then(function (data) { a.nullish(data, 'storage.remove 的 data'); });
  });

  T.test('§1', '成功响应不带 error 键', function () {
    return raw('probe-noerr', JSON.stringify({
      v: 1, id: 'probe-noerr', method: 'device.info', params: {},
    })).then(function (msg) {
      a.ok(msg, '没等到回包');
      a.ok(!Object.prototype.hasOwnProperty.call(msg, 'error'),
        '成功响应里 error 键必须省略,不许输出 "error": null');
    });
  });

  T.test('§1', '多余字段忽略,不报错', function () {
    return raw('probe-extra', JSON.stringify({
      v: 1, id: 'probe-extra', method: 'device.info', params: {}, whatIsThis: 'ignore me',
    })).then(function (msg) {
      a.ok(msg, '没等到回包');
      a.eq(msg.ok, true, '多余字段应被忽略');
    });
  });

  T.test('§1', '缺 method → BAD_REQUEST', function () {
    return expectRaw('probe-nomethod',
      JSON.stringify({ v: 1, id: 'probe-nomethod', params: {} }),
      'BAD_REQUEST', '缺 method');
  });

  T.test('§1', '缺 v → BAD_REQUEST', function () {
    return expectRaw('probe-nov',
      JSON.stringify({ id: 'probe-nov', method: 'device.info', params: {} }),
      'BAD_REQUEST', '缺 v');
  });

  T.test('§2', 'v 不是本端支持的版本 → UNSUPPORTED_VERSION', function () {
    return expectRaw('probe-badv',
      JSON.stringify({ v: 9999, id: 'probe-badv', method: 'device.info', params: {} }),
      'UNSUPPORTED_VERSION', 'v=9999');
  });

  T.test('§1', '非法 JSON 且抠不出 id → 整条丢弃,不回包', function () {
    unclaimed.length = 0;
    return raw(null, '{ 这不是 JSON', 1500).then(function () {
      a.eq(unclaimed.length, 0,
        '不该有任何回包,实际收到 ' + unclaimed.length + ' 条');
    });
  });

  T.test('§1', '报文解不开但能抠出字符串 id → 仍回 BAD_REQUEST', function () {
    // 合法 JSON、id 是字符串,但 params 的类型整个错了,解不成请求对象
    return expectRaw('probe-unparsable',
      '{"v":1,"id":"probe-unparsable","method":"storage.get","params":"应该是对象"}',
      'BAD_REQUEST', 'params 类型错');
  });

  T.test('§1', '在途重复的 id → 至少一条回 BAD_REQUEST,不许静默覆盖', function () {
    var id = 'probe-dup';
    var seen = [];
    unclaimed.length = 0;
    rawWaiters[id] = function (msg) { seen.push(msg); };
    var payload = JSON.stringify({ v: 1, id: id, method: 'device.info', params: {} });
    native.postMessage(payload);
    native.postMessage(payload);
    return new Promise(function (resolve) { global.setTimeout(resolve, 1500); }).then(function () {
      delete rawWaiters[id];
      var all = seen.concat(unclaimed.filter(function (m) { return m.id === id; }));
      a.ok(all.length >= 1, '一条回包都没有');
      var bad = all.filter(function (m) {
        return m.ok === false && m.error && m.error.code === 'BAD_REQUEST';
      });
      a.ok(bad.length >= 1,
        '两条同 id 的在途请求里至少要有一条 BAD_REQUEST,实际都成功了');
    });
  });

  T.test('§1', '类型不做隐式转换:ui.toast 的 text 传数字 → INVALID_PARAMS', function () {
    return a.failsWith(B.call('ui.toast', { text: 42 }), 'INVALID_PARAMS', 'text=42');
  });

  // ── §2 错误码与优先级 ────────────────────────────────────────────────────

  /*
   * 这是整份验收里最要紧的一条,而且它的形式和直觉不同。
   *
   * §4.1 的授权粒度是 (origin, 能力集合)。本页面这个 origin 被授权的是那九个能力,
   * 那么一个**根本不存在**的能力名同样不在它的集合里——于是先查授权就会先撞
   * PERMISSION_DENIED,永远走不到「注册表里有没有」那一步。
   *
   * 所以从页面里能断言的是:**METHOD_NOT_FOUND 不许漏给这个 origin。**
   * 两个不同的不存在的名字必须回同一个 code,一个字都不许透露 native 有什么。
   *
   * 更强的那半条——「已注册但未授权」与「未注册」对同一个 origin 不可区分——
   * 页面构造不出来:它需要第二个 origin,或者一个故意只授权半份能力的白名单。
   * 那半条归端侧单测,三端各自补,本页面判不了。
   */
  T.test('§2', '不存在的能力回 PERMISSION_DENIED,不是 METHOD_NOT_FOUND', function () {
    return a.failsWith(B.call('does.not.exist'), 'PERMISSION_DENIED', '不存在的能力');
  });

  T.test('§2', '两个不同的不存在的能力回同一个 code(防枚举)', function () {
    return Promise.all([
      B.call('nope.one').catch(function (e) { return e.code; }),
      B.call('nope.two.deeper').catch(function (e) { return e.code; }),
    ]).then(function (codes) {
      a.eq(codes[0], codes[1], '两个不存在的能力必须回同一个 code');
      a.eq(codes[0], 'PERMISSION_DENIED', 'code');
    });
  });

  // ── §3 能力清单 ──────────────────────────────────────────────────────────

  T.test('§3', 'bridge.capabilities 报的能力集恰好等于九个必须级', function () {
    return a.succeeds(B.ready, 'bridge.capabilities').then(function (data) {
      var got = data.methods.slice().sort();
      var want = MANDATORY_METHODS.slice().sort();
      a.eq(got.join(','), want.join(','),
        '可选级为空,所以这里必须一个不多一个不少');
    });
  });

  T.test('§3', '九个能力逐个可调用(不是只在清单里报个名)', function () {
    /*
     * 不含两个:
     *   - page.close  会关掉页面,只能人工点,见文件末尾
     *   - router.open 会把一个原生页压在验收页上面,放在最后单独跑
     * 其余七个的语义各自有单测,这一条只验「报了名就真的能进 handler」。
     * 并发发出,因为这里只看有没有报错,不看 storage 的值。
     */
    return a.succeeds(B.ready, 'bridge.capabilities').then(function () {
      return Promise.all(MANDATORY_METHODS.filter(function (m) {
        return m !== 'page.close' && m !== 'router.open';
      }).map(function (m) {
        var params = { 'ui.toast': { text: 'probe' }, 'page.setTitle': { title: '验收中' },
          'storage.get': { key: KEY }, 'storage.set': { key: KEY, value: 'probe' },
          'storage.remove': { key: KEY } }[m] || {};
        return B.call(m, params).then(function () { return null; },
          function (e) { return m + ':' + e.code; });
      }));
    }).then(function (fails) {
      var bad = fails.filter(Boolean);
      a.eq(bad.length, 0, '这些必须级能力调不通:' + bad.join('、'));
    });
  });

  T.test('§3', 'device.info 六个必须字段齐全且类型对', function () {
    return a.succeeds(B.call('device.info'), 'device.info').then(function (d) {
      Object.keys(DEVICE_REQUIRED).forEach(function (k) {
        a.type(d[k], DEVICE_REQUIRED[k], 'device.info.' + k);
      });
    });
  });

  T.test('§3', 'device.info.platform 是三端各自硬编码的那个值', function () {
    return a.succeeds(B.call('device.info'), 'device.info').then(function (d) {
      a.ok(PLATFORMS.indexOf(d.platform) >= 0,
        'platform 必须是 ' + PLATFORMS.join(' / ') + ' 之一,实际 ' + d.platform);
    });
  });

  T.test('§3', 'device.info 顶层不许有平台专有字段(只能进 extra)', function () {
    return a.succeeds(B.call('device.info'), 'device.info').then(function (d) {
      var extraneous = Object.keys(d).filter(function (k) {
        return !Object.prototype.hasOwnProperty.call(DEVICE_REQUIRED, k) && k !== 'extra';
      });
      a.eq(extraneous.length, 0,
        '顶层多出这些字段,按 §6.5 应移入 extra:' + extraneous.join('、'));
    });
  });

  T.test('§3', 'device.info 不返回可追踪用户的标识(含 extra 里)', function () {
    return a.succeeds(B.call('device.info'), 'device.info').then(function (d) {
      var keys = Object.keys(d).concat(Object.keys(d.extra || {}));
      var hit = keys.filter(function (k) {
        return TRACKING_KEYS.some(function (t) { return t.toLowerCase() === k.toLowerCase(); });
      });
      a.eq(hit.length, 0, '出现了追踪标识:' + hit.join('、'));
    });
  });

  /*
   * 「必填非空白」与「必填但允许空串」是两种不同校验(§3)。这四条是它们的分界线,
   * 也是最容易在某端被写成同一个 if 的地方——写成同一个,空标题就清不掉了。
   */
  T.test('§3', 'ui.toast:text 缺失 / 空串 / 全空白都 → INVALID_PARAMS', function () {
    return Promise.all([
      a.failsWith(B.call('ui.toast', {}), 'INVALID_PARAMS', 'text 缺失'),
      a.failsWith(B.call('ui.toast', { text: '' }), 'INVALID_PARAMS', 'text 空串'),
      a.failsWith(B.call('ui.toast', { text: '   ' }), 'INVALID_PARAMS', 'text 全空白'),
    ]);
  });

  T.test('§3', 'ui.toast:long 是可选的,不传也不报错', function () {
    return a.succeeds(B.call('ui.toast', { text: '不传 long' }), 'ui.toast 省略 long');
  });

  T.test('§3', 'page.setTitle:title 缺失 → INVALID_PARAMS', function () {
    return a.failsWith(B.call('page.setTitle', {}), 'INVALID_PARAMS', 'title 缺失');
  });

  T.test('§3', 'page.setTitle:空串合法(用于清空标题)', function () {
    return a.succeeds(B.call('page.setTitle', { title: '' }), 'title 空串')
      .then(function () {
        // 清完再写回去,后面的用例还要靠标题看自己在哪一端
        return a.succeeds(B.call('page.setTitle', { title: '三端一致性验收' }), '写回标题');
      });
  });

  T.test('§3', 'storage:set → get 拿到同一个值', function () {
    var value = 'probe-' + Date.now();
    return a.succeeds(B.call('storage.set', { key: KEY, value: value }), 'storage.set')
      .then(function () { return a.succeeds(B.call('storage.get', { key: KEY }), 'storage.get'); })
      .then(function (data) { a.eq(data.value, value, '读回的值'); });
  });

  T.test('§3', 'storage.set:value 允许空串,且读回来是空串不是 null', function () {
    return a.succeeds(B.call('storage.set', { key: KEY, value: '' }), 'value 空串')
      .then(function () { return a.succeeds(B.call('storage.get', { key: KEY }), 'storage.get'); })
      .then(function (data) { a.eq(data.value, '', '空串必须能存能取,不许退化成 null'); });
  });

  T.test('§3', 'storage.get:读不到的 key 回 {value: null},不是报错', function () {
    return a.succeeds(B.call('storage.get', { key: 'acceptance.never.written' }), 'storage.get')
      .then(function (data) {
        a.ok(data && typeof data === 'object', '要有一个 data 对象');
        a.eq(data.value, null, '读不到时 value');
      });
  });

  T.test('§3', 'storage.remove:删掉之后 get 回 null', function () {
    return a.succeeds(B.call('storage.set', { key: KEY, value: '待删' }), 'storage.set')
      .then(function () { return a.succeeds(B.call('storage.remove', { key: KEY }), 'storage.remove'); })
      .then(function () { return a.succeeds(B.call('storage.get', { key: KEY }), 'storage.get'); })
      .then(function (data) { a.eq(data.value, null, '删除后 value'); });
  });

  T.test('§3', 'storage.remove:删不存在的 key 也算成功(对齐 localStorage)', function () {
    return a.succeeds(B.call('storage.remove', { key: 'acceptance.never.written' }),
      'remove 不存在的 key');
  });

  T.test('§3', 'storage 三个能力的 key 都是必填非空白', function () {
    return Promise.all([
      a.failsWith(B.call('storage.get', {}), 'INVALID_PARAMS', 'get 缺 key'),
      a.failsWith(B.call('storage.get', { key: '  ' }), 'INVALID_PARAMS', 'get 空白 key'),
      a.failsWith(B.call('storage.set', { value: 'x' }), 'INVALID_PARAMS', 'set 缺 key'),
      a.failsWith(B.call('storage.set', { key: KEY }), 'INVALID_PARAMS', 'set 缺 value'),
      a.failsWith(B.call('storage.remove', {}), 'INVALID_PARAMS', 'remove 缺 key'),
    ]);
  });

  T.test('§3', 'router.open:route 缺失 / 空白 → INVALID_PARAMS', function () {
    return Promise.all([
      a.failsWith(B.call('router.open', {}), 'INVALID_PARAMS', 'route 缺失'),
      a.failsWith(B.call('router.open', { route: '  ' }), 'INVALID_PARAMS', 'route 空白'),
    ]);
  });

  /*
   * §3:router.open 只认白名单里的路由名,不做 URL / scheme 匹配。
   * 所以下面这三个都必须是 NOT_FOUND ——**尤其是后两个**:
   * 一个端要是拿 route 去拼 URL 或者当 deeplink 解,它就成了一个从 H5 打开任意
   * 原生页 / 任意站点的通道,而白名单形同不存在。
   */
  T.test('§3', 'router.open:不在白名单里的路由名 → NOT_FOUND', function () {
    return a.failsWith(B.call('router.open', { route: 'no.such.route' }),
      'NOT_FOUND', '不存在的路由');
  });

  T.test('§3', 'router.open:传 URL 或 scheme 不许被当成路由 → NOT_FOUND', function () {
    return Promise.all([
      a.failsWith(B.call('router.open', { route: 'https://example.invalid/anything' }),
        'NOT_FOUND', '传 https URL'),
      a.failsWith(B.call('router.open', { route: 'probe://../../etc/passwd' }),
        'NOT_FOUND', '传 scheme 形状的串'),
    ]);
  });

  // ── §4 origin 与安全 ────────────────────────────────────────────────────

  /*
   * §4.4 第 2 条:非主帧消息直接丢弃。
   *
   * 这一条的攻击面不直观:被授权的 origin 完全可以用 iframe 套一个同源页面,
   * 而那个页面未必是我们放进包里的那份。srcdoc 的 origin 继承父页,所以它正是
   * 「同源但不是我们那份」的最小构造。
   *
   * 两种通过方式都算对:注入层压根没给这个 frame 注入对象,或者注入了但分发层丢掉它的消息。
   * 不通过只有一种:iframe 里拿到了回包。
   */
  T.test('§4.4', 'iframe(同源)里的 bridge 请求拿不到回包', function () {
    return new Promise(function (resolve) {
      var iframe = global.document.createElement('iframe');
      iframe.setAttribute('sandbox', 'allow-scripts allow-same-origin');
      iframe.style.display = 'none';
      iframe.srcdoc =
        '<script>(function(){' +
        'var n = window.__hybridNative;' +
        'function say(s){ parent.postMessage("acceptance-iframe:" + s, "*"); }' +
        'if (!n) { say("no-inject"); return; }' +
        'try { n.onmessage = function(){ say("got-reply"); };' +
        'n.postMessage(JSON.stringify({v:1,id:"iframe-probe",method:"device.info",params:{}}));' +
        '} catch (e) { say("threw"); }' +
        'setTimeout(function(){ say("no-reply"); }, 1200);' +
        '}())<\/script>';
      var result = null;
      function onMsg(e) {
        var d = String(e.data || '');
        if (d.indexOf('acceptance-iframe:') !== 0) return;
        if (result === null || d === 'acceptance-iframe:got-reply') {
          result = d.slice('acceptance-iframe:'.length);
        }
      }
      global.addEventListener('message', onMsg);
      global.document.body.appendChild(iframe);
      global.setTimeout(function () {
        global.removeEventListener('message', onMsg);
        if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
        resolve(result);
      }, 2200);
    }).then(function (result) {
      a.ok(result !== null, 'iframe 没报告结果,它的脚本可能没跑起来');
      a.ok(result !== 'got-reply', 'iframe 里拿到了回包 —— 非主帧消息必须丢弃');
    });
  });

  T.test('§4.4', '非字符串消息被丢弃,不做类型转换', function () {
    unclaimed.length = 0;
    try {
      // 故意不 JSON.stringify。这条报文若被「顺手转成字符串」再解析,就等于放弃了类型闸门
      native.postMessage({ v: 1, id: 'probe-nonstring', method: 'device.info', params: {} });
    } catch (e) {
      return;   // 连收都不收,更好
    }
    return new Promise(function (resolve) { global.setTimeout(resolve, 1500); }).then(function () {
      var mine = unclaimed.filter(function (m) { return m.id === 'probe-nonstring'; });
      a.eq(mine.length, 0, '非字符串消息不该被处理,实际回了包');
    });
  });

  // ── §8.3 承载方案的三条不变量 ────────────────────────────────────────────

  /*
   * 这三条是 fixture 覆盖不到的部分(§8.5 末句):fixture 只验 resolver 的输入输出,
   * 「装好之后这个 origin 长什么样」它不管。
   *
   * 底下用的全是**相对路径**——这个文件不许出现 runtime host,而相对路径正好也是
   * 不变量 1 的探针:同源才解析得到同一个包里的兄弟文件。
   */
  T.test('§8.3', '不变量 1:origin 不是 opaque,storage 语义可用', function () {
    a.ok(global.location.origin && global.location.origin !== 'null',
      'location.origin 是 opaque —— §4 白名单与页面 storage 会同时失效,实际 ' +
      global.location.origin);
    // localStorage 是 H5 自己的 storage,与 bridge 的 storage.* 是两回事。
    // opaque origin 下这里会抛 SecurityError,而那正是不变量 1 要防的后果之一
    global.localStorage.setItem(KEY, 'probe');
    a.eq(global.localStorage.getItem(KEY), 'probe', 'localStorage 读回');
    global.localStorage.removeItem(KEY);
  });

  T.test('§8.3', '不变量 1:同包的兄弟文件用相对路径读得到', function () {
    return global.fetch('./bridge.js').then(function (res) {
      a.ok(res.ok, '读同包的 bridge.js 应当成功,实际 HTTP ' + res.status);
      return res.text();
    }).then(function (text) {
      a.ok(text.indexOf('HybridBridge') >= 0, '读到的内容不像 bridge.js');
    });
  });

  T.test('§8.3', '不变量 3:manifest 没声明的文件读不到', function () {
    return global.fetch('./not-declared-in-manifest.txt').then(function (res) {
      a.ok(!res.ok, '未声明的文件竟然读到了(HTTP ' + res.status + ')');
    }, function () {
      // 拦截层直接拒绝也算通过
    });
  });

  T.test('§8.3', '不变量 3:.. 穿越读不到包外(含编码过的 ..)', function () {
    var probes = [
      '../../../../etc/passwd',
      // 浏览器不会规范化 %2e%2e%2f,所以这一条测的是**端侧**先解码再拼路径的那个 bug
      './%2e%2e%2f%2e%2e%2fetc/passwd',
    ];
    return Promise.all(probes.map(function (p) {
      return global.fetch(p).then(function (res) {
        return res.ok ? p + '(HTTP ' + res.status + ')' : null;
      }, function () { return null; });
    })).then(function (leaks) {
      var bad = leaks.filter(Boolean);
      a.eq(bad.length, 0, '这些路径读到了包外的东西:' + bad.join('、'));
    });
  });

  // ── 要人眼看一下的两条 ──────────────────────────────────────────────────

  /*
   * 这两条用 T.manual 注册:调用本身是可判的(不报错),**看见了什么**判不了。
   * 所以它们照样跑、照样出错就红,但不计入 failed —— 自动化没有眼睛。
   */
  T.manual('§3', 'ui.toast:屏幕上应当出现「验收:toast 可见」', function () {
    return a.succeeds(B.call('ui.toast', { text: '验收:toast 可见', long: true }), 'ui.toast');
  });

  T.manual('§3', 'page.setTitle:标题栏应当变成「验收:标题已改」', function () {
    return a.succeeds(B.call('page.setTitle', { title: '验收:标题已改' }), 'page.setTitle');
  });

  // ── 最后一条自动用例:它会把一个原生页压在验收页上面 ────────────────────

  /*
   * README 完成标准第 3 条点名要它:router.open({route: "probe"}) 必须成功。
   * §3 规定三端白名单里都有 probe,所以这是唯一一个三端都能成功的 router.open。
   *
   * **它必须是最后一条。** 原生页一压上来,验收页就进后台,而后台里 setTimeout 可能被冻结
   * ——排在中间会让后面的用例卡住,看起来像页面挂了。
   * 端侧自动化的读法不变:等 __acceptance.done,必要时先按一次返回。
   */
  T.test('§3', 'router.open({route: "probe"}) 成功(会打开原生页,本条最后跑)', function () {
    return a.succeeds(B.call('router.open', { route: 'probe' }), 'router.open probe')
      .then(function (data) { a.nullish(data, 'router.open 的 data'); });
  });

  /*
   * ── 这个页面判不了的两条,别在这里找 ────────────────────────────────────
   *
   * 1. **page.close** —— 它会关掉页面,跑了就没有结果可看了。
   *    做成 index.html 上的一个按钮,人点一下:页面消失即通过,报错或没反应即失败。
   *    它是九个必须级能力里唯一一个不进 harness 的。
   *
   * 2. **§8.3 不变量 2「该 origin 不产生真实网络请求」** —— 从页面里看不见。
   *    fetch 成功只说明「有人回了内容」,回内容的是拦截层还是真网络,页面区分不了。
   *    这条归端侧可观察:抓包看那个 host 有没有出流量,或者在拦截层上打断言。
   *    三端各自证一次,结论写进 README 的三端现状。
   *
   * 还有一条更强的形式判不了,理由在 §2 那段注释里:「已注册但未授权」与「未注册」
   * 对同一个 origin 不可区分,需要第二个 origin 或半份白名单,归端侧单测。
   */
})(window);
