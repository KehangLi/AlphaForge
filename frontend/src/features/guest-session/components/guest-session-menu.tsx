import { useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";

export function GuestSessionMenu() {
  const router = useRouter();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isEndDialogOpen, setIsEndDialogOpen] = useState(false);

  function handleEndGuestExperience() {
    setIsMenuOpen(false);
    setIsEndDialogOpen(true);
  }

  function handleConfirmEndGuestExperience() {
    setIsEndDialogOpen(false);
    router.push("/");
  }

  return (
    <div className="relative">
      <Button
        aria-expanded={isMenuOpen}
        aria-haspopup="menu"
        onClick={() => setIsMenuOpen((isOpen) => !isOpen)}
        variant="secondary"
      >
        Guest session
        <span aria-hidden="true" className="ml-2 text-xs">
          {isMenuOpen ? "^" : "v"}
        </span>
      </Button>

      {isMenuOpen ? (
        <div
          className="absolute right-0 top-[calc(100%+0.5rem)] z-20 w-64 rounded-lg border border-[#cfd9d2] bg-white p-3 shadow-lg"
          role="menu"
        >
          <p className="px-3 py-2 text-sm font-semibold text-[#18221d]">
            Exploring as a guest
          </p>
          <p className="px-3 pb-2 text-xs leading-5 text-[#65746a]">
            Your guest data is temporary for this session.
          </p>
          <button
            className="w-full rounded-md px-3 py-2 text-left text-sm font-semibold text-[#9a3412] transition hover:bg-[#fff1eb]"
            onClick={handleEndGuestExperience}
            role="menuitem"
            type="button"
          >
            End guest experience
          </button>
        </div>
      ) : null}

      <Dialog
        description="You can return later and start a new guest session."
        onClose={() => setIsEndDialogOpen(false)}
        open={isEndDialogOpen}
        title="End guest experience?"
      >
        <div className="space-y-5">
          <p className="text-sm leading-6 text-[#526258]">
            Leave this workspace and return to the AlphaForge entry page?
          </p>
          <div className="flex justify-end gap-2">
            <Button
              onClick={() => setIsEndDialogOpen(false)}
              variant="secondary"
            >
              Stay here
            </Button>
            <Button
              className="bg-[#9a3412] hover:bg-[#7c2d12]"
              onClick={handleConfirmEndGuestExperience}
            >
              End experience
            </Button>
          </div>
        </div>
      </Dialog>
    </div>
  );
}
