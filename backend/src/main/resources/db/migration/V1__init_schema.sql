-- Người dùng. Bảng "nền" để mô phỏng 100.000 user thật.
CREATE TABLE users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    email      VARCHAR(120) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;

-- Sản phẩm flash sale. available_stock là "hot row": mọi request mua đều tranh nhau 1 dòng này.
CREATE TABLE flash_sale_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(200) NOT NULL,
    price           BIGINT       NOT NULL, -- VND, lưu số nguyên, KHÔNG dùng float/double cho tiền
    total_stock     INT          NOT NULL,
    available_stock INT          NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0, -- dùng cho optimistic lock
    PRIMARY KEY (id),
    -- Lưới an toàn cuối cùng: kho không bao giờ âm.
    -- Lưu ý: CHECK này KHÔNG chặn được lỗi "lost update" của chiến lược NAIVE (xem KNOWLEDGE.md).
    CONSTRAINT chk_item_stock CHECK (available_stock >= 0 AND available_stock <= total_stock)
) ENGINE = InnoDB;

CREATE TABLE orders (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    item_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    strategy   VARCHAR(30) NOT NULL, -- chiến lược nào đã tạo đơn này (để so sánh)
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Mỗi user chỉ mua 1 lần / sản phẩm. Ràng buộc ở DB => đúng kể cả khi có 10 instance app.
    UNIQUE KEY uk_orders_user_item (user_id, item_id),
    KEY idx_orders_item (item_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_item FOREIGN KEY (item_id) REFERENCES flash_sale_item (id)
) ENGINE = InnoDB;
