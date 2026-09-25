-- Fixed window counter
--   * The timeline is divided into fixed windows, each with its own counter.
--   * Every request increments the counter; once it passes the limit, requests are dropped until the next window.
-- The key name contains the window start, so each window gets a fresh counter that expires with the window.
--
-- KEYS[1]  counter of the current window
-- ARGV[1]  limit
-- ARGV[2]  ms until the current window ends
-- returns  { allowed, remaining, retry_after_ms, delay_ms }

local limit = tonumber(ARGV[1])
local window_left = tonumber(ARGV[2])

local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], window_left)
end

if count > limit then
  return { 0, 0, window_left, 0 }
end
return { 1, limit - count, 0, 0 }
