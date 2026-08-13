-- 상담사 배정. 두 명이 같은 순간에 눌러도 정확히 하나만 이겨야 한다.
--
-- SETNX 만으로는 부족하다. 락 획득 / 상태 전이 / 대기열 제거를 따로 하면, 그 사이에
-- 파드가 죽었을 때 "락은 잡혔는데 방은 WAITING" 이 남는다. 다른 상담사는 거절당하고
-- 사용자는 아무도 오지 않는 방에 갇힌다.
--
-- KEYS[1]=room  KEYS[2]=waiting(zset)
-- ARGV[1]=agentId ARGV[2]=agentNickname ARGV[3]=roomId ARGV[4]=now
if redis.call('EXISTS', KEYS[1]) == 0 then
  redis.call('ZREM', KEYS[2], ARGV[3])        -- 대기열의 유령 항목도 같이 치운다
  return -1                                   -- ROOM_GONE
end

if redis.call('HGET', KEYS[1], 'state') ~= 'WAITING' then
  return 0                                    -- 이미 누가 받았거나 종료됐다
end

redis.call('HSET', KEYS[1],
  'state', 'LIVE',
  'agentId', ARGV[1],
  'agentNickname', ARGV[2],
  'claimedAt', ARGV[4])
redis.call('ZREM', KEYS[2], ARGV[3])
return 1                                      -- 이긴 사람