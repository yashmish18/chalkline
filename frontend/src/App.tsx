
export default function App() {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="border-b border-border bg-white px-6 py-4 flex items-center justify-between shadow-sm">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-md bg-primary flex items-center justify-center text-white font-serif font-bold text-lg">
            IET
          </div>
          <div>
            <h1 className="font-serif text-lg font-bold text-slate-900 leading-tight">Faculty Dashboard</h1>
            <p className="text-xs text-slate-500">Department Coordinator & Faculty Portal</p>
          </div>
        </div>
      </header>
      <main className="flex-1 max-w-7xl w-full mx-auto p-6">
        <div className="rounded-lg border border-border bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900 mb-2">Welcome to Faculty Dashboard</h2>
          <p className="text-sm text-slate-600">Workspace initialized. Foundations and architecture configured.</p>
        </div>
      </main>
    </div>
  );
}
