import { Button } from "@/components/ui/button";

import type { AccountActivity } from "../types";

type AccountActivitiesTableProps = {
  activities: AccountActivity[];
};

export function AccountActivitiesTable({
  activities,
}: AccountActivitiesTableProps) {
  return (
    <section className="overflow-hidden rounded-lg border border-[#cfd9d2] bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b border-[#d8e1db] p-5 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="text-lg font-semibold">Account activities</h2>
          <p className="mt-1 text-sm text-[#65746a]">
            Recent rows from the selected brokerage account.
          </p>
        </div>
        <Button className="px-3" variant="secondary">
          Refresh
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[860px] border-collapse text-sm">
          <thead className="bg-[#f4f7f5] text-left text-xs uppercase tracking-[0.12em] text-[#65746a]">
            <tr>
              <th className="px-5 py-4 font-semibold">Date</th>
              <th className="px-5 py-4 font-semibold">Action</th>
              <th className="px-5 py-4 font-semibold">Instrument</th>
              <th className="px-5 py-4 font-semibold">Shares</th>
              <th className="px-5 py-4 text-right font-semibold">Price</th>
              <th className="px-5 py-4 text-right font-semibold">Total</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#e3e9e5]">
            {activities.map((activity) => (
              <tr
                className="hover:bg-[#f8faf8]"
                key={`${activity.date}-${activity.action}-${activity.ticker}`}
              >
                <td className="px-5 py-4 text-[#4e5d53]">{activity.date}</td>
                <td className="px-5 py-4">
                  <span className="rounded-full bg-[#edf5ef] px-2.5 py-1 text-xs font-semibold text-[#2d654b]">
                    {activity.action}
                  </span>
                </td>
                <td className="px-5 py-4">
                  <p className="font-medium">{activity.instrument}</p>
                  <p className="mt-1 text-xs text-[#65746a]">
                    {activity.ticker}
                  </p>
                </td>
                <td className="px-5 py-4 text-[#4e5d53]">{activity.shares}</td>
                <td className="px-5 py-4 text-right text-[#4e5d53]">
                  {activity.price === "-"
                    ? "-"
                    : `${activity.price} ${activity.currency}`}
                </td>
                <td className="px-5 py-4 text-right font-semibold">
                  {activity.total} {activity.currency}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
