# AI-generated with Claude Code, 2026-10-27, reviewed by Sungmin Jo
"""Lua scripts that change session state inside Redis (Design Documentation 2.7).

Redis runs a script as one step, so two workers cannot interleave inside a room transition. Every script gets the
current time from its caller, which lets the tests move a fake clock, and returns the messages to deliver as
`{connection, worker, kind, detail}` rows; the caller turns a row into JSON and sends it to that worker's socket.
The scripts avoid `cjson` and `unpack`, which the in-process Redis used by the tests does not provide.

Keys: `session:<id>` (hash; `photographer_token` and `subject_token` hold the SHA-256 of each place's rejoin
token, never the token), `code:<code>`, `conn:<id>` (hash), `conns:<worker>` (set), `worker:<worker>` (heartbeat
with a real expiry), `presence:<user>` (the signed-in user's connections), and the indexes `sessions`, `workers`,
`deadlines` (sorted by the next time a session has to be looked at), and `expired` (recently expired codes, sorted
by the time to forget them).

Only REGISTER adds a worker to `workers`. Every other script refuses to run for a worker that is not in it,
because that worker has lost its state: Redis restarted empty, or the sweeper took the worker for dead.
"""

# ARGV[1..5] of every session script: now, room TTL, empty-session TTL, the calling worker, heartbeat TTL in ms.
_PRELUDE = """
local now = tonumber(ARGV[1])
local room_ttl = tonumber(ARGV[2])
local empty_ttl = tonumber(ARGV[3])
local me = ARGV[4]
local out = {}

local function num(n)
  return string.format('%.6f', n)
end

-- Queues a message for a connection that still exists.
local function emit(conn, kind, a)
  if not conn or conn == '' then return end
  local worker = redis.call('HGET', 'conn:' .. conn, 'worker')
  if worker then out[#out + 1] = {conn, worker, kind, a or ''} end
end

-- Keeps `deadlines` at the earlier of the unjoined-room expiry and the empty-session removal.
local function schedule(sid)
  local s = redis.call('HMGET', 'session:' .. sid, 'expires_at', 'empty_since')
  local due = nil
  if s[1] and s[1] ~= '' then due = tonumber(s[1]) end
  if s[2] and s[2] ~= '' then
    local empty_due = tonumber(s[2]) + empty_ttl
    if not due or empty_due < due then due = empty_due end
  end
  if due then
    redis.call('ZADD', 'deadlines', num(due), sid)
  else
    redis.call('ZREM', 'deadlines', sid)
  end
end

local function remove_session(sid)
  local s = redis.call('HMGET', 'session:' .. sid, 'code', 'photographer', 'subject')
  redis.call('DEL', 'session:' .. sid)
  redis.call('ZREM', 'deadlines', sid)
  redis.call('SREM', 'sessions', sid)
  if s[1] and redis.call('GET', 'code:' .. s[1]) == sid then redis.call('DEL', 'code:' .. s[1]) end
  for i = 2, 3 do
    if s[i] and s[i] ~= '' and redis.call('EXISTS', 'conn:' .. s[i]) == 1 then
      redis.call('HSET', 'conn:' .. s[i], 'session', '')
    end
  end
end

-- A socket is gone: free its place and tell the other phone, or start the empty-session timer.
local function drop(conn)
  local c = redis.call('HMGET', 'conn:' .. conn, 'worker', 'session', 'user')
  if not c[1] then return end
  redis.call('DEL', 'conn:' .. conn)
  redis.call('SREM', 'conns:' .. c[1], conn)
  if c[3] and c[3] ~= '' then redis.call('SREM', 'presence:' .. c[3], conn) end
  local sid = c[2]
  if not sid or sid == '' then return end
  local s = redis.call('HMGET', 'session:' .. sid, 'photographer', 'subject')
  local other
  if s[1] == conn then
    redis.call('HSET', 'session:' .. sid, 'photographer', '')
    other = s[2]
  elseif s[2] == conn then
    redis.call('HSET', 'session:' .. sid, 'subject', '')
    other = s[1]
  else
    return
  end
  if other and other ~= '' then
    emit(other, 'peer.left', 'CONNECTION_LOST')
  else
    redis.call('HSET', 'session:' .. sid, 'empty_since', num(now))
    schedule(sid)
  end
end

local function sweep()
  redis.call('ZREMRANGEBYSCORE', 'expired', '-inf', num(now))
  for _, sid in ipairs(redis.call('ZRANGEBYSCORE', 'deadlines', '-inf', num(now))) do
    local s = redis.call('HMGET', 'session:' .. sid, 'expires_at', 'empty_since', 'photographer', 'code')
    if not s[4] then
      redis.call('ZREM', 'deadlines', sid)
    elseif s[1] and s[1] ~= '' and now >= tonumber(s[1]) then
      remove_session(sid)
      -- Remembered for one more TTL, so a late join answers EXPIRED and the code is not handed out again.
      redis.call('ZADD', 'expired', num(now + room_ttl), s[4])
      emit(s[3], 'error', 'EXPIRED')
    elseif s[2] and s[2] ~= '' and now >= tonumber(s[2]) + empty_ttl then
      remove_session(sid)
    else
      schedule(sid)
    end
  end
  -- A worker whose heartbeat ran out has lost its sockets without saying so.
  for _, worker in ipairs(redis.call('SMEMBERS', 'workers')) do
    if redis.call('EXISTS', 'worker:' .. worker) == 0 then
      for _, conn in ipairs(redis.call('SMEMBERS', 'conns:' .. worker)) do drop(conn) end
      redis.call('DEL', 'conns:' .. worker)
      redis.call('SREM', 'workers', worker)
    end
  end
end

-- Registers a connection of the calling worker. Repeating it is harmless, and it restores the record after
-- Redis itself restarted.
local function touch(conn)
  if redis.call('EXISTS', 'conn:' .. conn) == 0 then
    redis.call('HSET', 'conn:' .. conn, 'worker', me, 'session', '')
    redis.call('SADD', 'conns:' .. me, conn)
  end
end

-- A worker missing from `workers` has lost its state, and its sockets may have missed messages: it has to close
-- them and register again.
if redis.call('SISMEMBER', 'workers', me) == 0 then
  return redis.error_reply('WORKER_LOST ' .. me .. ' is not registered in the session store')
end
-- A worker that runs a script is alive, so its own heartbeat is renewed before anything is swept.
redis.call('SET', 'worker:' .. me, '1', 'PX', ARGV[5])
"""

# ARGV: the worker, heartbeat TTL in ms. A worker runs it when it starts, and again after it lost its state.
REGISTER = """
redis.call('SADD', 'workers', ARGV[1])
redis.call('SET', 'worker:' .. ARGV[1], '1', 'PX', ARGV[2])
return 1
"""

# ARGV[6] is "sweep" for the worker that holds the sweeper lock; the others only renew their heartbeat.
BEAT = (
    _PRELUDE
    + """
if ARGV[6] == 'sweep' then sweep() end
return out
"""
)

# ARGV[6..7]: connection, and the signed-in user it belongs to or "" for an anonymous socket.
CONNECT = (
    _PRELUDE
    + """
local conn, user = ARGV[6], ARGV[7]
sweep()
touch(conn)
if user ~= '' then
  redis.call('HSET', 'conn:' .. conn, 'user', user)
  redis.call('SADD', 'presence:' .. user, conn)
end
return out
"""
)

# ARGV[6..9]: connection, new session id, candidate room code, and the hash of the photographer's rejoin token.
# Returns {status, out}.
CREATE = (
    _PRELUDE
    + """
local conn, sid, code = ARGV[6], ARGV[7], ARGV[8]
sweep()
touch(conn)
local status = 'OK'
if redis.call('HGET', 'conn:' .. conn, 'session') ~= '' then
  status = 'INVALID_STATE'
elseif redis.call('EXISTS', 'code:' .. code) == 1 or redis.call('ZSCORE', 'expired', code) then
  status = 'COLLISION'
else
  redis.call('HSET', 'session:' .. sid, 'code', code, 'photographer', conn, 'subject', '',
    'expires_at', num(now + room_ttl), 'empty_since', '', 'photographer_token', ARGV[9], 'subject_token', '')
  redis.call('SET', 'code:' .. code, sid)
  redis.call('SADD', 'sessions', sid)
  redis.call('HSET', 'conn:' .. conn, 'session', sid)
  schedule(sid)
end
return {status, out}
"""
)

# ARGV[6..12]: connection, room code, the window of wrong codes from the client's address ("" for none), its limit,
# its length in seconds, a unique member, and the hash of the subject's rejoin token, which replaces an earlier
# subject's. Returns {status, session id, out}. A wrong code, unknown or
# expired, counts in the window; while it is full, every join from that address answers RATE_LIMITED without the
# code being looked at, so codes cannot be guessed.
JOIN = (
    _PRELUDE
    + """
local conn, code = ARGV[6], ARGV[7]
local fails, fail_limit, fail_window = ARGV[8], tonumber(ARGV[9]), tonumber(ARGV[10])
sweep()
touch(conn)
local status, sid = 'OK', ''
if fails ~= '' then redis.call('ZREMRANGEBYSCORE', fails, '-inf', num(now - fail_window)) end
if fails ~= '' and redis.call('ZCARD', fails) >= fail_limit then
  status = 'RATE_LIMITED'
elseif redis.call('HGET', 'conn:' .. conn, 'session') ~= '' then
  status = 'INVALID_STATE'
elseif redis.call('ZSCORE', 'expired', code) then
  status = 'EXPIRED'
else
  sid = redis.call('GET', 'code:' .. code) or ''
  local s = {}
  if sid ~= '' then s = redis.call('HMGET', 'session:' .. sid, 'photographer', 'subject') end
  if not s[1] or s[1] == '' or redis.call('EXISTS', 'conn:' .. s[1]) == 0 then
    status = 'NOT_FOUND'
  elseif s[2] ~= '' then
    status = 'FULL'
  else
    redis.call('HSET', 'session:' .. sid, 'subject', conn, 'expires_at', '', 'subject_token', ARGV[12])
    redis.call('HSET', 'conn:' .. conn, 'session', sid)
    schedule(sid)
    emit(s[1], 'peer.joined')
  end
end
if fails ~= '' and (status == 'NOT_FOUND' or status == 'EXPIRED') then
  redis.call('ZADD', fails, num(now), ARGV[11])
  redis.call('EXPIRE', fails, math.floor(fail_window) + 1)
end
return {status, sid, out}
"""
)

# ARGV[6..7]: connection, and the SDP type of the signal or "" for an ICE candidate.
# Returns {status, expected SDP type, target connection, target worker, out}.
ROUTE = (
    _PRELUDE
    + """
local conn, sdp = ARGV[6], ARGV[7]
sweep()
touch(conn)
local status, expected, target, target_worker = 'OK', '', '', ''
local sid = redis.call('HGET', 'conn:' .. conn, 'session')
local s = {}
if sid and sid ~= '' then s = redis.call('HMGET', 'session:' .. sid, 'photographer', 'subject') end
if not s[1] then
  status = 'INVALID_STATE'
else
  local other = s[1]
  expected = 'answer'
  if s[1] == conn then
    other = s[2]
    expected = 'offer'
  end
  if sdp ~= '' and sdp ~= expected then
    status = 'WRONG_SDP'
  else
    local worker = other and other ~= '' and redis.call('HGET', 'conn:' .. other, 'worker')
    if worker then
      target, target_worker = other, worker
    else
      status = 'PEER_UNAVAILABLE'
    end
  end
end
return {status, expected, target, target_worker, out}
"""
)

# ARGV[6..9]: connection, session id, the hash of a rejoin token, and "1" when the phone keeps its peer connection or
# "0" when it starts a new one. Returns {status, role, out}. The token decides the place. A socket that still holds it
# is taken to be the same phone's old one, which the server would notice only once its pings go unanswered: it gives
# the place up at once and keeps no session, so its later disconnect changes nothing. The other phone hears
# `peer.rejoined` (an ICE restart is enough) or `peer.joined` (a new peer connection).
REJOIN = (
    _PRELUDE
    + """
local conn, sid, token, keep = ARGV[6], ARGV[7], ARGV[8], ARGV[9]
sweep()
touch(conn)
local status, role = 'OK', ''
local current = redis.call('HGET', 'conn:' .. conn, 'session')
local s = redis.call('HMGET', 'session:' .. sid, 'code', 'photographer', 'subject',
  'photographer_token', 'subject_token')
local seat, holder, other
if current ~= '' and current ~= sid then
  status = 'INVALID_STATE'
elseif not s[1] then
  status = 'NOT_FOUND'
elseif s[4] == token then
  role, seat, holder, other = 'PHOTOGRAPHER', 'photographer', s[2], s[3]
elseif s[5] ~= '' and s[5] == token then
  role, seat, holder, other = 'SUBJECT', 'subject', s[3], s[2]
else
  status = 'FORBIDDEN'
end
if status == 'OK' and current == sid and holder ~= conn then
  -- This socket holds the other place.
  status, role = 'INVALID_STATE', ''
end
if status == 'OK' then
  if holder ~= '' and holder ~= conn and redis.call('EXISTS', 'conn:' .. holder) == 1 then
    redis.call('HSET', 'conn:' .. holder, 'session', '')
  end
  redis.call('HSET', 'session:' .. sid, seat, conn, 'empty_since', '')
  redis.call('HSET', 'conn:' .. conn, 'session', sid)
  schedule(sid)
  emit(other, keep == '1' and 'peer.rejoined' or 'peer.joined')
end
return {status, role, out}
"""
)

# ARGV[6]: connection. The photographer leaving ends the session; the subject leaving frees its place for good, so its
# rejoin token no longer works.
LEAVE = (
    _PRELUDE
    + """
local conn = ARGV[6]
sweep()
touch(conn)
local sid = redis.call('HGET', 'conn:' .. conn, 'session')
if sid and sid ~= '' then
  local s = redis.call('HMGET', 'session:' .. sid, 'photographer', 'subject')
  if s[1] == conn then
    remove_session(sid)
    emit(s[2], 'peer.left', 'PEER_LEFT')
  else
    redis.call('HSET', 'conn:' .. conn, 'session', '')
    if s[2] == conn then
      redis.call('HSET', 'session:' .. sid, 'subject', '', 'subject_token', '')
      if s[1] ~= '' then
        emit(s[1], 'peer.left', 'PEER_LEFT')
      else
        redis.call('HSET', 'session:' .. sid, 'empty_since', num(now))
        schedule(sid)
      end
    end
  end
end
return out
"""
)

# ARGV[6]: connection.
DISCONNECT = (
    _PRELUDE
    + """
sweep()
drop(ARGV[6])
return out
"""
)

# The calling worker stops: all its sockets are gone, and so is its heartbeat.
RELEASE = (
    _PRELUDE
    + """
for _, conn in ipairs(redis.call('SMEMBERS', 'conns:' .. me)) do drop(conn) end
redis.call('DEL', 'conns:' .. me, 'worker:' .. me)
redis.call('SREM', 'workers', me)
return out
"""
)

# Returns {open sessions, open connections} as strings. It does not sweep, because it has nobody to deliver to.
STATS = (
    _PRELUDE
    + """
local connections = 0
for _, worker in ipairs(redis.call('SMEMBERS', 'workers')) do
  connections = connections + redis.call('SCARD', 'conns:' .. worker)
end
return {tostring(redis.call('SCARD', 'sessions')), tostring(connections)}
"""
)

# ARGV: user ids. Returns {online, in a session} as 0 or 1 for each user, in order. A user is online while one of
# their connections exists; the sweeper drops the connections of a worker that died. It only reads, so it runs for
# any worker.
PRESENCE = """
local out = {}
for i, user in ipairs(ARGV) do
  local online, busy = 0, 0
  for _, conn in ipairs(redis.call('SMEMBERS', 'presence:' .. user)) do
    local sid = redis.call('HGET', 'conn:' .. conn, 'session')
    if sid then
      online = 1
      if sid ~= '' then busy = 1 end
    end
  end
  out[i] = {online, busy}
end
return out
"""

# KEYS: sliding windows that one request counts in. ARGV: now, a unique member, then the length in seconds and the
# limit of each window in turn. If none is full, the request is counted in all of them and 0 is returned; otherwise
# it is counted in none, and the 1-based index of the first full window is returned.
RATE_LIMIT = """
local now = tonumber(ARGV[1])
for i, key in ipairs(KEYS) do
  redis.call('ZREMRANGEBYSCORE', key, '-inf', string.format('%.6f', now - tonumber(ARGV[1 + 2 * i])))
  if redis.call('ZCARD', key) >= tonumber(ARGV[2 + 2 * i]) then return i end
end
for i, key in ipairs(KEYS) do
  redis.call('ZADD', key, string.format('%.6f', now), ARGV[2])
  redis.call('EXPIRE', key, math.floor(tonumber(ARGV[1 + 2 * i])) + 1)
end
return 0
"""
