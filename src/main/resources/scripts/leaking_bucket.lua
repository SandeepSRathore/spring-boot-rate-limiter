-- Leaking bucket
--   * A request joins a FIFO queue of size `capacity`; if the queue is full the request is dropped.
--   * The queue is drained at a fixed outflow rate: one request every `interval` ms.
-- The queue itself doesn't have to be stored: knowing when the next request may leave is enough.
-- The caller gets delay_ms = how long this request waits in the queue before it is processed.
--
-- KEYS[1]  time (ms) at which the next queued request may leave the bucket
-- ARGV[1]  capacity (queue size)
-- ARGV[2]  interval between two outgoing requests (ms)
-- ARGV[3]  now (ms)
-- returns  { allowed, remaining, retry_after_ms, delay_ms }

local capacity = tonumber(ARGV[1])
local interval = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local next_slot = math.max(now, tonumber(redis.call('GET', KEYS[1])) or now)
-- requests currently in the bucket (the one being processed included)
local level = math.ceil((next_slot - now) / interval)

if level >= capacity then
  -- wait until one more request has leaked out
  return { 0, 0, next_slot - (capacity - 1) * interval - now, 0 }
end

redis.call('SET', KEYS[1], next_slot + interval, 'PX', next_slot + interval - now)
return { 1, capacity - level - 1, 0, next_slot - now }
