import { useState, useEffect, useCallback } from 'react';
import { X, BookOpen, Layers, FileDown } from 'lucide-react';
import { TodayClassDto } from '../types/academics';
import { AcademicDocumentDto } from '../types/documents';
import { sectionLabel, batchLabel } from '../lib/labels';

interface CourseDescriptionModalProps {
  course: TodayClassDto;
  onClose: () => void;
}

export function CourseDescriptionModal({ course, onClose }: CourseDescriptionModalProps) {
  const [syllabusDoc, setSyllabusDoc] = useState<AcademicDocumentDto | null>(null);

  const fetchSyllabusDoc = useCallback(async () => {
    try {
      const res = await fetch(`/api/academic-documents?category=COURSE_SYLLABUS`, { credentials: 'include' });
      if (res.ok) {
        const docs: AcademicDocumentDto[] = await res.json();
        // Match either by course offering code or title
        const match = docs.find((d) =>
          d.title.toLowerCase().includes(course.courseCode.toLowerCase()) ||
          d.fileName.toLowerCase().includes(course.courseCode.toLowerCase())
        );
        if (match) {
          setSyllabusDoc(match);
        }
      }
    } catch {
      // ignore
    }
  }, [course.courseCode]);

  useEffect(() => {
    fetchSyllabusDoc();
  }, [fetchSyllabusDoc]);

  // Parse LTP (e.g. "3-0-2")
  const ltpParts = (course.ltp || '3-0-0').split('-');
  const lectureHours = parseInt(ltpParts[0] || '3', 10);
  const tutorialHours = parseInt(ltpParts[1] || '0', 10);
  const practicalHours = parseInt(ltpParts[2] || '0', 10);
  const totalWeeklyHours = lectureHours + tutorialHours + practicalHours;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-2xs animate-in fade-in">
      <div className="bg-white rounded-lg border border-border shadow-2xl max-w-2xl w-full max-h-[90vh] flex flex-col overflow-hidden">
        {/* Modal Header */}
        <div className="p-6 border-b border-border flex items-start justify-between bg-slate-50/70">
          <div>
            <div className="flex items-center gap-2">
              <span className="px-2 py-0.5 rounded text-xs font-bold bg-slate-900 text-white">
                {course.courseCode}
              </span>
              <span className="text-xs font-semibold text-slate-600">{sectionLabel(course.section)}</span>
              {batchLabel(course.subSection) && (
                <span className="chip chip-info">{batchLabel(course.subSection)}</span>
              )}
            </div>
            <h2 className="font-sans text-xl font-bold text-slate-900 mt-1">{course.courseName}</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition-colors"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Scrollable Body */}
        <div className="p-6 overflow-y-auto space-y-6 text-xs text-slate-700">
          {/* LTP & Contact Hours Grid */}
          <div className="grid grid-cols-4 gap-3 text-center">
            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Lectures (L)</div>
              <div className="text-lg font-bold text-slate-900 mt-0.5">{lectureHours} hrs/wk</div>
            </div>
            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Tutorials (T)</div>
              <div className="text-lg font-bold text-slate-800 mt-0.5">{tutorialHours} hrs/wk</div>
            </div>
            <div className="p-3 rounded-lg bg-slate-50 border border-slate-200">
              <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">Practicals (P)</div>
              <div className="text-lg font-bold text-blue-700 mt-0.5">{practicalHours} hrs/wk</div>
            </div>
            <div className="p-3 rounded-lg bg-emerald-50 border border-emerald-200">
              <div className="text-[10px] font-bold text-emerald-800 uppercase tracking-wider">Total Contact</div>
              <div className="text-lg font-bold text-emerald-900 mt-0.5">{totalWeeklyHours} hrs/wk</div>
            </div>
          </div>

          {/* Course Description */}
          <div>
            <div className="mb-2">
              <h3 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-1.5">
                <BookOpen className="w-4 h-4 text-slate-900" />
                Course Description &amp; Objectives
              </h3>
            </div>

            <p className="leading-relaxed text-slate-600 bg-slate-50 p-4 rounded-lg border border-slate-200">
              {course.courseDescription ||
                'This course covers comprehensive theoretical principles and laboratory implementations according to institutional academic curriculum standards. Designed to provide deep foundational and practical proficiency.'}
            </p>

            {/* Official Syllabus Document Download Card */}
            {syllabusDoc && (
              <div className="mt-3 p-3.5 rounded-lg bg-emerald-50/70 border border-emerald-200 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded bg-emerald-100 border border-emerald-300 text-emerald-800 flex items-center justify-center font-bold text-[10px]">
                    {syllabusDoc.fileName.endsWith('.pdf') ? 'PDF' : 'DOC'}
                  </div>
                  <div>
                    <div className="font-bold text-slate-900 text-xs">{syllabusDoc.title}</div>
                    <div className="text-[10px] text-slate-500">
                      {syllabusDoc.fileName} · {(syllabusDoc.fileSize / (1024 * 1024)).toFixed(2)} MB · Uploaded by {syllabusDoc.uploadedByName}
                    </div>
                  </div>
                </div>

                <a
                  href={`/api/academic-documents/${syllabusDoc.id}/download`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-emerald-600 text-white font-bold text-xs hover:bg-emerald-700 transition-colors shadow-2xs"
                >
                  <FileDown className="w-3.5 h-3.5" />
                  View / Download
                </a>
              </div>
            )}
          </div>

          {/* Unit Syllabus Outline */}
          {course.availableUnits && course.availableUnits.length > 0 && (
            <div>
              <h3 className="font-sans text-sm font-bold text-slate-900 uppercase tracking-wider flex items-center gap-1.5 mb-3">
                <Layers className="w-4 h-4 text-slate-900" />
                Syllabus Unit Structure & Pacing
              </h3>
              <div className="space-y-3">
                {course.availableUnits.map((u) => {
                  const isCurrentUnit = u.unitNumber === course.currentUnitNumber;
                  return (
                    <div
                      key={u.unitNumber}
                      className={`p-3.5 rounded-lg border transition-all ${
                        isCurrentUnit
                          ? 'border-slate-900 bg-slate-50 ring-1 ring-slate-900/10'
                          : 'border-border bg-white'
                      }`}
                    >
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-slate-900 text-xs">{u.unitTitle}</span>
                        {isCurrentUnit ? (
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-900 text-white">
                            Current Active Unit
                          </span>
                        ) : (
                          <span className="text-[11px] text-slate-500 font-medium">
                            {u.topics.length} topics
                          </span>
                        )}
                      </div>
                      <ul className="mt-2 space-y-1 list-disc list-inside text-slate-600 text-[11px]">
                        {u.topics.map((t) => (
                          <li key={t.id} className="truncate">
                            {t.topicName}
                          </li>
                        ))}
                      </ul>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 bg-slate-50 border-t border-border flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-md bg-slate-900 text-white font-bold text-xs hover:bg-slate-800 transition-colors"
          >
            Close Overview
          </button>
        </div>
      </div>
    </div>
  );
}
