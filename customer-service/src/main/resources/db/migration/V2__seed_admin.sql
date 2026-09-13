-- Demo admin account. Password: Admin@123
INSERT INTO customers (id, full_name, email, document_number, password_hash, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Bank Admin', 'admin@bank.com', '00000000000',
        '$2a$10$Y3UMuww.Uej.mTBsMFA3ouILHIAF2vYjtx1XX33jsO3oXRnaKAUD.', now());

INSERT INTO customer_roles (customer_id, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'ROLE_ADMIN');
