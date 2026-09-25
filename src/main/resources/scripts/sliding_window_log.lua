-- Sliding window log
--   * Keep a log of request timestamps in a Redis sorted set.
--   * On a new request, drop timestamps older than the start of the rolling window, then add the new one.
--   * Allowed if the log size is <= limit, otherwise rejected. As in the book, the timestamp of a rejected
--     request stays in the log, so a client that keeps hammering stays blocked.
--
-- Only the newest `limit` timestamps can ever change a decision, so the log is trimmed to that size.
-- This caps memory (the book's main criticism of this algorithm) without changing the outcome.
--
-- KEYS[1]  sorted set, member = request id, score = timestamp (ms)
-- ARGV[1]  limit
-- ARGV[2]  window (ms)
-- ARGV[3]  now (ms)
-- ARGV[4]  unique id of this request
-- returns  { allowed, remaining, retry_after_ms, delay_ms }

local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
redis.call('ZADD', KEYS[1], now, ARGV[4])
redis.call('PEXPIRE', KEYS[1], window)

local count = redis.call('ZCARD', KEYS[1])
if count <= limit then
  return { 1, limit - count, 0, 0 }
end

redis.call('ZREMRANGEBYRANK', KEYS[1], 0, count - limit - 1)
-- a new request is allowed again once the oldest remaining timestamp has left the window
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
return { 0, 0, tonumber(oldest[2]) + window - now, 0 }
