const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');

// Load controller with a deterministic DB double: no MySQL server needed.
function controller(db) {
    const context = { exports: {}, require: () => db, console };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/controllers/chat.controller.js'), 'utf8'), context);
    return context.exports;
}
function response() {
    return { code: 200, status(code) { this.code = code; return this; }, json(body) { this.body = body; return this; } };
}
test('non-member cannot send or broadcast', async () => {
    let queries = 0;
    const api = controller({ query: async () => { queries++; return [[]]; } });
    const res = response();
    await api.sendMessage({ user: { id: 1 }, body: { conversationId: 7, content: 'hello' } }, res);
    assert.equal(res.code, 403); assert.equal(queries, 1);
});
test('persisted message is delivered to all member account rooms', async () => {
    const message = { id: 9, conversation_id: 7, sender_id: 1 };
    const replies = [[{ ok: 1 }], { insertId: 9 }, [message], {}, [{ user_id: 1 }, { user_id: 2 }]];
    const events = [];
    const api = controller({ query: async () => [replies.shift()] });
    const res = response();
    await api.sendMessage({
        user: { id: 1 }, body: { conversationId: 7, content: 'hello', messageType: 'TEXT' },
        app: { get: () => ({ to: room => ({ emit: (event, data) => events.push({ room, event, data }) }) }) }
    }, res);
    assert.equal(res.code, 201);
    assert.equal(events.filter(e => e.event === 'new_message').length, 2);
    assert.equal(events[0].room, 'user_1');
    assert.equal(events[2].room, 'user_2');
    assert.equal(events[0].data.id, 9);
});
test('attachment requires a file', async () => {
    const api = controller({ query: async () => [[{ ok: 1 }]] });
    const res = response();
    await api.sendMessage({ user: { id: 1 }, body: { conversationId: 7, messageType: 'VIDEO' } }, res);
    assert.equal(res.code, 400);
});
test('invalid retry key is rejected before inserting a message', async () => {
    let queries = 0;
    const api = controller({ query: async () => { queries++; return [[{ ok: 1 }]]; } });
    const res = response();
    await api.sendMessage({ user: { id: 1 }, body: {
        conversationId: 7, content: 'hello', clientMessageId: 'bad'
    } }, res);
    assert.equal(res.code, 400); assert.equal(queries, 1);
});
test('reusing a retry key in a different conversation cannot broadcast the old message', async () => {
    const replies = [[{ ok: 1 }], { insertId: 9 }, [{ id: 9, conversation_id: 8 }]];
    const api = controller({ query: async () => [replies.shift()] });
    const res = response();
    await api.sendMessage({ user: { id: 1 }, body: {
        conversationId: 7, content: 'hello', clientMessageId: '11111111-1111-1111-1111-111111111111'
    } }, res);
    assert.equal(res.code, 409);
});
test('group creation rejects accounts outside the accepted friend list', async () => {
    let rolledBack = false, released = false, queries = 0;
    const api = controller({ getConnection: async () => ({
        beginTransaction: async () => {},
        query: async () => { queries++; return [[{ friend_id: 2 }]]; },
        rollback: async () => { rolledBack = true; },
        release: () => { released = true; }
    }) });
    const res = response();
    await api.createGroupChat({ user: { id: 1 }, body: { title: 'Team', memberIds: [2, 3] } }, res);
    assert.equal(res.code, 400); assert.equal(queries, 1); assert.ok(rolledBack); assert.ok(released);
});
test('group failure rolls back instead of leaving an incomplete room', async () => {
    let rolledBack = false, released = false, committed = false, queries = 0;
    const connection = {
        beginTransaction: async () => {},
        query: async () => {
            queries++;
            if (queries === 1) return [[{ friend_id: 2 }, { friend_id: 3 }]];
            if (queries === 2) return [{ insertId: 8 }];
            throw new Error('member insert failed');
        },
        rollback: async () => { rolledBack = true; },
        commit: async () => { committed = true; },
        release: () => { released = true; }
    };
    const api = controller({ getConnection: async () => connection });
    const res = response();
    await api.createGroupChat({ user: { id: 1 }, body: { title: 'Team', memberIds: [2, 3] } }, res);
    assert.equal(res.code, 500); assert.ok(rolledBack); assert.ok(released); assert.equal(committed, false);
});
test('read acknowledgement is monotonic and bounded to a message in the room', async () => {
    let sql, values;
    const api = controller({ query: async (query, args) => { sql = query; values = args; return [{ affectedRows: 1 }]; } });
    const res = response();
    await api.markRead({ user: { id: 1 }, params: { conversationId: '7' }, body: { messageId: 9 }, app: { get: () => null } }, res);
    assert.equal(res.code, 200); assert.match(sql, /GREATEST/); assert.match(sql, /EXISTS/);
    assert.deepEqual(Array.from(values), [9, 7, 1, 9, 7]);
});
test('socket rejects invalid credentials and joins only authenticated account room', () => {
    let middleware, connected;
    const context = { module: { exports: {} }, require: () => ({ verify: token => {
        if (token !== 'valid') throw new Error('bad token');
        return { id: 2 };
    } }), process: { env: {} } };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/sockets/chat.socket.js'), 'utf8'), context);
    context.module.exports({ use: fn => middleware = fn, on: (event, fn) => connected = fn });
    let error;
    middleware({ handshake: { auth: { token: 'bad' } } }, e => error = e);
    assert.ok(error);
    const rooms = [];
    const socket = { handshake: { auth: { token: 'valid' } }, join: room => rooms.push(room) };
    middleware(socket, e => error = e); assert.equal(error, undefined);
    connected(socket); assert.deepEqual(rooms, ['user_2']);
});
