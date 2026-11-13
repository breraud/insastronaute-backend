CREATE TABLE shop_reservation_series (
    id UUID PRIMARY KEY,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    location VARCHAR(255) NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_shop_reservation_series_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_shop_reservation_series_times CHECK (ends_at > starts_at)
);

CREATE TABLE shop_reservation_slots (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('EVENT', 'CUSTOM')),
    event_id UUID REFERENCES events(id) ON DELETE SET NULL,
    event_starts_at_snapshot TIMESTAMP WITH TIME ZONE,
    event_ends_at_snapshot TIMESTAMP WITH TIME ZONE,
    event_location_snapshot VARCHAR(255),
    series_id UUID REFERENCES shop_reservation_series(id) ON DELETE SET NULL,
    custom_starts_at TIMESTAMP WITH TIME ZONE,
    custom_ends_at TIMESTAMP WITH TIME ZONE,
    custom_location VARCHAR(255),
    note VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_shop_reservation_slot_source CHECK (
        (type = 'EVENT' AND (event_id IS NOT NULL OR active = FALSE) AND event_starts_at_snapshot IS NOT NULL AND custom_starts_at IS NULL AND custom_ends_at IS NULL AND custom_location IS NULL AND series_id IS NULL)
        OR
        (type = 'CUSTOM' AND event_id IS NULL AND event_starts_at_snapshot IS NULL AND event_ends_at_snapshot IS NULL AND event_location_snapshot IS NULL AND custom_starts_at IS NOT NULL AND custom_ends_at IS NOT NULL AND custom_location IS NOT NULL)
    ),
    CONSTRAINT ck_shop_reservation_slot_times CHECK (
        custom_ends_at IS NULL OR custom_ends_at > custom_starts_at
    )
);

CREATE INDEX ix_shop_reservation_slots_event ON shop_reservation_slots (event_id);
CREATE INDEX ix_shop_reservation_slots_series ON shop_reservation_slots (series_id);
CREATE INDEX ix_shop_reservation_slots_active_custom_start ON shop_reservation_slots (active, custom_starts_at);

ALTER TABLE shop_orders
    ADD COLUMN reservation_slot_id UUID REFERENCES shop_reservation_slots(id) ON DELETE RESTRICT;

CREATE INDEX ix_shop_orders_reservation_slot ON shop_orders (reservation_slot_id);

CREATE TABLE shop_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    lock_delay_minutes INTEGER NOT NULL CHECK (lock_delay_minutes >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO shop_settings (id, lock_delay_minutes, updated_at)
VALUES (1, 1440, CURRENT_TIMESTAMP);
