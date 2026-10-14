import { useState, useEffect, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Upload, FileText, X, Loader2, AlertCircle, Trash2, Plus } from 'lucide-react';
import { ParsedSyllabusDto } from '../types/academics';

interface SyllabusImportModalProps {
  courseId: number;
  courseCode: string;
  /** Document already chosen in the Add Course form — parsed automatically on open. */
  initialFile?: File | null;
  onClose: () => void;
  onConfirmed: () => void;
}

type Stage = 'upload' | 'review' | 'done';

/**
 * Syllabus ingestion: upload a PDF/DOC, review the parsed units and topics (editable:
 * rename units, edit topics, drop rows, add topics), then confirm to replace the course
 * syllabus. Nothing is saved until confirm.
 */
export function SyllabusImportModal({ courseId, courseCode, initialFile, onClose, onConfirmed }: SyllabusImportModalProps) {
  const [stage, setStage] = useState<Stage>('upload');
  const [file, setFile] = useState<File | null>(initialFile ?? null);
  const autoParsed = useRef(false);
  const [parsed, setParsed] = useState<ParsedSyllabusDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  const previewMutation = useMutation({
    mutationFn: async (selected: File) => {
      const formData = new FormData();
      formData.append('file', selected);
      const res = await fetch(`/api/courses/${courseId}/syllabus/preview`, {
        method: 'POST',
        credentials: 'include',
        body: formData,
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.detail || data.title || `Failed to parse document (${res.status})`);
      }
      return res.json() as Promise<ParsedSyllabusDto>;
    },
    onSuccess: (data) => {
      setParsed(data);
      setStage('review');
    },
    onError: (err: Error) => setError(err.message),
  });

  // When the Add Course form already attached a document, jump straight to the review step.
  // The ref guard makes repeat runs harmless, so the mutation can stay in the dependency list.
  useEffect(() => {
    if (initialFile && !autoParsed.current) {
      autoParsed.current = true;
      previewMutation.mutate(initialFile);
    }
  }, [initialFile, previewMutation]);

  const confirmMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`/api/courses/${courseId}/syllabus/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify(parsed),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.detail || data.title || `Failed to save syllabus (${res.status})`);
      }
      return res.json();
    },
    onSuccess: () => {
      setStage('done');
    },
    onError: (err: Error) => setError(err.message),
  });

  const updateUnitTitle = (unitIndex: number, title: string) => {
    if (!parsed) return;
    const units = parsed.units.map((u, i) => (i === unitIndex ? { ...u, unitTitle: title } : u));
    setParsed({ ...parsed, units });
  };

  const updateTopic = (unitIndex: number, topicIndex: number, name: string) => {
    if (!parsed) return;
    const units = parsed.units.map((u, i) =>
      i === unitIndex
        ? { ...u, topics: u.topics.map((t, j) => (j === topicIndex ? { ...t, topicName: name } : t)) }
        : u
    );
    setParsed({ ...parsed, units });
  };

  const removeTopic = (unitIndex: number, topicIndex: number) => {
    if (!parsed) return;
    const units = parsed.units
      .map((u, i) => (i === unitIndex ? { ...u, topics: u.topics.filter((_, j) => j !== topicIndex) } : u))
      .filter((u) => u.topics.length > 0);
    setParsed({ ...parsed, units });
  };

  const addTopic = (unitIndex: number) => {
    if (!parsed) return;
    const units = parsed.units.map((u, i) =>
      i === unitIndex
        ? { ...u, topics: [...u.topics, { topicName: 'New topic', targetWeek: u.topics.length > 0 ? u.topics[u.topics.length - 1].targetWeek : 1 }] }
        : u
    );
    setParsed({ ...parsed, units });
  };

  const topicCount = parsed?.units.reduce((sum, u) => sum + u.topics.length, 0) ?? 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 animate-in fade-in">
      <div className="bg-[#FFFFFF] rounded-xl border border-[#E6E4DD] shadow-2xl max-w-2xl w-full max-h-[90vh] flex flex-col text-[#16181D]">
        {/* Header */}
        <div className="flex items-start justify-between p-5 border-b border-[#E6E4DD]">
          <div>
            <h2 className="text-lg font-bold tracking-tight">Upload Course Description</h2>
            <p className="text-xs text-[#6B6F76] mt-0.5">
              <span className="font-mono">{courseCode}</span> — upload the course description document; its units
              and topics are mapped onto the course.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-[#8A8E95] hover:text-[#16181D] p-1 rounded-md"
            aria-label="Close syllabus import"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {error && (
          <div className="mx-5 mt-4 bg-[rgba(220,38,38,.14)] border border-[rgba(248,113,113,.35)] text-[#B42318] p-3 rounded-lg text-xs flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {/* Upload stage */}
        {stage === 'upload' && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (file) {
                setError(null);
                previewMutation.mutate(file);
              }
            }}
            className="p-5 space-y-4"
          >
            <div
              onClick={() => document.getElementById('syllabus-file-input')?.click()}
              className="border-2 border-dashed border-[#E6E4DD] hover:border-[#D6D3CA] rounded-xl p-8 text-center cursor-pointer transition-colors bg-[#F6F5F1]"
            >
              <input
                id="syllabus-file-input"
                type="file"
                accept=".pdf,.doc,.docx,.txt,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/plain"
                onChange={(e) => {
                  if (e.target.files?.[0]) setFile(e.target.files[0]);
                }}
                className="hidden"
              />
              <FileText className="w-8 h-8 text-[#0F5C5C] mx-auto mb-2" />
              <div className="text-sm font-semibold">{file ? file.name : 'Choose the course description document'}</div>
              <div className="text-[11px] text-[#8A8E95] mt-1">PDF, Word or text — up to 10MB. Parsing runs locally on the server.</div>
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-semibold text-[#6B6F76] hover:bg-[#EFEEE9] rounded-md"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={!file || previewMutation.isPending}
                className="px-4 py-2 bg-[#0F5C5C] text-white text-xs font-semibold rounded-md hover:bg-[#0B4747] flex items-center gap-1.5 disabled:opacity-50"
              >
                {previewMutation.isPending ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Parsing document…
                  </>
                ) : (
                  <>
                    <Upload className="w-3.5 h-3.5" />
                    Parse &amp; Preview
                  </>
                )}
              </button>
            </div>
          </form>
        )}

        {/* Review stage */}
        {stage === 'review' && parsed && (
          <>
            <div className="px-5 pt-4 flex items-center justify-between text-xs">
              <div className="text-[#6B6F76]">
                <span className="font-mono text-[#16181D]">{parsed.sourceFileName}</span>
                {' '}— {parsed.units.length} units, {topicCount} topics detected. Edit anything before saving.
              </div>
              <button
                type="button"
                onClick={() => {
                  setStage('upload');
                  setParsed(null);
                }}
                className="text-[#0B4747] hover:underline font-semibold"
              >
                Re-upload
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-5 space-y-4">
              {parsed.units.map((unit, unitIndex) => (
                <div key={unitIndex} className="rounded-lg border border-[#E6E4DD] bg-[#F6F5F1] p-4">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[11px] font-bold text-[#0B4747] shrink-0">U{unit.unitNumber}</span>
                    <input
                      value={unit.unitTitle}
                      onChange={(e) => updateUnitTitle(unitIndex, e.target.value)}
                      className="flex-1 bg-transparent text-sm font-semibold text-[#16181D] border-b border-transparent hover:border-[#D6D3CA] focus:border-[#0F5C5C] focus:outline-none py-0.5"
                      aria-label={`Unit ${unit.unitNumber} title`}
                    />
                  </div>
                  <div className="mt-3 space-y-1.5">
                    {unit.topics.map((topic, topicIndex) => (
                      <div key={topicIndex} className="flex items-center gap-2 group">
                        <input
                          value={topic.topicName}
                          onChange={(e) => updateTopic(unitIndex, topicIndex, e.target.value)}
                          className="flex-1 bg-[#FFFFFF] border border-[#E6E4DD] rounded-md px-2.5 py-1.5 text-xs text-[#16181D] focus:outline-none focus:border-[#0F5C5C]"
                          aria-label={`Topic ${topicIndex + 1} of unit ${unit.unitNumber}`}
                        />
                        <button
                          type="button"
                          onClick={() => removeTopic(unitIndex, topicIndex)}
                          className="p-1.5 text-[#8A8E95] hover:text-[#B42318] hover:bg-[rgba(220,38,38,.14)] rounded-md opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity"
                          title="Remove topic"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))}
                  </div>
                  <button
                    type="button"
                    onClick={() => addTopic(unitIndex)}
                    className="mt-2 inline-flex items-center gap-1 text-[11px] font-semibold text-[#0B4747] hover:underline"
                  >
                    <Plus className="w-3 h-3" />
                    Add topic
                  </button>
                </div>
              ))}
            </div>

            <div className="flex items-center justify-between p-4 border-t border-[#E6E4DD]">
              <p className="text-[11px] text-[#8A8E95]">
                Confirming replaces the existing syllabus for {courseCode}.
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 text-xs font-semibold text-[#6B6F76] hover:bg-[#EFEEE9] rounded-md"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setError(null);
                    confirmMutation.mutate();
                  }}
                  disabled={confirmMutation.isPending || topicCount === 0}
                  className="px-4 py-2 bg-[#0F5C5C] text-white text-xs font-semibold rounded-md hover:bg-[#0B4747] disabled:opacity-50"
                >
                  {confirmMutation.isPending ? 'Saving…' : `Save ${topicCount} topics`}
                </button>
              </div>
            </div>
          </>
        )}

        {/* Done stage */}
        {stage === 'done' && (
          <div className="p-8 text-center">
            <div className="text-sm font-semibold text-[#2E7D4F]">Syllabus saved.</div>
            <p className="text-xs text-[#6B6F76] mt-1">{topicCount} topics mapped to {courseCode} and feeding the daily-log dropdown.</p>
            <button
              type="button"
              onClick={() => {
                onConfirmed();
                onClose();
              }}
              className="mt-5 px-4 py-2 bg-[#0F5C5C] text-white text-xs font-semibold rounded-md hover:bg-[#0B4747]"
            >
              Done
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
