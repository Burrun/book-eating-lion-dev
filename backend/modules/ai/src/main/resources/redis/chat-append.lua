-- 대화 한 줄 적재 + 팬아웃. 저장과 발행이 갈라지면 안 되므로 한 스크립트로 묶는다.
--
-- 애플리케이션에서 INCR → RPUSH → PUBLISH 로 나눠 쓰면, 파드 A 가 seq 5 를 받고
-- 파드 B 가 seq 6 을 받은 뒤 B 가 먼저 발행하는 인터리빙이 생긴다. 저장 순서(5,6)와
-- 화면 순서(6,5)가 어긋난다. Redis 는 싱글 스레드라 스크립트 안에서는 전역 순서가 하나다.
--
-- KEYS[1]=seq  KEYS[2]=msgs  KEYS[3]=room
-- ARGV[1]=role ARGV[2]=nickname ARGV[3]=text ARGV[4]=at
-- ARGV[5]=channel ARGV[6]=ttlSeconds ARGV[7]=maxMessages
if redis.call('EXISTS', KEYS[3]) == 0 then
  return -1                                   -- 방이 없다. 유령 메시지를 만들지 않는다.
end

local seq = redis.call('INCR', KEYS[1])
-- type 을 넣어 서버가 직접 보내는 프레임(JOINED 등)과 형태를 맞춘다.
-- 클라이언트가 data.type 하나로 분기할 수 있어야 한다.
local msg = cjson.encode({
  type = 'MESSAGE', seq = seq, role = ARGV[1], nickname = ARGV[2], text = ARGV[3], at = ARGV[4]
})

redis.call('RPUSH', KEYS[2], msg)
redis.call('LTRIM', KEYS[2], -tonumber(ARGV[7]), -1)

-- 만료를 매번 다시 건다. 첫 호출에만 걸면 그때 실패한 키가 TTL 없이 영원히 남는다
-- (DailyQuota 에서 같은 이유로 같은 선택을 했다).
redis.call('EXPIRE', KEYS[1], ARGV[6])
redis.call('EXPIRE', KEYS[2], ARGV[6])
redis.call('EXPIRE', KEYS[3], ARGV[6])

redis.call('PUBLISH', ARGV[5], msg)
return seq