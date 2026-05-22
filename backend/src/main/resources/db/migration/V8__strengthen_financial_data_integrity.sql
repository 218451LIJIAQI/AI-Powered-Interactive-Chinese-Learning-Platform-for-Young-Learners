update orders set total_amount = 0.00 where total_amount is null;
update orders set currency_code = upper(trim(currency_code)) where currency_code is not null;
update order_items set unit_price = 0.00 where unit_price is null;
update order_items set line_total = 0.00 where line_total is null;
update payment_records set amount = 0.00 where amount is null;
update payment_records set currency_code = upper(trim(currency_code)) where currency_code is not null;
update refund_requests set amount = 0.00 where amount is null;
update teacher_earnings set gross_amount = 0.00 where gross_amount is null;
update teacher_earnings set commission_amount = 0.00 where commission_amount is null;
update teacher_earnings set net_amount = 0.00 where net_amount is null;

set @v8_orders_invalid_count = (
    select count(*) from orders
    where total_amount < 0 or currency_code is null or currency_code not regexp '^[A-Z]{3}$'
);
set @v8_sql = if(@v8_orders_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: orders contain negative totals or invalid currency codes''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_order_items_invalid_count = (
    select count(*) from order_items
    where quantity <= 0 or unit_price < 0 or line_total < 0
);
set @v8_sql = if(@v8_order_items_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: order_items contain non-positive quantities or negative amounts''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_payment_records_invalid_count = (
    select count(*) from payment_records
    where order_id is null or student_id is null or amount < 0 or currency_code is null or currency_code not regexp '^[A-Z]{3}$'
);
set @v8_sql = if(@v8_payment_records_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: payment_records contain missing owner/order, negative amounts, or invalid currency codes''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_payment_provider_duplicate_count = (
    select count(*) from (
        select provider_name, provider_reference
        from payment_records
        where provider_reference is not null
        group by provider_name, provider_reference
        having count(*) > 1
    ) duplicates
);
set @v8_sql = if(@v8_payment_provider_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: duplicate payment provider references exist''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_entitlements_invalid_count = (
    select count(*) from payment_entitlements
    where order_id is null or payment_record_id is null
);
set @v8_sql = if(@v8_entitlements_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: payment_entitlements contain missing order/payment links''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_entitlements_duplicate_count = (
    select count(*) from (
        select order_id, item_type, item_id, coalesce(feature_key, '') as feature_key_normalized
        from payment_entitlements
        group by order_id, item_type, item_id, coalesce(feature_key, '')
        having count(*) > 1
    ) duplicates
);
set @v8_sql = if(@v8_entitlements_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: duplicate payment entitlement tuples exist''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_refunds_invalid_count = (
    select count(*) from refund_requests
    where order_id is null or amount < 0
);
set @v8_sql = if(@v8_refunds_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: refund_requests contain missing orders or negative amounts''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_active_refund_duplicate_count = (
    select count(*) from (
        select order_id
        from refund_requests
        where refund_status in ('PENDING', 'APPROVED')
        group by order_id
        having count(*) > 1
    ) duplicates
);
set @v8_sql = if(@v8_active_refund_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: multiple active refund requests exist for an order''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_payouts_invalid_count = (
    select count(*) from payout_requests
    where amount is null or amount <= 0
);
set @v8_sql = if(@v8_payouts_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: payout_requests contain missing or non-positive amounts''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_teacher_earnings_invalid_count = (
    select count(*) from teacher_earnings
    where order_item_id is null
       or gross_amount < 0
       or commission_amount < 0
       or net_amount < 0
       or commission_amount > gross_amount
       or net_amount <> gross_amount - commission_amount
);
set @v8_sql = if(@v8_teacher_earnings_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: teacher_earnings contain missing order items or inconsistent amounts''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

set @v8_teacher_earnings_duplicate_count = (
    select count(*) from (
        select order_item_id
        from teacher_earnings
        group by order_item_id
        having count(*) > 1
    ) duplicates
);
set @v8_sql = if(@v8_teacher_earnings_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V8 blocked: duplicate teacher earning rows exist for an order item''');
prepare v8_stmt from @v8_sql;
execute v8_stmt;
deallocate prepare v8_stmt;

alter table payment_entitlements
    drop foreign key FK7d9y052u9mtl9qawornj5nwy7,
    drop foreign key FK7lm90rg92vjyw87e14w2jkwyx;

alter table payment_records
    drop foreign key FKcqu2d790mp868g1ogc678ygaj,
    drop foreign key FKqx0d20cubo6ak3qp79vdhpmnt;

alter table refund_requests
    drop foreign key FK452xm7hwgngbanwkdgs3601b1;

alter table teacher_earnings
    drop foreign key FKh0ecr76qq1quwldf7lapak879;

alter table orders
    add column version bigint not null default 0,
    modify total_amount decimal(10,2) not null,
    modify currency_code varchar(3) not null,
    add constraint chk_orders_total_amount_non_negative check (total_amount >= 0),
    add constraint chk_orders_currency_code_iso check (currency_code regexp '^[A-Z]{3}$');

alter table order_items
    modify unit_price decimal(10,2) not null,
    modify line_total decimal(10,2) not null,
    add constraint chk_order_items_quantity_positive check (quantity > 0),
    add constraint chk_order_items_unit_price_non_negative check (unit_price >= 0),
    add constraint chk_order_items_line_total_non_negative check (line_total >= 0);

alter table payment_records
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify student_id bigint not null,
    modify amount decimal(10,2) not null,
    modify currency_code varchar(3) not null,
    add constraint chk_payment_records_amount_non_negative check (amount >= 0),
    add constraint chk_payment_records_currency_code_iso check (currency_code regexp '^[A-Z]{3}$');

alter table payment_records
    add constraint uk_payment_records_provider_reference unique (provider_name, provider_reference);

alter table payment_entitlements
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify payment_record_id bigint not null,
    add column feature_key_normalized varchar(100) generated always as (coalesce(feature_key, '')) stored,
    add constraint uk_payment_entitlements_order_item_feature unique (order_id, item_type, item_id, feature_key_normalized);

alter table refund_requests
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify amount decimal(10,2) not null,
    add column active_refund_order_id bigint generated always as (case when refund_status in ('PENDING','APPROVED') then order_id else null end) stored,
    add constraint chk_refund_requests_amount_non_negative check (amount >= 0),
    add constraint uk_refund_requests_active_order unique (active_refund_order_id);

alter table payout_requests
    add column version bigint not null default 0,
    modify amount decimal(10,2) not null,
    add constraint chk_payout_requests_amount_positive check (amount > 0);

alter table teacher_earnings
    add column version bigint not null default 0,
    modify order_item_id bigint not null,
    modify gross_amount decimal(10,2) not null,
    modify commission_amount decimal(10,2) not null,
    modify net_amount decimal(10,2) not null,
    add constraint uk_teacher_earnings_order_item unique (order_item_id),
    add constraint chk_teacher_earnings_amounts_non_negative check (gross_amount >= 0 and commission_amount >= 0 and net_amount >= 0),
    add constraint chk_teacher_earnings_commission_not_above_gross check (commission_amount <= gross_amount),
    add constraint chk_teacher_earnings_net_matches_components check (net_amount = gross_amount - commission_amount);

alter table payment_records
    add constraint FKcqu2d790mp868g1ogc678ygaj foreign key (order_id) references orders (id),
    add constraint FKqx0d20cubo6ak3qp79vdhpmnt foreign key (student_id) references student_profiles (id);

alter table payment_entitlements
    add constraint FK7d9y052u9mtl9qawornj5nwy7 foreign key (order_id) references orders (id),
    add constraint FK7lm90rg92vjyw87e14w2jkwyx foreign key (payment_record_id) references payment_records (id);

alter table refund_requests
    add constraint FK452xm7hwgngbanwkdgs3601b1 foreign key (order_id) references orders (id);

alter table teacher_earnings
    add constraint FKh0ecr76qq1quwldf7lapak879 foreign key (order_item_id) references order_items (id);
