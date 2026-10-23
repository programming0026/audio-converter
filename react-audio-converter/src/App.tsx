import { AudioConverterCard } from "./components/AudioConverterCard";
import "./App.css";

function App() {
  return (
    <div className="page">
      <div className="page__glow page__glow--one" />
      <div className="page__glow page__glow--two" />

      <header className="page__header">
        <div className="brand">
          <span className="brand__icon">🎧</span>
          <span className="brand__name">Audio Converter</span>
        </div>
        <p className="page__tagline">
          Convierte tus archivos de audio entre MP3, WAV, OGG, AAC, FLAC y WMA con seguimiento de
          progreso en tiempo real.
        </p>
      </header>

      <main className="page__main">
        <AudioConverterCard />
      </main>

      <footer className="page__footer">
        <p>Audio Converter &bull; Copyright &copy; {new Date().getFullYear()}</p>
      </footer>
    </div>
  );
}

export default App;
