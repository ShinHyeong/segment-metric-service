CREATE TABLE account (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    age INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    order_count INT NOT NULL
);
