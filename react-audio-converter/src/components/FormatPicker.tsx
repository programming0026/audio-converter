import { SUPPORTED_FORMATS, type AudioFormat } from "../api/audioApi";

interface FormatPickerProps {
  selected: AudioFormat | null;
  onSelect: (format: AudioFormat) => void;
  disabled?: boolean;
  sourceFormat?: AudioFormat | null;
}

const FORMAT_LABELS: Record<AudioFormat, string> = {
  mp3: "MP3",
  wav: "WAV",
  ogg: "OGG",
  aac: "AAC",
  flac: "FLAC",
  wma: "WMA",
};

export function FormatPicker({ selected, onSelect, disabled, sourceFormat }: FormatPickerProps) {
  return (
    <div className="format-picker">
      {SUPPORTED_FORMATS.map((format) => {
        const isSameAsSource = format === sourceFormat;
        return (
          <button
            key={format}
            type="button"
            className={`format-chip ${selected === format ? "format-chip--selected" : ""}`}
            onClick={() => onSelect(format)}
            disabled={disabled || isSameAsSource}
            aria-pressed={selected === format}
            title={isSameAsSource ? "Tu archivo ya está en este formato" : undefined}
          >
            {FORMAT_LABELS[format]}
          </button>
        );
      })}
    </div>
  );
}
