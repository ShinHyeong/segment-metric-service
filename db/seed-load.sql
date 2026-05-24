LOAD DATA INFILE '/var/lib/mysql-files/accounts.csv'
INTO TABLE account
FIELDS TERMINATED BY ','
ENCLOSED BY '"'
LINES TERMINATED BY '\n'
(name, gender, age, location, order_count);