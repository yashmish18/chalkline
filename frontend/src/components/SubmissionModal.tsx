import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { DeadlineAssignmentDto } from '../types/deadlines';
import { SubmissionDto } from '../types/submissions';
import { useAuth } from '../context/AuthContext';
import {
  X,
  Upload,
  FileText,
  CheckCircle,
  AlertCircle,
  Clock,
  Download,
  RotateCcw,
  ShieldCheck,
  Check,
  Calendar,
} from 'lucide-react';

interface SubmissionModalProps {
  assignment: DeadlineAssignmentDto;
  onClose: () => void;
}

export function SubmissionModal({ assignment, onClose }: SubmissionModalProps) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const isCoordinator = user?.role === 'COORDINATOR';

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [reviewAction, setReviewAction] = useState<'ACCEPT' | 'RETURN' | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Fetch current submission for this assignment
  const {
    data: submission,
    isLoading,
    refetch,
  } = useQuery<SubmissionDto>({
    queryKey: ['submission', assignment.id],
    queryFn: async () => {
      const res = await fetch(`/api/deadline-assignments/${assignment.id}/submission`);
      if (!res.ok) {
        throw new Error('Failed to fetch submission details');
      }
      return res.json();
    },
  });

  // Upload mutation
  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);

      const res = await fetch(`/api/deadline-assignments/${assignment.id}/submissions`, {
        method: 'POST',
        body: formData,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.message || 'File upload failed');
      }

      return res.json();
    },
    onSuccess: () => {
      setSelectedFile(null);
      setErrorMessage(null);
      refetch();
      queryClient.invalidateQueries({ queryKey: ['deadlines'] });
    },
    onError: (err: Error) => {
      setErrorMessage(err.message);
    },
  });

  // Review mutation (Coordinator)
  const reviewMutation = useMutation({
    mutationFn: async ({ submissionId, action, comment }: { submissionId: number; action: string; comment?: string }) => {
      const res = await fetch(`/api/submissions/${submissionId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, comment }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.detail || errorData.message || 'Failed to submit review');
      }

      return res.json();
    },
    onSuccess: () => {
      setReviewAction(null);
      setReviewComment('');
      setErrorMessage(null);
      refetch();
      queryClient.invalidateQueries({ queryKey: ['deadlines'] });
    },
    onError: (err: Error) => {
      setErrorMessage(err.message);
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setErrorMessage(null);
    }
  };

  const handleUploadSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;
    uploadMutation.mutate(selectedFile);
  };

  const handleReviewSubmit = (action: 'ACCEPT' | 'RETURN') => {
    if (!submission) return;
    if (action === 'RETURN' && !reviewComment.trim()) {
      setErrorMessage('Please provide a comment explaining what needs revision.');
      return;
    }
    reviewMutation.mutate({
      submissionId: submission.id,
      action,
      comment: reviewComment.trim() || undefined,
    });
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const formatDate = (isoString?: string | null) => {
    if (!isoString) return '—';
    return new Date(isoString).toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-lg border border-border shadow-xl max-w-2xl w-full p-6 relative max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in-95 duration-150">
        <button
          type="button"
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1 rounded-md hover:bg-slate-100 transition-colors"
          aria-label="Close dialog"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Header */}
        <div className="border-b border-border pb-4 mb-5">
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
              {assignment.type.replace('_', ' ')}
            </span>
            <span className="text-slate-300">•</span>
            <span className="text-xs text-slate-600 flex items-center gap-1">
              <Calendar className="w-3.5 h-3.5 text-slate-400" />
              Due: <strong>{formatDate(assignment.dueAt)}</strong>
            </span>
          </div>
          <h2 className="font-sans text-xl font-bold text-slate-900 mt-1">{assignment.title}</h2>
          {assignment.description && (
            <p className="text-xs text-slate-600 mt-1">{assignment.description}</p>
          )}
          <div className="text-xs text-slate-500 mt-2">
            Assigned to: <strong className="text-slate-800">{assignment.facultyName}</strong>
          </div>
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="py-12 text-center text-slate-500 text-sm">Loading submission status...</div>
        ) : (
          <div className="space-y-6">
            {/* Status Banner */}
            <div className="flex items-center justify-between p-3.5 rounded-lg border bg-slate-50 border-slate-200">
              <div className="flex items-center gap-2.5">
                <span className="text-xs font-medium text-slate-600">Current Status:</span>
                {submission?.status === 'PENDING' && (
                  <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-slate-200 text-slate-700">
                    <Clock className="w-3.5 h-3.5 text-slate-500" /> Pending Submission
                  </span>
                )}
                {submission?.status === 'SUBMITTED' && (
                  <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-blue-50 text-blue-700 border border-blue-200">
                    <CheckCircle className="w-3.5 h-3.5 text-blue-500" /> Submitted (On Time)
                  </span>
                )}
                {submission?.status === 'LATE' && (
                  <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                    <Clock className="w-3.5 h-3.5 text-amber-600" /> Submitted Late
                  </span>
                )}
                {submission?.status === 'RETURNED' && (
                  <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-rose-50 text-rose-700 border border-rose-200">
                    <RotateCcw className="w-3.5 h-3.5 text-rose-600" /> Returned for Revision
                  </span>
                )}
                {submission?.status === 'ACCEPTED' && (
                  <span className="inline-flex items-center gap-1 text-xs px-2.5 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <Check className="w-3.5 h-3.5 text-emerald-600" /> Accepted & Approved
                  </span>
                )}
              </div>

              {submission?.submittedAt && (
                <span className="text-xs text-slate-500">
                  Last submitted: {formatDate(submission.submittedAt)}
                </span>
              )}
            </div>

            {/* Coordinator view of a slot that has not been submitted yet */}
            {isCoordinator && submission?.status === 'PENDING' && (
              <div className="p-3 rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-600 flex items-center gap-2">
                <Clock className="w-4 h-4 text-slate-400 shrink-0" />
                <span>Nothing submitted yet. The faculty member submits this from their own Deadlines tab.</span>
              </div>
            )}

            {/* Reviewer Feedback Banner if returned */}
            {submission?.status === 'RETURNED' && submission.reviewerComment && (
              <div className="p-4 rounded-lg bg-rose-50 border border-rose-200 text-rose-800 text-xs">
                <div className="flex items-center gap-1.5 font-semibold text-rose-900 mb-1">
                  <AlertCircle className="w-4 h-4 text-rose-600" />
                  Coordinator Feedback & Revision Request:
                </div>
                <p className="mt-1 whitespace-pre-wrap">{submission.reviewerComment}</p>
              </div>
            )}

            {/* Error Message */}
            {errorMessage && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-md flex items-center gap-2">
                <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
                <span>{errorMessage}</span>
              </div>
            )}

            {/* Latest File Section */}
            {submission?.latestFile && (
              <div className="bg-slate-50/70 border border-border rounded-lg p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded bg-white border border-border flex items-center justify-center text-blue-600">
                      <FileText className="w-5 h-5" />
                    </div>
                    <div>
                      <div className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                        {submission.latestFile.originalName}
                        <span className="text-[10px] bg-slate-200 text-slate-700 px-1.5 py-0.5 rounded font-mono">
                          v{submission.latestFile.version}
                        </span>
                      </div>
                      <div className="text-xs text-slate-500 mt-0.5">
                        {formatFileSize(submission.latestFile.sizeBytes)} • {formatDate(submission.latestFile.uploadedAt)}
                      </div>
                    </div>
                  </div>

                  <a
                    href={`/api/submission-files/${submission.latestFile.id}/download`}
                    download
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-white border border-border rounded-md text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors shadow-2xs"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Download File
                  </a>
                </div>

                <div className="mt-2.5 pt-2 border-t border-slate-200/60 flex items-center gap-1.5 text-[11px] text-slate-400 font-mono">
                  <ShieldCheck className="w-3.5 h-3.5 text-slate-400" />
                  <span className="truncate">SHA-256: {submission.latestFile.sha256}</span>
                </div>
              </div>
            )}

            {/* Coordinator Review Actions */}
            {isCoordinator && submission && submission.status !== 'PENDING' && (
              <div className="bg-white border border-border rounded-lg p-4 space-y-3">
                <h4 className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Coordinator Review
                </h4>

                {reviewAction === 'RETURN' ? (
                  <div className="space-y-3">
                    <label className="block text-xs font-medium text-slate-700">
                      Reason for Returning (Required feedback for faculty):
                    </label>
                    <textarea
                      rows={3}
                      value={reviewComment}
                      onChange={(e) => setReviewComment(e.target.value)}
                      placeholder="e.g. Please update the course evaluation rubric according to NBA criteria..."
                      className="w-full text-xs p-2.5 border border-border rounded-md focus:ring-1 focus:ring-slate-900 focus:border-slate-900"
                    />
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={() => handleReviewSubmit('RETURN')}
                        disabled={reviewMutation.isPending}
                        className="px-3 py-1.5 bg-rose-600 text-white text-xs font-medium rounded-md hover:bg-rose-700 transition-colors disabled:opacity-50"
                      >
                        {reviewMutation.isPending ? 'Returning...' : 'Confirm Return for Revision'}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setReviewAction(null);
                          setReviewComment('');
                        }}
                        className="px-3 py-1.5 border border-border text-xs text-slate-600 rounded-md hover:bg-slate-50 transition-colors"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => handleReviewSubmit('ACCEPT')}
                      disabled={reviewMutation.isPending || submission.status === 'ACCEPTED'}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-emerald-600 text-white text-xs font-medium rounded-md hover:bg-emerald-700 transition-colors shadow-xs disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <Check className="w-3.5 h-3.5" />
                      {submission.status === 'ACCEPTED' ? 'Accepted' : 'Accept & Approve'}
                    </button>
                    <button
                      type="button"
                      onClick={() => setReviewAction('RETURN')}
                      disabled={reviewMutation.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 border border-rose-300 text-rose-700 bg-rose-50 text-xs font-medium rounded-md hover:bg-rose-100 transition-colors"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      Return for Revision
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* Faculty File Upload — coordinators only review, they never upload on a faculty member's behalf */}
            {!isCoordinator && (
              <form onSubmit={handleUploadSubmit} className="space-y-4">
                <div className="border-2 border-dashed border-border rounded-lg p-6 text-center hover:border-slate-400 transition-colors bg-slate-50/50">
                  <Upload className="w-8 h-8 text-slate-400 mx-auto mb-2" />
                  <p className="text-xs font-medium text-slate-700 mb-1">
                    {submission?.status === 'RETURNED'
                      ? 'Upload revised file'
                      : submission?.status === 'PENDING'
                      ? 'Select file to submit'
                      : 'Upload a new version'}
                  </p>
                  <p className="text-[11px] text-slate-500 mb-3">
                    Allowed: PDF, Word (DOCX/DOC), Excel (XLSX/XLS), PNG, JPG, ZIP (max 25MB)
                  </p>

                  <input
                    type="file"
                    id="submission-file-input"
                    onChange={handleFileChange}
                    accept=".pdf,.docx,.doc,.xlsx,.xls,.png,.jpg,.jpeg,.zip,.txt"
                    className="hidden"
                  />
                  <label
                    htmlFor="submission-file-input"
                    className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-white border border-border text-slate-700 text-xs font-medium rounded-md hover:bg-slate-50 cursor-pointer shadow-2xs transition-colors"
                  >
                    Browse Files
                  </label>

                  {selectedFile && (
                    <div className="mt-3 text-xs text-blue-600 font-medium flex items-center justify-center gap-1">
                      <FileText className="w-3.5 h-3.5" />
                      <span>{selectedFile.name} ({formatFileSize(selectedFile.size)})</span>
                    </div>
                  )}
                </div>

                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={onClose}
                    className="px-4 py-2 border border-border rounded-md text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={!selectedFile || uploadMutation.isPending}
                    className="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-900 text-white text-xs font-semibold rounded-md hover:bg-slate-800 transition-colors shadow-xs disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <Upload className="w-3.5 h-3.5" />
                    {uploadMutation.isPending ? 'Uploading...' : 'Submit File'}
                  </button>
                </div>
              </form>
            )}

            {/* Version History */}
            {submission && submission.files && submission.files.length > 1 && (
              <div className="border-t border-border pt-4">
                <h4 className="text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2">
                  Previous Versions ({submission.files.length})
                </h4>
                <div className="divide-y divide-slate-100 border border-border rounded-md overflow-hidden text-xs">
                  {submission.files.map((f) => (
                    <div key={f.id} className="p-2.5 flex items-center justify-between hover:bg-slate-50 transition-colors">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[11px] font-semibold bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded">
                          v{f.version}
                        </span>
                        <span className="font-medium text-slate-800">{f.originalName}</span>
                        <span className="text-slate-400">•</span>
                        <span className="text-slate-500">{formatFileSize(f.sizeBytes)}</span>
                        <span className="text-slate-400">•</span>
                        <span className="text-slate-400">{formatDate(f.uploadedAt)}</span>
                      </div>
                      <a
                        href={`/api/submission-files/${f.id}/download`}
                        download
                        className="text-blue-600 hover:underline text-xs inline-flex items-center gap-1"
                      >
                        <Download className="w-3 h-3" /> Download
                      </a>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
