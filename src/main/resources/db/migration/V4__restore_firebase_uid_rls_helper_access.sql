-- firebase_uid() is used by RLS policies to resolve the caller identity.
-- It only reads request claims, so invoker security is sufficient.
ALTER FUNCTION public.firebase_uid() SECURITY INVOKER;

REVOKE EXECUTE ON FUNCTION public.firebase_uid() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.firebase_uid() TO anon, authenticated, service_role;
