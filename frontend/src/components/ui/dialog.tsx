import { useEffect, useRef, type ReactNode } from "react";

type DialogProps = {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
};

export function Dialog({
  open,
  title,
  description,
  onClose,
  children,
}: DialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;

    if (!dialog) {
      return;
    }

    if (open && !dialog.open) {
      dialog.showModal();
    }

    if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      aria-labelledby="dialog-title"
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-xl border border-[#cfd9d2] bg-[#fbfdfb] p-0 text-[#18221d] shadow-2xl outline-none backdrop:bg-[#10251d]/45"
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      ref={dialogRef}
    >
      <div className="border-b border-[#d8e1db] px-6 py-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold" id="dialog-title">
              {title}
            </h2>
            {description ? (
              <p className="mt-1 text-sm text-[#65746a]">{description}</p>
            ) : null}
          </div>
          <button
            aria-label="Close dialog"
            className="rounded-md px-2 py-1 text-xl leading-none text-[#65746a] transition hover:bg-[#eef3ef] hover:text-[#18221d]"
            onClick={onClose}
            type="button"
          >
            x
          </button>
        </div>
      </div>
      <div className="px-6 py-5">{children}</div>
    </dialog>
  );
}
