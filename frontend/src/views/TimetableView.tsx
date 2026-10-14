import { useState, useEffect, useMemo } from 'react';
import { Calendar, Clock, MapPin, RefreshCw, FileDown, Upload, FileText, Trash2 } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { TimetableSlotDto } from '../types/academics';
import { AcademicDocumentDto, AcademicDocumentCategory } from '../types/documents';
import { UploadAcademicDocumentModal } from '../components/UploadAcademicDocumentModal';
import { ImportSpreadsheetModal } from '../components/ImportSpreadsheetModal';
import { sectionLabel } from '../lib/labels';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

/** "09:00" -> "9:00 AM" for the row gutter. */
function formatTime(hhmm: string): string {
  const [h, m] = hhmm.split(':').map(Number);
  if (Number.isNaN(h)) return hhmm;
  const suffix = h >= 12 ? 'PM' : 'AM';
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:${String(m ?? 0).padStart(2, '0')} ${suffix}`;
}

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
  const [isImportOpen, setIsImportOpen] = useState(false);

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

  const handleDeleteSlot = async (slotId: number, courseCode: string, day: string) => {
    if (!window.confirm(`Delete this ${day} timetable slot for ${courseCode}?`)) return;
    try {
      const res = await fetch(`/api/academics/timetable/${slotId}`, { method: 'DELETE' });
      if (!res.ok) throw new Error('Failed to delete timetable slot');
      setSlots((prev) => prev.filter((s) => s.id !== slotId));
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Error deleting timetable slot');
    }
  };

  /** Columns of the grid: Mon-Fri plus any weekend that actually has slots. */
  const visibleDays = useMemo(() => {
    const weekendDays = ['SATURDAY', 'SUNDAY'].filter((d) =>
      slots.some((s) => s.dayOfWeek.toUpperCase() === d)
    );
    const all = [...DAYS, ...weekendDays];
    return selectedDay === 'ALL' ? all : all.filter((d) => d === selectedDay);
  }, [slots, selectedDay]);

  /** Rows of the grid: every distinct start time in the schedule, chronological. */
  const timeRows = useMemo(() => {
    const starts = new Set<string>();
    slots.forEach((s) => starts.add(s.startTime));
    return [...starts].sort((a, b) => a.localeCompare(b));
  }, [slots]);

  /** slot lookup: "MONDAY|10:00" -> slot (one slot per day+start for a faculty grid). */
  const slotIndex = useMemo(() => {
    const index = new Map<string, TimetableSlotDto>();
    slots.forEach((s) => index.set(`${s.dayOfWeek.toUpperCase()}|${s.startTime}`, s));
    return index;
  }, [slots]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <p className="text-sm text-[var(--ink-subtle)] max-w-xl">
            {isCoordinator
              ? 'Upload the master timetable once. Every slot is matched to a faculty allocation, and each person’s Today screen then lists exactly the classes they have that day.'
              : 'Your weekly classes. Upload your own timetable if the department’s master timetable is not in yet — Today will list each day’s classes for logging.'}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => setIsImportOpen(true)} className="btn btn-primary">
            <Upload className="w-3.5 h-3.5" /> {isCoordinator ? 'Import timetable' : 'Upload my timetable'}
          </button>
          <button
            type="button"
            onClick={fetchTimetable}
            className="p-2 border border-[#E6E4DD] rounded-md hover:bg-[#EFEEE9] text-[#6B6F76] hover:text-[#16181D] transition-colors"
            title="Refresh schedule"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-[#0F5C5C]' : ''}`} />
          </button>
          <div className="flex items-center gap-1 bg-[#F6F5F1] p-1 rounded-md border border-[#E6E4DD] text-xs font-medium">
            <button
              type="button"
              onClick={() => setSelectedDay('ALL')}
              className={`px-3 py-1 rounded transition-colors ${
                selectedDay === 'ALL'
                  ? 'bg-[#E7E5DF] shadow-xs text-[#16181D] font-bold'
                  : 'text-[#6B6F76] hover:text-[#16181D]'
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
                  selectedDay === d
                    ? 'bg-[#E7E5DF] shadow-xs text-[#16181D] font-bold'
                    : 'text-[#6B6F76] hover:text-[#16181D]'
                }`}
              >
                {d.substring(0, 3)}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Official Institutional Documents */}
      <div className="bg-[#FFFFFF] border border-[#E6E4DD] rounded-lg p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
        <div className="flex items-center gap-2 text-[#3A3D44]">
          <FileText className="w-4 h-4 text-[#0F5C5C] shrink-0" />
          <span className="font-bold text-[#16181D]">Official Institutional Documents:</span>
          <span className="text-[11px] text-[#8A8E95]">PDF &amp; Word (.doc, .docx) formats</span>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          {timetableDoc ? (
            <a
              href={`/api/academic-documents/${timetableDoc.id}/download`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#F6F5F1] border border-[#E6E4DD] text-[#3A3D44] hover:bg-[#EFEEE9] font-semibold transition-all"
              title={`Download official timetable (${timetableDoc.fileName})`}
            >
              <FileDown className="w-3.5 h-3.5 text-[#2E7D4F]" />
              <span>Official Timetable ({timetableDoc.fileName.endsWith('.pdf') ? 'PDF' : 'DOC'})</span>
            </a>
          ) : (
            <span className="text-[11px] text-[#8A8E95] italic">No timetable file uploaded</span>
          )}

          <button
            type="button"
            onClick={() => {
              setUploadCategory('MASTER_TIMETABLE');
              setIsUploadModalOpen(true);
            }}
            className="btn h-7 text-[12px]"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>{timetableDoc ? 'Replace PDF copy' : 'Attach PDF copy'}</span>
          </button>

          <div className="h-4 w-px bg-[#E6E4DD] hidden sm:block"></div>

          {calendarDoc ? (
            <a
              href={`/api/academic-documents/${calendarDoc.id}/download`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#F6F5F1] border border-[#E6E4DD] text-[#3A3D44] hover:bg-[#EFEEE9] font-semibold transition-all"
              title={`View/Download academic calendar (${calendarDoc.fileName})`}
            >
              <Calendar className="w-3.5 h-3.5 text-[#0F5C5C]" />
              <span>Academic Calendar ({calendarDoc.fileName.endsWith('.pdf') ? 'PDF' : 'DOC'})</span>
            </a>
          ) : (
            <span className="text-[11px] text-[#8A8E95] italic">No calendar file uploaded</span>
          )}

          {isCoordinator && (
            <button
              type="button"
              onClick={() => {
                setUploadCategory('ACADEMIC_CALENDAR');
                setIsUploadModalOpen(true);
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-[#E7E5DF] text-[#16181D] hover:bg-[#D6D3CA] font-semibold transition-all"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>{calendarDoc ? 'Replace Calendar File' : 'Upload Calendar (PDF/DOC)'}</span>
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="p-4 bg-[rgba(220,38,38,.14)] border border-[rgba(248,113,113,.35)] text-[#B42318] rounded-lg text-xs font-medium">
          {error}
        </div>
      )}

      {loading && slots.length === 0 ? (
        <div className="bg-[#FFFFFF] rounded-lg border border-[#E6E4DD] p-8">
          <div className="grid grid-cols-5 gap-3 animate-pulse" aria-label="Loading timetable">
            {DAYS.map((d) => (
              <div key={d} className="space-y-3">
                <div className="h-4 rounded bg-[#EFEEE9]" />
                <div className="h-16 rounded bg-[#F6F5F1]" />
                <div className="h-16 rounded bg-[#F6F5F1] opacity-60" />
              </div>
            ))}
          </div>
        </div>
      ) : slots.length === 0 ? (
        <div className="bg-[#FFFFFF] rounded-lg border border-[#E6E4DD] p-12 text-center">
          <Calendar className="w-10 h-10 text-[#BDB9AE] mx-auto mb-2" />
          <h3 className="font-sans text-base font-bold text-[#16181D]">No Timetable Slots</h3>
          <p className="text-xs text-[#6B6F76] mt-1">
            Slots appear here once classes are scheduled for the week.
          </p>
        </div>
      ) : (
        <div className="bg-[#FFFFFF] rounded-lg border border-[#E6E4DD] overflow-hidden">
          <div className="overflow-x-auto">
            <div className="min-w-[900px]">
              {/* Day header row */}
              <div
                className="grid border-b border-[#E6E4DD] bg-[#F6F5F1]"
                style={{ gridTemplateColumns: `88px repeat(${visibleDays.length}, minmax(160px, 1fr))` }}
              >
                <div className="px-3 py-2.5 text-[10px] font-bold text-[#8A8E95] uppercase tracking-wider flex items-center">
                  Time
                </div>
                {visibleDays.map((day) => {
                  const count = slots.filter((s) => s.dayOfWeek.toUpperCase() === day).length;
                  return (
                    <div
                      key={day}
                      className="px-3 py-2.5 text-[11px] font-bold text-[#16181D] uppercase tracking-wider border-l border-[#E6E4DD] flex items-center justify-between"
                    >
                      <span>{day.substring(0, 3)}</span>
                      <span className="text-[10px] font-medium text-[#8A8E95]">
                        {count} {count === 1 ? 'class' : 'classes'}
                      </span>
                    </div>
                  );
                })}
              </div>

              {/* Time rows */}
              {timeRows.map((time, rowIndex) => (
                <div
                  key={time}
                  className={`grid ${rowIndex % 2 === 1 ? 'bg-[#F6F5F1]' : ''}`}
                  style={{ gridTemplateColumns: `88px repeat(${visibleDays.length}, minmax(160px, 1fr))` }}
                >
                  <div className="px-3 py-3 text-[11px] font-mono text-[#6B6F76] flex items-start pt-3.5">
                    {formatTime(time)}
                  </div>
                  {visibleDays.map((day) => {
                    const slot = slotIndex.get(`${day}|${time}`);
                    return (
                      <div
                        key={`${day}-${time}`}
                        className="border-l border-t border-[#EFEEE9] p-2 min-h-[76px]"
                      >
                        {slot && <SlotCard slot={slot} onDelete={() => handleDeleteSlot(slot.id, slot.courseCode, slot.dayOfWeek)} />}
                      </div>
                    );
                  })}
                </div>
              ))}

              {timeRows.length === 0 && (
                <div className="p-6 text-center text-xs text-[#8A8E95] italic">No scheduled times.</div>
              )}
            </div>
          </div>
        </div>
      )}

      {isImportOpen && (
        <ImportSpreadsheetModal
          kind="timetable"
          help={isCoordinator ? undefined : 'Upload your own weekly timetable — the PDF from the portal, or a CSV/Excel sheet. It replaces only your own classes; nobody else’s timetable changes. Each class is matched to your allocation for that course, section and batch.'}
          onClose={() => setIsImportOpen(false)}
          onSuccess={fetchTimetable}
        />
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

/** One L/T/P class inside a grid cell. */
function SlotCard({ slot, onDelete }: { slot: TimetableSlotDto; onDelete: () => void }) {
  const comp = (slot.component || 'LECTURE').toUpperCase();
  const isPractical = comp === 'PRACTICAL' || comp === 'LAB';
  const isTutorial = comp === 'TUTORIAL';
  const compLabel = isPractical ? 'Practical' : isTutorial ? 'Tutorial' : 'Lecture';
  const badgeClass = isPractical
    ? 'bg-[rgba(94,106,210,.14)] text-[#0F5C5C] border-[rgba(94,106,210,.35)]'
    : isTutorial
      ? 'bg-[rgba(245,158,11,.12)] text-[#B7791F] border-[rgba(245,158,11,.3)]'
      : 'bg-[#EFEEE9] text-[#3A3D44] border-[#E6E4DD]';

  return (
    <div className="group relative h-full rounded-lg border border-[#E6E4DD] bg-[#FFFFFF] p-2.5 hover:border-[#D6D3CA] transition-colors">
      <button
        type="button"
        onClick={onDelete}
        className="absolute top-1.5 right-1.5 p-1 rounded-md text-[#8A8E95] hover:text-[#B42318] hover:bg-[rgba(220,38,38,.14)] opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity"
        title="Delete timetable slot"
      >
        <Trash2 className="w-3.5 h-3.5" />
      </button>

      <div className="flex items-center gap-1.5 pr-6 flex-wrap">
        <span className="font-mono text-[11px] font-bold text-[#16181D]">{slot.courseCode}</span>
        <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold border ${badgeClass}`}>
          {compLabel}
        </span>
      </div>

      <div className="text-[11px] text-[#3A3D44] mt-1 truncate" title={slot.courseName}>
        {slot.courseName}
      </div>

      <div className="mt-1.5 space-y-0.5 text-[10px] text-[#6B6F76]">
        <div className="flex items-center gap-1 font-mono">
          <Clock className="w-3 h-3 text-[#8A8E95]" />
          {slot.startTime} – {slot.endTime}
        </div>
        <div className="flex items-center gap-1">
          <MapPin className="w-3 h-3 text-[#8A8E95] shrink-0" />
          <span className="truncate" title={slot.room}>{slot.room}</span>
        </div>
        <div>
          {sectionLabel(slot.section, null, slot.subSection)}
        </div>
      </div>
    </div>
  );
}
