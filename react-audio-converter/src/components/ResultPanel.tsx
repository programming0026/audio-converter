interface SuccessPanelProps {
  filename: string;
  onReset: () => void;
}

export function SuccessPanel({ filename, onReset }: SuccessPanelProps) {
  return (
    <div className="result-panel result-panel--success">
      <div className="result-panel__icon">✅</div>
      <p className="result-panel__title">¡Conversión completada!</p>
      <p className="result-panel__detail">
        <span className="result-panel__filename">{filename}</span> se descargó automáticamente.
      </p>
      <button type="button" className="btn btn--primary" onClick={onReset}>
        Convertir otro archivo
      </button>
    </div>
  );
}

interface ErrorPanelProps {
  message: string;
  errorCode: string | null;
  onReset: () => void;
}

export function ErrorPanel({ message, errorCode, onReset }: ErrorPanelProps) {
  return (
    <div className="result-panel result-panel--error">
      <div className="result-panel__icon">⚠️</div>
      <p className="result-panel__title">No se pudo convertir el archivo</p>
      <p className="result-panel__detail">{message}</p>
      {errorCode && <p className="result-panel__code">Código: {errorCode}</p>}
      <button type="button" className="btn btn--primary" onClick={onReset}>
        Intentar de nuevo
      </button>
    </div>
  );
}
