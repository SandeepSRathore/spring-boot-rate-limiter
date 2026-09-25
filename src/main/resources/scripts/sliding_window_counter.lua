-- Sliding window counter (fixed window counter + sliding window log hybrid)
--   requests in rolling window = requests in current window
--                              + requests in previous window * overlap of rolling and previous window
-- The estimate is rounded down, as in the book's example (3 + 5 * 0.7 = 6.5 -> 6).
--
-- KEYS[1]  counter of the current window
-- KEYS[2]  counter of the previous window
-- ARGV[1]  limit
-- ARGV[2]  window (ms)
-- ARGV[3]  ms elapsed since the current window started
-- returns  { allowed, remaining, retry_after_ms, delay_ms }

local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local elapsed = tonumber(ARGV[3])

local current = tonumber(redis.call('GET', KEYS[1])) or 0
local previous = tonumber(redis.call('GET', KEYS[2])) or 0
local overlap = (window - elapsed) / window

if math.floor(current + previous * overlap) >= limit then
  local retry_after
  if current < limit then
    -- later in this window the previous window weighs less: solve current + previous * overlap < limit
    retry_after = math.floor(window - (limit - current) * window / previous) + 1 - elapsed
  else
    -- blocked for the rest of this window; in the next one today's `current` becomes `previous`
    retry_after = (window - elapsed) + math.floor(window - limit * window / current) + 1
  end
  return { 0, 0, math.max(retry_after, 1), 0 }
end

current = redis.call('INCR', KEYS[1])
-- the counter is still needed during the next window, as its "previous window"
redis.call('PEXPIRE', KEYS[1], window * 2)
return { 1, math.max(0, limit - math.floor(current + previous * overlap)), 0, 0 }
