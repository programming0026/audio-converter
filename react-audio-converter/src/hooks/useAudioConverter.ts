import { useCallback, useRef, useState } from "react";
import {
  ApiError,
  type AudioFormat,
  convertAudioWithProgress,
  downloadConvertedFile,
  triggerBrowserDownload,
} from "../api/audioApi";

export type ConversionStatus = "idle" | "converting" | "success" | "error";

interface ConverterState {
  status: ConversionStatus;
  progress: number;
  resultFilename: string | null;
  errorMessage: string | null;
  errorCode: string | null;
}

const initialState: ConverterState = {
  status: "idle",
  progress: 0,
  resultFilename: null,
  errorMessage: null,
  errorCode: null,
};

export function useAudioConverter() {
  const [state, setState] = useState<ConverterState>(initialState);
  const abortRef = useRef<AbortController | null>(null);

  const reset = useCallback(() => {
    setState(initialState);
  }, []);

  const convert = useCallback(async (file: File, format: AudioFormat) => {
    const controller = new AbortController();
    abortRef.current = controller;

    setState({ status: "converting", progress: 0, resultFilename: null, errorMessage: null, errorCode: null });

    try {
      await convertAudioWithProgress(file, format, {
        signal: controller.signal,
        onProgress: (progress) => {
          setState((prev) => (prev.status === "converting" ? { ...prev, progress } : prev));
        },
        onComplete: async (filename, downloadToken) => {
          const { blob, filename: downloadedFilename } = await downloadConvertedFile(downloadToken);
          triggerBrowserDownload(blob, filename || downloadedFilename || "converted-audio");
          setState((prev) => ({ ...prev, status: "success", progress: 100, resultFilename: filename }));
        },
      });
    } catch (error) {
      if (controller.signal.aborted) {
        setState(initialState);
        return;
      }
      if (error instanceof ApiError) {
        setState({
          status: "error",
          progress: 0,
          resultFilename: null,
          errorMessage: error.message,
          errorCode: error.errorCode,
        });
      } else {
        setState({
          status: "error",
          progress: 0,
          resultFilename: null,
          errorMessage: error instanceof Error ? error.message : "Error de conexión con el servidor",
          errorCode: "NETWORK_ERROR",
        });
      }
    }
  }, []);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { ...state, convert, cancel, reset };
}
