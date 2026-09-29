import { useState, useEffect } from 'react';
import {
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ListOrdered
} from 'lucide-react';

interface SyllabusTopic {
  id: number;
  unitNumber: number;
  unitTitle: string;
  topicName: string;
  targetWeek: number;
  lectureOrder: number;
  isCovered?: boolean;
}

interface CourseItem {
  id: number;
  code: string;
  name: string;
  section: string;
  component: string;
  credits: number;
  topics: SyllabusTopic[];
}

export function MyCoursesView() {
  const [courses, setCourses] = useState<CourseItem[]>([]);
  const [activeCourseId, setActiveCourseId] = useState<number>(1);
  const [expandedUnit, setExpandedUnit] = useState<number | null>(1);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // In Slice 8, fetch courses or load structured mock data
    setTimeout(() => {
      const mockCourse1Topics: SyllabusTopic[] = [
        { id: 1, unitNumber: 1, unitTitle: 'Unit 1: Fundamentals of Networking', topicName: 'Introduction to Computer Networks & Topologies', targetWeek: 1, lectureOrder: 1, isCovered: true },
        { id: 2, unitNumber: 1, unitTitle: 'Unit 1: Fundamentals of Networking', topicName: 'OSI 7-Layer Reference Model & Responsibilities', targetWeek: 1, lectureOrder: 2, isCovered: true },
        { id: 3, unitNumber: 1, unitTitle: 'Unit 1: Fundamentals of Networking', topicName: 'TCP/IP Protocol Suite & Architectural Comparison', targetWeek: 2, lectureOrder: 3, isCovered: true },
        { id: 4, unitNumber: 2, unitTitle: 'Unit 2: Physical & Data Link Layer', topicName: 'Transmission Media: Guided vs Unguided Media', targetWeek: 3, lectureOrder: 4, isCovered: true },
        { id: 5, unitNumber: 2, unitTitle: 'Unit 2: Physical & Data Link Layer', topicName: 'Data Link Framing, Flow Control & Stop-and-Wait', targetWeek: 3, lectureOrder: 5, isCovered: true },
        { id: 6, unitNumber: 2, unitTitle: 'Unit 2: Physical & Data Link Layer', topicName: 'Error Detection: CRC Cyclic Redundancy Check', targetWeek: 4, lectureOrder: 6, isCovered: true },
        { id: 7, unitNumber: 3, unitTitle: 'Unit 3: Medium Access & Ethernet', topicName: 'MAC Sublayer: Pure & Slotted ALOHA', targetWeek: 5, lectureOrder: 7, isCovered: true },
        { id: 8, unitNumber: 3, unitTitle: 'Unit 3: Medium Access & Ethernet', topicName: 'CSMA, CSMA/CD and CSMA/CA Protocols', targetWeek: 5, lectureOrder: 8, isCovered: true },
        { id: 9, unitNumber: 3, unitTitle: 'Unit 3: Medium Access & Ethernet', topicName: 'Standard Ethernet Architecture and Switching', targetWeek: 6, lectureOrder: 9, isCovered: true },
        { id: 10, unitNumber: 4, unitTitle: 'Unit 4: Network Layer & Routing', topicName: 'IPv4 Addressing, Subnet Masks and CIDR', targetWeek: 7, lectureOrder: 10, isCovered: true },
        { id: 11, unitNumber: 4, unitTitle: 'Unit 4: Network Layer & Routing', topicName: 'Distance Vector Routing Algorithm', targetWeek: 8, lectureOrder: 11, isCovered: true },
        { id: 12, unitNumber: 4, unitTitle: 'Unit 4: Network Layer & Routing', topicName: 'Link State Routing Algorithm (Dijkstra)', targetWeek: 9, lectureOrder: 12, isCovered: true },
        { id: 13, unitNumber: 5, unitTitle: 'Unit 5: Transport & Application Layer', topicName: 'TCP Connection Management & 3-Way Handshake', targetWeek: 10, lectureOrder: 13, isCovered: true },
        { id: 14, unitNumber: 5, unitTitle: 'Unit 5: Transport & Application Layer', topicName: 'TCP Congestion Control: AIMD & Slow Start', targetWeek: 11, lectureOrder: 14, isCovered: false },
        { id: 15, unitNumber: 5, unitTitle: 'Unit 5: Transport & Application Layer', topicName: 'Application Protocols: DNS, HTTP/2 and SMTP', targetWeek: 12, lectureOrder: 15, isCovered: false },
      ];

      const mockCourse2Topics: SyllabusTopic[] = [
        { id: 16, unitNumber: 1, unitTitle: 'Unit 1: Database Architecture & ER Model', topicName: '3-Schema Architecture & Data Independence', targetWeek: 1, lectureOrder: 1, isCovered: true },
        { id: 17, unitNumber: 1, unitTitle: 'Unit 1: Database Architecture & ER Model', topicName: 'ER Modeling: Entities & Relationships', targetWeek: 1, lectureOrder: 2, isCovered: true },
        { id: 18, unitNumber: 2, unitTitle: 'Unit 2: Relational Model & SQL', topicName: 'Relational Algebra Operations', targetWeek: 3, lectureOrder: 3, isCovered: true },
        { id: 19, unitNumber: 2, unitTitle: 'Unit 2: Relational Model & SQL', topicName: 'SQL DDL, DML and Subqueries', targetWeek: 3, lectureOrder: 4, isCovered: true },
        { id: 20, unitNumber: 3, unitTitle: 'Unit 3: Normalization', topicName: '1NF, 2NF and 3NF Normalization', targetWeek: 5, lectureOrder: 5, isCovered: true },
        { id: 21, unitNumber: 4, unitTitle: 'Unit 4: Transactions', topicName: 'ACID Properties & Serializability', targetWeek: 7, lectureOrder: 6, isCovered: true },
        { id: 22, unitNumber: 4, unitTitle: 'Unit 4: Transactions', topicName: 'Two-Phase Locking (2PL)', targetWeek: 8, lectureOrder: 7, isCovered: false },
        { id: 23, unitNumber: 5, unitTitle: 'Unit 5: Indexing', topicName: 'B-Tree and B+ Tree Indexing Structures', targetWeek: 10, lectureOrder: 8, isCovered: false },
      ];

      setCourses([
        {
          id: 1,
          code: 'CS501',
          name: 'Computer Networks',
          section: 'Section A',
          component: 'THEORY',
          credits: 4,
          topics: mockCourse1Topics,
        },
        {
          id: 2,
          code: 'CS502',
          name: 'Database Management Systems',
          section: 'Section A',
          component: 'THEORY',
          credits: 4,
          topics: mockCourse2Topics,
        },
      ]);
      setLoading(false);
    }, 200);
  }, []);

  const activeCourse = courses.find((c) => c.id === activeCourseId) || courses[0];
  const totalTopics = activeCourse?.topics.length || 0;
  const coveredTopics = activeCourse?.topics.filter((t) => t.isCovered).length || 0;
  const coveragePct = totalTopics > 0 ? Math.round((coveredTopics / totalTopics) * 100) : 0;

  // Group topics by unit
  const unitsMap = new Map<number, { title: string; topics: SyllabusTopic[] }>();
  activeCourse?.topics.forEach((t) => {
    if (!unitsMap.has(t.unitNumber)) {
      unitsMap.set(t.unitNumber, { title: t.unitTitle, topics: [] });
    }
    unitsMap.get(t.unitNumber)!.topics.push(t);
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-white rounded-lg border border-border p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-bold text-primary tracking-wide uppercase font-sans">
            <BookOpen className="w-4 h-4" />
            My Courses &amp; Syllabus Coverage
          </div>
          <h1 className="font-serif text-2xl font-bold text-slate-900 mt-1">
            {activeCourse ? `${activeCourse.code} — ${activeCourse.name}` : 'My Allocated Courses'}
          </h1>
          <p className="text-xs text-slate-600 mt-1">
            Track planned vs covered topics unit-by-unit. Coverage is automatically derived from your daily class logs.
          </p>
        </div>

        <div className="flex items-center gap-2">
          {courses.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => setActiveCourseId(c.id)}
              className={`px-3.5 py-2 rounded-md text-xs font-bold transition-all ${
                activeCourseId === c.id
                  ? 'bg-primary text-white shadow-xs'
                  : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {c.code} ({c.section})
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="p-8 text-center bg-white rounded-lg border border-border text-xs text-slate-500">
          Loading course syllabus data...
        </div>
      ) : activeCourse ? (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Progress Summary Card (1 col) */}
          <div className="space-y-4">
            <div className="bg-white rounded-lg border border-border p-5 shadow-xs space-y-4">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Course Health</span>
              
              <div className="text-center py-2">
                <div className="font-serif text-4xl font-bold text-slate-900">{coveragePct}%</div>
                <div className="text-xs text-slate-500 mt-1">Syllabus Covered</div>
              </div>

              <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-all"
                  style={{ width: `${coveragePct}%` }}
                ></div>
              </div>

              <div className="border-t border-border pt-3 space-y-2 text-xs">
                <div className="flex justify-between text-slate-600">
                  <span>Covered Topics:</span>
                  <span className="font-bold text-slate-900">{coveredTopics} / {totalTopics}</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Current Pace:</span>
                  <span className="font-bold text-emerald-700">On Track (Week 11)</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Credit Weight:</span>
                  <span className="font-bold text-slate-900">{activeCourse.credits} Credits ({activeCourse.component})</span>
                </div>
              </div>
            </div>
          </div>

          {/* Unit-by-Unit Syllabus Topics (3 cols) */}
          <div className="lg:col-span-3 space-y-3">
            <h2 className="text-sm font-bold font-serif text-slate-900 uppercase tracking-wider flex items-center gap-2">
              <ListOrdered className="w-4 h-4 text-primary" />
              Syllabus Units &amp; Topic Master
            </h2>

            {Array.from(unitsMap.entries()).map(([unitNum, unitData]) => {
              const isExpanded = expandedUnit === unitNum;
              const unitTopicsCount = unitData.topics.length;
              const unitCoveredCount = unitData.topics.filter((t) => t.isCovered).length;
              const isFullyCovered = unitCoveredCount === unitTopicsCount;

              return (
                <div key={unitNum} className="bg-white rounded-lg border border-border overflow-hidden shadow-xs">
                  <button
                    type="button"
                    onClick={() => setExpandedUnit(isExpanded ? null : unitNum)}
                    className="w-full px-5 py-4 flex items-center justify-between bg-white hover:bg-slate-50 transition-colors text-left"
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${
                          isFullyCovered
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-primary/10 text-primary'
                        }`}
                      >
                        {unitNum}
                      </span>
                      <div>
                        <div className="text-xs font-bold text-slate-900">{unitData.title}</div>
                        <div className="text-[11px] text-slate-500">
                          {unitCoveredCount} of {unitTopicsCount} topics completed
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      {isFullyCovered && (
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                          Unit Completed
                        </span>
                      )}
                      {isExpanded ? <ChevronDown className="w-4 h-4 text-slate-400" /> : <ChevronRight className="w-4 h-4 text-slate-400" />}
                    </div>
                  </button>

                  {isExpanded && (
                    <div className="border-t border-border bg-slate-50/50 p-4 space-y-2">
                      {unitData.topics.map((topic) => (
                        <div
                          key={topic.id}
                          className="flex items-center justify-between p-2.5 rounded bg-white border border-border/80 text-xs"
                        >
                          <div className="flex items-center gap-2.5">
                            {topic.isCovered ? (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                            ) : (
                              <div className="w-4 h-4 rounded-full border-2 border-slate-300 shrink-0"></div>
                            )}
                            <span className={topic.isCovered ? 'text-slate-800 font-medium' : 'text-slate-500'}>
                              {topic.lectureOrder}. {topic.topicName}
                            </span>
                          </div>

                          <div className="flex items-center gap-3 text-slate-500 text-[11px]">
                            <span>Target: Week {topic.targetWeek}</span>
                            <span
                              className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                topic.isCovered
                                  ? 'bg-emerald-100 text-emerald-800'
                                  : 'bg-slate-100 text-slate-600'
                              }`}
                            >
                              {topic.isCovered ? 'Covered' : 'Pending'}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}
