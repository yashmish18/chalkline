import { useState, useEffect } from 'react';
import { Clock } from 'lucide-react';

export function HeaderClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = setInterval(() => {
      setNow(new Date());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const timeString = now.toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });

  const dateString = now.toLocaleDateString([], {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });

  return (
    <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-[#F6F5F1] border border-[#E6E4DD] text-xs">
      <Clock className="w-3.5 h-3.5 text-[#0B4747] shrink-0" />
      <span className="text-[#6B6F76] text-[11px] font-medium hidden sm:inline">{dateString}</span>
      <span className="w-1 h-1 rounded-full bg-[#D6D3CA] hidden sm:inline" />
      <span className="font-mono text-[#16181D] font-semibold text-[11px] tracking-tight">{timeString}</span>
    </div>
  );
}
