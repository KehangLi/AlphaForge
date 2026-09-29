type MetricCardProps = {
  label: string;
  value: string;
};

export function MetricCard({ label, value }: MetricCardProps) {
  return (
    <div className="rounded-lg border border-[#cfd9d2] bg-white p-5 shadow-sm">
      <p className="text-sm text-[#65746a]">{label}</p>
      <p className="mt-2 text-3xl font-semibold">{value}</p>
    </div>
  );
}
