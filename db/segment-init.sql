SET SESSION cte_max_recursion_depth = 10000;

CREATE TABLE segment (
    segment_id BIGINT PRIMARY KEY,
    conditions JSON COMMENT '세그먼트 타겟팅 조건 리스트 (JSON Array)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT check_json_format CHECK (JSON_VALID(conditions))
);

CREATE TABLE segment_daily_metric (
    id BIGINT AUTO_INCREMENT,
    segment_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    user_count BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    PRIMARY KEY (id, metric_date),

    UNIQUE KEY uk_segment_date (segment_id, metric_date),

    INDEX idx_segment_date_count (segment_id, metric_date, user_count)
)
PARTITION BY RANGE COLUMNS(metric_date) (
    PARTITION p2026_01 VALUES LESS THAN ('2026-02-01'),
    PARTITION p2026_02 VALUES LESS THAN ('2026-03-01'),
    PARTITION p2026_03 VALUES LESS THAN ('2026-04-01'),
    PARTITION p2026_04 VALUES LESS THAN ('2026-05-01'),
    PARTITION p2026_05 VALUES LESS THAN ('2026-06-01'),
    PARTITION p2026_06 VALUES LESS THAN ('2026-07-01'),
    PARTITION p_max VALUES LESS THAN MAXVALUE
);

DROP FUNCTION IF EXISTS gen_gender_cond;
DROP FUNCTION IF EXISTS gen_location_cond;
DROP FUNCTION IF EXISTS gen_age_cond;
DROP FUNCTION IF EXISTS gen_order_count_cond;

DELIMITER //

CREATE FUNCTION gen_gender_cond(n BIGINT) RETURNS JSON
DETERMINISTIC NO SQL
BEGIN
    DECLARE g VARCHAR(10);
    DECLARE r INT DEFAULT (n * 668265263)  MOD 100;
    IF r < 50 THEN
        SET g = 'MALE';
    ELSE
        SET g = 'FEMALE';
    END IF;
    RETURN JSON_ARRAY(
        JSON_OBJECT('category', 'gender', 'operator', 'EQUALS', 'value', g)
    );
END //

CREATE FUNCTION gen_location_cond(n BIGINT) RETURNS JSON
DETERMINISTIC NO SQL
BEGIN
    DECLARE loc VARCHAR(10);
    DECLARE r INT DEFAULT (n * 2654435761) MOD 1000;
    IF      r < 300 THEN SET loc = '서울';
    ELSEIF  r < 550 THEN SET loc = '경기';
    ELSEIF  r < 670 THEN SET loc = '부산';
    ELSEIF  r < 740 THEN SET loc = '인천';
    ELSEIF  r < 790 THEN SET loc = '대구';
    ELSEIF  r < 830 THEN SET loc = '대전';
    ELSEIF  r < 860 THEN SET loc = '광주';
    ELSEIF  r < 885 THEN SET loc = '울산';
    ELSEIF  r < 905 THEN SET loc = '충남';
    ELSEIF  r < 923 THEN SET loc = '충북';
    ELSEIF  r < 940 THEN SET loc = '경남';
    ELSEIF  r < 955 THEN SET loc = '경북';
    ELSEIF  r < 968 THEN SET loc = '전북';
    ELSEIF  r < 980 THEN SET loc = '전남';
    ELSEIF  r < 990 THEN SET loc = '강원';
    ELSEIF  r < 997 THEN SET loc = '제주';
    ELSE                 SET loc = '세종';
    END IF;
    RETURN JSON_ARRAY(
        JSON_OBJECT('category', 'location', 'operator', 'EQUALS', 'value', loc)
    );
END //

CREATE FUNCTION gen_age_cond(n BIGINT) RETURNS JSON
DETERMINISTIC NO SQL
BEGIN
		DECLARE r BIGINT DEFAULT (n * 2246822519);
    DECLARE op_choice INT DEFAULT r MOD 100;
		DECLARE range_idx INT;


    IF op_choice < 5 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'age', 'operator', 'EQUALS',
                'value', ELT(1 + (r MOD 4),
	                '16', '18', '19', '60'))
        );

    ELSEIF op_choice < 25 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'age', 'operator', 'GTE',
                'value', ELT(1 + (r MOD 8), '20', '25', '30', '40', '50', '55', '60', '65'))
        );

    ELSEIF op_choice < 45 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'age', 'operator', 'LT',
                'value', ELT(1 + (r MOD 6), '14', '19', '20', '30', '40', '50'))
        );

    ELSE
        SET range_idx = ELT(1 + (r MOD 22),
            '1','1','2','2','3','3','3','4','4',
            '5','5','5','5','6','6','6','6',
            '7','7','8','8','9') + 0;
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'age', 'operator', 'GTE',
                'value', ELT(range_idx, '14', '19', '20', '20', '30', '30', '40', '50', '60')),
            JSON_OBJECT('category', 'age', 'operator', 'LT',
                'value', ELT(range_idx, '20', '25', '30', '35', '40', '50', '60', '70', '100'))
        );
    END IF;
END //


CREATE FUNCTION gen_order_count_cond(n BIGINT) RETURNS JSON
DETERMINISTIC NO SQL
BEGIN
		DECLARE r BIGINT DEFAULT (n * 3266489917);
    DECLARE op_choice INT DEFAULT r MOD 100;
    DECLARE range_idx INT;


    IF op_choice < 3 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'order_count', 'operator', 'EQUALS',
                'value', ELT(1 + (r MOD 4), '0', '0', '0', '1'))
        );

    ELSEIF op_choice < 58 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'order_count', 'operator', 'GTE',
                'value', ELT(1 + (r MOD 7), '5', '10', '20', '30', '50', '100', '200'))
        );

    ELSEIF op_choice < 73 THEN
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'order_count', 'operator', 'LTE',
                'value', ELT(1 + (r MOD 5), '0', '1', '3', '5', '10'))
        );

    ELSE
        SET range_idx = ELT(1 + (r MOD 22),
            '1','1','2','2','3','3','3','3',
            '4','4','4','4','5','5','5','5',
            '6','6','6','7','7','7') + 0;
        RETURN JSON_ARRAY(
            JSON_OBJECT('category', 'order_count', 'operator', 'GTE',
                'value', ELT(range_idx, '1', '2', '6', '11', '21', '51', '101')),
            JSON_OBJECT('category', 'order_count', 'operator', 'LTE',
                'value', ELT(range_idx, '5', '5', '20', '30', '50', '100', '200'))
        );
    END IF;
END //

DELIMITER ;


INSERT INTO segment (segment_id, conditions, created_at)
WITH RECURSIVE seq(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 5000
)
SELECT
    n AS segment_id,
    CASE

        WHEN n <=  500 THEN gen_order_count_cond(n)
        WHEN n <=  950 THEN gen_location_cond(n)
        WHEN n <= 1250 THEN gen_age_cond(n)
        WHEN n <= 1500 THEN gen_gender_cond(n)


        WHEN n <= 2100 THEN JSON_MERGE_PRESERVE(gen_location_cond(n), gen_order_count_cond(n))
        WHEN n <= 2550 THEN JSON_MERGE_PRESERVE(gen_age_cond(n),      gen_gender_cond(n))
        WHEN n <= 2900 THEN JSON_MERGE_PRESERVE(gen_age_cond(n),      gen_location_cond(n))
        WHEN n <= 3200 THEN JSON_MERGE_PRESERVE(gen_gender_cond(n),   gen_location_cond(n))
        WHEN n <= 3400 THEN JSON_MERGE_PRESERVE(gen_age_cond(n),      gen_order_count_cond(n))
        WHEN n <= 3500 THEN JSON_MERGE_PRESERVE(gen_gender_cond(n),   gen_order_count_cond(n))


        WHEN n <= 3850 THEN JSON_MERGE_PRESERVE(gen_age_cond(n), gen_gender_cond(n), gen_location_cond(n))
        WHEN n <= 4150 THEN JSON_MERGE_PRESERVE(gen_age_cond(n), gen_location_cond(n), gen_order_count_cond(n))
        WHEN n <= 4350 THEN JSON_MERGE_PRESERVE(gen_gender_cond(n), gen_location_cond(n), gen_order_count_cond(n))
        WHEN n <= 4500 THEN JSON_MERGE_PRESERVE(gen_age_cond(n), gen_gender_cond(n), gen_order_count_cond(n))


        ELSE JSON_MERGE_PRESERVE(
                gen_age_cond(n),
                gen_gender_cond(n),
                gen_location_cond(n),
                gen_order_count_cond(n)
             )
    END AS conditions,
    DATE_SUB(CURRENT_TIMESTAMP, INTERVAL (n % 365) DAY) AS created_at
FROM seq;


INSERT INTO segment_daily_metric (segment_id, metric_date, user_count, status)
WITH RECURSIVE
    seg(s) AS (SELECT 1 UNION ALL SELECT s+1 FROM seg WHERE s < 5000),
    dt(d) AS (SELECT DATE('2026-01-01')
              UNION ALL
              SELECT d + INTERVAL 1 DAY FROM dt WHERE d < DATE('2026-04-30'))
SELECT
    seg.s AS segment_id,
    dt.d AS metric_date,
    1000 + (seg.s * 7 % 5000) + (DAYOFYEAR(dt.d) % 100) AS user_count,
    'INSERTED' AS status
FROM seg, dt;