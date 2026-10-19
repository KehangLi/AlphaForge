"use client";

import { ArrowRight, CircleAlert, LoaderCircle, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";

import { useLoginMutation, useRegisterMutation } from "../queries";
import { AuthFormField } from "./auth-form-field";

type AuthMode = "login" | "register";
type FormValues = { displayName: string; email: string; password: string };
type FieldName = keyof FormValues;
type FormErrors = Partial<Record<FieldName, string>>;

export function AuthForm({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const loginMutation = useLoginMutation();
  const registerMutation = useRegisterMutation();
  const [values, setValues] = useState<FormValues>({ displayName: "", email: "", password: "" });
  const [touched, setTouched] = useState<Partial<Record<FieldName, boolean>>>({});
  const [isSubmitted, setIsSubmitted] = useState(false);
  const isRegister = mode === "register";
  const mutation = isRegister ? registerMutation : loginMutation;
  const isPending = mutation.isPending;
  const errors = validateForm(mode, values);
  const visibleErrors = Object.entries(errors).filter(([field]) => isSubmitted || touched[field as FieldName]);

  function fieldProps(field: FieldName) {
    return {
      value: values[field],
      disabled: isPending,
      error: isSubmitted || touched[field] ? errors[field] : undefined,
      onChange: (value: string) => {
        mutation.reset();
        setValues((current) => ({ ...current, [field]: value }));
      },
      onBlur: () => setTouched((current) => ({ ...current, [field]: true })),
    };
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isPending) return;
    setIsSubmitted(true);

    const firstInvalidField = Object.keys(errors)[0];
    if (firstInvalidField) {
      document.getElementById(firstInvalidField)?.focus();
      return;
    }

    const credentials = { email: values.email.trim(), password: values.password };
    const onSuccess = () => {
      setValues((current) => ({ ...current, password: "" }));
      router.replace("/app");
    };

    if (isRegister) {
      registerMutation.mutate({ ...credentials, displayName: values.displayName.trim() }, { onSuccess });
    } else {
      loginMutation.mutate(credentials, { onSuccess });
    }
  }

  return (
    <>
      <div className="mb-8">
        <span className="mb-5 inline-flex size-11 items-center justify-center rounded-lg border border-[#d8dfeb] bg-[#edf0f8] text-[#5a648c]">
          <UserRound aria-hidden="true" size={22} strokeWidth={1.6} />
        </span>
        <h1 className="text-[32px] font-semibold leading-tight text-[#101713]">
          {isRegister ? "Create your account" : "Welcome back"}
        </h1>
        <p className="mt-3 text-sm leading-6 text-[#65746a]">
          {isRegister ? "Already have an account? " : "New to AlphaForge? "}
          <Link className="rounded-sm font-semibold text-[#235640] underline decoration-[#a4beb0] underline-offset-4 hover:decoration-[#235640] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href={isRegister ? "/login" : "/register"}>
            {isRegister ? "Sign in" : "Create an account"}
          </Link>
        </p>
      </div>

      <form aria-busy={isPending} noValidate onSubmit={handleSubmit}>
        {isRegister ? (
          <AuthFormField
            {...fieldProps("displayName")}
            autoComplete="name"
            id="displayName"
            label="Display name"
            maxLength={120}
            placeholder="Your name"
          />
        ) : null}
        <AuthFormField
          {...fieldProps("email")}
          autoComplete="username"
          id="email"
          label="Email address"
          maxLength={320}
          placeholder="you@example.com"
          type="email"
        />
        <AuthFormField
          {...fieldProps("password")}
          autoComplete={isRegister ? "new-password" : "current-password"}
          hint={isRegister ? "Use 15-128 characters. Spaces and symbols are welcome." : undefined}
          id="password"
          label="Password"
          maxLength={128}
          type="password"
        />

        {isSubmitted && visibleErrors.length > 0 ? (
          <div className="mb-4 flex items-start gap-2 rounded-md border border-[#e4c8c4] bg-[#fcf1ef] px-3 py-2.5 text-sm leading-5 text-[#a3352e]" role="alert">
            <CircleAlert aria-hidden="true" className="mt-0.5 shrink-0" size={16} />
            <p>Check the highlighted fields and try again.</p>
          </div>
        ) : null}

        {mutation.isError ? (
          <div className="mb-4 flex items-start gap-2 rounded-md border border-[#e4c8c4] bg-[#fcf1ef] px-3 py-2.5 text-sm leading-5 text-[#a3352e]" role="alert">
            <CircleAlert aria-hidden="true" className="mt-0.5 shrink-0" size={16} />
            <p>{mutation.error instanceof ApiError
              ? mutation.error.message
              : "Unable to reach AlphaForge. Please try again."}</p>
          </div>
        ) : null}

        <Button className="mt-1 flex h-12 w-full items-center justify-center gap-2 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" disabled={isPending} type="submit">
          {isPending ? (
            <><LoaderCircle aria-hidden="true" className="animate-spin motion-reduce:animate-none" size={17} />{isRegister ? "Creating account..." : "Signing in..."}</>
          ) : (
            <>{isRegister ? "Create account" : "Sign in"}<ArrowRight aria-hidden="true" size={17} /></>
          )}
        </Button>
        <p aria-live="polite" className="sr-only">{isPending ? "Opening your workspace." : ""}</p>
      </form>

      <div className="mt-7 border-t border-[#dce4df] pt-6 text-center text-sm">
        <Link className="inline-flex min-h-10 items-center gap-2 rounded-sm font-medium text-[#526258] hover:text-[#173f35] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-[#3e6f57]" href="/">
          Explore as a guest
          <ArrowRight aria-hidden="true" size={15} />
        </Link>
        {isRegister ? (
          <p className="mx-auto mt-3 max-w-sm text-xs leading-5 text-[#65746a]">
            A new account starts a separate workspace. Existing guest accounts and imports will not move with you.
          </p>
        ) : null}
      </div>
    </>
  );
}

function validateForm(mode: AuthMode, values: FormValues): FormErrors {
  const errors: FormErrors = {};

  if (mode === "register") {
    if (!values.displayName.trim()) errors.displayName = "Enter your display name.";
    else if (values.displayName.trim().length > 120) errors.displayName = "Use 120 characters or fewer.";
  }

  const email = values.email.trim();
  if (!email) errors.email = "Enter your email address.";
  else if (email.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = "Enter a valid email address.";

  if (!values.password.trim()) errors.password = "Enter your password.";
  else if (mode === "register" && values.password.length < 15) errors.password = "Use at least 15 characters.";
  else if (values.password.length > 128) errors.password = "Use 128 characters or fewer.";

  return errors;
}
