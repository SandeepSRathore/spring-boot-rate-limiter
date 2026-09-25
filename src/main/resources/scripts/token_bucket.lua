-- Token bucket
--   * A bucket holds at most `capacity` tokens and is refilled at a constant rate.
--   * Each request takes one token; a request that finds the bucket empty is dropped.
-- Instead of a background refiller, tokens are topped up lazily from the time elapsed since the last request.
--
-- KEYS[1]  hash { tokens, ts }
-- ARGV[1]  capacity (bucket size)
-- ARGV[2]  tokens added per refill period
-- ARGV[3]  refill period (ms)
-- ARGV[4]  now (ms)
-- returns  { allowed, remaining, retry_after_ms, delay_ms }

local capacity = tonumber(ARGV[1])
local refill = tonumber(ARGV[2])
local period = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local last = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + math.max(0, now - last) * refill / period)

local allowed, retry_after = 0, 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retry_after = math.ceil((1 - tokens) * period / refill)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', math.max(now, last))
-- an idle bucket is full again after this long, so the key can go
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * period / refill))
return { allowed, math.floor(tokens), retry_after, 0 }
