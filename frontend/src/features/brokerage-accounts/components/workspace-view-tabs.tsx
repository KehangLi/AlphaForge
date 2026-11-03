type WorkspaceView = "activities" | "holdings";

type WorkspaceViewTabsProps = {
  activeView: WorkspaceView;
  onChange: (view: WorkspaceView) => void;
};

export function WorkspaceViewTabs({
  activeView,
  onChange,
}: WorkspaceViewTabsProps) {
  return (
    <div
      aria-label="Workspace views"
      className="flex gap-1 border-b border-[#cfd9d2] pt-5"
      role="tablist"
    >
      <TabButton
        active={activeView === "activities"}
        onClick={() => onChange("activities")}
        tab="activities"
      >
        Activities
      </TabButton>
      <TabButton
        active={activeView === "holdings"}
        onClick={() => onChange("holdings")}
        tab="holdings"
      >
        Holdings
      </TabButton>
    </div>
  );
}

function TabButton({
  active,
  children,
  onClick,
  tab,
}: {
  active: boolean;
  children: string;
  onClick: () => void;
  tab: WorkspaceView;
}) {
  return (
    <button
      aria-selected={active}
      className={[
        "min-h-11 border-b-2 px-4 text-sm font-semibold transition",
        active
          ? "border-[#3e6f57] text-[#173f35]"
          : "border-transparent text-[#65746a] hover:border-[#b9c9be] hover:text-[#173f35]",
      ].join(" ")}
      id={`workspace-tab-${tab}`}
      onClick={onClick}
      role="tab"
      type="button"
    >
      {children}
    </button>
  );
}
