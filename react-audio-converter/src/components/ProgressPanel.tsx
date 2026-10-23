interface ProgressPanelProps {
  progress: number;
  onCancel: () => void;
}

export function ProgressPanel({ progress, onCancel }: ProgressPanelProps) {
  return (
    <div className="progress-panel">
      <div className="progress-panel__header">
        <span className="progress-panel__label">Convirtiendo tu archivo…</span>
        <span className="progress-panel__value">{Math.round(progress)}%</span>
      </div>
      <div className="progress-bar">
        <div className="progress-bar__fill" style={{ width: `${Math.max(progress, 4)}%` }} />
      </div>
      <button type="button" className="btn btn--ghost" onClick={onCancel}>
        Cancelar
      </button>
    </div>
  );
}
