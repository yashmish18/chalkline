import { useState } from 'react';
import { Download, Upload, X, AlertTriangle, CheckCircle2 } from 'lucide-react';

export type ImportKind = 'allocations' | 'timetable';

interface ImportIssue {
  row: number;
  severity: 'ERROR' | 'WARN' | 'INFO';
  message: string;
}

const CONFIG: Record<ImportKind, { title: string; help: string; endpoint: string; template: string; accept: string; fileHint: string; summary: [string, string][] }> = {
  allocations: {
    title: 'Import allocations',
    help: 'One row per faculty, course, section, component and batch. Re-importing the same sheet updates rows instead of duplicating them. Role = Coordinator emails that person a link to activate coordinator access.',
    endpoint: '/api/allocations/import',
    template: '/api/allocations/import-template',
    accept: '.csv,.xlsx,.xls',
    fileHint: 'Choose a .csv or .xlsx file',
    summary: [
      ['rows', 'Rows read'],
      ['facultyCreated', 'New faculty'],
      ['coursesCreated', 'New courses'],
      ['offeringsCreated', 'New sections'],
      ['assignmentsCreated', 'New allocations'],
      ['assignmentsUpdated', 'Updated allocations'],
      ['coordinatorInvites', 'Coordinator links emailed'],
    ],
  },
  timetable: {
    title: 'Import timetable',
    help: 'One row per weekly class slot. Each slot is matched to the faculty member’s allocation for that course, section and batch. Importing replaces the previous timetable.',
    endpoint: '/api/timetable/import',
    template: '/api/timetable/import-template',
    accept: '.csv,.xlsx,.xls,.pdf',
    fileHint: 'Choose a timetable PDF, .csv or .xlsx file',
    summary: [
      ['rows', 'Rows read'],
      ['slotsCreated', 'Class slots'],
      ['facultyCovered', 'Faculty with a timetable'],
    ],
  },
};

interface ImportSpreadsheetModalProps {
  kind: ImportKind;
  onClose: () => void;
  onSuccess: () => void;
  /** Replaces the default explanation, e.g. for a faculty member uploading only their own week. */
  help?: string;
}

export function ImportSpreadsheetModal({ kind, onClose, onSuccess, help }: ImportSpreadsheetModalProps) {
  const config = CONFIG[kind];
  const [file, setFile] = useState<File | null>(null);
  const isPdf = file?.name.toLowerCase().endsWith('.pdf') ?? false;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<(Record<string, number> & { issues: ImportIssue[] }) | null>(null);

  const upload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;
    setBusy(true);
    setError(null);
    const form = new FormData();
    form.append('file', file);
    try {
      const res = await fetch(config.endpoint, { method: 'POST', credentials: 'include', body: form });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.detail || body.title || `Upload failed (${res.status})`);
      }
      setResult(await res.json());
      onSuccess();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setBusy(false);
    }
  };

  const errors = result?.issues.filter((i) => i.severity === 'ERROR').length ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4" role="dialog" aria-modal="true" aria-labelledby="import-title">
      <div className="card w-full max-w-lg p-6 shadow-lg">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="import-title" className="font-display text-2xl font-medium">{config.title}</h2>
            <p className="mt-1.5 text-[13px] leading-relaxed text-[var(--ink-subtle)]">{help ?? config.help}</p>
          </div>
          <button type="button" onClick={onClose} className="btn btn-quiet w-8 px-0" aria-label="Close">
            <X className="w-4 h-4" />
          </button>
        </div>

        {!result ? (
          <form onSubmit={upload} className="mt-6 space-y-4">
            <label className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-[var(--hairline-strong)] bg-[var(--surface-3)] px-4 py-8 text-center cursor-pointer hover:border-[var(--accent)]">
              <Upload className="w-5 h-5 text-[var(--ink-subtle)]" />
              <span className="text-[13px] font-medium">{file ? file.name : config.fileHint}</span>
              <span className="text-[12px] text-[var(--ink-subtle)]">
                {isPdf
                  ? 'A week grid: days down the side, class times across the top.'
                  : 'Headers are matched by name, so column order doesn’t matter.'}
              </span>
              <input
                type="file"
                accept={config.accept}
                className="sr-only"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setError(null);
                }}
              />
            </label>

            {error && (
              <p role="alert" className="flex gap-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-[13px] text-rose-800">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" /> {error}
              </p>
            )}

            <div className="flex items-center justify-between">
              <a href={config.template} className="inline-flex items-center gap-1.5 text-[13px] font-medium text-[var(--accent)] hover:underline">
                <Download className="w-3.5 h-3.5" /> Template
              </a>
              <button type="submit" disabled={!file || busy} className="btn btn-primary">
                {busy ? 'Importing…' : 'Import'}
              </button>
            </div>
          </form>
        ) : (
          <div className="mt-6 space-y-5">
            <p className={`flex items-center gap-2 text-[13px] ${errors ? 'text-[var(--warn-text)]' : 'text-[var(--success-text)]'}`}>
              {errors ? <AlertTriangle className="w-4 h-4" /> : <CheckCircle2 className="w-4 h-4" />}
              {errors ? `Imported, but ${errors} row${errors === 1 ? ' was' : 's were'} skipped.` : 'Imported without problems.'}
            </p>
            <dl className="grid grid-cols-3 gap-px overflow-hidden rounded-lg border border-[var(--hairline)] bg-[var(--hairline)]">
              {config.summary.map(([key, label]) => (
                <div key={key} className="bg-white px-3 py-2.5">
                  <dt className="text-[11px] text-[var(--ink-subtle)]">{label}</dt>
                  <dd className="mt-0.5 font-display text-xl num">{result[key] ?? 0}</dd>
                </div>
              ))}
            </dl>
            {result.issues.length > 0 && (
              <div className="max-h-56 overflow-y-auto rounded-lg border border-[var(--hairline)]">
                <table className="min-w-full text-[12px]">
                  <thead className="sticky top-0 bg-[var(--surface-3)] text-left text-[var(--ink-subtle)]">
                    <tr>
                      <th className="px-3 py-2 font-medium">Row</th>
                      <th className="px-3 py-2 font-medium">Problem</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--hairline-soft)]">
                    {result.issues.map((issue, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2 num align-top">{issue.row}</td>
                        <td className={`px-3 py-2 ${issue.severity === 'ERROR' ? 'text-[var(--danger-text)]' : 'text-[var(--ink-muted)]'}`}>{issue.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="flex justify-end">
              <button type="button" onClick={onClose} className="btn btn-primary">Done</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
