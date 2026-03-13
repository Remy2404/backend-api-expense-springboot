--
-- PostgreSQL database dump
--

\restrict ymYj4eHs34xrBJk50CeXOrEuayZmKPWgV5DbpD4bBaRuLHPgKCZukQhbUKNxjAB

-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;

SET lock_timeout = 0;

SET idle_in_transaction_session_timeout = 0;

SET transaction_timeout = 0;

SET client_encoding = 'UTF8';

SET standard_conforming_strings = on;

SELECT pg_catalog.set_config ('search_path', '', false);

SET check_function_bodies = false;

SET xmloption = content;

SET client_min_messages = warning;

SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: pg_database_owner
--

CREATE SCHEMA public;

ALTER SCHEMA public OWNER TO pg_database_owner;

--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: pg_database_owner
--

COMMENT ON SCHEMA public IS 'standard public schema';

--
-- Name: apply_expense_to_balance(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.apply_expense_to_balance() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$

BEGIN

  -- Try to update the balance if profile exists

  UPDATE profiles

  SET current_balance = current_balance - NEW.amount

  WHERE firebase_uid = NEW.firebase_uid;

  

  -- Always return NEW regardless of whether update happened

  RETURN NEW;

END;

$$;

ALTER FUNCTION public.apply_expense_to_balance() OWNER TO postgres;

--
-- Name: create_initial_balance(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.create_initial_balance() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$

BEGIN

  -- Insert profile for new user

  INSERT INTO profiles (firebase_uid, email, initial_balance, current_balance)

  VALUES (NEW.id, NEW.email, 0, 0)

  ON CONFLICT (firebase_uid) DO NOTHING;

  

  -- Always return NEW

  RETURN NEW;

END;

$$;

ALTER FUNCTION public.create_initial_balance() OWNER TO postgres;

--
-- Name: firebase_uid(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.firebase_uid() RETURNS text
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
  uid TEXT;
BEGIN
  -- Try to get UID from JWT claims
  uid := COALESCE(
    current_setting('request.jwt.claims', true)::json->>'sub',
    current_setting('request.jwt.claims', true)::json->>'user_id'
  );
  
  -- Return the UID (can be NULL for service role)
  RETURN uid;
END;
$$;

ALTER FUNCTION public.firebase_uid() OWNER TO postgres;

--
-- Name: FUNCTION firebase_uid(); Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON FUNCTION public.firebase_uid () IS 'Extracts Firebase UID from JWT claims with secure search_path';

--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_catalog'
    AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

ALTER FUNCTION public.update_updated_at_column() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ai_chat_messages; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_chat_messages (
    id uuid NOT NULL,
    firebase_uid character varying(255) NOT NULL,
    role character varying(32) NOT NULL,
    content text NOT NULL,
    request_id character varying(128),
    created_at timestamp without time zone NOT NULL
);

ALTER TABLE public.ai_chat_messages OWNER TO postgres;

--
-- Name: ai_corrections; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_corrections (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    expense_id uuid NOT NULL,
    original_category_id uuid,
    corrected_category_id uuid NOT NULL,
    original_amount numeric(12, 2),
    corrected_amount double precision,
    original_merchant text,
    corrected_merchant character varying(255),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

ALTER TABLE public.ai_corrections OWNER TO postgres;

--
-- Name: ai_insights; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_insights (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    insight_type text NOT NULL,
    period_start timestamp with time zone NOT NULL,
    period_end timestamp with time zone NOT NULL,
    summary_text text NOT NULL,
    data_snapshot jsonb,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    is_read boolean DEFAULT false NOT NULL,
    CONSTRAINT ai_insights_insight_type_check CHECK (
        (
            insight_type = ANY (
                ARRAY[
                    'daily'::text,
                    'weekly'::text,
                    'monthly'::text,
                    'alert'::text
                ]
            )
        )
    )
);

ALTER TABLE public.ai_insights OWNER TO postgres;

--
-- Name: ai_memories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ai_memories (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    merchant text NOT NULL,
    resolved_category_id uuid,
    override_count integer DEFAULT 1 NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    last_corrected_at timestamp with time zone
);

ALTER TABLE public.ai_memories OWNER TO postgres;

--
-- Name: auth_refresh_tokens; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.auth_refresh_tokens (
    id uuid NOT NULL,
    family_id uuid NOT NULL,
    firebase_uid text NOT NULL,
    token_hash text NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone,
    revoked_at timestamp with time zone,
    replaced_by_token_id uuid,
    created_by_ip text,
    user_agent text
);

ALTER TABLE public.auth_refresh_tokens OWNER TO postgres;

--
-- Name: bill_split_expenses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_split_expenses (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    group_id uuid NOT NULL,
    title text NOT NULL,
    amount numeric NOT NULL,
    currency text DEFAULT 'USD'::text NOT NULL,
    payer_participant_id uuid NOT NULL,
    split_type text DEFAULT 'equal'::text NOT NULL,
    date timestamp with time zone DEFAULT now() NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    CONSTRAINT bill_split_expenses_split_type_check CHECK (
        (
            split_type = ANY (
                ARRAY[
                    'equal'::text,
                    'custom'::text,
                    'percentage'::text
                ]
            )
        )
    )
);

ALTER TABLE public.bill_split_expenses OWNER TO postgres;

--
-- Name: bill_split_groups; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_split_groups (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    name text NOT NULL,
    currency text DEFAULT 'USD'::text NOT NULL,
    created_by text NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone
);

ALTER TABLE public.bill_split_groups OWNER TO postgres;

--
-- Name: bill_split_participants; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_split_participants (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    group_id uuid NOT NULL,
    name text NOT NULL,
    user_id text,
    created_at timestamp with time zone DEFAULT now()
);

ALTER TABLE public.bill_split_participants OWNER TO postgres;

--
-- Name: bill_split_settlements; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_split_settlements (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    group_id uuid NOT NULL,
    expense_id uuid,
    participant_id uuid NOT NULL,
    amount numeric NOT NULL,
    method text NOT NULL,
    note text,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT bill_split_settlements_method_check CHECK (
        (
            method = ANY (
                ARRAY[
                    'cash'::text,
                    'transfer'::text,
                    'other'::text
                ]
            )
        )
    )
);

ALTER TABLE public.bill_split_settlements OWNER TO postgres;

--
-- Name: bill_split_shares; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_split_shares (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    expense_id uuid NOT NULL,
    participant_id uuid NOT NULL,
    amount numeric NOT NULL,
    is_settled boolean DEFAULT false,
    settled_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now()
);

ALTER TABLE public.bill_split_shares OWNER TO postgres;

--
-- Name: budgets; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.budgets (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    month text NOT NULL,
    total_amount numeric(12, 2) NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    retry_count integer DEFAULT 0,
    last_error text,
    CONSTRAINT budgets_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.budgets OWNER TO postgres;

--
-- Name: COLUMN budgets.sync_status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.sync_status IS 'Tracks whether the record has been synced to cloud: pending or synced';

--
-- Name: COLUMN budgets.synced_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.synced_at IS 'Timestamp of last successful sync to cloud';

--
-- Name: COLUMN budgets.is_deleted; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.is_deleted IS 'Soft delete flag - TRUE means record is deleted but kept for sync';

--
-- Name: COLUMN budgets.deleted_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.deleted_at IS 'Timestamp when record was soft-deleted';

--
-- Name: COLUMN budgets.retry_count; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.retry_count IS 'Number of failed sync attempts';

--
-- Name: COLUMN budgets.last_error; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.budgets.last_error IS 'Last sync error message for debugging';

--
-- Name: categories; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.categories (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    name text NOT NULL,
    icon text NOT NULL,
    color text NOT NULL,
    is_default boolean DEFAULT false,
    sort_order integer DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    retry_count integer DEFAULT 0,
    last_error text,
    category_type text DEFAULT 'EXPENSE'::text NOT NULL,
    CONSTRAINT categories_category_type_chk CHECK (
        (
            category_type = ANY (
                ARRAY[
                    'EXPENSE'::text,
                    'INCOME'::text
                ]
            )
        )
    ),
    CONSTRAINT categories_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.categories OWNER TO postgres;

--
-- Name: COLUMN categories.sync_status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.sync_status IS 'Tracks whether the record has been synced to cloud: pending or synced';

--
-- Name: COLUMN categories.synced_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.synced_at IS 'Timestamp of last successful sync to cloud';

--
-- Name: COLUMN categories.is_deleted; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.is_deleted IS 'Soft delete flag - TRUE means record is deleted but kept for sync';

--
-- Name: COLUMN categories.deleted_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.deleted_at IS 'Timestamp when record was soft-deleted';

--
-- Name: COLUMN categories.retry_count; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.retry_count IS 'Number of failed sync attempts';

--
-- Name: COLUMN categories.last_error; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.categories.last_error IS 'Last sync error message for debugging';

--
-- Name: category_budgets; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.category_budgets (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    budget_id uuid NOT NULL,
    category_id uuid,
    amount numeric(12, 2) NOT NULL,
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    CONSTRAINT category_budgets_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.category_budgets OWNER TO postgres;

--
-- Name: expenses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.expenses (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    amount double precision NOT NULL,
    category_id uuid,
    date timestamp with time zone NOT NULL,
    notes text,
    recurring_expense_id uuid,
    currency text DEFAULT 'USD'::text,
    original_amount numeric(12, 2),
    exchange_rate numeric(18, 8),
    rate_source text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    retry_count integer DEFAULT 0,
    last_error text,
    merchant text,
    ai_category_id uuid,
    ai_confidence double precision,
    ai_source text,
    ai_last_updated timestamp with time zone,
    note_summary character varying(255),
    receipt_paths text [],
    transaction_type text DEFAULT 'EXPENSE'::text NOT NULL,
    CONSTRAINT expenses_ai_source_check CHECK (
        (
            (ai_source IS NULL)
            OR (
                ai_source = ANY (
                    ARRAY[
                        'memory'::text,
                        'gemini'::text,
                        'gemini_vision'::text,
                        'manual'::text
                    ]
                )
            )
        )
    ),
    CONSTRAINT expenses_rate_source_check CHECK (
        (
            rate_source = ANY (
                ARRAY[
                    'api'::text,
                    'manual'::text,
                    'cached'::text
                ]
            )
        )
    ),
    CONSTRAINT expenses_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    ),
    CONSTRAINT expenses_transaction_type_chk CHECK (
        (
            transaction_type = ANY (
                ARRAY[
                    'EXPENSE'::text,
                    'INCOME'::text
                ]
            )
        )
    )
);

ALTER TABLE public.expenses OWNER TO postgres;

--
-- Name: COLUMN expenses.sync_status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.sync_status IS 'Tracks whether the record has been synced to cloud: pending or synced';

--
-- Name: COLUMN expenses.synced_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.synced_at IS 'Timestamp of last successful sync to cloud';

--
-- Name: COLUMN expenses.is_deleted; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.is_deleted IS 'Soft delete flag - TRUE means record is deleted but kept for sync';

--
-- Name: COLUMN expenses.deleted_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.deleted_at IS 'Timestamp when record was soft-deleted';

--
-- Name: COLUMN expenses.retry_count; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.retry_count IS 'Number of failed sync attempts';

--
-- Name: COLUMN expenses.last_error; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.expenses.last_error IS 'Last sync error message for debugging';

--
-- Name: goal_transactions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.goal_transactions (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    goal_id uuid NOT NULL,
    amount numeric(12, 2) NOT NULL,
    type text NOT NULL,
    note text,
    date timestamp with time zone DEFAULT now(),
    CONSTRAINT goal_transactions_type_check CHECK (
        (
            type = ANY (
                ARRAY[
                    'deposit'::text,
                    'withdraw'::text
                ]
            )
        )
    )
);

ALTER TABLE public.goal_transactions OWNER TO postgres;

--
-- Name: profiles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.profiles (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    email text,
    display_name text,
    photo_url text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    initial_balance numeric(12, 2) DEFAULT 0,
    current_balance numeric(12, 2) DEFAULT 0,
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    ai_enabled boolean DEFAULT false NOT NULL,
    risk_level text DEFAULT 'low'::text NOT NULL,
    role text DEFAULT 'USER'::text NOT NULL,
    CONSTRAINT profiles_risk_level_check CHECK (
        (
            risk_level = ANY (
                ARRAY[
                    'low'::text,
                    'medium'::text,
                    'high'::text
                ]
            )
        )
    ),
    CONSTRAINT profiles_role_check CHECK (
        (
            role = ANY (
                ARRAY['USER'::text, 'ADMIN'::text]
            )
        )
    ),
    CONSTRAINT profiles_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.profiles OWNER TO postgres;

--
-- Name: COLUMN profiles.initial_balance; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.profiles.initial_balance IS 'Initial account balance set by user during setup';

--
-- Name: COLUMN profiles.current_balance; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.profiles.current_balance IS 'Current account balance (initial_balance - total_expenses)';

--
-- Name: recurring_expenses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.recurring_expenses (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    amount numeric(12, 2) NOT NULL,
    category_id uuid,
    notes text,
    frequency text NOT NULL,
    currency text DEFAULT 'USD'::text,
    original_amount numeric(12, 2),
    exchange_rate numeric(18, 8),
    start_date timestamp with time zone NOT NULL,
    end_date timestamp with time zone,
    last_generated timestamp with time zone,
    next_due_date timestamp with time zone NOT NULL,
    is_active boolean DEFAULT true,
    notification_enabled boolean DEFAULT false,
    notification_days_before integer DEFAULT 1,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    retry_count integer DEFAULT 0,
    last_error text,
    CONSTRAINT recurring_expenses_frequency_check CHECK (
        (
            frequency = ANY (
                ARRAY[
                    'daily'::text,
                    'weekly'::text,
                    'biweekly'::text,
                    'monthly'::text,
                    'yearly'::text
                ]
            )
        )
    ),
    CONSTRAINT recurring_expenses_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.recurring_expenses OWNER TO postgres;

--
-- Name: COLUMN recurring_expenses.sync_status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.sync_status IS 'Tracks whether the record has been synced to cloud: pending or synced';

--
-- Name: COLUMN recurring_expenses.synced_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.synced_at IS 'Timestamp of last successful sync to cloud';

--
-- Name: COLUMN recurring_expenses.is_deleted; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.is_deleted IS 'Soft delete flag - TRUE means record is deleted but kept for sync';

--
-- Name: COLUMN recurring_expenses.deleted_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.deleted_at IS 'Timestamp when record was soft-deleted';

--
-- Name: COLUMN recurring_expenses.retry_count; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.retry_count IS 'Number of failed sync attempts';

--
-- Name: COLUMN recurring_expenses.last_error; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.recurring_expenses.last_error IS 'Last sync error message for debugging';

--
-- Name: savings_goals; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.savings_goals (
    id uuid DEFAULT gen_random_uuid () NOT NULL,
    firebase_uid text NOT NULL,
    name text NOT NULL,
    target_amount numeric(12, 2) NOT NULL,
    current_amount numeric(12, 2) DEFAULT 0,
    deadline timestamp with time zone,
    color text NOT NULL,
    icon text NOT NULL,
    is_archived boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    sync_status text DEFAULT 'pending'::text,
    synced_at timestamp with time zone,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone,
    retry_count integer DEFAULT 0,
    last_error text,
    CONSTRAINT savings_goals_sync_status_check CHECK (
        (
            sync_status = ANY (
                ARRAY[
                    'pending'::text,
                    'synced'::text
                ]
            )
        )
    )
);

ALTER TABLE public.savings_goals OWNER TO postgres;

--
-- Name: COLUMN savings_goals.sync_status; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.sync_status IS 'Tracks whether the record has been synced to cloud: pending or synced';

--
-- Name: COLUMN savings_goals.synced_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.synced_at IS 'Timestamp of last successful sync to cloud';

--
-- Name: COLUMN savings_goals.is_deleted; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.is_deleted IS 'Soft delete flag - TRUE means record is deleted but kept for sync';

--
-- Name: COLUMN savings_goals.deleted_at; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.deleted_at IS 'Timestamp when record was soft-deleted';

--
-- Name: COLUMN savings_goals.retry_count; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.retry_count IS 'Number of failed sync attempts';

--
-- Name: COLUMN savings_goals.last_error; Type: COMMENT; Schema: public; Owner: postgres
--

COMMENT ON COLUMN public.savings_goals.last_error IS 'Last sync error message for debugging';

--
-- Name: ai_chat_messages ai_chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_chat_messages
ADD CONSTRAINT ai_chat_messages_pkey PRIMARY KEY (id);

--
-- Name: ai_corrections ai_corrections_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_corrections
ADD CONSTRAINT ai_corrections_pkey PRIMARY KEY (id);

--
-- Name: ai_insights ai_insights_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_insights
ADD CONSTRAINT ai_insights_pkey PRIMARY KEY (id);

--
-- Name: ai_memories ai_memory_firebase_uid_merchant_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_memories
ADD CONSTRAINT ai_memory_firebase_uid_merchant_key UNIQUE (firebase_uid, merchant);

--
-- Name: ai_memories ai_memory_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_memories
ADD CONSTRAINT ai_memory_pkey PRIMARY KEY (id);

--
-- Name: auth_refresh_tokens auth_refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auth_refresh_tokens
ADD CONSTRAINT auth_refresh_tokens_pkey PRIMARY KEY (id);

--
-- Name: bill_split_expenses bill_split_expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_expenses
ADD CONSTRAINT bill_split_expenses_pkey PRIMARY KEY (id);

--
-- Name: bill_split_groups bill_split_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_groups
ADD CONSTRAINT bill_split_groups_pkey PRIMARY KEY (id);

--
-- Name: bill_split_participants bill_split_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_participants
ADD CONSTRAINT bill_split_participants_pkey PRIMARY KEY (id);

--
-- Name: bill_split_settlements bill_split_settlements_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_settlements
ADD CONSTRAINT bill_split_settlements_pkey PRIMARY KEY (id);

--
-- Name: bill_split_shares bill_split_shares_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_shares
ADD CONSTRAINT bill_split_shares_pkey PRIMARY KEY (id);

--
-- Name: budgets budgets_firebase_uid_month_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budgets
ADD CONSTRAINT budgets_firebase_uid_month_key UNIQUE (firebase_uid, month);

--
-- Name: budgets budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budgets
ADD CONSTRAINT budgets_pkey PRIMARY KEY (id);

--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categories
ADD CONSTRAINT categories_pkey PRIMARY KEY (id);

--
-- Name: category_budgets category_budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.category_budgets
ADD CONSTRAINT category_budgets_pkey PRIMARY KEY (id);

--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);

--
-- Name: goal_transactions goal_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goal_transactions
ADD CONSTRAINT goal_transactions_pkey PRIMARY KEY (id);

--
-- Name: profiles profiles_firebase_uid_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.profiles
ADD CONSTRAINT profiles_firebase_uid_key UNIQUE (firebase_uid);

--
-- Name: profiles profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.profiles
ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);

--
-- Name: recurring_expenses recurring_expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurring_expenses
ADD CONSTRAINT recurring_expenses_pkey PRIMARY KEY (id);

--
-- Name: savings_goals savings_goals_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.savings_goals
ADD CONSTRAINT savings_goals_pkey PRIMARY KEY (id);

--
-- Name: categories_user_name_unique; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX categories_user_name_unique ON public.categories USING btree (
    firebase_uid,
    name,
    category_type
)
WHERE (is_deleted = false);

--
-- Name: idx_ai_chat_messages_uid_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_chat_messages_uid_created_at ON public.ai_chat_messages USING btree (firebase_uid, created_at DESC);

--
-- Name: idx_ai_corrections_corrected_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_corrections_corrected_category_id ON public.ai_corrections USING btree (corrected_category_id);

--
-- Name: idx_ai_corrections_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_corrections_created_at ON public.ai_corrections USING btree (created_at DESC);

--
-- Name: idx_ai_corrections_expense_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_corrections_expense_id ON public.ai_corrections USING btree (expense_id);

--
-- Name: idx_ai_corrections_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_corrections_firebase_uid ON public.ai_corrections USING btree (firebase_uid);

--
-- Name: idx_ai_corrections_original_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_corrections_original_category_id ON public.ai_corrections USING btree (original_category_id);

--
-- Name: idx_ai_insights_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_insights_created_at ON public.ai_insights USING btree (created_at DESC);

--
-- Name: idx_ai_insights_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_insights_firebase_uid ON public.ai_insights USING btree (firebase_uid);

--
-- Name: idx_ai_insights_type_period; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_insights_type_period ON public.ai_insights USING btree (
    insight_type,
    period_start,
    period_end
);

--
-- Name: idx_ai_memories_resolved_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_memories_resolved_category_id ON public.ai_memories USING btree (resolved_category_id);

--
-- Name: idx_ai_memory_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_memory_firebase_uid ON public.ai_memories USING btree (firebase_uid);

--
-- Name: idx_ai_memory_last_used_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ai_memory_last_used_at ON public.ai_memories USING btree (last_used_at DESC);

--
-- Name: idx_auth_refresh_tokens_expires_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_auth_refresh_tokens_expires_at ON public.auth_refresh_tokens USING btree (expires_at);

--
-- Name: idx_auth_refresh_tokens_family_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_auth_refresh_tokens_family_id ON public.auth_refresh_tokens USING btree (family_id);

--
-- Name: idx_auth_refresh_tokens_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_auth_refresh_tokens_firebase_uid ON public.auth_refresh_tokens USING btree (firebase_uid);

--
-- Name: idx_bill_split_expenses_group_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_expenses_group_id ON public.bill_split_expenses USING btree (group_id);

--
-- Name: idx_bill_split_expenses_payer_participant_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_expenses_payer_participant_id ON public.bill_split_expenses USING btree (payer_participant_id);

--
-- Name: idx_bill_split_participants_group_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_participants_group_id ON public.bill_split_participants USING btree (group_id);

--
-- Name: idx_bill_split_settlements_expense_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_settlements_expense_id ON public.bill_split_settlements USING btree (expense_id);

--
-- Name: idx_bill_split_settlements_group_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_settlements_group_id ON public.bill_split_settlements USING btree (group_id);

--
-- Name: idx_bill_split_settlements_participant_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_settlements_participant_id ON public.bill_split_settlements USING btree (participant_id);

--
-- Name: idx_bill_split_shares_expense_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_shares_expense_id ON public.bill_split_shares USING btree (expense_id);

--
-- Name: idx_bill_split_shares_participant_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_split_shares_participant_id ON public.bill_split_shares USING btree (participant_id);

--
-- Name: idx_budgets_deleted_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_deleted_at ON public.budgets USING btree (deleted_at)
WHERE (is_deleted = true);

--
-- Name: idx_budgets_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_firebase_uid ON public.budgets USING btree (firebase_uid);

--
-- Name: idx_budgets_is_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_is_deleted ON public.budgets USING btree (is_deleted);

--
-- Name: idx_budgets_month; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_month ON public.budgets USING btree (month);

--
-- Name: idx_budgets_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_sync_status ON public.budgets USING btree (sync_status);

--
-- Name: idx_budgets_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_budgets_user ON public.budgets USING btree (firebase_uid);

--
-- Name: idx_categories_deleted_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_categories_deleted_at ON public.categories USING btree (deleted_at)
WHERE (is_deleted = true);

--
-- Name: idx_categories_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_categories_firebase_uid ON public.categories USING btree (firebase_uid);

--
-- Name: idx_categories_is_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_categories_is_deleted ON public.categories USING btree (is_deleted);

--
-- Name: idx_categories_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_categories_sync_status ON public.categories USING btree (sync_status);

--
-- Name: idx_categories_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_categories_user ON public.categories USING btree (firebase_uid);

--
-- Name: idx_category_budgets_budget_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_category_budgets_budget_id ON public.category_budgets USING btree (budget_id);

--
-- Name: idx_category_budgets_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_category_budgets_category_id ON public.category_budgets USING btree (category_id);

--
-- Name: idx_category_budgets_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_category_budgets_sync_status ON public.category_budgets USING btree (sync_status);

--
-- Name: idx_expenses_ai_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_ai_category_id ON public.expenses USING btree (ai_category_id);

--
-- Name: idx_expenses_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_category_id ON public.expenses USING btree (category_id);

--
-- Name: idx_expenses_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_date ON public.expenses USING btree (date);

--
-- Name: idx_expenses_deleted_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_deleted_at ON public.expenses USING btree (deleted_at)
WHERE (is_deleted = true);

--
-- Name: idx_expenses_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_firebase_uid ON public.expenses USING btree (firebase_uid);

--
-- Name: idx_expenses_is_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_is_deleted ON public.expenses USING btree (is_deleted);

--
-- Name: idx_expenses_merchant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_merchant ON public.expenses USING btree (merchant);

--
-- Name: idx_expenses_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_sync_status ON public.expenses USING btree (sync_status);

--
-- Name: idx_expenses_user_sync; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_expenses_user_sync ON public.expenses USING btree (firebase_uid, sync_status);

--
-- Name: idx_goal_transactions_goal_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_goal_transactions_goal_id ON public.goal_transactions USING btree (goal_id);

--
-- Name: idx_profiles_current_balance; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_profiles_current_balance ON public.profiles USING btree (current_balance);

--
-- Name: idx_profiles_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_profiles_firebase_uid ON public.profiles USING btree (firebase_uid);

--
-- Name: idx_profiles_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_profiles_sync_status ON public.profiles USING btree (sync_status);

--
-- Name: idx_recurring_expenses_category_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_category_id ON public.recurring_expenses USING btree (category_id);

--
-- Name: idx_recurring_expenses_deleted_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_deleted_at ON public.recurring_expenses USING btree (deleted_at)
WHERE (is_deleted = true);

--
-- Name: idx_recurring_expenses_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_firebase_uid ON public.recurring_expenses USING btree (firebase_uid);

--
-- Name: idx_recurring_expenses_is_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_is_deleted ON public.recurring_expenses USING btree (is_deleted);

--
-- Name: idx_recurring_expenses_next_due; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_next_due ON public.recurring_expenses USING btree (next_due_date);

--
-- Name: idx_recurring_expenses_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_recurring_expenses_sync_status ON public.recurring_expenses USING btree (sync_status);

--
-- Name: idx_savings_goals_deleted_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_savings_goals_deleted_at ON public.savings_goals USING btree (deleted_at)
WHERE (is_deleted = true);

--
-- Name: idx_savings_goals_firebase_uid; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_savings_goals_firebase_uid ON public.savings_goals USING btree (firebase_uid);

--
-- Name: idx_savings_goals_is_deleted; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_savings_goals_is_deleted ON public.savings_goals USING btree (is_deleted);

--
-- Name: idx_savings_goals_sync_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_savings_goals_sync_status ON public.savings_goals USING btree (sync_status);

--
-- Name: uq_bill_split_shares_expense_participant; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_bill_split_shares_expense_participant ON public.bill_split_shares USING btree (expense_id, participant_id);

--
-- Name: uq_category_budgets_budget_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_category_budgets_budget_category ON public.category_budgets USING btree (budget_id, category_id);

--
-- Name: ux_ai_memories_firebase_uid_merchant_ci; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_ai_memories_firebase_uid_merchant_ci ON public.ai_memories USING btree (firebase_uid, lower(merchant));

--
-- Name: ux_categories_user_name_type_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX ux_categories_user_name_type_active ON public.categories USING btree (
    firebase_uid,
    lower(name),
    category_type
)
WHERE (
        COALESCE(is_deleted, false) = false
    );

--
-- Name: ai_memories update_ai_memory_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_ai_memory_updated_at BEFORE UPDATE ON public.ai_memories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: budgets update_budgets_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_budgets_updated_at BEFORE UPDATE ON public.budgets FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: categories update_categories_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON public.categories FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: expenses update_expenses_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_expenses_updated_at BEFORE UPDATE ON public.expenses FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: profiles update_profiles_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: recurring_expenses update_recurring_expenses_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_recurring_expenses_updated_at BEFORE UPDATE ON public.recurring_expenses FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: savings_goals update_savings_goals_updated_at; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER update_savings_goals_updated_at BEFORE UPDATE ON public.savings_goals FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

--
-- Name: ai_chat_messages ai_chat_messages_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_chat_messages
ADD CONSTRAINT ai_chat_messages_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: ai_corrections ai_corrections_corrected_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_corrections
ADD CONSTRAINT ai_corrections_corrected_category_id_fkey FOREIGN KEY (corrected_category_id) REFERENCES public.categories (id) ON DELETE RESTRICT;

--
-- Name: ai_corrections ai_corrections_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_corrections
ADD CONSTRAINT ai_corrections_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.expenses (id) ON DELETE CASCADE;

--
-- Name: ai_corrections ai_corrections_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_corrections
ADD CONSTRAINT ai_corrections_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: ai_corrections ai_corrections_original_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_corrections
ADD CONSTRAINT ai_corrections_original_category_id_fkey FOREIGN KEY (original_category_id) REFERENCES public.categories (id) ON DELETE SET NULL;

--
-- Name: ai_insights ai_insights_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_insights
ADD CONSTRAINT ai_insights_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: ai_memories ai_memory_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_memories
ADD CONSTRAINT ai_memory_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: ai_memories ai_memory_resolved_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ai_memories
ADD CONSTRAINT ai_memory_resolved_category_id_fkey FOREIGN KEY (resolved_category_id) REFERENCES public.categories (id) ON DELETE SET NULL;

--
-- Name: auth_refresh_tokens auth_refresh_tokens_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auth_refresh_tokens
ADD CONSTRAINT auth_refresh_tokens_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: auth_refresh_tokens auth_refresh_tokens_replaced_by_token_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auth_refresh_tokens
ADD CONSTRAINT auth_refresh_tokens_replaced_by_token_id_fkey FOREIGN KEY (replaced_by_token_id) REFERENCES public.auth_refresh_tokens (id);

--
-- Name: bill_split_expenses bill_split_expenses_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_expenses
ADD CONSTRAINT bill_split_expenses_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.bill_split_groups (id) ON DELETE CASCADE;

--
-- Name: bill_split_expenses bill_split_expenses_payer_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_expenses
ADD CONSTRAINT bill_split_expenses_payer_participant_id_fkey FOREIGN KEY (payer_participant_id) REFERENCES public.bill_split_participants (id) ON DELETE CASCADE;

--
-- Name: bill_split_participants bill_split_participants_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_participants
ADD CONSTRAINT bill_split_participants_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.bill_split_groups (id) ON DELETE CASCADE;

--
-- Name: bill_split_settlements bill_split_settlements_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_settlements
ADD CONSTRAINT bill_split_settlements_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.bill_split_expenses (id) ON DELETE SET NULL;

--
-- Name: bill_split_settlements bill_split_settlements_group_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_settlements
ADD CONSTRAINT bill_split_settlements_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.bill_split_groups (id) ON DELETE CASCADE;

--
-- Name: bill_split_settlements bill_split_settlements_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_settlements
ADD CONSTRAINT bill_split_settlements_participant_id_fkey FOREIGN KEY (participant_id) REFERENCES public.bill_split_participants (id) ON DELETE CASCADE;

--
-- Name: bill_split_shares bill_split_shares_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_shares
ADD CONSTRAINT bill_split_shares_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.bill_split_expenses (id) ON DELETE CASCADE;

--
-- Name: bill_split_shares bill_split_shares_participant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_split_shares
ADD CONSTRAINT bill_split_shares_participant_id_fkey FOREIGN KEY (participant_id) REFERENCES public.bill_split_participants (id) ON DELETE CASCADE;

--
-- Name: budgets budgets_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.budgets
ADD CONSTRAINT budgets_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: categories categories_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categories
ADD CONSTRAINT categories_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: category_budgets category_budgets_budget_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.category_budgets
ADD CONSTRAINT category_budgets_budget_id_fkey FOREIGN KEY (budget_id) REFERENCES public.budgets (id) ON DELETE CASCADE;

--
-- Name: category_budgets category_budgets_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.category_budgets
ADD CONSTRAINT category_budgets_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories (id) ON DELETE CASCADE;

--
-- Name: expenses expenses_ai_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
ADD CONSTRAINT expenses_ai_category_id_fkey FOREIGN KEY (ai_category_id) REFERENCES public.categories (id) ON DELETE SET NULL;

--
-- Name: expenses expenses_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
ADD CONSTRAINT expenses_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories (id) ON DELETE SET NULL;

--
-- Name: expenses expenses_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
ADD CONSTRAINT expenses_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: expenses expenses_recurring_expense_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
ADD CONSTRAINT expenses_recurring_expense_id_fkey FOREIGN KEY (recurring_expense_id) REFERENCES public.recurring_expenses (id) ON DELETE SET NULL;

--
-- Name: goal_transactions goal_transactions_goal_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.goal_transactions
ADD CONSTRAINT goal_transactions_goal_id_fkey FOREIGN KEY (goal_id) REFERENCES public.savings_goals (id) ON DELETE CASCADE;

--
-- Name: recurring_expenses recurring_expenses_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurring_expenses
ADD CONSTRAINT recurring_expenses_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories (id) ON DELETE SET NULL;

--
-- Name: recurring_expenses recurring_expenses_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurring_expenses
ADD CONSTRAINT recurring_expenses_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: savings_goals savings_goals_firebase_uid_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.savings_goals
ADD CONSTRAINT savings_goals_firebase_uid_fkey FOREIGN KEY (firebase_uid) REFERENCES public.profiles (firebase_uid) ON DELETE CASCADE;

--
-- Name: ai_corrections Users can delete own ai_corrections; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own ai_corrections" ON public.ai_corrections FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: ai_insights Users can delete own ai_insights; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own ai_insights" ON public.ai_insights FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: ai_memories Users can delete own ai_memory; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own ai_memory" ON public.ai_memories FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: budgets Users can delete own budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own budgets" ON public.budgets FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: categories Users can delete own categories; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own categories" ON public.categories FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: category_budgets Users can delete own category budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own category budgets" ON public.category_budgets FOR DELETE USING (
    (
        (
            EXISTS (
                SELECT 1
                FROM public.budgets
                WHERE (
                        (
                            budgets.id = category_budgets.budget_id
                        )
                        AND (
                            budgets.firebase_uid = (
                                SELECT public.firebase_uid () AS firebase_uid
                            )
                        )
                    )
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: expenses Users can delete own expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own expenses" ON public.expenses FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: goal_transactions Users can delete own goal transactions; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own goal transactions" ON public.goal_transactions FOR DELETE USING (
    (
        EXISTS (
            SELECT 1
            FROM public.savings_goals g
            WHERE (
                    (
                        g.id = goal_transactions.goal_id
                    )
                    AND (
                        g.firebase_uid = (
                            SELECT public.firebase_uid () AS firebase_uid
                        )
                    )
                )
        )
    )
);

--
-- Name: savings_goals Users can delete own goals; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own goals" ON public.savings_goals FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: profiles Users can delete own profile; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own profile" ON public.profiles FOR DELETE USING (
    (
        firebase_uid = (
            SELECT public.firebase_uid () AS firebase_uid
        )
    )
);

--
-- Name: recurring_expenses Users can delete own recurring expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can delete own recurring expenses" ON public.recurring_expenses FOR DELETE USING (
    (
        (
            firebase_uid = (
                SELECT public.firebase_uid () AS firebase_uid
            )
        )
        OR (
            (
                SELECT current_setting('role'::text) AS current_setting
            ) = 'service_role'::text
        )
    )
);

--
-- Name: ai_corrections Users can insert own ai_corrections; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own ai_corrections" ON public.ai_corrections FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_insights Users can insert own ai_insights; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own ai_insights" ON public.ai_insights FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_memories Users can insert own ai_memory; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own ai_memory" ON public.ai_memories FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: budgets Users can insert own budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own budgets" ON public.budgets FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: categories Users can insert own categories; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own categories" ON public.categories FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: category_budgets Users can insert own category budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own category budgets" ON public.category_budgets FOR INSERT
WITH
    CHECK (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.budgets
                    WHERE (
                            (
                                budgets.id = category_budgets.budget_id
                            )
                            AND (
                                budgets.firebase_uid = (
                                    SELECT public.firebase_uid () AS firebase_uid
                                )
                            )
                        )
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: expenses Users can insert own expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own expenses" ON public.expenses FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: goal_transactions Users can insert own goal transactions; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own goal transactions" ON public.goal_transactions FOR INSERT
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM public.savings_goals g
                WHERE (
                        (
                            g.id = goal_transactions.goal_id
                        )
                        AND (
                            g.firebase_uid = (
                                SELECT public.firebase_uid () AS firebase_uid
                            )
                        )
                    )
            )
        )
    );

--
-- Name: savings_goals Users can insert own goals; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own goals" ON public.savings_goals FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: profiles Users can insert own profile; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own profile" ON public.profiles FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: recurring_expenses Users can insert own recurring expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can insert own recurring expenses" ON public.recurring_expenses FOR INSERT
WITH
    CHECK (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_corrections Users can update own ai_corrections; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own ai_corrections" ON public.ai_corrections
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_insights Users can update own ai_insights; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own ai_insights" ON public.ai_insights
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_memories Users can update own ai_memory; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own ai_memory" ON public.ai_memories
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: budgets Users can update own budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own budgets" ON public.budgets
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: categories Users can update own categories; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own categories" ON public.categories
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: category_budgets Users can update own category budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own category budgets" ON public.category_budgets
FOR UPDATE
    USING (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.budgets
                    WHERE (
                            (
                                budgets.id = category_budgets.budget_id
                            )
                            AND (
                                budgets.firebase_uid = (
                                    SELECT public.firebase_uid () AS firebase_uid
                                )
                            )
                        )
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: expenses Users can update own expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own expenses" ON public.expenses
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: goal_transactions Users can update own goal transactions; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own goal transactions" ON public.goal_transactions
FOR UPDATE
    USING (
        (
            EXISTS (
                SELECT 1
                FROM public.savings_goals g
                WHERE (
                        (
                            g.id = goal_transactions.goal_id
                        )
                        AND (
                            g.firebase_uid = (
                                SELECT public.firebase_uid () AS firebase_uid
                            )
                        )
                    )
            )
        )
    );

--
-- Name: savings_goals Users can update own goals; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own goals" ON public.savings_goals
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: profiles Users can update own profile; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own profile" ON public.profiles
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: recurring_expenses Users can update own recurring expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can update own recurring expenses" ON public.recurring_expenses
FOR UPDATE
    USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_corrections Users can view own ai_corrections; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own ai_corrections" ON public.ai_corrections FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_insights Users can view own ai_insights; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own ai_insights" ON public.ai_insights FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_memories Users can view own ai_memory; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own ai_memory" ON public.ai_memories FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: budgets Users can view own budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own budgets" ON public.budgets FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: categories Users can view own categories; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own categories" ON public.categories FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: category_budgets Users can view own category budgets; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own category budgets" ON public.category_budgets FOR
SELECT USING (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.budgets
                    WHERE (
                            (
                                budgets.id = category_budgets.budget_id
                            )
                            AND (
                                budgets.firebase_uid = (
                                    SELECT public.firebase_uid () AS firebase_uid
                                )
                            )
                        )
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: expenses Users can view own expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own expenses" ON public.expenses FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: goal_transactions Users can view own goal transactions; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own goal transactions" ON public.goal_transactions FOR
SELECT USING (
        (
            EXISTS (
                SELECT 1
                FROM public.savings_goals g
                WHERE (
                        (
                            g.id = goal_transactions.goal_id
                        )
                        AND (
                            g.firebase_uid = (
                                SELECT public.firebase_uid () AS firebase_uid
                            )
                        )
                    )
            )
        )
    );

--
-- Name: savings_goals Users can view own goals; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own goals" ON public.savings_goals FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: profiles Users can view own profile; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own profile" ON public.profiles FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: recurring_expenses Users can view own recurring expenses; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY "Users can view own recurring expenses" ON public.recurring_expenses FOR
SELECT USING (
        (
            (
                firebase_uid = (
                    SELECT public.firebase_uid () AS firebase_uid
                )
            )
            OR (
                (
                    SELECT current_setting('role'::text) AS current_setting
                ) = 'service_role'::text
            )
        )
    );

--
-- Name: ai_chat_messages; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.ai_chat_messages ENABLE ROW LEVEL SECURITY;

--
-- Name: ai_corrections; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.ai_corrections ENABLE ROW LEVEL SECURITY;

--
-- Name: ai_insights; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.ai_insights ENABLE ROW LEVEL SECURITY;

--
-- Name: ai_memories; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.ai_memories ENABLE ROW LEVEL SECURITY;

--
-- Name: auth_refresh_tokens; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.auth_refresh_tokens ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_expenses; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.bill_split_expenses ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_expenses bill_split_expenses_delete_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_expenses_delete_owner ON public.bill_split_expenses FOR DELETE USING (
    (
        EXISTS (
            SELECT 1
            FROM public.bill_split_groups g
            WHERE (
                    (
                        g.id = bill_split_expenses.group_id
                    )
                    AND (
                        g.created_by = public.firebase_uid ()
                    )
                )
        )
    )
);

--
-- Name: bill_split_expenses bill_split_expenses_insert_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_expenses_insert_owner ON public.bill_split_expenses FOR INSERT
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_expenses.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_expenses bill_split_expenses_select_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_expenses_select_owner ON public.bill_split_expenses FOR
SELECT USING (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_expenses.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_expenses bill_split_expenses_update_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_expenses_update_owner ON public.bill_split_expenses
FOR UPDATE
    USING (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_expenses.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    )
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_expenses.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_groups; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.bill_split_groups ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_groups bill_split_groups_delete_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_groups_delete_owner ON public.bill_split_groups FOR DELETE USING (
    (
        created_by = public.firebase_uid ()
    )
);

--
-- Name: bill_split_groups bill_split_groups_insert_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_groups_insert_owner ON public.bill_split_groups FOR INSERT
WITH
    CHECK (
        (
            created_by = public.firebase_uid ()
        )
    );

--
-- Name: bill_split_groups bill_split_groups_select_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_groups_select_owner ON public.bill_split_groups FOR
SELECT USING (
        (
            created_by = public.firebase_uid ()
        )
    );

--
-- Name: bill_split_groups bill_split_groups_update_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_groups_update_owner ON public.bill_split_groups
FOR UPDATE
    USING (
        (
            created_by = public.firebase_uid ()
        )
    )
WITH
    CHECK (
        (
            created_by = public.firebase_uid ()
        )
    );

--
-- Name: bill_split_participants; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.bill_split_participants ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_participants bill_split_participants_delete_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_participants_delete_owner ON public.bill_split_participants FOR DELETE USING (
    (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_participants.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
        OR (
            user_id = public.firebase_uid ()
        )
    )
);

--
-- Name: bill_split_participants bill_split_participants_insert_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_participants_insert_owner ON public.bill_split_participants FOR INSERT
WITH
    CHECK (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.bill_split_groups g
                    WHERE (
                            (
                                g.id = bill_split_participants.group_id
                            )
                            AND (
                                g.created_by = public.firebase_uid ()
                            )
                        )
                )
            )
            OR (
                user_id = public.firebase_uid ()
            )
        )
    );

--
-- Name: bill_split_participants bill_split_participants_select_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_participants_select_owner ON public.bill_split_participants FOR
SELECT USING (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.bill_split_groups g
                    WHERE (
                            (
                                g.id = bill_split_participants.group_id
                            )
                            AND (
                                g.created_by = public.firebase_uid ()
                            )
                        )
                )
            )
            OR (
                user_id = public.firebase_uid ()
            )
        )
    );

--
-- Name: bill_split_participants bill_split_participants_update_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_participants_update_owner ON public.bill_split_participants
FOR UPDATE
    USING (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.bill_split_groups g
                    WHERE (
                            (
                                g.id = bill_split_participants.group_id
                            )
                            AND (
                                g.created_by = public.firebase_uid ()
                            )
                        )
                )
            )
            OR (
                user_id = public.firebase_uid ()
            )
        )
    )
WITH
    CHECK (
        (
            (
                EXISTS (
                    SELECT 1
                    FROM public.bill_split_groups g
                    WHERE (
                            (
                                g.id = bill_split_participants.group_id
                            )
                            AND (
                                g.created_by = public.firebase_uid ()
                            )
                        )
                )
            )
            OR (
                user_id = public.firebase_uid ()
            )
        )
    );

--
-- Name: bill_split_settlements; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.bill_split_settlements ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_settlements bill_split_settlements_delete_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_settlements_delete_owner ON public.bill_split_settlements FOR DELETE USING (
    (
        EXISTS (
            SELECT 1
            FROM public.bill_split_groups g
            WHERE (
                    (
                        g.id = bill_split_settlements.group_id
                    )
                    AND (
                        g.created_by = public.firebase_uid ()
                    )
                )
        )
    )
);

--
-- Name: bill_split_settlements bill_split_settlements_insert_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_settlements_insert_owner ON public.bill_split_settlements FOR INSERT
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_settlements.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_settlements bill_split_settlements_select_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_settlements_select_owner ON public.bill_split_settlements FOR
SELECT USING (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_settlements.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_settlements bill_split_settlements_update_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_settlements_update_owner ON public.bill_split_settlements
FOR UPDATE
    USING (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_settlements.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    )
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM public.bill_split_groups g
                WHERE (
                        (
                            g.id = bill_split_settlements.group_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_shares; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.bill_split_shares ENABLE ROW LEVEL SECURITY;

--
-- Name: bill_split_shares bill_split_shares_delete_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_shares_delete_owner ON public.bill_split_shares FOR DELETE USING (
    (
        EXISTS (
            SELECT 1
            FROM (
                    public.bill_split_expenses e
                    JOIN public.bill_split_groups g ON ((g.id = e.group_id))
                )
            WHERE (
                    (
                        e.id = bill_split_shares.expense_id
                    )
                    AND (
                        g.created_by = public.firebase_uid ()
                    )
                )
        )
    )
);

--
-- Name: bill_split_shares bill_split_shares_insert_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_shares_insert_owner ON public.bill_split_shares FOR INSERT
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM (
                        public.bill_split_expenses e
                        JOIN public.bill_split_groups g ON ((g.id = e.group_id))
                    )
                WHERE (
                        (
                            e.id = bill_split_shares.expense_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_shares bill_split_shares_select_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_shares_select_owner ON public.bill_split_shares FOR
SELECT USING (
        (
            EXISTS (
                SELECT 1
                FROM (
                        public.bill_split_expenses e
                        JOIN public.bill_split_groups g ON ((g.id = e.group_id))
                    )
                WHERE (
                        (
                            e.id = bill_split_shares.expense_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: bill_split_shares bill_split_shares_update_owner; Type: POLICY; Schema: public; Owner: postgres
--

CREATE POLICY bill_split_shares_update_owner ON public.bill_split_shares
FOR UPDATE
    USING (
        (
            EXISTS (
                SELECT 1
                FROM (
                        public.bill_split_expenses e
                        JOIN public.bill_split_groups g ON ((g.id = e.group_id))
                    )
                WHERE (
                        (
                            e.id = bill_split_shares.expense_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    )
WITH
    CHECK (
        (
            EXISTS (
                SELECT 1
                FROM (
                        public.bill_split_expenses e
                        JOIN public.bill_split_groups g ON ((g.id = e.group_id))
                    )
                WHERE (
                        (
                            e.id = bill_split_shares.expense_id
                        )
                        AND (
                            g.created_by = public.firebase_uid ()
                        )
                    )
            )
        )
    );

--
-- Name: budgets; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;

--
-- Name: categories; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

--
-- Name: category_budgets; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.category_budgets ENABLE ROW LEVEL SECURITY;

--
-- Name: expenses; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;

--
-- Name: goal_transactions; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.goal_transactions ENABLE ROW LEVEL SECURITY;

--
-- Name: profiles; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

--
-- Name: recurring_expenses; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.recurring_expenses ENABLE ROW LEVEL SECURITY;

--
-- Name: savings_goals; Type: ROW SECURITY; Schema: public; Owner: postgres
--

ALTER TABLE public.savings_goals ENABLE ROW LEVEL SECURITY;

--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: pg_database_owner
--

GRANT USAGE ON SCHEMA public TO postgres;

GRANT USAGE ON SCHEMA public TO anon;

GRANT USAGE ON SCHEMA public TO authenticated;

GRANT USAGE ON SCHEMA public TO service_role;

--
-- Name: FUNCTION apply_expense_to_balance(); Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON FUNCTION public.apply_expense_to_balance () TO anon;

GRANT ALL ON FUNCTION public.apply_expense_to_balance () TO authenticated;

GRANT ALL ON FUNCTION public.apply_expense_to_balance () TO service_role;

--
-- Name: FUNCTION create_initial_balance(); Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON FUNCTION public.create_initial_balance () TO anon;

GRANT ALL ON FUNCTION public.create_initial_balance () TO authenticated;

GRANT ALL ON FUNCTION public.create_initial_balance () TO service_role;

--
-- Name: FUNCTION firebase_uid(); Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON FUNCTION public.firebase_uid () TO anon;

GRANT ALL ON FUNCTION public.firebase_uid () TO authenticated;

GRANT ALL ON FUNCTION public.firebase_uid () TO service_role;

--
-- Name: FUNCTION update_updated_at_column(); Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON FUNCTION public.update_updated_at_column () TO anon;

GRANT ALL ON FUNCTION public.update_updated_at_column () TO authenticated;

GRANT ALL ON FUNCTION public.update_updated_at_column () TO service_role;

--
-- Name: TABLE ai_chat_messages; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.ai_chat_messages TO service_role;

--
-- Name: TABLE ai_corrections; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.ai_corrections TO anon;

GRANT ALL ON TABLE public.ai_corrections TO authenticated;

GRANT ALL ON TABLE public.ai_corrections TO service_role;

--
-- Name: TABLE ai_insights; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.ai_insights TO anon;

GRANT ALL ON TABLE public.ai_insights TO authenticated;

GRANT ALL ON TABLE public.ai_insights TO service_role;

--
-- Name: TABLE ai_memories; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.ai_memories TO anon;

GRANT ALL ON TABLE public.ai_memories TO authenticated;

GRANT ALL ON TABLE public.ai_memories TO service_role;

--
-- Name: TABLE auth_refresh_tokens; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.auth_refresh_tokens TO service_role;

--
-- Name: TABLE bill_split_expenses; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.bill_split_expenses TO anon;

GRANT ALL ON TABLE public.bill_split_expenses TO authenticated;

GRANT ALL ON TABLE public.bill_split_expenses TO service_role;

--
-- Name: TABLE bill_split_groups; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.bill_split_groups TO anon;

GRANT ALL ON TABLE public.bill_split_groups TO authenticated;

GRANT ALL ON TABLE public.bill_split_groups TO service_role;

--
-- Name: TABLE bill_split_participants; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.bill_split_participants TO anon;

GRANT ALL ON TABLE public.bill_split_participants TO authenticated;

GRANT ALL ON TABLE public.bill_split_participants TO service_role;

--
-- Name: TABLE bill_split_settlements; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.bill_split_settlements TO anon;

GRANT ALL ON TABLE public.bill_split_settlements TO authenticated;

GRANT ALL ON TABLE public.bill_split_settlements TO service_role;

--
-- Name: TABLE bill_split_shares; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.bill_split_shares TO anon;

GRANT ALL ON TABLE public.bill_split_shares TO authenticated;

GRANT ALL ON TABLE public.bill_split_shares TO service_role;

--
-- Name: TABLE budgets; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.budgets TO anon;

GRANT ALL ON TABLE public.budgets TO authenticated;

GRANT ALL ON TABLE public.budgets TO service_role;

--
-- Name: TABLE categories; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.categories TO anon;

GRANT ALL ON TABLE public.categories TO authenticated;

GRANT ALL ON TABLE public.categories TO service_role;

--
-- Name: TABLE category_budgets; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.category_budgets TO anon;

GRANT ALL ON TABLE public.category_budgets TO authenticated;

GRANT ALL ON TABLE public.category_budgets TO service_role;

--
-- Name: TABLE expenses; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.expenses TO anon;

GRANT ALL ON TABLE public.expenses TO authenticated;

GRANT ALL ON TABLE public.expenses TO service_role;

--
-- Name: TABLE goal_transactions; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.goal_transactions TO anon;

GRANT ALL ON TABLE public.goal_transactions TO authenticated;

GRANT ALL ON TABLE public.goal_transactions TO service_role;

--
-- Name: TABLE profiles; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.profiles TO anon;

GRANT ALL ON TABLE public.profiles TO authenticated;

GRANT ALL ON TABLE public.profiles TO service_role;

--
-- Name: TABLE recurring_expenses; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.recurring_expenses TO anon;

GRANT ALL ON TABLE public.recurring_expenses TO authenticated;

GRANT ALL ON TABLE public.recurring_expenses TO service_role;

--
-- Name: TABLE savings_goals; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.savings_goals TO anon;

GRANT ALL ON TABLE public.savings_goals TO authenticated;

GRANT ALL ON TABLE public.savings_goals TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: public; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON SEQUENCES TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON SEQUENCES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON SEQUENCES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON SEQUENCES TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR SEQUENCES; Type: DEFAULT ACL; Schema: public; Owner: supabase_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON SEQUENCES TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON SEQUENCES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON SEQUENCES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON SEQUENCES TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR FUNCTIONS; Type: DEFAULT ACL; Schema: public; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON FUNCTIONS TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON FUNCTIONS TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON FUNCTIONS TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON FUNCTIONS TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR FUNCTIONS; Type: DEFAULT ACL; Schema: public; Owner: supabase_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON FUNCTIONS TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON FUNCTIONS TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON FUNCTIONS TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON FUNCTIONS TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: public; Owner: postgres
--

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON TABLES TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON TABLES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON TABLES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
GRANT ALL ON TABLES TO service_role;

--
-- Name: DEFAULT PRIVILEGES FOR TABLES; Type: DEFAULT ACL; Schema: public; Owner: supabase_admin
--

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON TABLES TO postgres;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON TABLES TO anon;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON TABLES TO authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin IN SCHEMA public
GRANT ALL ON TABLES TO service_role;

--
-- PostgreSQL database dump complete
--