import { useMemo, useState } from 'react';
import { ChevronRight, Trash2 } from 'lucide-react';
import type { TeachingAssignmentDto, CourseOfferingDto } from '../types/academics';
import { batchLabel, canonicalComponent, sectionLabel } from '../lib/labels';

/**
 * Allocations grouped the way a coordinator reads them: course → section → who teaches the
 * lecture, tutorial and practical (with batches), plus student strength at every level.
 */

type Component = 'LECTURE' | 'TUTORIAL' | 'PRACTICAL';
const COMPONENTS: { key: Component; label: string }[] = [
  { key: 'LECTURE', label: 'Lecture' },
  { key: 'TUTORIAL', label: 'Tutorial' },
  { key: 'PRACTICAL', label: 'Practical' },
];

interface SectionGroup {
  offering: CourseOfferingDto;
  byComponent: Record<Component, TeachingAssignmentDto[]>;
}

interface CourseGroup {
  code: string;
  name: string;
  ltp: string;
  sections: SectionGroup[];
  students: number;
  faculty: Set<string>;
}

function group(assignments: TeachingAssignmentDto[]): CourseGroup[] {
  const courses = new Map<string, CourseGroup>();
  const sections = new Map<number, SectionGroup>();
  for (const a of assignments) {
    const course = a.offering.course;
    let cg = courses.get(course.code);
    if (!cg) {
      cg = { code: course.code, name: course.name, ltp: course.ltp ?? '', sections: [], students: 0, faculty: new Set() };
      courses.set(course.code, cg);
    }
    cg.faculty.add(a.facultyName);
    let sg = sections.get(a.offering.id);
    if (!sg) {
      sg = { offering: a.offering, byComponent: { LECTURE: [], TUTORIAL: [], PRACTICAL: [] } };
      sections.set(a.offering.id, sg);
      cg.sections.push(sg);
      cg.students += a.offering.studentCount ?? 0;
    }
    const c = canonicalComponent(a.component);
    if (c === 'ALL') {
      COMPONENTS.forEach(({ key }) => sg!.byComponent[key].push(a));
    } else {
      sg.byComponent[c].push(a);
    }
  }
  const sorted = [...courses.values()].sort((x, y) => x.code.localeCompare(y.code));
  sorted.forEach((cg) =>
    cg.sections.sort((x, y) => x.offering.semester - y.offering.semester || x.offering.section.localeCompare(y.offering.section))
  );
  return sorted;
}

interface Props {
  assignments: TeachingAssignmentDto[];
  canEdit: boolean;
  onDelete: (a: TeachingAssignmentDto) => void;
  /** Expand every course, e.g. while searching. */
  expandAll?: boolean;
}

export function CourseAllocationGroups({ assignments, canEdit, onDelete, expandAll = false }: Props) {
  const courses = useMemo(() => group(assignments), [assignments]);
  const [open, setOpen] = useState<Set<string>>(new Set());
  const toggle = (code: string) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });

  const totalStudents = courses.reduce((n, c) => n + c.students, 0);

  return (
    <div className="space-y-3">
      <p className="text-[13px] text-[var(--ink-subtle)] num">
        {courses.length} courses · {courses.reduce((n, c) => n + c.sections.length, 0)} sections ·{' '}
        {totalStudents > 0 ? `${totalStudents.toLocaleString()} students` : 'student strength not imported yet'}
      </p>

      {courses.map((course) => {
        const isOpen = expandAll || open.has(course.code);
        return (
          <section key={course.code} className="card overflow-hidden">
            <button
              type="button"
              onClick={() => toggle(course.code)}
              aria-expanded={isOpen}
              className="w-full px-5 py-4 flex flex-wrap items-center gap-x-6 gap-y-1 text-left hover:bg-[var(--surface-3)]"
            >
              <ChevronRight className={`w-4 h-4 text-[var(--ink-subtle)] transition-transform ${isOpen ? 'rotate-90' : ''}`} />
              <div className="flex items-baseline gap-2.5 min-w-0 flex-1">
                <span className="font-mono text-[13px] font-medium">{course.code}</span>
                <span className="text-[15px] font-medium truncate">{course.name}</span>
                {course.ltp && <span className="text-[12px] text-[var(--ink-subtle)] font-mono">L-T-P {course.ltp}</span>}
              </div>
              <dl className="flex gap-6 text-[12px] text-[var(--ink-subtle)] num">
                <div><dt className="sr-only">Sections</dt><dd><span className="text-[var(--ink)]">{course.sections.length}</span> sections</dd></div>
                <div><dt className="sr-only">Faculty</dt><dd><span className="text-[var(--ink)]">{course.faculty.size}</span> faculty</dd></div>
                <div><dt className="sr-only">Students</dt><dd><span className="text-[var(--ink)]">{course.students || '—'}</span> students</dd></div>
              </dl>
            </button>

            {isOpen && (
              <div className="overflow-x-auto border-t border-[var(--hairline)]">
                <table className="min-w-full text-[13px]">
                  <thead className="bg-[var(--surface-3)] text-left text-[var(--ink-subtle)]">
                    <tr>
                      <th className="px-5 py-2 font-medium w-48">Section</th>
                      <th className="px-3 py-2 font-medium text-right w-24">Students</th>
                      {COMPONENTS.map((c) => (
                        <th key={c.key} className="px-3 py-2 font-medium">{c.label}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--hairline-soft)] align-top">
                    {course.sections.map((sg) => (
                      <tr key={sg.offering.id}>
                        <td className="px-5 py-3 whitespace-nowrap">{sectionLabel(sg.offering.section, sg.offering.semester)}</td>
                        <td className="px-3 py-3 text-right num">{sg.offering.studentCount || '—'}</td>
                        {COMPONENTS.map(({ key }) => (
                          <td key={key} className="px-3 py-3">
                            {sg.byComponent[key].length === 0 ? (
                              <span className="text-[var(--ink-tertiary)]">—</span>
                            ) : (
                              <ul className="space-y-1.5">
                                {sg.byComponent[key].map((a) => (
                                  <li key={`${a.id}-${key}`} className="group flex items-start gap-2">
                                    <div className="min-w-0">
                                      <div className="text-[var(--ink)]">{a.facultyName}</div>
                                      <div className="text-[12px] text-[var(--ink-subtle)] num">
                                        {[batchLabel(a.subSection), `${a.contactHours}h/wk`,
                                          batchLabel(a.subSection) && a.studentCount ? `${a.studentCount} students` : '']
                                          .filter(Boolean).join(' · ')}
                                      </div>
                                    </div>
                                    {canEdit && (
                                      <button
                                        type="button"
                                        onClick={() => onDelete(a)}
                                        className="opacity-0 group-hover:opacity-100 focus:opacity-100 p-1 text-[var(--ink-tertiary)] hover:text-[var(--danger-text)]"
                                        aria-label={`Remove ${a.facultyName} from ${course.code} ${sectionLabel(sg.offering.section)}`}
                                      >
                                        <Trash2 className="w-3.5 h-3.5" />
                                      </button>
                                    )}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                  {course.sections.length > 1 && (
                    <tfoot className="border-t border-[var(--hairline)] text-[12px] text-[var(--ink-subtle)]">
                      <tr>
                        <td className="px-5 py-2">Total</td>
                        <td className="px-3 py-2 text-right num text-[var(--ink)]">{course.students || '—'}</td>
                        <td colSpan={3} />
                      </tr>
                    </tfoot>
                  )}
                </table>
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
