import { useState, useRef, useEffect } from 'react';
import { X, Plus } from 'lucide-react';

interface TagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  suggestions?: string[];
  placeholder?: string;
  disabled?: boolean;
}

export function TagInput({
  tags,
  onChange,
  suggestions = [],
  placeholder = 'Add a tag and press Enter...',
  disabled = false
}: TagInputProps) {
  const [inputValue, setInputValue] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const trimmedInput = inputValue.trim().toLowerCase();
  const filteredSuggestions = suggestions.filter(
    (s) => s.toLowerCase().includes(trimmedInput) && !tags.some((t) => t.toLowerCase() === s.toLowerCase())
  );

  const addTag = (tagToAdd: string) => {
    const trimmed = tagToAdd.trim();
    if (!trimmed) return;
    if (tags.some((t) => t.toLowerCase() === trimmed.toLowerCase())) {
      setInputValue('');
      return;
    }
    onChange([...tags, trimmed]);
    setInputValue('');
    setIsOpen(false);
  };

  const removeTag = (indexToRemove: number) => {
    onChange(tags.filter((_, idx) => idx !== indexToRemove));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (inputValue.trim()) {
        addTag(inputValue);
      }
    } else if (e.key === 'Backspace' && !inputValue && tags.length > 0) {
      removeTag(tags.length - 1);
    }
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div ref={containerRef} className="relative w-full">
      <div
        className={`flex flex-wrap items-center gap-1.5 p-2 bg-white border rounded-md min-h-[42px] transition-all ${
          disabled
            ? 'bg-slate-50 border-slate-200 cursor-not-allowed'
            : 'border-border focus-within:border-primary focus-within:ring-1 focus-within:ring-primary'
        }`}
      >
        {tags.map((tag, idx) => (
          <span
            key={tag + idx}
            className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-800 border border-slate-200"
          >
            <span>{tag}</span>
            {!disabled && (
              <button
                type="button"
                onClick={() => removeTag(idx)}
                className="text-slate-400 hover:text-rose-600 focus:outline-hidden p-0.5 rounded-full"
                aria-label={`Remove ${tag}`}
              >
                <X className="w-3 h-3" />
              </button>
            )}
          </span>
        ))}

        {!disabled && (
          <input
            type="text"
            value={inputValue}
            onChange={(e) => {
              setInputValue(e.target.value);
              setIsOpen(true);
            }}
            onFocus={() => setIsOpen(true)}
            onKeyDown={handleKeyDown}
            placeholder={tags.length === 0 ? placeholder : ''}
            className="flex-1 min-w-[120px] text-xs bg-transparent border-none outline-hidden text-slate-900 placeholder:text-slate-400"
          />
        )}
      </div>

      {isOpen && !disabled && (filteredSuggestions.length > 0 || inputValue.trim()) && (
        <div className="absolute left-0 right-0 z-30 mt-1 max-h-48 overflow-y-auto bg-white border border-border rounded-md shadow-lg py-1 text-xs">
          {inputValue.trim() && !suggestions.some((s) => s.toLowerCase() === trimmedInput) && (
            <button
              type="button"
              onClick={() => addTag(inputValue)}
              className="w-full text-left px-3 py-1.5 font-medium text-primary hover:bg-slate-50 flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              Create tag &ldquo;<strong>{inputValue.trim()}</strong>&rdquo;
            </button>
          )}

          {filteredSuggestions.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              onClick={() => addTag(suggestion)}
              className="w-full text-left px-3 py-1.5 text-slate-700 hover:bg-slate-100 flex items-center justify-between"
            >
              <span>{suggestion}</span>
              <span className="text-[10px] text-slate-400">Existing skill</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
