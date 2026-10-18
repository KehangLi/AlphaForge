import type { Metadata } from "next";

import { WorkspacePreview } from "@/features/auth/preview/workspace-preview";

export const metadata: Metadata = { title: "Sample workspace | AlphaForge" };

export default function WorkspacePreviewPage() {
  return <WorkspacePreview />;
}
