CREATE TABLE account (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    order_count INT NOT NULL
);

CREATE TABLE account_cube (
	cube_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    location VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NOT NULL,

    age_start_val INT NOT NULL DEFAULT 0,
    age_end_val INT NOT NULL DEFAULT 999,

    order_count_start_val INT NOT NULL DEFAULT 0,
    order_count_end_val INT NOT NULL DEFAULT 2147483647,

    user_count BIGINT NOT NULL DEFAULT 0
);