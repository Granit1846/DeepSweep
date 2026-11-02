-- Pro licence pool and paid orders for the Telegram delivery bot (see docs/BOT.md).
-- The app verifies codes offline: rows here are only a stock of already signed codes.

create table if not exists public.codes (
    code      text primary key,
    order_id  text,
    issued_at timestamptz not null default now(),
    used_at   timestamptz,
    used_by   bigint
);

-- one code per order; that is what makes a repeated request idempotent
create unique index if not exists codes_order_id_key
    on public.codes (order_id) where order_id is not null;

create table if not exists public.payments (
    order_id     text primary key,
    email        text        not null,
    amount       int         not null check (amount > 0),
    paid_at      timestamptz not null default now(),
    delivered_at timestamptz
);

create index if not exists payments_email_idx on public.payments (email);

-- e-mails come from a donation comment or a CSV import, so normalise them once
create or replace function public.normalize_email() returns trigger
language plpgsql as $$
begin
    new.email := lower(trim(new.email));
    return new;
end $$;
drop trigger if exists payments_normalize on public.payments;
create trigger payments_normalize
    before insert or update on public.payments
    for each row execute function public.normalize_email();

alter table public.codes    enable row level security;
alter table public.payments enable row level security;
-- no policies on purpose: only service_role (the edge function) touches these tables

grant select, insert, update on public.codes    to service_role;
grant select, insert, update on public.payments to service_role;

/**
 * Hands out one unused code for an order and marks the order delivered.
 * Safe to call twice: an order that already has a code gets that same code back.
 * Raises 'pool_empty' when the stock is used up.
 */
create or replace function public.claim_code(p_order text, p_chat bigint)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code text;
begin
    select code into v_code from codes where order_id = p_order;
    if found then
        return v_code;
    end if;

    update codes
       set used_at = now(), used_by = p_chat, order_id = p_order
     where code = (
         select code from codes
          where used_at is null and order_id is null
          order by issued_at
          limit 1
          for update skip locked
     )
    returning code into v_code;

    if v_code is null then
        raise exception 'pool_empty' using errcode = 'P0001';
    end if;

    update payments
       set delivered_at = now()
     where order_id = p_order and delivered_at is null;

    return v_code;
end $$;

revoke all on function public.claim_code(text, bigint) from public;
grant execute on function public.claim_code(text, bigint) to service_role;
