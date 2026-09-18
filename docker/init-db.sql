-- Initialize independent databases for microservices
CREATE DATABASE standing_order_db;
CREATE DATABASE execution_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;

GRANT ALL PRIVILEGES ON DATABASE standing_order_db TO ewb_user;
GRANT ALL PRIVILEGES ON DATABASE execution_db TO ewb_user;
GRANT ALL PRIVILEGES ON DATABASE payment_db TO ewb_user;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO ewb_user;
