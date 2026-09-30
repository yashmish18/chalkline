import React, { useState } from 'react';
import { Upload, FileSpreadsheet, Download, CheckCircle2, AlertCircle, X, Loader2 } from 'lucide-react';

interface ImportResult {
  totalRows: number;
  facultyCreated: number;
  facultyUpdated: number;
  assignmentsCreated: number;
  messages: string[];
}

interface ImportFacultyModalProps {
  onClose: () => void;
  onSuccess: () => void;
}

export function ImportFacultyModal({ onClose, onSuccess }: ImportFacultyModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0]);
      setError(null);
    }
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a CSV or Excel file to upload.');
      return;
    }

    setIsUploading(true);
    setError(null);

    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await fetch('/api/faculty/import', {
        method: 'POST',
        credentials: 'include',
        body: formData,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.title || `Upload failed with status ${res.status}`);
      }

      const data: ImportResult = await res.json();
      setResult(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error importing faculty spreadsheet';
      setError(msg);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDownloadTemplate = () => {
    window.open('/api/faculty/import-template', '_blank');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-2xs animate-in fade-in">
      <div className="bg-white rounded-xl border border-slate-200 shadow-xl max-w-lg w-full p-6 animate-in zoom-in-95 duration-150">
        <div className="flex items-start justify-between pb-3 border-b border-slate-100">
          <div>
            <span className="text-xs font-bold text-primary uppercase tracking-wider">Bulk Data Import</span>
            <h2 className="text-lg font-bold text-slate-900 mt-0.5">Import Faculty &amp; Allocations</h2>
            <p className="text-xs text-slate-500 mt-1">
              Upload an Excel (.xlsx, .xls) or CSV file. The system automatically creates faculty accounts and maps course allocations.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-700 text-lg font-bold p-1 rounded-md"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {error && (
          <div className="mt-4 bg-rose-50 border border-rose-200 text-rose-800 p-3 rounded-lg text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {result ? (
          <div className="mt-4 space-y-4">
            <div className="bg-emerald-50 border border-emerald-200 text-emerald-900 p-4 rounded-lg text-xs space-y-2">
              <div className="flex items-center gap-2 font-bold text-sm text-emerald-800">
                <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                Spreadsheet Successfully Imported
              </div>
              <div className="grid grid-cols-3 gap-2 pt-2 border-t border-emerald-200/60 text-center font-medium">
                <div className="bg-white/80 p-2 rounded border border-emerald-200">
                  <div className="text-lg font-bold text-slate-900">{result.totalRows}</div>
                  <div className="text-[10px] text-slate-500">Rows Processed</div>
                </div>
                <div className="bg-white/80 p-2 rounded border border-emerald-200">
                  <div className="text-lg font-bold text-emerald-700">{result.facultyCreated + result.facultyUpdated}</div>
                  <div className="text-[10px] text-slate-500">Faculty Synced</div>
                </div>
                <div className="bg-white/80 p-2 rounded border border-emerald-200">
                  <div className="text-lg font-bold text-primary">{result.assignmentsCreated}</div>
                  <div className="text-[10px] text-slate-500">Allocations Created</div>
                </div>
              </div>
            </div>

            {result.messages.length > 0 && (
              <div className="max-h-32 overflow-y-auto bg-slate-50 border border-slate-200 rounded p-2.5 text-[11px] text-slate-600 space-y-1 font-mono">
                {result.messages.map((m, idx) => (
                  <div key={idx}>&rsaquo; {m}</div>
                ))}
              </div>
            )}

            <div className="flex justify-end pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={() => {
                  onSuccess();
                  onClose();
                }}
                className="px-4 py-2 bg-primary text-white text-xs font-bold rounded-md hover:bg-primary-hover shadow-xs"
              >
                Done &amp; Refresh Data
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleUpload} className="mt-4 space-y-4">
            <div className="p-4 border-2 border-dashed border-slate-200 hover:border-primary/60 rounded-xl text-center cursor-pointer transition-colors bg-slate-50/50">
              <input
                type="file"
                id="faculty-file-input"
                accept=".csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel"
                onChange={handleFileChange}
                className="hidden"
              />
              <label htmlFor="faculty-file-input" className="cursor-pointer block">
                <FileSpreadsheet className="w-8 h-8 text-primary mx-auto mb-2" />
                <div className="text-xs font-bold text-slate-800">
                  {file ? file.name : 'Click or drag Excel or CSV file here'}
                </div>
                <div className="text-[11px] text-slate-500 mt-1">
                  Supports .csv, .xlsx, .xls up to 10MB
                </div>
              </label>
            </div>

            <div className="flex items-center justify-between text-xs pt-1">
              <button
                type="button"
                onClick={handleDownloadTemplate}
                className="text-primary hover:underline font-semibold flex items-center gap-1.5"
              >
                <Download className="w-3.5 h-3.5" />
                Download Sample CSV Template
              </button>
              {file && (
                <span className="text-[11px] text-slate-500 font-mono">
                  {(file.size / 1024).toFixed(1)} KB
                </span>
              )}
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-md"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!file || isUploading}
                className="px-4 py-2 bg-primary text-white text-xs font-bold rounded-md hover:bg-primary-hover shadow-xs flex items-center gap-1.5 disabled:opacity-50"
              >
                {isUploading ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Parsing &amp; Importing...
                  </>
                ) : (
                  <>
                    <Upload className="w-3.5 h-3.5" />
                    Import Spreadsheet
                  </>
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
