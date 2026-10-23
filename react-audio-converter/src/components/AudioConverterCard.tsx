import { useEffect, useMemo, useState } from "react";
import { SUPPORTED_FORMATS, type AudioFormat } from "../api/audioApi";
import { useAudioConverter } from "../hooks/useAudioConverter";
import { FileDropZone } from "./FileDropZone";
import { FormatPicker } from "./FormatPicker";
import { ProgressPanel } from "./ProgressPanel";
import { ErrorPanel, SuccessPanel } from "./ResultPanel";

function getSourceFormat(file: File | null): AudioFormat | null {
  if (!file) return null;
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  return (SUPPORTED_FORMATS as readonly string[]).includes(extension)
    ? (extension as AudioFormat)
    : null;
}

export function AudioConverterCard() {
  const [file, setFile] = useState<File | null>(null);
  const [format, setFormat] = useState<AudioFormat | null>(null);
  const { status, progress, resultFilename, errorMessage, errorCode, convert, cancel, reset } =
    useAudioConverter();

  const isConverting = status === "converting";
  const canConvert = Boolean(file) && Boolean(format) && !isConverting;
  const sourceFormat = useMemo(() => getSourceFormat(file), [file]);

  useEffect(() => {
    if (format && format === sourceFormat) {
      setFormat(null);
    }
  }, [sourceFormat, format]);

  const handleReset = () => {
    setFile(null);
    setFormat(null);
    reset();
  };

  const handleConvert = () => {
    if (!file || !format) return;
    void convert(file, format);
  };

  return (
    <div className="converter-card">
      {status === "success" && resultFilename ? (
        <SuccessPanel filename={resultFilename} onReset={handleReset} />
      ) : status === "error" ? (
        <ErrorPanel message={errorMessage ?? "Error desconocido"} errorCode={errorCode} onReset={handleReset} />
      ) : (
        <>
          <section className="converter-card__section">
            <h2 className="converter-card__step">1. Elige tu archivo</h2>
            <FileDropZone file={file} onFileSelected={setFile} disabled={isConverting} />
          </section>

          <section className="converter-card__section">
            <h2 className="converter-card__step">2. Elige el formato de salida</h2>
            <FormatPicker
              selected={format}
              onSelect={setFormat}
              disabled={isConverting}
              sourceFormat={sourceFormat}
            />
          </section>

          {isConverting ? (
            <ProgressPanel progress={progress} onCancel={cancel} />
          ) : (
            <button type="button" className="btn btn--primary btn--block" disabled={!canConvert} onClick={handleConvert}>
              Convertir audio
            </button>
          )}
        </>
      )}
    </div>
  );
}
