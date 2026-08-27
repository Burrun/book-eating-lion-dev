-- 서비스 계정에 자기 스키마 USAGE/CREATE 를 부여한다.
--
-- 왜 필요한가 - 앱은 bookadmin(SUPERUSER)으로 붙으므로 평소 쿼리는 권한 검사를
-- 통과한다. 딱 하나 예외가 FK 무결성 검사다. PostgreSQL 은 RI trigger 의 확인
-- 쿼리를 **참조 대상 테이블 소유자** 권한으로 강등해서 실행한다:
--
--   ERROR: permission denied for schema catalog_db
--   QUERY: SELECT 1 FROM ONLY "catalog_db"."books" x WHERE "book_id" = $1 FOR KEY SHARE OF x
--
-- catalog_db 의 테이블 소유자는 catalog_svc 인데 그 계정에 스키마 USAGE 가
-- 없어서, superuser 로 접속했는데도 FK 가 걸린 INSERT 만 500 이 났다.
-- (UPDATE 는 FK 컬럼을 안 건드리면 RI 검사를 안 타므로 멀쩡했다 - 그래서
--  "이미 본 책은 되고 새 책만 안 되는" 형태로 보였다.)
--
-- 영향받던 자식 테이블 13개: recent_books, reviews, wishlists, restock_alerts,
-- product_inquiries, reading_progress, book_memos, book_swipes,
-- recommendation_exposures, categories, order_items, payments, member_coupons.
-- order_items/payments 가 포함되므로 주문·결제도 같이 막혀 있었다.
--
-- 내용은 db/postgres/00-init.sql 의 GRANT 와 같다. 그 파일은 docker-compose
-- initdb 전용이라 배포 DB 에는 적용된 적이 없다.
--
-- ddl-auto: update 를 유지하는 동안은 CREATE 도 함께 필요하다.

DO $$
DECLARE
    r record;
BEGIN
    -- 배포 DB 에 서비스 계정이 아직 없을 수 있다(docs/db-앱-서비스계정-분리.md).
    -- 없는 롤에 GRANT 하면 migrate 단계 전체가 멈추므로 존재하는 것만 처리한다.
    FOR r IN
        SELECT * FROM (VALUES
            ('catalog_db', 'catalog_svc'),
            ('order_db',   'order_svc'),
            ('member_db',  'member_svc'),
            ('ai_db',      'ai_svc')
        ) AS t(schema_name, role_name)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r.role_name) THEN
            RAISE NOTICE 'skip: role % 없음', r.role_name;
        ELSIF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = r.schema_name) THEN
            RAISE NOTICE 'skip: schema % 없음', r.schema_name;
        ELSE
            EXECUTE format('GRANT USAGE, CREATE ON SCHEMA %I TO %I', r.schema_name, r.role_name);
            RAISE NOTICE 'granted: % -> %', r.schema_name, r.role_name;
        END IF;
    END LOOP;
END $$;
