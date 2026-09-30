import { useState, useEffect } from 'react';
import { Calendar, Clock, MapPin, RefreshCw, FileDown, Upload, FileText } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { TimetableSlotDto } from '../types/academics';
import { AcademicDocumentDto, AcademicDocumentCategory } from '../types/documents';
import { UploadAcademicDocumentModal } from '../components/UploadAcademicDocumentModal';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

export function TimetableView() {
  const { user } = useAuth();
  const isCoordinator = user?.role === 'COORDINATOR';

  const [slots, setSlots] = useState<TimetableSlotDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDay, setSelectedDay] = useState<string>('ALL');

  // Documents state (PDF / DOC)
  const [timetableDoc, setTimetableDoc] = useState<AcademicDocumentDto | null>(null);
  const [calendarDoc, setCalendarDoc] = useState<AcademicDocumentDto | null>(null);
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [uploadCategory, setUploadCategory] = useState<AcademicDocumentCategory>('MASTER_TIMETABLE');

  const fetchDocuments = async () => {
    try {
      const [resTT, resCal] = await Promise.all([
        fetch('/api/academic-documents/latest?category=MASTER_TIMETABLE', { credentials: 'include' }),
        fetch('/api/academic-documents/latest?category=ACADEMIC_CALENDAR', { credentials: 'include' }),
      ]);
      if (resTT.ok && resTT.status === 200) {
        setTimetableDoc(await resTT.json());
      }
      if (resCal.ok && resCal.status === 200) {
        setCalendarDoc(await resCal.json());
      }
    } catch {
      // ignore
    }
  };

  const fetchTimetable = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/academics/timetable', {
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
      });
      if (!res.ok) {
        throw new Error(`Failed to load timetable (${res.status})`);
      }
      const data: TimetableSlotDto[] = await res.json();
      setSlots(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error fetching timetable schedule';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTimetable();
    fetchDocuments();
  }, []);

  const getComponentBadge = (comp: string) => {
    const c = (comp || 'LECTURE').toUpperCase();
    if (c === 'PRACTICAL' || c === 'LAB') {
      return (
        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-800 border border-blue-200">
          Practical (Lab)
        </span>
      );
    }
    if (c === 'TUTORIAL') {
      return (
        <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
          Tutorial
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-800 border border-slate-200">
        Lecture (Theory)
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <Calendar className="w-4 h-4" />
            Academic Operations — Faculty Teaching Timetable
          </div>
          <h1 className="font-serif text-2xl font-bold text-slate-900 mt-1">
            Weekly Master Timetable (Odd 2026-27)
          </h1>
          <p className="text-xs text-slate-600 mt-1">
            Institutional schedule mapping weekly Lecture, Tutorial, and Practical (LTP) allocations with room assignments.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={fetchTimetable}
            className="p-2 border border-border rounded-md hover:bg-slate-50 text-slate-600 hover:text-slate-900 transition-colors"
            title="Refresh schedule"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-primary' : ''}`} />
          </button>
          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-md border border-slate-200 text-xs font-medium">
            <button
              type="button"
              onClick={() => setSelectedDay('ALL')}
              className={`px-3 py-1 rounded transition-colors ${
                selectedDay === 'ALL' ? 'bg-white shadow-xs text-primary font-bold' : 'text-slate-600'
              }`}
            >
              All Week
            </button>
            {DAYS.map((d) => (
              <button
                key={d}
                type="button"
                onClick={() => setSelectedDay(d)}
                className={`px-2.5 py-1 rounded transition-colors ${
                  selectedDay === d ? 'bg-white shadow-xs text-primary font-bold' : 'text-slate-600'
                }`}
              >
                {d.substring(0, 3)}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Official Institutional Documents Action Strip */}
      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2 text-slate-700">
          <FileText className="w-4 h-4 text-primary shrink-0" />
          <span className="font-bold text-slate-800">Official Institutional Documents:</span>
          <span className="text-[11px] text-slate-500">PDF &amp; Word (.doc, .docx) formats</span>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {/* Master Timetable Document */}
          {timetableDoc ? (
            <a
              href={`/api/academic-documents/${timetableDoc.id}/download`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-white border border-emerald-300 text-emerald-800 hover:bg-emerald-50 font-bold transition-all shadow-2xs"
              title={`Download official timetable (${timetableDoc.fileName})`}
            >
              <FileDown className="w-3.5 h-3.5 text-emerald-600" />
              <span>Official Timetable ({timetableDoc.fileName.endsWith('.pdf') ? 'PDF' : 'DOC'})</span>
            </a>
          ) : (
            <span className="text-[11px] text-slate-400 italic">No timetable file uploaded</span>
          )}

          {isCoordinator && (
            <button
              type="button"
              onClick={() => {
                setUploadCategory('MASTER_TIMETABLE');
                setIsUploadModalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-primary text-white hover:bg-primary-hover font-bold transition-all shadow-xs"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>{timetableDoc ? 'Replace Timetable File' : 'Upload Timetable (PDF/DOC)'}</span>
            </button>
          )}

          <div className="h-4 w-px bg-slate-300 hidden sm:block"></div>

          {/* Academic Calendar Document */}
          {calendarDoc ? (
            <a
              href={`/api/academic-documents/${calendarDoc.id}/download`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-white border border-blue-300 text-blue-800 hover:bg-blue-50 font-bold transition-all shadow-2xs"
              title={`View/Download academic calendar (${calendarDoc.fileName})`}
            >
              <Calendar className="w-3.5 h-3.5 text-blue-600" />
              <span>Academic Calendar ({calendarDoc.fileName.endsWith('.pdf') ? 'PDF' : 'DOC'})</span>
            </a>
          ) : (
            <span className="text-[11px] text-slate-400 italic">No calendar file uploaded</span>
          )}

          {isCoordinator && (
            <button
              type="button"
              onClick={() => {
                setUploadCategory('ACADEMIC_CALENDAR');
                setIsUploadModalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-slate-200 text-slate-800 hover:bg-slate-300 font-bold transition-all"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>{calendarDoc ? 'Replace Calendar File' : 'Upload Calendar (PDF/DOC)'}</span>
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 text-rose-900 rounded-lg text-xs font-medium">
          {error}
        </div>
      )}

      {loading && slots.length === 0 ? (
        <div className="p-12 text-center bg-white rounded-lg border border-border text-xs text-slate-500 animate-pulse">
          Loading master academic timetable schedule...
        </div>
      ) : (
        <div className="space-y-6">
          {DAYS.filter((d) => selectedDay === 'ALL' || selectedDay === d).map((day) => {
            const daySlots = slots.filter((s) => s.dayOfWeek.toUpperCase() === day);

            return (
              <div key={day} className="bg-white rounded-lg border border-border overflow-hidden shadow-xs">
                {/* Day Header */}
                <div className="px-5 py-3 bg-slate-50 border-b border-border flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="font-serif text-sm font-bold text-slate-900 tracking-wider">
                      {day}
                    </span>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-200 text-slate-700">
                      {daySlots.length} {daySlots.length === 1 ? 'Class' : 'Classes'}
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-500 font-medium">
                    {day === 'SATURDAY' || day === 'SUNDAY' ? 'Weekend' : 'Institutional Working Day'}
                  </span>
                </div>

                {/* Day Slots Grid */}
                {daySlots.length === 0 ? (
                  <div className="p-6 text-center text-xs text-slate-400 italic">
                    No classes scheduled for {day}
                  </div>
                ) : (
                  <div className="divide-y divide-border/60">
                    {daySlots.map((slot) => (
                      <div
                        key={slot.id}
                        className="p-4 md:px-6 hover:bg-slate-50/50 transition-colors flex flex-col md:flex-row md:items-center justify-between gap-4"
                      >
                        <div className="space-y-1.5">
                          <div className="flex items-center gap-2">
                            <span className="px-2 py-0.5 rounded text-xs font-bold bg-primary text-white">
                              {slot.courseCode}
                            </span>
                            <span className="font-semibold text-xs text-slate-700">
                              Section {slot.section}
                            </span>
                            {slot.subSection && slot.subSection !== 'ALL' && (
                              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-700 border border-blue-200">
                                Sub-Section {slot.subSection}
                              </span>
                            )}
                            {getComponentBadge(slot.component)}
                            <span className="text-[10px] text-slate-400 font-mono">
                              LTP: {slot.ltp}
                            </span>
                          </div>

                          <h3 className="font-serif text-base font-bold text-slate-900">
                            {slot.courseName}
                          </h3>

                          {slot.courseDescription && (
                            <p className="text-[11px] text-slate-500 line-clamp-1 max-w-2xl">
                              {slot.courseDescription}
                            </p>
                          )}
                        </div>

                        <div className="flex md:flex-col items-center md:items-end justify-between gap-2 shrink-0">
                          <div className="flex items-center gap-1.5 text-xs font-bold text-slate-800">
                            <Clock className="w-3.5 h-3.5 text-primary" />
                            <span>{slot.startTime} – {slot.endTime}</span>
                          </div>
                          <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                            <MapPin className="w-3.5 h-3.5 text-slate-400" />
                            <span>{slot.room}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Upload Academic Document Modal */}
      {isUploadModalOpen && (
        <UploadAcademicDocumentModal
          initialCategory={uploadCategory}
          onClose={() => setIsUploadModalOpen(false)}
          onSuccess={(doc) => {
            if (doc.category === 'MASTER_TIMETABLE') {
              setTimetableDoc(doc);
            } else if (doc.category === 'ACADEMIC_CALENDAR') {
              setCalendarDoc(doc);
            }
            setIsUploadModalOpen(false);
          }}
        />
      )}
    </div>
  );
}
