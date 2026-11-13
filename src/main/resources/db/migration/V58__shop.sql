CREATE TABLE shop_products (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    out_of_stock BOOLEAN NOT NULL DEFAULT FALSE,
    article_id UUID REFERENCES articles(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE shop_product_photos (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE CASCADE,
    url VARCHAR(1024) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_shop_product_photos_order UNIQUE (product_id, display_order)
);

CREATE TABLE shop_carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE shop_cart_lines (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES shop_carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_shop_cart_lines_cart_product UNIQUE (cart_id, product_id)
);

CREATE TABLE shop_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_shop_orders_status CHECK (status IN ('PENDING', 'VALIDATED', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE shop_order_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES shop_orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES shop_products(id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX ix_shop_orders_user_created_at ON shop_orders (user_id, created_at DESC);
CREATE INDEX ix_shop_orders_status_created_at ON shop_orders (status, created_at ASC);
