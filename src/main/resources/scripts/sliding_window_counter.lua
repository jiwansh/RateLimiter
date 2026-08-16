--KEYS[1] - previous window key
--KEYS[2] - current window key
-- ARGV[1] limit
-- ARGV[2] overlap percentage
-- ARGV[3] windowSeconds

local prevCount = tonumber(redis.call('GET',KEYS[1]) or "0")
local currCount = tonumber(redis.call('GET',KEYS[2]) or "0")

local limit = tonumber(ARGV[1])
local overlapPercentage = tonumber(ARGV[2])
local windowSeconds = tonumber(ARGV[3])

local estimatedCount = (overlapPercentage*prevCount) +currCount;

if(estimatedCount<limit) then
--     still inside then same script execution - this is the fix
--     no other client or command can run in between
   redis.call('INCR',KEYS[2])
   redis.call('EXPIRE', KEYS[2],windowSeconds*2)
   return {1, tostring(estimatedCount)}; -- becaise between need then preserve decimal values
else
    return {0, tostring(estimatedCount)};
end