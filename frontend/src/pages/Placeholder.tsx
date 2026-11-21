export default function Placeholder({ title }: { title: string }) {
  return (
    <div>
      <h1 className="mb-2 text-2xl font-semibold">{title}</h1>
      <p className="text-slate-500">This screen is built in the next steps.</p>
    </div>
  )
}