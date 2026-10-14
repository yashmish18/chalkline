import { useState, useRef } from 'react';
import { X, Upload, FileText, CheckCircle2, AlertCircle } from 'lucide-react';
import { AcademicDocumentCategory, AcademicDocumentDto } from '../types/documents';
import { CourseDto } from '../types/academics';

interface UploadAcademicDocumentModalProps {
  initialCategory?: AcademicDocumentCategory;
  initialCourseId?: number;
  courses?: CourseDto[];
  onClose: () => void;
  onSuccess: (doc: AcademicDocumentDto) => void;
}

export function UploadAcademicDocumentModal({
  initialCategory = 'MASTER_TIMETABLE',
  initialCourseId,
  courses = [],
  onClose,
  onSuccess,
}: UploadAcademicDocumentModalProps) {
  const [category, setCategory] = useState<AcademicDocumentCategory>(initialCategory);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [courseId, setCourseId] = useState<number | ''>(initialCourseId || '');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selectedFile = e.target.files[0];
      setFile(selectedFile);
      if (!title) {
        setTitle(selectedFile.name.replace(/\.[^/.]+$/, ''));
      }
      setError(null);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      setFile(droppedFile);
      if (!title) {
        setTitle(droppedFile.name.replace(/\.[^/.]+$/, ''));
      }
      setError(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('Please select a PDF or Word document (.pdf, .doc, .docx) to upload.');
      return;
    }

    setUploading(true);
    setError(null);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('category', category);
    formData.append('title', title.trim() || file.name);
    if (description.trim()) {
      formData.append('description', description.trim());
    }
    if (courseId) {
      formData.append('courseId', courseId.toString());
    }

    try {
      const res = await fetch('/api/academic-documents/upload', {
        method: 'POST',
        credentials: 'include',
        body: formData,
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => null);
        throw new Error(errorData?.detail || `Upload failed with status ${res.status}`);
      }

      const uploadedDoc: AcademicDocumentDto = await res.json();
      setSuccess(true);
      setTimeout(() => {
        onSuccess(uploadedDoc);
      }, 700);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Upload failed. Please try again.';
      setError(msg);
    } finally {
      setUploading(false);
    }
  };

  const getCategoryLabel = (cat: AcademicDocumentCategory) => {
    switch (cat) {
      case 'MASTER_TIMETABLE':
        return 'Master Timetable';
      case 'ACADEMIC_CALENDAR':
        return 'Academic Calendar';
      case 'COURSE_SYLLABUS':
        return 'Course Syllabus';
      case 'MID_SEM_QUESTION_BANK':
        return 'Mid-Sem Question Bank';
      case 'CONTINUOUS_ASSESSMENT':
        return 'Continuous Assessment';
      case 'COURSE_FILE':
        return 'Course Document / File (CD)';
      case 'REPORTS':
        return 'Academic Report';
      default:
        return cat;
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-2xs animate-in fade-in">
      <div className="bg-white rounded-lg border border-border shadow-2xl max-w-lg w-full overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-border flex items-start justify-between bg-slate-50/70">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-slate-900 text-white">
                Institutional Document
              </span>
              <span className="text-xs font-semibold text-slate-600">PDF / DOC / DOCX</span>
            </div>
            <h2 className="font-sans text-xl font-bold text-slate-900 mt-1">Upload {getCategoryLabel(category)}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4 text-xs">
          {/* Category Selector */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">Document Category</label>
            <div className="grid grid-cols-3 gap-2">
              {(['COURSE_SYLLABUS', 'MID_SEM_QUESTION_BANK', 'CONTINUOUS_ASSESSMENT', 'COURSE_FILE', 'MASTER_TIMETABLE', 'ACADEMIC_CALENDAR'] as const).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setCategory(cat)}
                  className={`py-2 px-2 text-center rounded-md font-bold border transition-all text-[11px] leading-tight ${
                    category === cat
                      ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                      : 'bg-slate-50 text-slate-700 border-border hover:bg-slate-100'
                  }`}
                >
                  {getCategoryLabel(cat)}
                </button>
              ))}
            </div>
          </div>

          {/* If Course-related, Course Selector */}
          {category !== 'MASTER_TIMETABLE' && category !== 'ACADEMIC_CALENDAR' && (
            <div>
              <label className="block font-semibold text-slate-700 mb-1">Associated Course</label>
              <select
                value={courseId}
                onChange={(e) => setCourseId(e.target.value ? Number(e.target.value) : '')}
                className="w-full px-3 py-2 text-xs border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
              >
                <option value="">Select Course (optional)...</option>
                {courses.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Title Input */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">Document Title</label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={`e.g. ${getCategoryLabel(category)}`}
              className="w-full px-3 py-2 text-xs border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900"
            />
          </div>

          {/* Description Input */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">Description / Notes (Optional)</label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Official approved version signed by HOD"
              className="w-full px-3 py-2 text-xs border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-slate-900/20 focus:border-slate-900 resize-none"
            />
          </div>

          {/* File Dropzone */}
          <div>
            <label className="block font-semibold text-slate-700 mb-1">File (PDF, DOC, DOCX)</label>
            <div
              onDragOver={(e) => e.preventDefault()}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-lg p-5 text-center cursor-pointer transition-colors ${
                file ? 'border-slate-900 bg-slate-50' : 'border-slate-300 hover:border-slate-900 hover:bg-slate-50'
              }`}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={handleFileChange}
                className="hidden"
              />

              {file ? (
                <div className="flex items-center justify-center gap-3">
                  <FileText className="w-6 h-6 text-blue-600" />
                  <div className="text-left">
                    <div className="font-bold text-slate-900 truncate max-w-xs">{file.name}</div>
                    <div className="text-[10px] text-slate-500">{(file.size / (1024 * 1024)).toFixed(2)} MB</div>
                  </div>
                  <span className="ml-auto text-[10px] font-bold text-blue-600 underline">Change</span>
                </div>
              ) : (
                <div className="space-y-1">
                  <Upload className="w-6 h-6 text-slate-400 mx-auto" />
                  <div className="font-semibold text-slate-700">Click to browse or drag &amp; drop file here</div>
                  <div className="text-[10px] text-slate-500">Supports PDF (.pdf) and Word documents (.doc, .docx) up to 25MB</div>
                </div>
              )}
            </div>
          </div>

          {error && (
            <div className="bg-rose-50 border border-rose-200 text-rose-800 p-2.5 rounded-md flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          {success && (
            <div className="bg-emerald-50 border border-emerald-200 text-emerald-800 p-2.5 rounded-md flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>Document uploaded successfully!</span>
            </div>
          )}

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-2 pt-3 border-t border-border">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={uploading || !file || success}
              className="px-4 py-2 text-xs font-bold bg-slate-900 text-white rounded-md hover:bg-slate-800 transition-colors disabled:opacity-50 flex items-center gap-1.5"
            >
              <Upload className="w-3.5 h-3.5" />
              {uploading ? 'Uploading...' : 'Upload Document'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
