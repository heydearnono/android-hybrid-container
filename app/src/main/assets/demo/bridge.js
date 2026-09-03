/*
 * HybridBridge —— JSBridge 的 JS 侧 SDK。
 *
 * 手写，不引 npm、不加构建步骤：加一个 JS 工具链就等于给这个仓库多一套版本、多一层缓存、
 * 多一处 CI 配置，而这份 SDK 只有两百行。
 *
 * 两处必须和 native 对齐，而编译器管不到这个文件：
 *   1. NATIVE_OBJECT_NAME 必须等于 Kotlin 侧的 BRIDGE_JS_OBJECT_NAME。
 *   2. 报文格式必须和 :core:bridge 的 BridgeMessage.kt 一致。
 * 改了任何一边都要手动改另一边。
 *
 * 报文：
 *   JS → Native   {"id":"7","method":"storage.set","params":{...}}   省略 id = 单向通知，不回包
 *   Native → JS   {"id":"7","ok":true,"data":{...}}
 *                 {"id":"7","ok":false,"error":{"code":"INVALID_PARAMS","message":"缺少 key"}}
 *                 {"event":"page.resume","data":{...}}
 */
(function (global) {
  'use strict';

  var NATIVE_OBJECT_NAME = '__hybridNative';
  var DEFAULT_TIMEOUT_MS = 10000;

  var native = global[NATIVE_OBJECT_NAME];
  var pending = {};
  var listeners = {};
  var nextId = 0;

  function bridgeError(code, message) {
    var err = new Error(message || code);
    err.code = code;
    return err;
  }

  function settle(id, settleFn, value) {
    var entry = pending[id];
    if (!entry) {
      // 已经超时被清掉了。晚到的回包直接丢，不要去 resolve 一个已经 reject 的 Promise。
      return;
    }
    delete pending[id];
    global.clearTimeout(entry.timer);
    settleFn(entry, value);
  }

  function handleResponse(msg) {
    if (msg.ok) {
      settle(msg.id, function (entry, value) { entry.resolve(value); }, msg.data);
      return;
    }
    var error = msg.error || {};
    settle(msg.id, function (entry) {
      entry.reject(bridgeError(error.code || 'INTERNAL', error.message));
    });
  }

  function handleEvent(msg) {
    var subscribers = listeners[msg.event];
    if (!subscribers) {
      return;
    }
    // 复制一份再遍历：回调里调 off() 会改动原数组。
    subscribers.slice().forEach(function (cb) {
      try {
        cb(msg.data);
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
   * @param method 能力名，例如 'storage.set'
   * @param params 参数对象，可省略
   * @param options {timeout} 毫秒，默认 10000
   * @return Promise，失败时 reject 一个带 .code 的 Error
   */
  function call(method, params, options) {
    return new Promise(function (resolve, reject) {
      if (!native) {
        reject(bridgeError('BRIDGE_UNAVAILABLE', '当前环境没有注入 ' + NATIVE_OBJECT_NAME));
        return;
      }
      nextId += 1;
      var id = String(nextId);
      var timeoutMs = (options && options.timeout) || DEFAULT_TIMEOUT_MS;
      pending[id] = {
        resolve: resolve,
        reject: reject,
        // native 侧任何一条路径都会回包，所以超时只可能是 bug 或页面被冻结。
        // 但没有超时的话那个 Promise 会永远悬着，调用方连「出错了」都不知道。
        timer: global.setTimeout(function () {
          delete pending[id];
          reject(bridgeError('TIMEOUT', method + ' 超过 ' + timeoutMs + 'ms 没有回包'));
        }, timeoutMs),
      };
      native.postMessage(JSON.stringify({ id: id, method: method, params: params || {} }));
    });
  }

  /**
   * 单向通知：不带 id，native 不回包，连错误也不回。所以调用方无法知道它成功没有。
   *
   * @return false 表示 bridge 根本没注入
   */
  function notify(method, params) {
    if (!native) {
      return false;
    }
    native.postMessage(JSON.stringify({ method: method, params: params || {} }));
    return true;
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

  if (native) {
    native.onmessage = onMessage;

    // 握手。addWebMessageListener 的机制是：native 只有在 JS 先 postMessage 过之后，
    // 才拿到那个 frame 的 replyProxy。不先开口，native 就发不出任何事件。
    //
    // 'bridge.handshake' 刻意不在能力白名单里，native 会判它 PERMISSION_DENIED——
    // 但因为没带 id，那个失败不会回包，什么都不会发生。要的只是「开口」这个副作用。
    notify('bridge.handshake');
  }

  global.HybridBridge = {
    available: !!native,
    call: call,
    notify: notify,
    on: on,
    off: off,
  };
})(window);
