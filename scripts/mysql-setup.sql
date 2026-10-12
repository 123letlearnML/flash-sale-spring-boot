-- Tạo database + user riêng cho dự án. KHÔNG dùng root cho ứng dụng (nguyên tắc least privilege).
-- Chạy tự động bởi scripts/dev-mysql.ps1, hoặc tay: mysql -u root -p < scripts/mysql-setup.sql
-- Mật khẩu dưới đây CHỈ dành cho máy dev local.

CREATE DATABASE IF NOT EXISTS flash_sale      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS flash_sale_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'devuser'@'localhost' IDENTIFIED BY 'devuser_local_123';
GRANT ALL PRIVILEGES ON flash_sale.*      TO 'devuser'@'localhost';
GRANT ALL PRIVILEGES ON flash_sale_test.* TO 'devuser'@'localhost';

-- Để tự học cách soi khoá: SHOW ENGINE INNODB STATUS, performance_schema.data_locks
GRANT PROCESS ON *.* TO 'devuser'@'localhost';
GRANT SELECT ON performance_schema.* TO 'devuser'@'localhost';
