DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOR table_name IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        AND tablename NOT IN ('schema_version', 'flyway_schema_history', 'products')
        ORDER BY tablename
    ) LOOP
        EXECUTE 'TRUNCATE TABLE ' || table_name || ' CASCADE;';
        RAISE NOTICE 'Truncated table: %', table_name;
    END LOOP;
END $$;
