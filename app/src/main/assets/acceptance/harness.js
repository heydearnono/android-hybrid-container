/*
 * 验收页的断言骨架。零依赖、零构建——这个页面要能在三端的容器里直接打开。
 *
 * 输出两份:
 *   1. 给人看的:页面上每条一行,红绿分明
 *   2. 给端侧自动化看的:window.__acceptance = {done, total, passed, failed, cases:[...]}
 *      端侧断言这一条就够:__acceptance.done === true && __acceptance.failed === 0
 *
 * 判定规则:**一条断言失败即整页失败**,不做「已知问题」豁免。
 * 豁免的位置在 PROTOCOL,不在这里——某端做不到某条,是改契约还是改端,那要写下来。
 */
(function (global) {
  'use strict';

  var cases = [];
  var current = null;

  function AssertionError(message) {
    var err = new Error(message);
    err.name = 'AssertionError';
    return err;
  }

  function show(value) {
    if (value === undefined) return 'undefined';
    try {
      return JSON.stringify(value);
    } catch (e) {
      return String(value);
    }
  }

  var assert = {
    ok: function (cond, what) {
      if (!cond) throw AssertionError(what + ':期望真值,实际 ' + show(cond));
    },
    eq: function (actual, expected, what) {
      if (actual !== expected) {
        throw AssertionError(what + ':期望 ' + show(expected) + ',实际 ' + show(actual));
      }
    },
    /** PROTOCOL §1:键省略与键为 null 必须等价处理,所以这两种都算「空」 */
    nullish: function (actual, what) {
      if (actual !== null && actual !== undefined) {
        throw AssertionError(what + ':期望 null 或键省略,实际 ' + show(actual));
      }
    },
    type: function (actual, expected, what) {
      if (typeof actual !== expected) {
        throw AssertionError(what + ':期望 ' + expected + ',实际 ' + typeof actual +
          '(' + show(actual) + ')');
      }
    },
    /** 期望 reject,且 err.code 等于 code。返回那个 error,方便继续断言 details */
    failsWith: function (promise, code, what) {
      return promise.then(function (data) {
        throw AssertionError(what + ':期望失败并回 ' + code + ',实际成功了,data=' + show(data));
      }, function (err) {
        if (err.name === 'AssertionError') throw err;
        if (err.code !== code) {
          throw AssertionError(what + ':期望 code ' + code + ',实际 ' + err.code +
            '(' + err.message + ')');
        }
        return err;
      });
    },
    /** 期望成功。返回 data */
    succeeds: function (promise, what) {
      return promise.then(null, function (err) {
        throw AssertionError(what + ':期望成功,实际失败 [' + err.code + '] ' + err.message);
      });
    },
  };

  /**
   * 注册一条断言。
   *
   * @param ref   契约出处,例如 '§3' —— 失败时第一眼就知道去查哪一节
   * @param title 这条在验什么
   * @param fn    可以返回 Promise。抛异常即失败
   */
  function test(ref, title, fn) {
    cases.push({ ref: ref, title: title, fn: fn, status: 'pending', detail: '' });
  }

  /**
   * 有副作用、要人眼确认的那几条(toast 弹没弹、原生页开没开)。
   * 它们不计入 failed —— 自动化判不了「用户看见了什么」。
   * 但它们仍然要跑,因为「调用本身不报错」是可判的。
   */
  function manual(ref, title, fn) {
    cases.push({ ref: ref, title: title, fn: fn, status: 'pending', detail: '', manual: true });
  }

  /*
   * detail 里带的是 native 回来的文案。转义一下——这个页面手里有 bridge 权限,
   * 不该让一条回包顺手往它自己的 DOM 里塞标签。
   */
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }

  function render(target) {
    var passed = 0, failed = 0, manualCount = 0;
    var rows = cases.map(function (c) {
      if (c.status === 'pass') passed += 1;
      else if (c.status === 'fail') failed += 1;
      if (c.manual) manualCount += 1;
      var mark = c.status === 'pass' ? '✓' : c.status === 'fail' ? '✗' : '·';
      return '<li class="' + c.status + (c.manual ? ' manual' : '') + '">' +
        '<span class="mark">' + mark + '</span>' +
        '<span class="ref">' + esc(c.ref) + '</span>' +
        '<span class="title">' + esc(c.title) + '</span>' +
        (c.detail ? '<span class="detail">' + esc(c.detail) + '</span>' : '') +
        '</li>';
    });
    target.innerHTML = '<ul>' + rows.join('') + '</ul>';
    return { passed: passed, failed: failed, manual: manualCount };
  }

  /**
   * 顺序跑。不并发——storage 那几条互相有先后,而且并发会让「在途 id 不重复」这条
   * 变成一个隐式压测,失败时分不清是哪个原因。
   */
  function run(target, onDone) {
    var i = 0;
    function step() {
      if (i >= cases.length) {
        var stats = render(target);
        var result = {
          done: true,
          total: cases.length,
          passed: stats.passed,
          failed: stats.failed,
          manual: stats.manual,
          cases: cases.map(function (c) {
            return { ref: c.ref, title: c.title, status: c.status, detail: c.detail };
          }),
        };
        global.__acceptance = result;
        if (onDone) onDone(result);
        return;
      }
      var c = cases[i];
      i += 1;
      var p;
      try {
        p = Promise.resolve(c.fn());
      } catch (e) {
        p = Promise.reject(e);
      }
      p.then(function () {
        c.status = 'pass';
      }, function (err) {
        c.status = c.manual ? 'pass' : 'fail';
        c.detail = (err && err.message) || String(err);
      }).then(function () {
        render(target);
        global.setTimeout(step, 0);
      });
    }
    global.__acceptance = { done: false, total: cases.length };
    step();
  }

  global.Harness = { test: test, manual: manual, run: run, assert: assert };
})(window);
