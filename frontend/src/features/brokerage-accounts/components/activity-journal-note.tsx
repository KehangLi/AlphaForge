import { useState } from "react";
import { Check, FileText, Pencil, X } from "lucide-react";

import { useSaveJournalNoteMutation } from "../queries";
import type { AccountActivityResponse } from "../types";

type ActivityJournalNoteProps = {
  activity: AccountActivityResponse;
  brokerageAccountId?: string;
};

export function ActivityJournalNote({
  activity,
  brokerageAccountId,
}: ActivityJournalNoteProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState(activity.journalNote ?? "");
  const saveMutation = useSaveJournalNoteMutation();

  if (isEditing && brokerageAccountId) {
    return (
      <div className="min-w-[240px] space-y-2">
        <textarea
          aria-label="Journal note"
          className="min-h-20 w-full resize-y rounded-md border border-[#b9c9be] bg-white px-3 py-2 text-sm text-[#18221d] outline-none focus:border-[#3e6f57] focus:ring-2 focus:ring-[#b9c9be]"
          maxLength={5000}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Add a note about this activity..."
          value={draft}
        />
        <div className="flex items-center gap-2">
          <button
            className="inline-flex min-h-8 items-center gap-1 rounded-md bg-[#173f35] px-2.5 text-xs font-semibold text-white disabled:cursor-wait disabled:opacity-60"
            disabled={saveMutation.isPending}
            onClick={() =>
              saveMutation.mutate({
                activityId: activity.id,
                brokerageAccountId,
                journalNote: draft,
              }, {
                onSuccess: () => setIsEditing(false),
              })
            }
            type="button"
          >
            <Check aria-hidden="true" size={14} />
            {saveMutation.isPending ? "Saving" : "Save"}
          </button>
          <button
            className="inline-flex min-h-8 items-center gap-1 rounded-md border border-[#bac8bf] px-2.5 text-xs font-semibold text-[#173f35]"
            disabled={saveMutation.isPending}
            onClick={() => {
              setDraft(activity.journalNote ?? "");
              setIsEditing(false);
              saveMutation.reset();
            }}
            type="button"
          >
            <X aria-hidden="true" size={14} />
            Cancel
          </button>
        </div>
        {saveMutation.isError ? (
          <p className="text-xs text-[#9a3412]">
            {saveMutation.error instanceof Error
              ? saveMutation.error.message
              : "Unable to save note."}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="max-w-[260px] space-y-1">
      {activity.notes ? (
        <p className="truncate text-sm text-[#4e5d53]" title={`CSV: ${activity.notes}`}>
          <span className="mr-1 text-xs font-semibold uppercase text-[#65746a]">CSV</span>
          {activity.notes}
        </p>
      ) : null}
      {activity.journalNote ? (
        <p
          className="truncate text-sm text-[#4e5d53]"
          title={`Journal: ${activity.journalNote}`}
        >
          <span className="mr-1 text-xs font-semibold uppercase text-[#3e6f57]">Note</span>
          {activity.journalNote}
        </p>
      ) : null}
      {!activity.notes && !activity.journalNote ? (
        <span className="text-[#65746a]">-</span>
      ) : null}
      {brokerageAccountId ? (
        <button
          className="inline-flex items-center gap-1 text-xs font-semibold text-[#3e6f57] hover:text-[#173f35]"
          onClick={() => {
            saveMutation.reset();
            setDraft(activity.journalNote ?? "");
            setIsEditing(true);
          }}
          type="button"
        >
          {activity.journalNote ? (
            <Pencil aria-hidden="true" size={13} />
          ) : (
            <FileText aria-hidden="true" size={13} />
          )}
          {activity.journalNote ? "Edit note" : "Add note"}
        </button>
      ) : null}
    </div>
  );
}
