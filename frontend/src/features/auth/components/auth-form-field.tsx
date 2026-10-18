import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

type AuthFormFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  type?: "text" | "email" | "password";
  autoComplete: string;
  maxLength: number;
  placeholder?: string;
  hint?: string;
  error?: string;
  disabled?: boolean;
};

export function AuthFormField({
  id,
  label,
  value,
  onChange,
  onBlur,
  type = "text",
  autoComplete,
  maxLength,
  placeholder,
  hint,
  error,
  disabled = false,
}: AuthFormFieldProps) {
  const [isPasswordVisible, setIsPasswordVisible] = useState(false);
  const isPassword = type === "password";
  const descriptionId = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div>
      <label className="mb-2 block text-sm font-medium" htmlFor={id}>{label}</label>
      <div className="relative">
        <input
          aria-describedby={descriptionId}
          aria-invalid={Boolean(error)}
          autoCapitalize={type === "email" ? "none" : undefined}
          autoComplete={autoComplete}
          className={[
            "h-12 w-full rounded-md border bg-white px-3.5 text-base outline-none transition placeholder:text-[#98a39c] disabled:cursor-wait disabled:bg-[#f0f3f1] disabled:text-[#65746a] focus:ring-2 sm:text-sm",
            error
              ? "border-[#bd554d] focus:border-[#bd554d] focus:ring-[#f3d8d5]"
              : "border-[#c3cfc7] focus:border-[#3e6f57] focus:ring-[#d5e5dc]",
            isPassword ? "pr-12" : "",
          ].join(" ")}
          disabled={disabled}
          id={id}
          maxLength={maxLength}
          name={id}
          onBlur={onBlur}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
          required
          spellCheck={false}
          type={isPassword && isPasswordVisible ? "text" : type}
          value={value}
        />
        {isPassword ? (
          <button
            aria-label={isPasswordVisible ? "Hide password" : "Show password"}
            aria-pressed={isPasswordVisible}
            className="absolute right-1 top-1 flex size-10 items-center justify-center rounded-sm text-[#65746a] transition hover:bg-[#eef3ef] hover:text-[#173f35] focus-visible:outline-2 focus-visible:outline-[#3e6f57] disabled:cursor-wait"
            disabled={disabled}
            onClick={() => setIsPasswordVisible((visible) => !visible)}
            title={isPasswordVisible ? "Hide password" : "Show password"}
            type="button"
          >
            {isPasswordVisible ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
          </button>
        ) : null}
      </div>
      <div aria-live="polite" className="mt-1.5 min-h-5 text-xs leading-5">
        {error ? (
          <p className="text-[#a3352e]" id={`${id}-error`}>{error}</p>
        ) : hint ? (
          <p className="text-[#65746a]" id={`${id}-hint`}>{hint}</p>
        ) : null}
      </div>
    </div>
  );
}
