ALTER TABLE public.categories DROP CONSTRAINT IF EXISTS unique_category_per_user;
DROP INDEX IF EXISTS public.unique_category_per_user;
