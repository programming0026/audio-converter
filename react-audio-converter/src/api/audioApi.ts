export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const SUPPORTED_FORMATS = ["mp3", "wav", "ogg", "aac", "flac", "wma"] as const;
export type AudioFormat = (typeof SUPPORTED_FORMATS)[number];

export interface ErrorResponseBody {
  errorCode: string;
  message: string;
  timestamp: string;
}

export class ApiError extends Error {
  errorCode: string;
  status: number;

  constructor(status: number, body: ErrorResponseBody) {
    super(body.message);
    this.name = "ApiError";
    this.errorCode = body.errorCode;
    this.status = status;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ErrorResponseBody;
    return new ApiError(response.status, body);
  } catch {
    return new ApiError(response.status, {
      errorCode: "UNKNOWN_ERROR",
      message: response.statusText || "Ocurrió un error inesperado",
      timestamp: new Date().toISOString(),
    });
  }
}

export interface ConversionEvents {
  onProgress?: (progress: number) => void;
  onComplete?: (filename: string, downloadToken: string) => void;
  signal?: AbortSignal;
}

/**
 * Consumes POST /api/audio/convert/stream. Uses a manual fetch + ReadableStream
 * reader because the native EventSource API cannot send a POST with a multipart body.
 */
export async function convertAudioWithProgress(
  file: File,
  format: AudioFormat,
  events: ConversionEvents
): Promise<void> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(
    `${API_BASE_URL}/api/audio/convert/stream?format=${encodeURIComponent(format)}`,
    { method: "POST", body: formData, signal: events.signal }
  );

  if (!response.ok || !response.body) {
    throw await toApiError(response);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const rawEvents = buffer.split("\n\n");
    buffer = rawEvents.pop() ?? "";

    for (const rawEvent of rawEvents) {
      let eventName = "message";
      let data = "";
      for (const line of rawEvent.split("\n")) {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      if (!data) continue;

      const parsed = JSON.parse(data) as { progress?: number; filename?: string; downloadToken?: string };
      if (eventName === "progress" && typeof parsed.progress === "number") {
        events.onProgress?.(parsed.progress);
      } else if (eventName === "complete" && parsed.filename && parsed.downloadToken) {
        events.onComplete?.(parsed.filename, parsed.downloadToken);
      }
    }
  }
}

export async function downloadConvertedFile(
  token: string
): Promise<{ blob: Blob; filename: string | null }> {
  const response = await fetch(`${API_BASE_URL}/api/audio/download/${token}`);
  if (!response.ok) throw await toApiError(response);

  const disposition = response.headers.get("content-disposition") ?? "";
  const match = disposition.match(/filename="(.+)"/);
  const blob = await response.blob();
  return { blob, filename: match ? match[1] : null };
}

export function triggerBrowserDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
