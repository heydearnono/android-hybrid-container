/*
 * HybridBridge —— JSBridge 的 JS 侧 SDK。
 *
 * **三端唯一来源**(PROTOCOL §7)。三端各自把这个文件装进离线包,不许改、不许各留一份。
 * 改了它就是改契约,要同时改 PROTOCOL。
 *
 * 手写,不引 npm、不加构建步骤:加一个 JS 工具链就等于多一套版本、多一层缓存、多一处 CI,
 * 而这份 SDK 只有一百多行。
 *
 * 三处必须和 native 对齐,而编译器管不到这个文件:
 *   1. NATIVE_OBJECT_NAME 与注入对象的形状 —— PROTOCOL §5.1
 *   2. 报文格式 —— PROTOCOL §1
 *   3. CONTRACT_VERSION —— PROTOCOL §1 的 v,对不上 native 回 UNSUPPORTED_VERSION
 *
 * 报文(PROTOCOL §1):
 *   JS → Native   {"v":1,"id":"7","method":"storage.set","params":{...}}
 *   Native → JS   {"v":1,"id":"7","ok":true,"data":{...}}
 *                 {"v":1,"id":"7","ok":false,"error":{"code":"INVALID_PARAMS","message":"..."}}
 *                 {"event":"page.resume","data":{...}}
 *
 * 与 `and` 那份旧 SDK 的两处差异,都来自 PROTOCOL §6:
 *   - 每条请求都带 v 和 id。**id 不再可省略**,所以旧的 notify()(单向通知)删掉了(§6.2):
 *     它制造了一整类静默失败——拼错 method、origin 没授权,全都无声无息。
 *     要 fire-and-forget,不 await 返回的 Promise 就够了,不必让协议层放弃报错能力。
 *   - 握手改成一次真实的 bridge.capabilities 调用,见文件末尾。
 */
(function (global) {
  'use strict';

  var CONTRACT_VERSION = 1;
  var NATIVE_OBJECT_NAME = '__hybridNative';
  var DEFAULT_TIMEOUT_MS = 10000;

  var native = global[NATIVE_OBJECT_NAME];
  var pending = {};
  var listeners = {};
  var nextId = 0;

  /*
   * 这两个 code 是 **JS 侧自己造的**,不在 PROTOCOL §2 那七个里,native 永远不会返回它们。
   * 调用方拿到它们意味着「没走到 native」或「native 没回包」,而不是某个能力失败了。
   */
  var JS_SIDE_CODES = ['BRIDGE_UNAVAILABLE', 'TIMEOUT'];

  function bridgeError(code, message) {
    var err = new Error(message || code);
    err.code = code;
    return err;
  }

  function settle(id, settleFn, value) {
    var entry = pending[id];
    if (!entry) {
      // 已经超时被清掉了。晚到的回包直接丢,不要去 resolve 一个已经 reject 的 Promise。
      return;
    }
    delete pending[id];
    global.clearTimeout(entry.timer);
    settleFn(entry, value);
  }

  function handleResponse(msg) {
    if (msg.ok) {
      // PROTOCOL §1:无返回值统一是 null;而「键省略」与「键为 null」必须等价处理,
      // 因为三端 JSON 库对这两者的取舍不同,契约不指望它们一致。
      settle(msg.id, function (entry, value) { entry.resolve(value); },
        msg.data === undefined ? null : msg.data);
      return;
    }
    var error = msg.error || {};
    settle(msg.id, function (entry) {
      var err = bridgeError(error.code || 'INTERNAL', error.message);
      err.details = error.details;   // §6.3 的可选字段,不带就是 undefined
      entry.reject(err);
    });
  }

  function handleEvent(msg) {
    var subscribers = listeners[msg.event];
    if (!subscribers) {
      return;
    }
    // 复制一份再遍历:回调里调 off() 会改动原数组。
    subscribers.slice().forEach(function (cb) {
      try {
        cb(msg.data === undefined ? null : msg.data);
      } catch (e) {
        // 一个订阅者抛异常不该让其余订阅者收不到。
        global.console.error('[bridge] event listener failed', msg.event, e);
      }
    });
  }

  function onMessage(event) {
    var msg;
    try {
      msg = JSON.parse(event.data);
    } catch (e) {
      global.console.error('[bridge] 收到无法解析的报文', event.data);
      return;
    }
    if (msg.id !== undefined && msg.id !== null) {
      handleResponse(msg);
    } else if (msg.event) {
      handleEvent(msg);
    }
  }

  /**
   * 调一个原生能力。
   *
   * @param method 能力名,见 PROTOCOL §3
   * @param params 参数对象,可省略
   * @param options {timeout} 毫秒,默认 10000
   * @return Promise。失败时 reject 一个 Error,`.code` 是 PROTOCOL §2 那七个之一,
   *         或者上面 JS_SIDE_CODES 里那两个;`.details` 可能有(§6.3)
   */
  function call(method, params, options) {
    return new Promise(function (resolve, reject) {
      if (!native) {
        reject(bridgeError('BRIDGE_UNAVAILABLE', '当前环境没有注入 ' + NATIVE_OBJECT_NAME));
        return;
      }
      nextId += 1;
      var id = String(nextId);   // 单调递增,所以不会撞上「在途 id 重复」那条(§1、§6.2)
      var timeoutMs = (options && options.timeout) || DEFAULT_TIMEOUT_MS;
      pending[id] = {
        resolve: resolve,
        reject: reject,
        // native 侧任何一条路径都会回包,所以超时只可能是 bug 或页面被冻结。
        // 但没有超时的话那个 Promise 会永远悬着,调用方连「出错了」都不知道。
        timer: global.setTimeout(function () {
          delete pending[id];
          reject(bridgeError('TIMEOUT', method + ' 超过 ' + timeoutMs + 'ms 没有回包'));
        }, timeoutMs),
      };
      native.postMessage(JSON.stringify({
        v: CONTRACT_VERSION,
        id: id,
        method: method,
        params: params || {},
      }));
    });
  }

  function on(event, callback) {
    if (!listeners[event]) {
      listeners[event] = [];
    }
    listeners[event].push(callback);
    return function () { off(event, callback); };
  }

  function off(event, callback) {
    var subscribers = listeners[event];
    if (!subscribers) {
      return;
    }
    var index = subscribers.indexOf(callback);
    if (index >= 0) {
      subscribers.splice(index, 1);
    }
  }

  var ready = Promise.reject(bridgeError('BRIDGE_UNAVAILABLE', '没有注入 ' + NATIVE_OBJECT_NAME));
  ready.catch(function () {});   // 没注入时不要冒一个 unhandled rejection 出来

  if (native) {
    native.onmessage = onMessage;

    /*
     * 握手。机制是:native 只有在 JS 先开口之后才拿到这个 frame 的回包通道
     * (Android 是 JavaScriptReplyProxy,见 PROTOCOL §5 第 2 条;iOS / ArkWeb 是否同样,待核实)。
     * 不先开口,native 就发不出任何事件——所以页面加载早期的事件必然收不到,这是协议级的事实。
     *
     * 旧版发的是一条不带 id 的 bridge.handshake,靠「没 id 就不回包」把那次必然的
     * PERMISSION_DENIED 咽掉。§6.2 取消了无 id 请求,于是握手改成一次真实调用:
     * bridge.capabilities 是必须级能力(§3),它的回包顺带告诉页面「这一端支持到哪」,
     * 比猜 platform 字段可靠。
     */
    ready = call('bridge.capabilities');
  }

  global.HybridBridge = {
    available: !!native,
    contractVersion: CONTRACT_VERSION,
    nativeObjectName: NATIVE_OBJECT_NAME,
    jsSideCodes: JS_SIDE_CODES,
    /** Promise<{v, methods}>。resolve 之后事件通道才一定可用 */
    ready: ready,
    call: call,
    on: on,
    off: off,
  };
})(window);
