package com.work.converter.service;

import com.work.converter.exception.AudioConversionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;
import ws.schild.jave.info.MultimediaInfo;
import ws.schild.jave.progress.EncoderProgressListener;

import java.io.File;

@Service
@Slf4j
public class AudioConverter {

    private static class ProgressListener implements EncoderProgressListener {

        private final FluxSink<Double> sink;
        private long totalDuration = 0;
        private long lastUpdateTime = 0;
        private static final long UPDATE_INTERVAL = 500;

        public ProgressListener(long totalDuration, FluxSink<Double> sink) {
            this.totalDuration = totalDuration;
            this.sink = sink;
        }

        @Override
        public void sourceInfo(MultimediaInfo info) {
            if (info.getDuration() > 0) {
                this.totalDuration = info.getDuration();
            }
            log.debug("Informacion del archivo:");
            log.debug("Duracion: {}", formatDuration(info.getDuration()));
            if (info.getAudio() != null) {
                log.debug("Bitrate: {} bps", info.getAudio().getBitRate());
                log.debug("Canales: {}", info.getAudio().getChannels());
                log.debug("Sampling Rate: {} Hz", info.getAudio().getSamplingRate());
            }
        }

        @Override
        public void progress(int permil) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime >= UPDATE_INTERVAL || permil >= 1000) {
                lastUpdateTime = currentTime;

                double percentage = permil / 10.0;
                long processedDuration = (totalDuration * permil) / 1000;
                log.info("Progreso: {}{}({}/{})",
                        createProgressBar(percentage),
                        String.format(" %.1f%% ", percentage),
                        formatDuration(processedDuration),
                        formatDuration(totalDuration));

                sink.next(percentage);
            }
        }

        @Override
        public void message(String message) {
            if (StringUtils.hasText(message)) {
                log.info("Info: {}", message);
            }
        }

        private String createProgressBar(double percentage) {
            int barLength = 30;
            int filled = (int) (barLength * percentage / 100.0);
            StringBuilder bar = new StringBuilder("[");
            for (int i = 0; i < barLength; i++) {
                if (i < filled) {
                    bar.append("=");
                } else if (i == filled && percentage % (100.0 / barLength) > 0) {
                    bar.append("==");
                } else {
                    bar.append("-");
                }
            }
            bar.append("]");
            return bar.toString();
        }

        private String formatDuration(long durationMs) {
            if (durationMs <= 0) {
                return "00:00";
            }
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            seconds %= 60;
            minutes %= 60;
            if (hours > 0) {
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%02d:%02d", minutes, seconds);
            }
        }
    }

    public Flux<Double> converter(String inputFileName, String outputFileName) {
        File inputFile = new File(inputFileName);
        File outputFile = new File(outputFileName);

        return Flux.<Double>create(sink -> {
            if (!validateFile(inputFile)) {
                sink.error(new AudioConversionException(
                        AudioConversionException.ErrorCode.CONVERSION_FAILED,
                        "Archivo de entrada inválido: " + inputFile.getName()));
                return;
            }
            try {
                Encoder encoder = new Encoder();
                MultimediaObject inputObject = new MultimediaObject(inputFile);
                long duration = getDuration(inputObject);
                String outputExtension = getFileExtension(outputFileName).toLowerCase();
                AudioAttributes audioAttributes = createAudioAttributes(outputExtension);
                EncodingAttributes encodingAttributes = createEncodingAttributes(outputExtension, audioAttributes);

                log.debug("Iniciando conversion de audio...");
                log.info("Entrada: {}", inputFileName);
                log.info("Salida: {}", outputFileName);
                log.info("Formato: {}", outputExtension.toUpperCase());

                long startTime = System.currentTimeMillis();
                encoder.encode(inputObject, outputFile, encodingAttributes,
                        new ProgressListener(duration, sink));
                long endTime = System.currentTimeMillis();

                log.debug("Conversion completada exitosamente");
                log.debug("Tiempo transcurrido: {}", formatTime(endTime - startTime));
                log.debug("Archivo original: {}", formatFileSize(inputFile.length()));
                log.debug("Archivo convertido: {}", formatFileSize(outputFile.length()));

                if (inputFile.length() > 0) {
                    displayRatio((double) outputFile.length() / inputFile.length());
                }

                sink.complete();
            } catch (EncoderException e) {
                log.error("Error durante la conversion: {}", e.getMessage());
                sink.error(new AudioConversionException(
                        AudioConversionException.ErrorCode.CONVERSION_FAILED, e.getMessage(), e));
            } catch (IllegalArgumentException e) {
                log.error("Error inesperado: {}", e.getMessage());
                sink.error(new AudioConversionException(
                        AudioConversionException.ErrorCode.FILE_PROCESSING_ERROR, e.getMessage(), e));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private boolean validateFile(File inputFile) {
        if (!inputFile.exists()) {
            log.error("Error: El archivo de entrada '{}' no existe.", inputFile.getName());
            return false;
        }
        if (!inputFile.isFile()) {
            log.error("Error: '{}' no es un archivo válido.", inputFile.getName());
            return false;
        }
        return true;
    }

    private void displayRatio(double ratio) {
        if (ratio < 1.0) {
            double reduction = (1 - ratio) * 100;
            log.debug("Ratio de compresion: {} ({}", String.format("%.2f", ratio),
                    String.format("%.1f%% reduccion)", reduction));
        } else if (ratio > 1.0) {
            double increase = (ratio - 1) * 100;
            log.debug("Ratio de compresion: {} ({}", String.format("%.2f", ratio),
                    String.format("%.1f%% incremento)", increase));
        } else {
            log.debug("Cambio de tamaño: 0.0% (sin cambio)");
        }
    }

    private long getDuration(MultimediaObject inputObject) {
        long duration = 0;
        try {
            MultimediaInfo inputInfo = inputObject.getInfo();
            duration = inputInfo.getDuration();
        } catch (EncoderException e) {
            log.info("No se pudo obtener la duracion del archivo, continuando...");
        }
        return duration;
    }

    private AudioAttributes createAudioAttributes(String format) {
        AudioAttributes audioAttributes = new AudioAttributes();
        audioAttributes.setChannels(2);
        audioAttributes.setSamplingRate(44100);
        switch (format) {
            case "mp3" -> {
                audioAttributes.setCodec("libmp3lame");
                audioAttributes.setBitRate(128000);
            }
            case "wav" -> {
                audioAttributes.setCodec("pcm_s16le");
                audioAttributes.setBitRate(null);
            }
            case "ogg" -> {
                audioAttributes.setCodec("libvorbis");
                audioAttributes.setBitRate(128000);
            }
            case "aac" -> {
                audioAttributes.setCodec("aac");
                audioAttributes.setBitRate(128000);
            }
            case "flac" -> {
                audioAttributes.setCodec("flac");
                audioAttributes.setBitRate(null);
            }
            case "wma" -> {
                audioAttributes.setCodec("wmav2");
                audioAttributes.setBitRate(128000);
            }
            default -> {
                log.info("Formato no reconocido, usando configuración por defecto (MP3)");
                audioAttributes.setCodec("libmp3lame");
                audioAttributes.setBitRate(128000);
            }
        }
        return audioAttributes;
    }

    private EncodingAttributes createEncodingAttributes(String outputExtension, AudioAttributes audioAttributes) {
        EncodingAttributes encodingAttributes = new EncodingAttributes();
        encodingAttributes.setOutputFormat(outputExtension);
        encodingAttributes.setAudioAttributes(audioAttributes);
        return encodingAttributes;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "mp3";
        }
        return fileName.substring(lastDotIndex + 1);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
