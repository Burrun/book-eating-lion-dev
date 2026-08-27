-- order_db.inventory.version 이 전 행 NULL 이라 주문이 100% 실패하던 문제.
--
-- 증상 - POST /api/orders 가 500. 주문/주문항목은 저장되고 ReviewPermissionGranted
-- 이벤트까지 발행된 뒤, 재고 차감 직전 auto-flush 에서 터진다:
--
--   java.lang.NullPointerException:
--     Cannot invoke "java.lang.Long.longValue()" because "current" is null
--       at org.hibernate.type.descriptor.java.LongJavaType.next(LongJavaType.java:213)
--       at org.hibernate.engine.internal.Versioning.increment
--       at DefaultFlushEntityEventListener.getNextVersion
--
-- Inventory 엔티티의 @Version 필드(Long)가 NULL 이면 Hibernate 가 다음 버전을
-- 계산하지 못한다. 낙관적 락 대상 엔티티를 UPDATE 하는 모든 경로가 막힌다.
--
-- 왜 NULL 이 됐나 - 배포 DB 의 inventory 테이블은 Hibernate(ddl-auto: update)가
-- 만들어서 version 이 nullable 이고 DEFAULT 도 없다. 시드 파일이 들고 있는
-- CREATE TABLE IF NOT EXISTS (90-demo-data.sql:421-427) 에는 NOT NULL DEFAULT 0 이
-- 붙어 있지만, 테이블이 이미 있으므로 그 정의는 적용되지 않는다. 그 상태에서
-- 시드의 INSERT 가 (book_id, stock) 만 채우니 version 이 NULL 로 남는다.
--
-- db-seed 의 preflight 는 이걸 못 잡는다. 그 검사는 "NOT NULL 인데 기본값이 없는
-- 컬럼에 값을 안 주는 INSERT" 를 찾는데, 여기서는 컬럼이 nullable 이라 조건에서 빠진다.
-- 그래서 컬럼 정의 자체를 시드가 의도한 모양으로 맞춘다.
--
-- member_db.cards 도 같은 @Version 패턴이지만 행이 전부 Hibernate 로 들어와서
-- NULL 이 없다. 시드가 cards 를 직접 INSERT 하게 되면 같은 문제가 난다.

DO $$
BEGIN
    -- 앱이 아직 안 떠서 테이블이 없을 수 있다(migrate 는 seed 보다 먼저 돈다).
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
         WHERE table_schema = 'order_db' AND table_name = 'inventory'
    ) THEN
        RAISE NOTICE 'skip: order_db.inventory 없음';
        RETURN;
    END IF;

    UPDATE order_db.inventory SET version = 0 WHERE version IS NULL;

    ALTER TABLE order_db.inventory ALTER COLUMN version SET DEFAULT 0;
    ALTER TABLE order_db.inventory ALTER COLUMN version SET NOT NULL;
END $$;
