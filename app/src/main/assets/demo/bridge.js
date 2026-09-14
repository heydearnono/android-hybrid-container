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
 * 报文（三端契约 v1）：
 *   JS → Native   {"v":1,"id":"7","method":"storage.set","params":{...}}   id 必填
 *   Native → JS   {"v":1,"id":"7","ok":true,"data":{...}}
 *                 {"v":1,"id":"7","ok":false,"error":{"code":"INVALID_PARAMS","message":"缺少 key"}}
 *                 {"event":"page.resume","data":{...}}
 */
(function (global) {
  'use strict';

  var NATIVE_OBJECT_NAME = '__hybridNative';
  var DEFAULT_TIMEOUT_MS = 10000;
  var PROTOCOL_VERSION = 1;

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
      native.postMessage(JSON.stringify({ v: PROTOCOL_VERSION, id: id, method: method, params: params || {} }));
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

  /**
   * 握手：等 bridge.capabilities 真的回包之后才把 ready 置 true。
   *
   * 三端契约 §6.2 把 id 改成必填之后，「不带 id 的单向通知」这条路没有了——
   * 想知道握手有没有成功，就必须真的 await 一次 call()，而不是像原来那样
   * fire-and-forget 靠「有没有副作用」去猜。
   *
   * addWebMessageListener 的机制是：native 只有在 JS 先 postMessage 过之后，
   * 才拿到那个 frame 的 replyProxy——不先开口，native 就发不出任何事件。
   * 这次 call() 顺带满足了这个「先开口」的前提。
   */
  var ready = native
    ? call('bridge.capabilities').then(
        function (data) {
          global.HybridBridge.methods = (data && data.methods) || [];
          return data;
        },
        function (err) {
          // 握手失败不该让整个 SDK 抛出去——调用方后续调具体能力时自然会拿到
          // 同样的错误码，这里只负责把"能力清单还没拿到"这件事记下来。
          global.console.error('[bridge] 握手失败', err);
          throw err;
        },
      )
    : Promise.reject(bridgeError('BRIDGE_UNAVAILABLE', '当前环境没有注入 ' + NATIVE_OBJECT_NAME));

  if (native) {
    native.onmessage = onMessage;
  }

  global.HybridBridge = {
    available: !!native,
    methods: [],
    ready: ready,
    call: call,
    on: on,
    off: off,
  };
})(window);

