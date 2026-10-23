import { useCallback, useId, useRef, useState } from "react";
import type { DragEvent } from "react";

interface FileDropZoneProps {
  file: File | null;
  onFileSelected: (file: File | null) => void;
  disabled?: boolean;
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value.toFixed(1)} ${units[unitIndex]}`;
}

export function FileDropZone({ file, onFileSelected, disabled }: FileDropZoneProps) {
  const [isDragging, setIsDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const inputId = useId();

  const handleDrop = useCallback(
    (event: DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setIsDragging(false);
      if (disabled) return;
      const droppedFile = event.dataTransfer.files?.[0];
      if (droppedFile) onFileSelected(droppedFile);
    },
    [disabled, onFileSelected]
  );

  return (
    <div
      className={`drop-zone ${isDragging ? "drop-zone--active" : ""} ${file ? "drop-zone--filled" : ""}`}
      onDragOver={(event) => {
        event.preventDefault();
        if (!disabled) setIsDragging(true);
      }}
      onDragLeave={() => setIsDragging(false)}
      onDrop={handleDrop}
      onClick={() => !disabled && inputRef.current?.click()}
      role="button"
      tabIndex={0}
      aria-label="Selecciona un archivo de audio"
      onKeyDown={(event) => {
        if ((event.key === "Enter" || event.key === " ") && !disabled) inputRef.current?.click();
      }}
    >
      <input
        ref={inputRef}
        id={inputId}
        type="file"
        accept="audio/*"
        className="drop-zone__input"
        disabled={disabled}
        onChange={(event) => onFileSelected(event.target.files?.[0] ?? null)}
      />

      {file ? (
        <div className="drop-zone__file">
          <div className="drop-zone__file-icon">🎵</div>
          <div className="drop-zone__file-info">
            <p className="drop-zone__file-name">{file.name}</p>
            <p className="drop-zone__file-size">{formatBytes(file.size)}</p>
          </div>
          {!disabled && (
            <button
              type="button"
              className="drop-zone__clear"
              onClick={(event) => {
                event.stopPropagation();
                onFileSelected(null);
                if (inputRef.current) inputRef.current.value = "";
              }}
              aria-label="Quitar archivo"
            >
              ✕
            </button>
          )}
        </div>
      ) : (
        <div className="drop-zone__placeholder">
          <div className="drop-zone__icon">⬆️</div>
          <p className="drop-zone__title">Arrastra tu archivo de audio aquí</p>
          <p className="drop-zone__subtitle">o haz clic para explorar tus archivos</p>
        </div>
      )}
    </div>
  );
}
